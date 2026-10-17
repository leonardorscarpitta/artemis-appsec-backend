"""Processes one claimed job end to end."""
import logging
import shutil
import time
from pathlib import Path

from .api import ApiClient
from .clone import CloneRefused, SshSession, clone, keyscan, known_hosts_for, ls_remote, validate_ssh_url
from .config import Config
from .sanitize import safe_log
from .scan import run_stage
from .tools import MAX_FINDINGS

log = logging.getLogger("artemis.runner")


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
    """Verifies the deploy key can read the repository (git ls-remote of the branch); nothing is cloned."""
    check_id = job["checkId"]
    repo = job["repository"]
    work = config.workspace / f"check-{check_id}"
    wipe(work)
    work.mkdir(parents=True)
    ssh = None
    host_key = None
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
    except CloneRefused as refused:
        ok, message = False, str(refused)
    except KeyError:
        ok, message = False, "No deploy key was provided for this repository"
    finally:
        if ssh:
            ssh.close()
        wipe(work)
    log.info("access check %s for %s -> %s", check_id, repo.get("name"), "ok" if ok else "failed")
    api.access_check(check_id, ok, message, host_key if ok else None)


def open_ssh_session(work: Path, url: str, credentials: dict | None) -> SshSession:
    if not credentials or not credentials.get("sshPrivateKey"):
        raise CloneRefused("No deploy key was provided for this private repository")
    target = validate_ssh_url(url)
    known_hosts = known_hosts_for(target, credentials.get("knownHosts"))
    return SshSession(work / "ssh", credentials["sshPrivateKey"], known_hosts)


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
