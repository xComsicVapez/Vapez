from __future__ import annotations

import json
import re
from typing import Any

from .config import SETTINGS
from .ollama_client import chat
from .tools import TOOL_SPECS, run_tool


SYSTEM_PROMPT = """You are Vapez PC Agent, a careful Windows gaming-PC assistant.
You control the user's local computer ONLY through tools.
Be concise. Prefer the smallest useful tool sequence.
Never invent tool results. Never claim you did something unless a tool confirmed it.
For destructive or irreversible actions, explain briefly and use tools that require confirmation.
If a request is unclear, ask a short clarifying question as a final answer.

You MUST reply with a single JSON object and nothing else:
{"type":"tool","name":"<tool_name>","args":{...}}
or
{"type":"final","message":"<answer to the user>"}

Available tools:
""" + json.dumps(TOOL_SPECS, indent=2)


def _extract_json(text: str) -> dict[str, Any]:
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    fence = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.DOTALL)
    if fence:
        return json.loads(fence.group(1))

    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        return json.loads(text[start : end + 1])
    raise ValueError(f"Model did not return JSON: {text[:400]}")


class PCAgent:
    def __init__(self, model: str | None = None) -> None:
        self.model = model or SETTINGS.model
        self.messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]

    def ask(self, user_text: str) -> str:
        self.messages.append({"role": "user", "content": user_text})

        for _ in range(SETTINGS.max_tool_rounds):
            raw = chat(self.messages, model=self.model)
            self.messages.append({"role": "assistant", "content": raw})

            try:
                payload = _extract_json(raw)
            except Exception as exc:  # noqa: BLE001
                repair = (
                    "Your last reply was invalid. Reply again with ONLY one JSON object "
                    f'of type tool or final. Error: {exc}'
                )
                self.messages.append({"role": "user", "content": repair})
                continue

            kind = payload.get("type")
            if kind == "final":
                message = str(payload.get("message", "")).strip() or "(no message)"
                return message

            if kind == "tool":
                name = str(payload.get("name", "")).strip()
                args = payload.get("args") or {}
                if not isinstance(args, dict):
                    args = {}
                result = run_tool(name, args)
                tool_msg = {
                    "role": "user",
                    "content": json.dumps(
                        {
                            "type": "tool_result",
                            "name": name,
                            "ok": result.ok,
                            "output": result.output,
                        }
                    ),
                }
                self.messages.append(tool_msg)
                continue

            self.messages.append(
                {
                    "role": "user",
                    "content": 'Invalid type. Use {"type":"tool"...} or {"type":"final"...}.',
                }
            )

        return "I hit the tool-round limit. Try a smaller request."
