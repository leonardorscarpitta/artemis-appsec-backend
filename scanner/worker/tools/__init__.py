"""Tool adapters keyed by (stage id, tool name), matching the API's stage catalog."""
from . import gitleaks, grype, hadolint, semgrep, syft, trivy
from .base import MAX_FINDINGS, ToolError, ToolResult

ADAPTERS = {
    ("secrets", "Gitleaks"): gitleaks.scan,
    ("sast", "Semgrep"): semgrep.scan,
    ("sca", "Trivy"): trivy.vulnerabilities,
    ("sca", "Grype"): grype.vulnerabilities,
    ("license", "Trivy"): trivy.licenses,
    ("dockerfile", "Hadolint"): hadolint.scan,
    ("dockerfile", "Trivy"): trivy.dockerfile,
    ("sbom", "Syft"): syft.sbom,
    ("iac", "Trivy"): trivy.iac,
}

# Stages the runner cannot execute safely yet (they need an isolated build or a running target).
NOT_AVAILABLE = {
    "image": "Container image scanning is not available yet: it requires building the image in an isolated builder.",
    "dast": "Dynamic analysis (DAST) is not available yet: it requires a running, authorized staging target.",
}

__all__ = ["ADAPTERS", "NOT_AVAILABLE", "MAX_FINDINGS", "ToolError", "ToolResult"]
