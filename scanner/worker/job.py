"""Processes one claimed job end to end."""
import logging
import shutil
import time
from pathlib import Path

from .api import ApiClient, ApiError
from .clone import CloneRefused, SshSession, clone, keyscan, known_hosts_for, ls_remote, validate_ssh_url
from .config import Config
from .detect import detect
from .sanitize import safe_log
from .scan import run_stage
from .tools import MAX_FINDINGS

log = logging.getLogger("artemis.runner")

# Stack detection only reads manifests, so it gets a smaller clone than a scan (access checks are cheap to request).
DETECT_TIMEOUT_SECONDS = 120
DETECT_MAX_REPO_MB = 200
# Must match the API's limit for stored SBOMs.
MAX_SBOM_BYTES = 20 * 1024 * 1024


def process(job: dict, api: ApiClient, config: Config) -> None:
    run_id = job["runId"]
    repo = job["repository"]
    limits = job.get("limits") or {}
    timeout = int(limits.get("stageTimeoutSeconds", 600))
    # One wall-clock budget for the whole run, so many folders x long stages cannot hold the runner for hours.
    deadline = time.monotonic() + config.run_timeout
    out_of_time = f"Not run: the run time limit ({config.run_timeout // 60} min) was reached."
    work = config.workspace / f"run-{job.get('number', 'x')}"
    wipe(work)
    work.mkdir(parents=True)
    try:
        src = work / "src"
        if repo.get("access") != "ssh":
            # Only repositories reached through their own deploy key are scanned (the key proves access was granted).
            api.fail(run_id, "Only SSH repositories with an Artemis deploy key are scanned.")
            return
        ssh = None
        try:
            if repo.get("access") == "ssh":
                ssh = open_ssh_session(work, repo["cloneUrl"], job.get("credentials"))
                # Re-check access first: the deploy key may have been removed since the last "Test access".
                ok, message = ls_remote(repo["cloneUrl"], repo.get("branch", "main"), ssh)
                if not ok:
                    access_lost = not message.startswith("Access OK")
                    log.info("run %s: access check failed (%s)", job.get("number"), message)
                    api.fail(run_id, f"Repository access check failed: {message}" if access_lost else message,
                             access_lost=access_lost)
                    return
            sha, subject = clone(repo["cloneUrl"], repo.get("branch", "main"), src, config.clone_timeout,
                                 int(limits.get("maxRepoSizeMb", 500)), ssh)
        except CloneRefused as refused:
            log.info("run %s: clone refused (%s)", job.get("number"), refused)
            api.fail(run_id, str(refused))
            return
        finally:
            # The deploy key never stays on disk while the tools run.
            if ssh:
                ssh.close()
        api.source(run_id, sha, subject[:500])

        folders = repo.get("paths") or []
        targets = []
        for folder in folders:
            target = (src / folder).resolve()
            if not target.is_relative_to(src.resolve()) or not target.is_dir():
                api.fail(run_id, f"Folder '{folder}' was not found on branch {repo.get('branch', 'main')}.")
                return
            targets.append((folder, target))

        def budget() -> int:
            return min(timeout, int(deadline - time.monotonic()))

        def run_part(stage: dict, target: Path, **extra) -> dict:
            if budget() <= 0:
                return skipped(stage, out_of_time)
            return run_stage(stage["id"], stage.get("tool", ""), target, work, max(1, budget()), **extra)

        def sbom_part(stage: dict, target: Path, folder: str) -> dict:
            """Runs Syft on one folder and stores its SBOM with the run; storing is best effort."""
            path = work / "sbom.cdx.json"
            try:
                result = run_part(stage, target, keep_artifact=path)
                if path.is_file():
                    result["log"] += "\n" + store_sbom(api, run_id, folder, path)
                return result
            finally:
                path.unlink(missing_ok=True)

        failed_stage = None
        timed_out = False
        for stage in job.get("stages", []):
            if failed_stage:
                api.stage(run_id, stage["id"], skipped(stage, f"Not run: the {failed_stage} stage failed, so the pipeline stopped."))
                continue
            if budget() <= 0:
                timed_out = True
                api.stage(run_id, stage["id"], skipped(stage, out_of_time))
                continue
            if stage["id"] == "image":
                result = run_part(stage, src, image=repo.get("image"))
            elif stage["id"] == "sbom":
                parts = [(folder, sbom_part(stage, target, folder)) for folder, target in targets or [("", src)]]
                result = merge(parts) if targets else parts[0][1]
            elif targets:
                result = merge([(folder, run_part(stage, target)) for folder, target in targets])
            else:
                result = run_part(stage, src)
            status = api.stage(run_id, stage["id"], result).get("status", result["status"])
            log.info("run %s: %s/%s -> %s (%d finding(s))", job.get("number"), stage["id"], result["tool"],
                     status, len(result["findings"]))
            if status in ("failed", "error"):
                failed_stage = stage["id"]
        if timed_out or budget() <= 0:
            api.fail(run_id, f"The run time limit ({config.run_timeout // 60} min) was reached; the remaining stages were not run.")
        else:
            api.complete(run_id)
    finally:
        # Source code never outlives the run.
        wipe(work)


def process_access_check(job: dict, api: ApiClient, config: Config) -> None:
    """Verifies the deploy key can read the repository (git ls-remote of the branch); when it can, clones the branch
    only to detect the project's stack from its manifest files, then wipes the copy."""
    check_id = job["checkId"]
    repo = job["repository"]
    work = config.workspace / f"check-{check_id}"
    wipe(work)
    work.mkdir(parents=True)
    ssh = None
    host_key = None
    detected = None
    try:
        target = validate_ssh_url(repo["cloneUrl"])
        credentials = job.get("credentials") or {}
        if target.pinned:
            known_hosts = known_hosts_for(target, None)
        else:
            known_hosts = credentials.get("knownHosts") or keyscan(target)
            host_key = known_hosts
        ssh = SshSession(work / "ssh", credentials["sshPrivateKey"], known_hosts)
        ok, message = ls_remote(repo["cloneUrl"], repo.get("branch", "main"), ssh)
        if ok:
            detected = detect_stack(repo, work, ssh, config)
    except CloneRefused as refused:
        ok, message = False, str(refused)
    except KeyError:
        ok, message = False, "No deploy key was provided for this repository"
    finally:
        if ssh:
            ssh.close()
        wipe(work)
    log.info("access check %s for %s -> %s", check_id, repo.get("name"), "ok" if ok else "failed")
    api.access_check(check_id, ok, message, host_key if ok else None, detected)


def detect_stack(repo: dict, work: Path, ssh: SshSession, config: Config) -> dict | None:
    """Best effort: a failed clone or detection never turns a successful access check into a failure."""
    try:
        clone(repo["cloneUrl"], repo.get("branch", "main"), work / "src", min(config.clone_timeout, DETECT_TIMEOUT_SECONDS),
              DETECT_MAX_REPO_MB, ssh)
        return detect(work / "src")
    except Exception as error:  # noqa: BLE001 - detection is optional
        log.info("stack detection for %s skipped (%s)", repo.get("name"), type(error).__name__)
        return None
    finally:
        wipe(work / "src")


def open_ssh_session(work: Path, url: str, credentials: dict | None) -> SshSession:
    if not credentials or not credentials.get("sshPrivateKey"):
        raise CloneRefused("No deploy key was provided for this private repository")
    target = validate_ssh_url(url)
    known_hosts = known_hosts_for(target, credentials.get("knownHosts"))
    return SshSession(work / "ssh", credentials["sshPrivateKey"], known_hosts)


def store_sbom(api: ApiClient, run_id: str, folder: str, path: Path) -> str:
    """Uploads the SBOM; returns the stage log line. A failure never fails the stage."""
    size = path.stat().st_size
    if size > MAX_SBOM_BYTES:
        return f"SBOM too large to store: {size / (1024 * 1024):.1f} MB (limit {MAX_SBOM_BYTES // (1024 * 1024)} MB)"
    try:
        api.sbom(run_id, folder, path)
    except (ApiError, OSError) as error:
        log.warning("run %s: SBOM upload failed (%s)", run_id, error)
        return f"Warning: the SBOM could not be stored ({error})"
    return "SBOM stored with this run (download it from the run page)"


def merge(parts: list[tuple[str, dict]]) -> dict:
    """Combines one stage's results from several monorepo folders into a single stage result."""
    statuses = [result["status"] for _, result in parts]
    status = "error" if "error" in statuses else "skipped" if all(st == "skipped" for st in statuses) else "completed"
    findings = []
    for folder, result in parts:
        for item in result["findings"]:
            # Paths stay relative to the repository root, not to the scanned folder.
            if item.get("location"):
                item["location"]["path"] = f"{folder}/{item['location']['path']}"
            findings.append(item)
    return {
        "status": status,
        "tool": parts[0][1]["tool"],
        "durationSeconds": sum(result["durationSeconds"] for _, result in parts),
        "log": safe_log("\n\n".join(f"[{folder}]\n{result['log']}" for folder, result in parts)),
        "findings": findings[:MAX_FINDINGS],
    }


def skipped(stage: dict, reason: str) -> dict:
    """A stage that is reported without running (a previous stage failed, or the run is out of time)."""
    return {"status": "skipped", "tool": stage.get("tool", ""), "durationSeconds": 0, "log": reason, "findings": []}


def wipe(path: Path) -> None:
    shutil.rmtree(path, ignore_errors=True)


def wipe_workspace(root: Path) -> None:
    if not root.exists():
        return
    for child in root.iterdir():
        if child.is_dir() and not child.is_symlink():
            shutil.rmtree(child, ignore_errors=True)
        else:
            child.unlink(missing_ok=True)
