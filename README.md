# Vapez PC Agent

Local AI assistant that can control **your gaming PC** when you ask it to.

This does **not** train a brand-new frontier model. It runs a strong local model with [Ollama](https://ollama.com) on your machine (CPU + GPU), then uses tools to open apps, run commands, browse, type, and manage files.

## What you need

- Windows gaming PC (your i7-13th gen is fine)
- Python 3.10+
- [Ollama for Windows](https://ollama.com/download/windows)
- Enough RAM/VRAM for the model you pick

### Model size guide (rough)

| Hardware | Suggested model | Command flag |
|---|---|---|
| 8–12 GB VRAM or CPU-only | `qwen2.5:7b` | default |
| 12–16 GB VRAM | `qwen2.5:14b` or `llama3.1:8b` | `--model qwen2.5:14b` |
| 24 GB+ VRAM | `qwen2.5:32b` | `--model qwen2.5:32b` |

NVIDIA GPUs are used automatically by Ollama. AMD/Intel still work, usually slower.

## Quick setup (Windows)

In PowerShell, from this repo folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-windows.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-agent.ps1
```

Or manually:

```powershell
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
ollama pull qwen2.5:7b
.\.venv\Scripts\python -m pc_agent
```

## Example asks

- "Open Discord and Chrome"
- "What's my RAM usage?"
- "Open Steam"
- "List files on my Desktop"
- "Open youtube.com"

Risky actions (shell commands, typing, file writes) ask for **y/N** confirmation first.

## Safety

- Confirmations are ON by default
- Dangerous shell patterns are flagged
- Only run this on your own PC
- Do not use `--no-confirm` unless you accept full risk

## One-shot mode

```powershell
.\.venv\Scripts\python -m pc_agent --once "Open Notepad"
```

## Config

| Env var | Default | Meaning |
|---|---|---|
| `PC_AGENT_MODEL` | `qwen2.5:7b` | Ollama model |
| `PC_AGENT_CONFIRM` | `1` | Confirmations on/off |
| `OLLAMA_HOST` | `http://127.0.0.1:11434` | Ollama URL |
