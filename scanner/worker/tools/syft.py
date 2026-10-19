"""Syft: software bill of materials. Artifact only (uploaded by the job); it never produces findings."""
from pathlib import Path

from .base import ToolResult, read_json, run


def sbom(src: Path, out: Path, timeout: int) -> ToolResult:
    report = out / "sbom.cdx.json"
    run(["syft", "scan", f"dir:{src}", "-o", f"cyclonedx-json={report}", "-q"], timeout, out)
    components = len((read_json(report, {}) or {}).get("components") or [])
    return ToolResult([], f"[Syft] CycloneDX SBOM generated with {components} component(s)", report)
