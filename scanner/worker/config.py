import os
from dataclasses import dataclass
from pathlib import Path

MIN_TOKEN_LENGTH = 32


@dataclass(frozen=True)
class Config:
    api_url: str
    token: str
    workspace: Path
    poll_interval: float
    clone_timeout: int
    heartbeat_file: Path
    run_timeout: int

    @staticmethod
    def from_env(require_api: bool = True) -> "Config":
        api_url = os.environ.get("ARTEMIS_API_URL", "http://api:8080").rstrip("/")
        # Read once and dropped from the environment so child processes (tools) never inherit it.
        token = os.environ.pop("SCANNER_TOKEN", "")
        if require_api:
            if not api_url.startswith(("http://", "https://")):
                raise SystemExit("ARTEMIS_API_URL must be an http(s) URL")
            if len(token) < MIN_TOKEN_LENGTH:
                raise SystemExit(f"SCANNER_TOKEN must be set and at least {MIN_TOKEN_LENGTH} characters long")
        return Config(
            api_url=api_url,
            token=token,
            workspace=Path(os.environ.get("WORKSPACE", "/workspace")),
            poll_interval=float(os.environ.get("POLL_INTERVAL_SECONDS", "5")),
            clone_timeout=int(os.environ.get("CLONE_TIMEOUT_SECONDS", "300")),
            heartbeat_file=Path(os.environ.get("HEARTBEAT_FILE", "/tmp/artemis-runner.heartbeat")),
            run_timeout=int(os.environ.get("SCANNER_RUN_TIMEOUT_SECONDS", "3600")),
        )
