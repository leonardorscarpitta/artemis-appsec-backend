"""SAST with the Semgrep CE engine and Artemis's own rules (never the Semgrep registry)."""
import os
from pathlib import Path

from .base import ToolResult, finding, location, read_json, relative, run, severity, summary

RULES_DIR = os.environ.get("ARTEMIS_RULES_DIR", "/opt/artemis/rules")


def scan(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "semgrep.json"
    run(
        ["semgrep", "scan", "--config", RULES_DIR, "--json", "--output", str(report), "--metrics=off",
         "--disable-version-check", "--quiet", "--timeout", "30", "--max-target-bytes", "1000000",
         "--jobs", "2", str(src)],
        timeout, out, ok_codes=(0, 1),
    )
    data = read_json(report, {}) or {}
    findings = []
    for result in data.get("results", []):
        extra = result.get("extra", {})
        metadata = extra.get("metadata", {})
        rule = (result.get("check_id") or "rule").rsplit(".", 1)[-1]
        cwe = metadata.get("cwe")
        if isinstance(cwe, list):
            cwe = cwe[0] if cwe else None
        if isinstance(cwe, str):
            cwe = cwe.split(":")[0].strip()
        # Code snippets ("lines") are deliberately not forwarded: only rule, file and line are stored.
        findings.append(finding(
            "code", rule, metadata.get("title") or extra.get("message", rule).split(".")[0],
            extra.get("message", ""),
            severity(metadata.get("artemis-severity") or extra.get("severity"), "medium"),
            cwe=cwe,
            location=location(relative(result.get("path"), src), result.get("start", {}).get("line")),
        ))
    errors = [e for e in data.get("errors", []) if e.get("level") == "error"]
    log = summary("Semgrep", findings)
    if errors:
        log += f"\n[Semgrep] {len(errors)} file(s) could not be analyzed"
    return ToolResult(findings, log)
