"""Runs the requested stages on a checked-out source tree."""
import shutil
import time
from pathlib import Path

from .sanitize import safe_log
from .tools import ADAPTERS, MAX_FINDINGS, NOT_AVAILABLE, ToolError

DEFAULT_STAGE_TIMEOUT = 600


def run_stage(stage_id: str, tool: str, src: Path, work: Path, timeout: int = DEFAULT_STAGE_TIMEOUT) -> dict:
    """Returns the contract's stage result: status completed|skipped|error, tool, durationSeconds, log, findings."""
    started = time.monotonic()
    adapter = ADAPTERS.get((stage_id, tool))
    if stage_id in NOT_AVAILABLE:
        return _result("skipped", tool, started, NOT_AVAILABLE[stage_id])
    if adapter is None:
        return _result("skipped", tool, started, f"{tool} is not available for the {stage_id} stage in this runner.")

    out = work / f"out-{stage_id}"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    try:
        result = adapter(src, out, timeout)
    except ToolError as error:
        return _result("error", tool, started, str(error))
    finally:
        # Tool reports may contain code context; nothing but the normalized result leaves this function.
        shutil.rmtree(out, ignore_errors=True)

    findings = result.findings[:MAX_FINDINGS]
    log = result.log
    if len(result.findings) > MAX_FINDINGS:
        log += f"\nOnly the first {MAX_FINDINGS} findings were reported."
    return _result("completed", tool, started, log, findings)


def _result(status: str, tool: str, started: float, log: str, findings: list | None = None) -> dict:
    return {
        "status": status,
        "tool": tool,
        "durationSeconds": max(0, round(time.monotonic() - started)),
        "log": safe_log(log),
        "findings": findings or [],
    }
