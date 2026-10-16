"""Processes one claimed job end to end."""
import logging
import shutil
from pathlib import Path

from .api import ApiClient
from .clone import CloneRefused, clone
from .config import Config
from .scan import run_stage

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

        failed_stage = None
        for stage in job.get("stages", []):
            if failed_stage:
                api.stage(run_id, stage["id"], not_run(stage, failed_stage))
                continue
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
