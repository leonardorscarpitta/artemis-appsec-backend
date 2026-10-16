"""Hadolint: Dockerfile best-practice and security checks."""
import json
import os
from pathlib import Path

from .base import ToolResult, ToolError, finding, location, relative, run, severity, summary

MAX_FILES = 50


def _dockerfiles(src: Path) -> list[Path]:
    found = []
    for dirpath, dirs, files in os.walk(src, followlinks=False):
        dirs[:] = [d for d in dirs if d not in ("node_modules", ".git", "vendor")]
        for name in files:
            lower = name.lower()
            if lower.startswith(("dockerfile", "containerfile")) or lower.endswith(".dockerfile"):
                path = Path(dirpath) / name
                if not path.is_symlink():
                    found.append(path)
                    if len(found) >= MAX_FILES:
                        return found
    return found


def scan(src: Path, out: Path, timeout: int) -> ToolResult:
    files = _dockerfiles(src)
    if not files:
        return ToolResult([], "[Hadolint] no Dockerfile found")
    result = run(["hadolint", "--format", "json", "--no-fail", *map(str, files)], timeout, out)
    try:
        items = json.loads(result.stdout or "[]")
    except json.JSONDecodeError:
        raise ToolError("Could not parse Hadolint output") from None
    findings = [
        finding("config", item.get("code", "hadolint"), item.get("message", ""), item.get("message", ""),
                severity(item.get("level"), "low"),
                location=location(relative(item.get("file"), src), item.get("line")))
        for item in items
    ]
    return ToolResult(findings, summary("Hadolint", findings) + f" in {len(files)} Dockerfile(s)")
