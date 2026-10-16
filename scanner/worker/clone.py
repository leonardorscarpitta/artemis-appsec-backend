"""Hardened, shallow clone of public HTTPS repositories and of private repositories over SSH (deploy key)."""
import ipaddress
import os
import re
import shlex
import shutil
import socket
import subprocess
import urllib.parse
from pathlib import Path

BRANCH_PATTERN = re.compile(r"^(?!-)[A-Za-z0-9._/-]{1,255}$")
HOST_PATTERN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252}[A-Za-z0-9])?$")
SSH_PATH_PATTERN = re.compile(r"^(?![-/])[A-Za-z0-9._~/-]{1,400}$")
SCP_LIKE = re.compile(r"^git@([^:/]+):(.+)$")
PINNED_KNOWN_HOSTS = Path("/etc/artemis/known_hosts")
PINNED_HOSTS = {"github.com", "gitlab.com", "bitbucket.org"}


class CloneRefused(Exception):
    """The repository cannot be scanned; the message is safe to show to users."""


class SshTarget:
    """The SSH server of a private repository URL."""

    def __init__(self, host: str, port: int):
        self.host = host
        self.port = port

    @property
    def pinned(self) -> bool:
        return self.port == 22 and self.host in PINNED_HOSTS

    @property
    def known_hosts_name(self) -> str:
        return self.host if self.port == 22 else f"[{self.host}]:{self.port}"


def is_ssh_url(url: str) -> bool:
    return isinstance(url, str) and (url.startswith("git@") or url.startswith("ssh://"))


def validate_ssh_url(url: str) -> SshTarget:
    """git@host:owner/repo.git or ssh://[git@]host[:port]/owner/repo(.git); the host must be public."""
    if not isinstance(url, str) or len(url) > 512 or any(c.isspace() for c in url):
        raise CloneRefused("Invalid repository URL")
    scp = SCP_LIKE.match(url)
    if scp:
        host, path, port = scp.group(1), scp.group(2), 22
    else:
        parts = urllib.parse.urlsplit(url)
        if parts.scheme != "ssh" or parts.password or parts.username not in (None, "git") or parts.query or parts.fragment:
            raise CloneRefused("Invalid SSH repository URL (use git@host:owner/repo.git or ssh://git@host/owner/repo.git)")
        try:
            port = parts.port or 22
        except ValueError:
            raise CloneRefused("Invalid SSH port") from None
        host, path = parts.hostname or "", parts.path.lstrip("/")
    if not HOST_PATTERN.match(host) or not SSH_PATH_PATTERN.match(path) or ".." in path:
        raise CloneRefused("Invalid SSH repository URL (use git@host:owner/repo.git or ssh://git@host/owner/repo.git)")
    _require_public_host(host.lower(), port)
    return SshTarget(host.lower(), port)


class SshSession:
    """A deploy key and known_hosts written to the in-memory workspace, used only through GIT_SSH_COMMAND."""

    def __init__(self, directory: Path, private_key: str, known_hosts: str):
        self.directory = directory
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir(mode=0o700, parents=True)
        self.key_file = directory / "key"
        self.known_hosts_file = directory / "known_hosts"
        fd = os.open(self.key_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as handle:
            handle.write(private_key if private_key.endswith("\n") else private_key + "\n")
        self.known_hosts_file.write_text(known_hosts.strip() + "\n")

    def command(self) -> str:
        options = [
            "-F", "/dev/null", "-i", str(self.key_file),
            "-o", "IdentitiesOnly=yes", "-o", "IdentityAgent=none", "-o", "BatchMode=yes",
            "-o", "PreferredAuthentications=publickey", "-o", "StrictHostKeyChecking=yes",
            "-o", f"UserKnownHostsFile={self.known_hosts_file}", "-o", "GlobalKnownHostsFile=/dev/null",
            "-o", "UpdateHostKeys=no", "-o", "ConnectTimeout=15", "-o", "ServerAliveInterval=15",
            "-o", "ServerAliveCountMax=2", "-o", "LogLevel=ERROR",
        ]
        return "ssh " + " ".join(shlex.quote(o) for o in options)

    def close(self) -> None:
        """Deletes the key as soon as git no longer needs it."""
        try:
            self.key_file.unlink(missing_ok=True)
        finally:
            shutil.rmtree(self.directory, ignore_errors=True)


def known_hosts_for(target: SshTarget, recorded: str | None) -> str:
    """Pinned providers use only their official keys; other servers the key recorded on first contact."""
    if target.pinned:
        return "\n".join(line for line in PINNED_KNOWN_HOSTS.read_text().splitlines()
                         if line.split(" ", 1)[0] == target.host)
    if not recorded:
        raise CloneRefused("The Git server's host key is not known yet: run 'Test access' in the repository's Access tab first")
    return recorded


def keyscan(target: SshTarget, timeout: int = 20) -> str:
    """Reads the server's host keys on first contact (trust on first use) for non-pinned servers."""
    try:
        result = subprocess.run(
            ["ssh-keyscan", "-T", "10", "-t", "ed25519,ecdsa,rsa", "-p", str(target.port), "--", target.host],
            capture_output=True, text=True, timeout=timeout, env={"PATH": "/usr/bin:/bin"},
        )
    except subprocess.TimeoutExpired:
        raise CloneRefused("Could not connect to the Git server to read its host key") from None
    lines = [line.strip() for line in result.stdout.splitlines()
             if line.strip() and not line.startswith("#") and line.split(" ", 1)[0] == target.known_hosts_name]
    if not lines:
        raise CloneRefused("Could not read the Git server's host key (is SSH reachable on port %d?)" % target.port)
    order = {"ssh-ed25519": 0, "ecdsa-sha2-nistp256": 1, "ecdsa-sha2-nistp384": 1, "ecdsa-sha2-nistp521": 1, "ssh-rsa": 2}
    return "\n".join(sorted(lines, key=lambda line: order.get(line.split(" ")[1], 3))[:5])


def validate_url(url: str) -> urllib.parse.SplitResult:
    if not isinstance(url, str) or len(url) > 512:
        raise CloneRefused("Invalid repository URL")
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "https":
        raise CloneRefused("Only HTTPS URLs can be cloned without a deploy key")
    if parts.username or parts.password:
        raise CloneRefused("Repository URLs must not contain credentials")
    if not parts.hostname or parts.port not in (None, 443):
        raise CloneRefused("Repository URL must use the default HTTPS port")
    if not parts.path.strip("/") or parts.query or parts.fragment:
        raise CloneRefused("Invalid repository URL")
    _require_public_host(parts.hostname)
    return parts


def _require_public_host(host: str, port: int = 443) -> None:
    try:
        infos = socket.getaddrinfo(host, port, proto=socket.IPPROTO_TCP)
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


def _git_env(ssh: SshSession | None = None) -> dict:
    env = {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "HOME": os.environ.get("HOME", "/tmp"),
        "GIT_TERMINAL_PROMPT": "0",
        "GIT_ASKPASS": "/bin/false",
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": "/dev/null",
        "GIT_LFS_SKIP_SMUDGE": "1",
        "GIT_ALLOW_PROTOCOL": "ssh" if ssh else "https",
    }
    if ssh:
        env["GIT_SSH_COMMAND"] = ssh.command()
        env["GIT_SSH_VARIANT"] = "ssh"
    return env


def _protocol_options(ssh: SshSession | None) -> list[str]:
    allowed = "ssh" if ssh else "https"
    return ["-c", "protocol.allow=never", "-c", f"protocol.{allowed}.allow=always"]


def explain_ssh_error(stderr: str) -> str:
    """Maps git/ssh errors to messages that are safe to show (never echoes server output)."""
    text = stderr.lower()
    if "host key verification failed" in text or "remote host identification has changed" in text:
        return "Host key verification failed: the Git server's host key does not match the one Artemis trusts"
    if "permission denied" in text:
        return "Permission denied: add the deploy key to the repository (read-only) and try again"
    if "remote branch" in text and "not found" in text:
        return "Branch not found in the repository"
    if "repository not found" in text or "does not appear to be a git repository" in text or "could not be found" in text:
        return "Repository not found: check the URL and that the deploy key was added to this repository"
    if "could not resolve hostname" in text or "connection timed out" in text or "connection refused" in text \
            or "no route to host" in text or "network is unreachable" in text:
        return "Could not connect to the Git server over SSH"
    return "The repository could not be read over SSH"


def ls_remote(url: str, branch: str, ssh: SshSession, timeout: int = 60) -> tuple[bool, str]:
    """Checks read access to a private repository and that the branch exists, without cloning."""
    validate_ssh_url(url)
    validate_branch(branch)
    command = ["git", *_protocol_options(ssh), "ls-remote", "--heads", "--", url, f"refs/heads/{branch}"]
    try:
        result = subprocess.run(command, env=_git_env(ssh), capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return False, "The Git server did not answer in time"
    if result.returncode != 0:
        return False, explain_ssh_error(result.stderr)
    if not result.stdout.strip():
        return False, f"Access OK, but branch '{branch}' was not found"
    return True, f"Access OK — branch '{branch}' found"


def clone(url: str, branch: str, destination: Path, timeout: int, max_size_mb: int,
          ssh: SshSession | None = None) -> tuple[str, str]:
    """Clones a single branch at depth 1 and returns (commit sha, commit subject). Removes .git afterwards.
    SSH URLs need an `ssh` session (the repository's deploy key)."""
    if is_ssh_url(url):
        if ssh is None:
            raise CloneRefused("Private repositories need a deploy key")
        validate_ssh_url(url)
    else:
        validate_url(url)
        ssh = None
    validate_branch(branch)
    command = [
        "git",
        *_protocol_options(ssh),
        "-c", "core.hooksPath=/dev/null",
        "-c", "core.symlinks=false",
        "-c", "http.followRedirects=false",
        "-c", "submodule.recurse=false",
        "-c", "filter.lfs.smudge=", "-c", "filter.lfs.process=", "-c", "filter.lfs.required=false",
        "clone", "--quiet", "--depth", "1", "--single-branch", "--no-tags", "--no-recurse-submodules",
        "--branch", branch, "--", url, str(destination),
    ]
    try:
        result = subprocess.run(command, env=_git_env(ssh), capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        shutil.rmtree(destination, ignore_errors=True)
        raise CloneRefused(f"Cloning took longer than {timeout} seconds") from None
    if result.returncode != 0:
        shutil.rmtree(destination, ignore_errors=True)
        if ssh:
            raise CloneRefused(explain_ssh_error(result.stderr))
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
