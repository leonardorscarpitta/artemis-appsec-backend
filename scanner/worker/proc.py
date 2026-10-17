"""Process hygiene for a runner that executes tools on untrusted input."""
import ctypes
import logging
import os
import signal
import subprocess

log = logging.getLogger("artemis.runner")
PR_SET_DUMPABLE = 4


def run_isolated(command: list[str], *, timeout: float, **kwargs) -> subprocess.CompletedProcess:
    """Like subprocess.run(capture_output, text), but in its own process group: a timeout kills everything the
    command started (e.g. semgrep-core, ssh, git helpers), not only the direct child. Re-raises TimeoutExpired."""
    proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                            start_new_session=True, **kwargs)
    try:
        stdout, stderr = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.communicate()
        raise
    return subprocess.CompletedProcess(command, proc.returncode, stdout, stderr)


def reap_orphans() -> None:
    """Collects exited processes re-parented to the worker (it is PID 1). Call only between jobs, while no
    subprocess is running, so no other wait() is affected."""
    while True:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            return
        if pid == 0:
            return


def harden() -> None:
    """Makes the worker non-dumpable: tools running as the same user can no longer read its memory or
    /proc/<pid>/environ (SCANNER_TOKEN, decrypted deploy keys)."""
    try:
        libc = ctypes.CDLL(None, use_errno=True)
        if libc.prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) != 0:
            log.warning("could not mark the runner non-dumpable (errno %d)", ctypes.get_errno())
    except (OSError, AttributeError):
        log.warning("could not mark the runner non-dumpable")
