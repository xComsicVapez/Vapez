# Launch the PC agent on Windows.
#   powershell -ExecutionPolicy Bypass -File .\scripts\run-agent.ps1
# Optional:
#   .\scripts\run-agent.ps1 -Model qwen2.5:14b

param(
    [string]$Model = "qwen2.5:7b"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path .\.venv\Scripts\python.exe)) {
    Write-Host "Virtual env missing. Run .\scripts\setup-windows.ps1 first." -ForegroundColor Red
    exit 1
}

& .\.venv\Scripts\python -m pc_agent --model $Model @args
