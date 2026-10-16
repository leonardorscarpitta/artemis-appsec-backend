"""Hardened, shallow clone of public HTTPS repositories."""
import ipaddress
import os
import re
import shutil
import socket
import subprocess
import urllib.parse
from pathlib import Path

BRANCH_PATTERN = re.compile(r"^(?!-)[A-Za-z0-9._/-]{1,255}$")


class CloneRefused(Exception):
    """The repository cannot be scanned; the message is safe to show to users."""


def validate_url(url: str) -> urllib.parse.SplitResult:
    if not isinstance(url, str) or len(url) > 512:
        raise CloneRefused("Invalid repository URL")
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "https":
        raise CloneRefused("Only public HTTPS repositories can be scanned for now (SSH/private repositories are not supported yet)")
    if parts.username or parts.password:
        raise CloneRefused("Repository URLs must not contain credentials")
    if not parts.hostname or parts.port not in (None, 443):
        raise CloneRefused("Repository URL must use the default HTTPS port")
    if not parts.path.strip("/") or parts.query or parts.fragment:
        raise CloneRefused("Invalid repository URL")
    _require_public_host(parts.hostname)
    return parts


def _require_public_host(host: str) -> None:
    try:
        infos = socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
    except socket.gaierror:
        raise CloneRefused("Repository host could not be resolved") from None
    for info in infos:
        address = ipaddress.ip_address(info[4][0])
        if not address.is_global:
            raise CloneRefused("Repository host is not a public address")


def validate_branch(branch: str) -> str:
    if not isinstance(branch, str) or not BRANCH_PATTERN.match(branch) or ".." in branch:
        raise CloneRefused("Invalid branch name")
    return branch


def _git_env() -> dict:
    return {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "HOME": os.environ.get("HOME", "/tmp"),
        "GIT_TERMINAL_PROMPT": "0",
        "GIT_ASKPASS": "/bin/false",
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": "/dev/null",
        "GIT_LFS_SKIP_SMUDGE": "1",
        "GIT_ALLOW_PROTOCOL": "https",
    }


def clone(url: str, branch: str, destination: Path, timeout: int, max_size_mb: int) -> tuple[str, str]:
    """Clones a single branch at depth 1 and returns (commit sha, commit subject). Removes .git afterwards."""
    validate_url(url)
    validate_branch(branch)
    command = [
        "git",
        "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
        "-c", "core.hooksPath=/dev/null",
        "-c", "core.symlinks=false",
        "-c", "http.followRedirects=false",
        "-c", "submodule.recurse=false",
        "-c", "filter.lfs.smudge=", "-c", "filter.lfs.process=", "-c", "filter.lfs.required=false",
        "clone", "--quiet", "--depth", "1", "--single-branch", "--no-tags", "--no-recurse-submodules",
        "--branch", branch, "--", url, str(destination),
    ]
    try:
        result = subprocess.run(command, env=_git_env(), capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        shutil.rmtree(destination, ignore_errors=True)
        raise CloneRefused(f"Cloning took longer than {timeout} seconds") from None
    if result.returncode != 0:
        shutil.rmtree(destination, ignore_errors=True)
        raise CloneRefused("The repository could not be cloned (check that it is public and the branch exists)")

    log = subprocess.run(
        ["git", "-C", str(destination), "log", "-1", "--format=%H%x00%s"],
        env=_git_env(), capture_output=True, text=True, timeout=30,
    )
    sha, _, subject = log.stdout.strip().partition("\x00")
    shutil.rmtree(destination / ".git", ignore_errors=True)

    size = _tree_size(destination)
    if size > max_size_mb * 1024 * 1024:
        shutil.rmtree(destination, ignore_errors=True)
        raise CloneRefused(f"Repository is larger than {max_size_mb} MB")
    return sha, subject


def _tree_size(root: Path) -> int:
    total = 0
    for dirpath, _dirs, files in os.walk(root, followlinks=False):
        for name in files:
            try:
                total += os.lstat(os.path.join(dirpath, name)).st_size
            except OSError:
                pass
    return total
