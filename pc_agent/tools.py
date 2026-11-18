from __future__ import annotations

import os
import platform
import shutil
import subprocess
import webbrowser
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

import psutil

from .config import SETTINGS
from .safety import assess_shell_command, confirm


@dataclass
class ToolResult:
    ok: bool
    output: str


ToolFn = Callable[[dict[str, Any]], ToolResult]


def _expand(path: str) -> Path:
    return Path(os.path.expandvars(os.path.expanduser(path))).resolve()


def tool_open_app(args: dict[str, Any]) -> ToolResult:
    name = str(args.get("name", "")).strip()
    if not name:
        return ToolResult(False, "Missing app name")

    system = platform.system()
    try:
        if system == "Windows":
            # start is a shell builtin; empty title arg is required.
            subprocess.Popen(["cmd", "/c", "start", "", name], shell=False)
        elif system == "Darwin":
            subprocess.Popen(["open", "-a", name])
        else:
            subprocess.Popen([name])
        return ToolResult(True, f"Launched: {name}")
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Failed to open app: {exc}")


def tool_open_url(args: dict[str, Any]) -> ToolResult:
    url = str(args.get("url", "")).strip()
    if not url:
        return ToolResult(False, "Missing url")
    if not url.startswith(("http://", "https://")):
        url = "https://" + url
    webbrowser.open(url)
    return ToolResult(True, f"Opened browser to {url}")


def tool_run_shell(args: dict[str, Any]) -> ToolResult:
    command = str(args.get("command", "")).strip()
    if not command:
        return ToolResult(False, "Missing command")

    risk = assess_shell_command(command)
    if SETTINGS.require_confirm and risk.risky:
        if not confirm("run_shell (risky)", f"{command}\nReason: {risk.reason}"):
            return ToolResult(False, "User denied risky command")
    elif SETTINGS.require_confirm:
        if not confirm("run_shell", command):
            return ToolResult(False, "User denied command")

    system = platform.system()
    try:
        if system == "Windows":
            completed = subprocess.run(
                ["powershell", "-NoProfile", "-Command", command],
                capture_output=True,
                text=True,
                timeout=SETTINGS.shell_timeout_sec,
            )
        else:
            completed = subprocess.run(
                command,
                shell=True,
                capture_output=True,
                text=True,
                timeout=SETTINGS.shell_timeout_sec,
            )
    except subprocess.TimeoutExpired:
        return ToolResult(False, f"Timed out after {SETTINGS.shell_timeout_sec}s")
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Shell error: {exc}")

    out = (completed.stdout or "") + (completed.stderr or "")
    out = out.strip() or "(no output)"
    if len(out) > 8000:
        out = out[:8000] + "\n...[truncated]"
    ok = completed.returncode == 0
    return ToolResult(ok, f"exit={completed.returncode}\n{out}")


def tool_list_dir(args: dict[str, Any]) -> ToolResult:
    path = _expand(str(args.get("path", ".")))
    if not path.exists():
        return ToolResult(False, f"Path not found: {path}")
    if not path.is_dir():
        return ToolResult(False, f"Not a directory: {path}")
    entries = []
    for item in sorted(path.iterdir(), key=lambda p: (not p.is_dir(), p.name.lower())):
        kind = "dir" if item.is_dir() else "file"
        entries.append(f"{kind:4} {item.name}")
    preview = "\n".join(entries[:200]) or "(empty)"
    if len(entries) > 200:
        preview += f"\n...[{len(entries) - 200} more]"
    return ToolResult(True, f"{path}\n{preview}")


def tool_read_file(args: dict[str, Any]) -> ToolResult:
    path = _expand(str(args.get("path", "")))
    max_chars = int(args.get("max_chars", 6000))
    if not path.exists() or not path.is_file():
        return ToolResult(False, f"File not found: {path}")
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Read failed: {exc}")
    if len(text) > max_chars:
        text = text[:max_chars] + "\n...[truncated]"
    return ToolResult(True, text)


def tool_write_file(args: dict[str, Any]) -> ToolResult:
    path = _expand(str(args.get("path", "")))
    content = str(args.get("content", ""))
    if SETTINGS.require_confirm:
        if not confirm("write_file", str(path)):
            return ToolResult(False, "User denied write")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Write failed: {exc}")
    return ToolResult(True, f"Wrote {len(content)} chars to {path}")


def tool_type_text(args: dict[str, Any]) -> ToolResult:
    text = str(args.get("text", ""))
    if not text:
        return ToolResult(False, "Missing text")
    if SETTINGS.require_confirm:
        preview = text if len(text) <= 120 else text[:120] + "..."
        if not confirm("type_text", preview):
            return ToolResult(False, "User denied typing")
    try:
        import pyautogui

        # write() supports unicode; interval keeps typing readable.
        pyautogui.write(text, interval=0.02)
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Typing failed (focus a text field first): {exc}")
    return ToolResult(True, f"Typed {len(text)} characters")


def tool_hotkey(args: dict[str, Any]) -> ToolResult:
    keys = args.get("keys", [])
    if not isinstance(keys, list) or not keys:
        return ToolResult(False, "Provide keys as a list, e.g. [\"ctrl\", \"c\"]")
    key_list = [str(k).lower() for k in keys]
    if SETTINGS.require_confirm:
        if not confirm("hotkey", "+".join(key_list)):
            return ToolResult(False, "User denied hotkey")
    try:
        import pyautogui

        pyautogui.hotkey(*key_list)
    except Exception as exc:  # noqa: BLE001
        return ToolResult(False, f"Hotkey failed: {exc}")
    return ToolResult(True, f"Pressed {'+'.join(key_list)}")


def tool_system_info(_: dict[str, Any]) -> ToolResult:
    mem = psutil.virtual_memory()
    disk = shutil.disk_usage(Path.home())
    lines = [
        f"os={platform.system()} {platform.release()}",
        f"machine={platform.machine()}",
        f"cpu_count={psutil.cpu_count(logical=True)}",
        f"ram_used_gb={mem.used / (1024**3):.1f}/{mem.total / (1024**3):.1f}",
        f"disk_free_gb={disk.free / (1024**3):.1f}",
        f"time={datetime.now().isoformat(timespec='seconds')}",
    ]
    return ToolResult(True, "\n".join(lines))


TOOLS: dict[str, ToolFn] = {
    "open_app": tool_open_app,
    "open_url": tool_open_url,
    "run_shell": tool_run_shell,
    "list_dir": tool_list_dir,
    "read_file": tool_read_file,
    "write_file": tool_write_file,
    "type_text": tool_type_text,
    "hotkey": tool_hotkey,
    "system_info": tool_system_info,
}


TOOL_SPECS = [
    {
        "name": "open_app",
        "description": "Open an application by name (Windows: notepad, calc, steam, discord, chrome).",
        "parameters": {"name": "string"},
    },
    {
        "name": "open_url",
        "description": "Open a website in the default browser.",
        "parameters": {"url": "string"},
    },
    {
        "name": "run_shell",
        "description": "Run a shell command. On Windows this uses PowerShell. Ask confirmation for risky actions.",
        "parameters": {"command": "string"},
    },
    {
        "name": "list_dir",
        "description": "List files in a directory.",
        "parameters": {"path": "string"},
    },
    {
        "name": "read_file",
        "description": "Read a text file.",
        "parameters": {"path": "string", "max_chars": "number?"},
    },
    {
        "name": "write_file",
        "description": "Write text to a file (creates folders as needed).",
        "parameters": {"path": "string", "content": "string"},
    },
    {
        "name": "type_text",
        "description": "Type text into the currently focused window.",
        "parameters": {"text": "string"},
    },
    {
        "name": "hotkey",
        "description": "Press a keyboard shortcut, e.g. keys=[\"alt\",\"tab\"] or [\"ctrl\",\"shift\",\"esc\"].",
        "parameters": {"keys": "string[]"},
    },
    {
        "name": "system_info",
        "description": "Get basic OS/CPU/RAM/disk info.",
        "parameters": {},
    },
]


def run_tool(name: str, args: dict[str, Any] | None = None) -> ToolResult:
    fn = TOOLS.get(name)
    if not fn:
        return ToolResult(False, f"Unknown tool: {name}")
    return fn(args or {})
