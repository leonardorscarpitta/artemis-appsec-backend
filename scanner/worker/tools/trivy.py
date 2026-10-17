"""Trivy: dependency vulnerabilities, licenses and misconfigurations (IaC and Dockerfiles)."""
import ipaddress
import re
import socket
from pathlib import Path

from .base import ToolError, ToolResult, finding, location, read_json, run, severity, summary

ECOSYSTEMS = {
    "npm": "npm", "yarn": "npm", "pnpm": "npm", "node-pkg": "npm", "bun": "npm",
    "pom": "maven", "jar": "maven", "gradle": "maven", "sbt": "maven",
    "pip": "pypi", "pipenv": "pypi", "poetry": "pypi", "uv": "pypi", "python-pkg": "pypi",
    "gomod": "go", "gobinary": "go", "cargo": "cargo", "composer": "composer", "nuget": "nuget",
    "bundler": "rubygems", "gemspec": "rubygems",
}
COMMON = ["--format", "json", "--quiet", "--skip-version-check", "--timeout", "10m"]
# Single-threaded filesystem scans keep memory within the runner limit (`trivy config` has no such flag).
# --offline-scan: never query package registries (e.g. Maven Central for parent POMs) — they rate-limit (429)
# and would learn the repository's dependencies. The vulnerability database is still updated.
FS_ONLY = ["--parallel", "1", "--offline-scan"]
# Compressed size; layers are unpacked in the 1 GB in-memory workspace.
MAX_IMAGE_SIZE = "700MB"


def _cvss(vuln: dict) -> tuple[float | None, str | None]:
    sources = vuln.get("CVSS") or {}
    for source in ("nvd", "ghsa", "redhat", *sources.keys()):
        entry = sources.get(source) or {}
        if entry.get("V3Score"):
            return float(entry["V3Score"]), entry.get("V3Vector")
    return None, None


def vulnerabilities(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "trivy-vuln.json"
    run(["trivy", "fs", "--scanners", "vuln", *COMMON, *FS_ONLY, "--output", str(report), str(src)], timeout, out)
    findings = _vulnerability_findings(report)
    return ToolResult(findings, summary("Trivy", findings))


def image(reference: str, out: Path, timeout: int) -> ToolResult:
    """Scans a published image pulled straight from its registry: nothing is built or run, no container socket."""
    require_public_registry(reference)
    report = out / "trivy-image.json"
    # Layers are unpacked under `out` (the in-memory workspace), never in the read-only image or /tmp.
    run(["trivy", "image", "--image-src", "remote", "--scanners", "vuln", "--max-image-size", MAX_IMAGE_SIZE,
         *COMMON, *FS_ONLY, "--output", str(report), "--", reference], timeout, out, env={"TMPDIR": str(out)})
    findings = _vulnerability_findings(report)
    return ToolResult(findings, summary("Trivy", findings))


def require_public_registry(reference: str) -> None:
    first, _, rest = reference.partition("/")
    registry = first if rest and ("." in first or ":" in first) else "registry-1.docker.io"
    host, _, port = registry.partition(":")
    if host == "localhost" or (port and port != "443"):
        raise ToolError("Only images from public registries on the default HTTPS port can be scanned")
    try:
        infos = socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
    except socket.gaierror:
        raise ToolError(f"Registry {host} could not be resolved") from None
    if any(not ipaddress.ip_address(info[4][0]).is_global for info in infos):
        raise ToolError(f"Registry {host} is not a public address")


def _vulnerability_findings(report: Path) -> list[dict]:
    findings = []
    for result in (read_json(report, {}) or {}).get("Results") or []:
        ecosystem = ECOSYSTEMS.get(result.get("Type", ""), result.get("Type") or "other")
        for vuln in result.get("Vulnerabilities") or []:
            vid = vuln.get("VulnerabilityID", "")
            score, vector = _cvss(vuln)
            cwes = vuln.get("CweIDs") or []
            findings.append(finding(
                "vulnerability", vid, vuln.get("Title") or vid, vuln.get("Description") or vuln.get("Title") or vid,
                severity(vuln.get("Severity")),
                cve=vid if vid.startswith("CVE-") else None,
                cvssScore=score, cvssVector=vector, cwe=cwes[0] if cwes else None,
                package={"name": vuln.get("PkgName"), "version": vuln.get("InstalledVersion") or "unknown",
                         "fixedVersion": vuln.get("FixedVersion") or None, "ecosystem": ecosystem},
                location=location(result.get("Target"), None),
            ))
    return findings


def licenses(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "trivy-license.json"
    run(["trivy", "fs", "--scanners", "license", *COMMON, *FS_ONLY, "--output", str(report), str(src)], timeout, out)
    findings = []
    for result in (read_json(report, {}) or {}).get("Results") or []:
        for lic in result.get("Licenses") or []:
            category = (lic.get("Category") or "").lower()
            # Permissive/notice licenses are not findings; only those that usually need a decision.
            if category not in ("forbidden", "restricted", "reciprocal"):
                continue
            name = lic.get("Name") or "unknown"
            pkg = lic.get("PkgName") or lic.get("FilePath") or "unknown"
            findings.append(finding(
                "license", f"license:{name}", f"{name} license in {pkg}",
                f"{pkg} is distributed under {name} ({category}). Check it against your license policy.",
                severity(lic.get("Severity")),
                package={"name": pkg, "version": "unknown", "fixedVersion": None, "ecosystem": "license"},
                location=location(lic.get("FilePath"), None),
            ))
    return ToolResult(findings, summary("Trivy", findings))


# Terraform only treats sources starting with ./ or ../ as local; anything else (git::, URLs, registry, GitHub
# shorthand, …) is downloaded.
_REMOTE_SOURCE = re.compile(r'(?P<head>"?source"?\s*[=:]\s*")(?P<value>(?!\.\.?/)[^"\n]+)(?P<tail>")')


def disable_remote_modules(src: Path) -> int:
    """Points remote Terraform module sources at a missing local path in the scanned copy.

    Trivy downloads remote modules (git, HTTP, S3, registry) while evaluating Terraform and ignores --offline-scan
    and proxy settings for it, so a repository could make the runner request internal URLs. Findings in downloaded
    modules are excluded anyway, so nothing is lost.
    """
    changed = 0
    for path in [*src.rglob("*.tf"), *src.rglob("*.tf.json")]:
        if path.is_symlink() or not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        rewritten, count = _REMOTE_SOURCE.subn(r"\g<head>./.artemis-remote-module-not-fetched\g<tail>", text)
        if count:
            path.write_text(rewritten, encoding="utf-8")
            changed += count
    return changed


def _misconfig(src: Path, out: Path, timeout: int, scanners: str, name: str) -> ToolResult:
    report = out / f"trivy-{name}.json"
    if "terraform" in scanners:
        disable_remote_modules(src)
    run(["trivy", "config", "--misconfig-scanners", scanners, "--skip-check-update", "--tf-exclude-downloaded-modules",
         *COMMON, "--output", str(report), str(src)], timeout, out)
    findings = []
    for result in (read_json(report, {}) or {}).get("Results") or []:
        for item in result.get("Misconfigurations") or []:
            if item.get("Status") == "PASS":
                continue
            rule = item.get("AVDID") or item.get("ID") or "misconfiguration"
            detail = " ".join(filter(None, [item.get("Description"), item.get("Resolution")]))
            findings.append(finding(
                "config", rule, item.get("Title") or rule, detail, severity(item.get("Severity")),
                location=location(result.get("Target"), (item.get("CauseMetadata") or {}).get("StartLine")),
            ))
    return ToolResult(findings, summary("Trivy", findings))


def iac(src: Path, out: Path, timeout: int) -> ToolResult:
    return _misconfig(src, out, timeout, "terraform,cloudformation,kubernetes,helm,azure-arm", "iac")


def dockerfile(src: Path, out: Path, timeout: int) -> ToolResult:
    return _misconfig(src, out, timeout, "dockerfile", "dockerfile")
