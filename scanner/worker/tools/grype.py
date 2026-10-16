"""Grype: dependency vulnerabilities (alternative to Trivy for the SCA stage)."""
from pathlib import Path

from .base import ToolResult, finding, location, read_json, run, severity, summary


def vulnerabilities(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "grype.json"
    run(["grype", f"dir:{src}", "-o", "json", "--file", str(report), "-q"], timeout, out)
    findings = []
    for match in (read_json(report, {}) or {}).get("matches") or []:
        vuln = match.get("vulnerability", {})
        artifact = match.get("artifact", {})
        vid = vuln.get("id", "")
        score, vector = None, None
        for entry in vuln.get("cvss") or []:
            if str(entry.get("version", "")).startswith("3"):
                score, vector = entry.get("metrics", {}).get("baseScore"), entry.get("vector")
                break
        fixes = (vuln.get("fix") or {}).get("versions") or []
        locations = artifact.get("locations") or [{}]
        findings.append(finding(
            "vulnerability", vid, vid, vuln.get("description") or vid, severity(vuln.get("severity")),
            cve=vid if vid.startswith("CVE-") else None,
            cvssScore=score, cvssVector=vector,
            package={"name": artifact.get("name"), "version": artifact.get("version") or "unknown",
                     "fixedVersion": fixes[0] if fixes else None, "ecosystem": artifact.get("type") or "other"},
            location=location(str(locations[0].get("path", "")).lstrip("/") or None, None),
        ))
    return ToolResult(findings, summary("Grype", findings))
