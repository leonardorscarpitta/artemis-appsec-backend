"""Trivy: dependency vulnerabilities, licenses and misconfigurations (IaC and Dockerfiles)."""
from pathlib import Path

from .base import ToolResult, finding, location, read_json, run, severity, summary

ECOSYSTEMS = {
    "npm": "npm", "yarn": "npm", "pnpm": "npm", "node-pkg": "npm", "bun": "npm",
    "pom": "maven", "jar": "maven", "gradle": "maven", "sbt": "maven",
    "pip": "pypi", "pipenv": "pypi", "poetry": "pypi", "uv": "pypi", "python-pkg": "pypi",
    "gomod": "go", "gobinary": "go", "cargo": "cargo", "composer": "composer", "nuget": "nuget",
    "bundler": "rubygems", "gemspec": "rubygems",
}
COMMON = ["--format", "json", "--quiet", "--skip-version-check", "--timeout", "10m"]
# Single-threaded filesystem scans keep memory within the runner limit (`trivy config` has no such flag).
FS_ONLY = ["--parallel", "1"]


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
    return ToolResult(findings, summary("Trivy", findings))


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


def _misconfig(src: Path, out: Path, timeout: int, scanners: str, name: str) -> ToolResult:
    report = out / f"trivy-{name}.json"
    run(["trivy", "config", "--misconfig-scanners", scanners, *COMMON, "--output", str(report), str(src)], timeout, out)
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
