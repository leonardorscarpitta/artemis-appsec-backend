"""Processes one claimed job end to end."""
import logging
import shutil
from pathlib import Path

from .api import ApiClient
from .clone import CloneRefused, clone
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
    work = config.workspace / f"run-{job.get('number', 'x')}"
    wipe(work)
    work.mkdir(parents=True)
    try:
        src = work / "src"
        try:
            sha, subject = clone(repo["cloneUrl"], repo.get("branch", "main"), src, config.clone_timeout,
                                 int(limits.get("maxRepoSizeMb", 500)))
        except CloneRefused as refused:
            log.info("run %s: clone refused (%s)", job.get("number"), refused)
            api.fail(run_id, str(refused))
            return
        api.source(run_id, sha, subject[:500])

        folders = repo.get("paths") or []
        targets = []
        for folder in folders:
            target = (src / folder).resolve()
            if not target.is_relative_to(src.resolve()) or not target.is_dir():
                api.fail(run_id, f"Folder '{folder}' was not found on branch {repo.get('branch', 'main')}.")
                return
            targets.append((folder, target))

        failed_stage = None
        for stage in job.get("stages", []):
            if failed_stage:
                api.stage(run_id, stage["id"], not_run(stage, failed_stage))
                continue
            if targets:
                result = merge([(folder, run_stage(stage["id"], stage.get("tool", ""), target, work, timeout))
                                for folder, target in targets])
            else:
                result = run_stage(stage["id"], stage.get("tool", ""), src, work, timeout)
            status = api.stage(run_id, stage["id"], result).get("status", result["status"])
            log.info("run %s: %s/%s -> %s (%d finding(s))", job.get("number"), stage["id"], result["tool"],
                     status, len(result["findings"]))
            if status in ("failed", "error"):
                failed_stage = stage["id"]
        api.complete(run_id)
    finally:
        # Source code never outlives the run.
        wipe(work)


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


def not_run(stage: dict, failed_stage: str) -> dict:
    """A failed stage stops the pipeline: the stages after it are reported as skipped, never run."""
    return {"status": "skipped", "tool": stage.get("tool", ""), "durationSeconds": 0,
            "log": f"Not run: the {failed_stage} stage failed, so the pipeline stopped.", "findings": []}


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
