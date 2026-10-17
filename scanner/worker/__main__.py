"""Entry point: `run` (job loop), `scan` (local mode, no API) and `healthcheck`."""
import argparse
import json
import logging
import random
import shutil
import signal
import sys
import tempfile
import time
from pathlib import Path

from .api import ApiClient, ApiError
from .clone import CloneRefused, clone
from .config import Config
from .job import process, process_access_check, wipe, wipe_workspace
from .proc import harden, reap_orphans
from .scan import run_stage

log = logging.getLogger("artemis.runner")
STAGE_ORDER = ["secrets", "sast", "sca", "license", "dockerfile", "image", "sbom", "iac", "dast"]
DEFAULT_TOOLS = {"secrets": "Gitleaks", "sast": "Semgrep", "sca": "Trivy", "license": "Trivy", "dockerfile": "Hadolint",
                 "image": "Trivy", "sbom": "Syft", "iac": "Trivy", "dast": "OWASP ZAP"}
HEARTBEAT_MAX_AGE = 300


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    parser = argparse.ArgumentParser(prog="worker")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("run", help="pull and process jobs from the Artemis API")
    sub.add_parser("healthcheck")
    scan = sub.add_parser("scan", help="scan locally and print normalized results (no API)")
    source = scan.add_mutually_exclusive_group(required=True)
    source.add_argument("--dir", type=Path)
    source.add_argument("--url")
    scan.add_argument("--branch", default="main")
    scan.add_argument("--stages", default=",".join(STAGE_ORDER))
    scan.add_argument("--tool", action="append", default=[], help="stage=Tool, e.g. sca=Grype")
    args = parser.parse_args()

    if args.command == "run":
        return run_loop()
    if args.command == "healthcheck":
        return healthcheck()
    return local_scan(args)


def run_loop() -> int:
    harden()
    config = Config.from_env()
    api = ApiClient(config)
    stopping = False

    def stop(_signum, _frame):
        nonlocal stopping
        stopping = True
        log.info("shutdown requested; finishing the current job")

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    config.workspace.mkdir(parents=True, exist_ok=True)
    wipe_workspace(config.workspace)
    log.info("runner started; polling %s", config.api_url)

    while not stopping:
        reap_orphans()
        config.heartbeat_file.touch()
        try:
            job = api.claim()
        except ApiError as error:
            log.warning("claim failed: %s", error)
            job = None
        if job is None:
            _sleep(config.poll_interval + random.uniform(0, 1), lambda: stopping)
            continue
        if job.get("type") == "access-check":
            try:
                process_access_check(job, api, config)
            except Exception:  # noqa: BLE001
                log.exception("access check %s failed unexpectedly", job.get("checkId"))
                try:
                    api.access_check(job["checkId"], False, "The scanner runner hit an internal error.", None)
                except ApiError:
                    pass
            continue
        log.info("run %s claimed", job.get("number"))
        try:
            process(job, api, config)
        except Exception:  # noqa: BLE001 - never crash the loop; details stay in the runner log only
            log.exception("run %s failed unexpectedly", job.get("number"))
            try:
                api.fail(job["runId"], "The scanner runner hit an internal error.")
            except ApiError:
                pass
    return 0


def _sleep(seconds: float, should_stop) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline and not should_stop():
        time.sleep(0.25)


def healthcheck() -> int:
    config = Config.from_env(require_api=False)
    try:
        age = time.time() - config.heartbeat_file.stat().st_mtime
    except FileNotFoundError:
        return 1
    return 0 if age < HEARTBEAT_MAX_AGE else 1


def local_scan(args) -> int:
    tools = dict(DEFAULT_TOOLS)
    for override in args.tool:
        stage, _, tool = override.partition("=")
        tools[stage] = tool
    stages = [s for s in args.stages.split(",") if s]
    workspace = Config.from_env(require_api=False).workspace
    work = Path(tempfile.mkdtemp(prefix="artemis-local-", dir=workspace if workspace.is_dir() else None))
    output = {"source": None, "stages": []}
    try:
        if args.url:
            try:
                sha, subject = clone(args.url, args.branch, work / "src", 300, 500)
            except CloneRefused as refused:
                print(json.dumps({"error": str(refused)}, indent=2))
                return 2
            src = work / "src"
            output["source"] = {"url": args.url, "commitSha": sha, "commitMessage": subject}
        else:
            # Scanned as a copy: some stages neutralize repository files (e.g. Gitleaks config, remote Terraform modules).
            src = work / "src"
            shutil.copytree(args.dir.resolve(), src, symlinks=True)
            output["source"] = {"dir": str(args.dir.resolve())}
        for stage in stages:
            result = run_stage(stage, tools.get(stage, ""), src, work)
            output["stages"].append({"id": stage, **result})
    finally:
        wipe(work)
    json.dump(output, sys.stdout, indent=2)
    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
