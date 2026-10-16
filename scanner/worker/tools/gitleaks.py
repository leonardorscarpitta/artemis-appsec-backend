"""Secret scanning. Secrets are redacted by Gitleaks and the raw values are never forwarded."""
from pathlib import Path

from .base import ToolResult, finding, location, read_json, relative, run, summary


def scan(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "gitleaks.json"
    run(
        ["gitleaks", "dir", str(src), "--redact", "--no-banner", "--no-color", "--log-level", "error",
         "--report-format", "json", "--report-path", str(report), "--exit-code", "0", "--max-target-megabytes", "5"],
        timeout, out,
    )
    findings = []
    for leak in read_json(report, []) or []:
        rule = leak.get("RuleID") or "secret"
        description = leak.get("Description") or rule
        findings.append(finding(
            "secret", rule, f"Secret in source code: {description}",
            f"A credential matching the '{rule}' rule is committed to the repository. Revoke and rotate it, "
            "then remove it from the code and the git history. The value itself is not stored by Artemis.",
            "high",
            cwe="CWE-798",
            location=location(relative(leak.get("File"), src), leak.get("StartLine")),
        ))
    return ToolResult(findings, summary("Gitleaks", findings))
