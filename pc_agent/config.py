from __future__ import annotations

import os
from dataclasses import dataclass, field


@dataclass
class Settings:
    """Runtime settings for the PC agent."""

    ollama_host: str = field(
        default_factory=lambda: os.environ.get("OLLAMA_HOST", "http://127.0.0.1:11434")
    )
    model: str = field(
        default_factory=lambda: os.environ.get("PC_AGENT_MODEL", "qwen2.5:7b")
    )
    require_confirm: bool = field(
        default_factory=lambda: os.environ.get("PC_AGENT_CONFIRM", "1") != "0"
    )
    max_tool_rounds: int = field(
        default_factory=lambda: int(os.environ.get("PC_AGENT_MAX_ROUNDS", "8"))
    )
    shell_timeout_sec: int = field(
        default_factory=lambda: int(os.environ.get("PC_AGENT_SHELL_TIMEOUT", "60"))
    )


SETTINGS = Settings()
