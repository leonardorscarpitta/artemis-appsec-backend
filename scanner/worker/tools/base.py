"""Shared helpers for tool adapters."""
import json
import os
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from ..sanitize import clip

SEVERITIES = ("critical", "high", "medium", "low")
MAX_FINDINGS = 5000


class ToolError(Exception):
    """A tool failed to run; the message is safe to put in the stage log."""


@dataclass
class ToolResult:
    findings: list[dict] = field(default_factory=list)
    log: str = ""


def tool_env(extra: dict | None = None) -> dict:
    base = {
        key: os.environ[key]
        for key in (
            "PATH", "HOME", "XDG_CACHE_HOME", "TRIVY_CACHE_DIR", "TRIVY_NO_PROGRESS", "GRYPE_DB_CACHE_DIR",
            "GRYPE_CHECK_FOR_APP_UPDATE", "SYFT_CHECK_FOR_APP_UPDATE", "SEMGREP_SEND_METRICS",
            "SEMGREP_ENABLE_VERSION_CHECK", "SSL_CERT_FILE",
        )
        if key in os.environ
    }
    base.setdefault("PATH", "/usr/local/bin:/usr/bin:/bin")
    if extra:
        base.update(extra)
    return base


def run(command: list[str], timeout: int, cwd: Path, env: dict | None = None, ok_codes=(0,)) -> subprocess.CompletedProcess:
    try:
        result = subprocess.run(command, cwd=cwd, env=tool_env(env), capture_output=True, text=True, timeout=timeout)
    except FileNotFoundError:
        raise ToolError(f"{command[0]} is not installed in this runner") from None
    except subprocess.TimeoutExpired:
        raise ToolError(f"{command[0]} timed out after {timeout} seconds") from None
    if result.returncode not in ok_codes:
        tail = "\n".join((result.stderr or result.stdout or "").strip().splitlines()[-15:])
        raise ToolError(f"{command[0]} exited with code {result.returncode}\n{tail}")
    return result


def read_json(path: Path, default):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except FileNotFoundError:
        return default
    except json.JSONDecodeError:
        raise ToolError(f"Could not parse {path.name}") from None


def severity(value: str | None, default: str = "low") -> str:
    value = (value or "").lower()
    mapping = {"error": "high", "warning": "medium", "info": "low", "style": "low", "negligible": "low", "unknown": default}
    value = mapping.get(value, value)
    return value if value in SEVERITIES else default


def relative(path: str | None, root: Path) -> str | None:
    if not path:
        return None
    try:
        return str(Path(path).resolve().relative_to(root.resolve()))
    except ValueError:
        return str(path).lstrip("/")


def finding(kind: str, rule_id: str, title: str, description: str, sev: str, **extra) -> dict:
    data = {
        "kind": kind,
        "ruleId": clip(rule_id, 200),
        "title": clip(title or rule_id, 255),
        "description": clip(description or title or rule_id, 2000),
        "severity": sev,
        "cve": None,
        "cvssScore": None,
        "cvssVector": None,
        "cwe": None,
        "package": None,
        "location": None,
    }
    data.update({k: v for k, v in extra.items() if v is not None})
    return data


def location(path: str | None, line) -> dict | None:
    if not path:
        return None
    return {"path": clip(path, 500), "line": int(line) if isinstance(line, int) or str(line).isdigit() else None}


def summary(tool: str, findings: list[dict]) -> str:
    counts = {s: 0 for s in SEVERITIES}
    for f in findings:
        counts[f["severity"]] += 1
    breakdown = ", ".join(f"{counts[s]} {s}" for s in SEVERITIES if counts[s])
    return f"[{tool}] {len(findings)} finding(s)" + (f": {breakdown}" if breakdown else "")
