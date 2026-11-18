from __future__ import annotations

import json
from typing import Any

import requests

from .config import SETTINGS


class OllamaError(RuntimeError):
    pass


def check_server() -> None:
    try:
        resp = requests.get(f"{SETTINGS.ollama_host}/api/tags", timeout=5)
        resp.raise_for_status()
    except requests.RequestException as exc:
        raise OllamaError(
            "Cannot reach Ollama. On your gaming PC: install https://ollama.com, "
            "open it, then retry. Details: "
            f"{exc}"
        ) from exc


def list_models() -> list[str]:
    check_server()
    resp = requests.get(f"{SETTINGS.ollama_host}/api/tags", timeout=10)
    resp.raise_for_status()
    models = resp.json().get("models", [])
    return [m.get("name", "") for m in models if m.get("name")]


def _model_present(model: str, installed: list[str]) -> bool:
    if model in installed:
        return True
    # Accept "qwen2.5:7b" when installed list has exact match or same name with digest tags.
    return any(name == model or name.startswith(model + "-") for name in installed)


def ensure_model(model: str) -> None:
    installed = list_models()
    if _model_present(model, installed):
        return

    print(f"Pulling model '{model}' (first time can take a while on your PC)...")
    with requests.post(
        f"{SETTINGS.ollama_host}/api/pull",
        json={"name": model, "stream": True},
        stream=True,
        timeout=600,
    ) as resp:
        resp.raise_for_status()
        for line in resp.iter_lines():
            if not line:
                continue
            data = json.loads(line)
            status = data.get("status")
            if status:
                print(f"  {status}", end="\r", flush=True)
            if data.get("error"):
                raise OllamaError(data["error"])
    print()


def chat(messages: list[dict[str, Any]], model: str | None = None) -> str:
    check_server()
    payload = {
        "model": model or SETTINGS.model,
        "messages": messages,
        "stream": False,
        "options": {
            "temperature": 0.2,
        },
    }
    try:
        resp = requests.post(
            f"{SETTINGS.ollama_host}/api/chat",
            json=payload,
            timeout=300,
        )
        resp.raise_for_status()
    except requests.RequestException as exc:
        raise OllamaError(f"Ollama chat failed: {exc}") from exc
    data = resp.json()
    content = data.get("message", {}).get("content", "")
    if not content:
        raise OllamaError(f"Empty model response: {data}")
    return content
