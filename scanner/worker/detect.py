"""Detects a repository's stack from its manifest files. Reads files as data only: nothing is executed or imported."""
import json
import os
import re
from pathlib import Path

MAX_DEPTH = 3
MAX_FOLDERS = 20
MAX_READ_BYTES = 512 * 1024
# Total read per folder for Python manifests (a folder may hold many requirements*.txt files).
MAX_PYTHON_BYTES = 2 * 1024 * 1024
SKIP_DIRS = {"node_modules", "vendor", ".venv", "venv", "dist", "build", "target", "out", "coverage", "__pycache__"}
FOLDER_PATH = re.compile(r"^[\w.-]+(/[\w.-]+)*$")
PYTHON_MANIFESTS = ("pyproject.toml", "Pipfile", "setup.py", "setup.cfg")
PYTHON_FRAMEWORKS = (("django", "Python + Django"), ("fastapi", "Python + FastAPI"), ("flask", "Python + Flask"))


def detect(root: Path) -> dict | None:
    """Returns {"stack", "folders": [{"path", "stack"}]} or None when no supported stack is found."""
    hits: dict[str, str] = {}
    root = root.resolve()
    for current, dirs, files in os.walk(root, followlinks=False):
        here = Path(current)
        rel = here.relative_to(root).as_posix()
        depth = 0 if rel == "." else rel.count("/") + 1
        dirs[:] = [] if depth >= MAX_DEPTH else sorted(
            d for d in dirs if not d.startswith(".") and d not in SKIP_DIRS and not (here / d).is_symlink())
        stack = _stack_of(here, set(files))
        if stack:
            hits["" if rel == "." else rel] = stack

    folders = {path: stack for path, stack in hits.items() if path and FOLDER_PATH.match(path)}
    # Keep the shallowest match of each subtree (e.g. "frontend" rather than "frontend/e2e").
    folders = {p: s for p, s in folders.items() if not any(p.startswith(other + "/") for other in folders)}
    if folders:
        ordered = [{"path": p, "stack": folders[p]} for p in sorted(folders)][:MAX_FOLDERS]
        return {"stack": ordered[0]["stack"], "folders": ordered}
    if "" in hits:
        return {"stack": hits[""], "folders": []}
    return None


def _stack_of(directory: Path, files: set[str]) -> str | None:
    if "package.json" in files:
        deps = _package_dependencies(directory / "package.json")
        if "@angular/core" in deps:
            return "Angular"
        if deps & {"react", "react-dom", "next"}:
            return "React"
    for manifest in ("pom.xml", "build.gradle", "build.gradle.kts"):
        if manifest in files and "spring-boot" in _read(directory / manifest):
            return "Java + Spring"
    python = [f for f in files if f in PYTHON_MANIFESTS or (f.startswith("requirements") and f.endswith(".txt"))]
    if python:
        parts, total = [], 0
        for f in sorted(python):
            if total >= MAX_PYTHON_BYTES:
                break
            part = _read(directory / f)[:MAX_PYTHON_BYTES - total]
            total += len(part)
            parts.append(part.lower())
        text = " ".join(parts)
        for name, stack in PYTHON_FRAMEWORKS:
            if re.search(rf"(?<![\w-]){name}(?![\w-])", text):
                return stack
    return None


def _package_dependencies(path: Path) -> set[str]:
    try:
        data = json.loads(_read(path))
    except ValueError:
        return set()
    if not isinstance(data, dict):
        return set()
    names: set[str] = set()
    for key in ("dependencies", "devDependencies", "peerDependencies"):
        section = data.get(key)
        if isinstance(section, dict):
            names.update(section)
    return names


def _read(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        return ""
    try:
        with open(path, "rb") as handle:
            return handle.read(MAX_READ_BYTES).decode("utf-8", errors="ignore")
    except OSError:
        return ""
