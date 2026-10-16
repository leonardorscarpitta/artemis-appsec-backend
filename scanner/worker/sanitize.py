"""Redaction and size limits applied to everything the runner reports."""
import re

MAX_LOG_BYTES = 64 * 1024

_SECRET_PATTERNS = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", re.S),
    re.compile(r"\b(AKIA|ASIA)[0-9A-Z]{16}\b"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{30,}\b"),
    re.compile(r"\bglpat-[A-Za-z0-9_-]{20,}\b"),
    re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b"),
    re.compile(r"\bsk_(live|test)_[A-Za-z0-9]{16,}\b"),
    re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
    re.compile(r"(?i)\b(bearer|token|password|passwd|secret|api[_-]?key)\b(\s*[:=]\s*|\s+)[^\s'\"]{6,}"),
    re.compile(r"(?i)https?://[^/\s:@]+:[^/\s@]+@"),
]


def redact(text: str) -> str:
    for pattern in _SECRET_PATTERNS:
        text = pattern.sub("[REDACTED]", text)
    return text


def clip(text: str | None, limit: int) -> str | None:
    if text is None:
        return None
    text = str(text)
    return text if len(text) <= limit else text[: limit - 1] + "…"


def truncate_bytes(text: str, limit: int = MAX_LOG_BYTES) -> str:
    data = text.encode("utf-8", "replace")
    if len(data) <= limit:
        return text
    marker = "\n… [log truncated]"
    return data[: limit - len(marker.encode())].decode("utf-8", "ignore") + marker


def safe_log(text: str) -> str:
    return truncate_bytes(redact(text))
