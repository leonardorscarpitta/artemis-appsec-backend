"""Client for the API's internal job endpoints (bearer SCANNER_TOKEN)."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from .config import Config

TIMEOUT_SECONDS = 30
RETRIES = 5


class ApiError(Exception):
    pass


class ApiClient:
    def __init__(self, config: Config):
        self._base = config.api_url + "/internal/v1"
        self._headers = {
            "Authorization": f"Bearer {config.token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "artemisappsec-runner",
        }

    def claim(self) -> dict | None:
        status, body = self._request("POST", "/jobs/claim", {})
        return body if status == 200 else None

    def source(self, run_id: str, commit_sha: str, commit_message: str) -> None:
        self._request("POST", f"/jobs/{run_id}/source", {"commitSha": commit_sha, "commitMessage": commit_message})

    def stage(self, run_id: str, stage_id: str, result: dict) -> dict:
        return self._request("PUT", f"/jobs/{run_id}/stages/{stage_id}", result)[1] or {}

    def sbom(self, run_id: str, folder: str, path: Path) -> None:
        """Streams the CycloneDX JSON file to the API (one stored SBOM per run and folder)."""
        query = urllib.parse.urlencode({"folder": folder})
        self._request("PUT", f"/jobs/{run_id}/sbom?{query}", body_file=path)

    def complete(self, run_id: str) -> None:
        self._request("POST", f"/jobs/{run_id}/complete", {})

    def fail(self, run_id: str, reason: str, access_lost: bool = False) -> None:
        self._request("POST", f"/jobs/{run_id}/fail", {"reason": reason[:500], "accessLost": access_lost})

    def access_check(self, check_id: str, ok: bool, message: str, host_key: str | None,
                     detected: dict | None = None) -> None:
        self._request("PUT", f"/access-checks/{check_id}",
                      {"status": "ok" if ok else "failed", "message": message[:500], "hostKey": host_key,
                       "detected": detected})

    def _request(self, method: str, path: str, payload: dict | None = None,
                 body_file: Path | None = None) -> tuple[int, dict | None]:
        data = json.dumps(payload).encode() if body_file is None else None
        headers = dict(self._headers)
        if body_file is not None:
            headers["Content-Length"] = str(body_file.stat().st_size)
        delay = 1.0
        for attempt in range(1, RETRIES + 1):
            handle = open(body_file, "rb") if body_file is not None else None
            request = urllib.request.Request(self._base + path, data=handle or data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                    raw = response.read()
                    return response.status, (json.loads(raw) if raw else None)
            except urllib.error.HTTPError as error:
                # 4xx are definitive (bad request, auth, conflict); 5xx may be transient.
                if error.code < 500 or attempt == RETRIES:
                    raise ApiError(f"{method} {path} -> HTTP {error.code}") from None
            except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
                if attempt == RETRIES:
                    raise ApiError(f"{method} {path} -> {type(error).__name__}") from None
            finally:
                if handle:
                    handle.close()
            time.sleep(delay)
            delay = min(delay * 2, 30)
        raise ApiError(f"{method} {path} failed")
