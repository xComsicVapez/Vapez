# Setup Vapez PC Agent on a Windows gaming PC.
# Run in PowerShell from the repo folder:
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-windows.ps1

$ErrorActionPreference = "Stop"

Write-Host "==> Checking Python..." -ForegroundColor Cyan
python --version

Write-Host "==> Creating virtual environment (.venv)..." -ForegroundColor Cyan
if (-not (Test-Path .venv)) {
    python -m venv .venv
}

Write-Host "==> Installing Python packages..." -ForegroundColor Cyan
.\.venv\Scripts\python -m pip install --upgrade pip
.\.venv\Scripts\pip install -r requirements.txt

Write-Host "==> Checking Ollama..." -ForegroundColor Cyan
$ollama = Get-Command ollama -ErrorAction SilentlyContinue
if (-not $ollama) {
    Write-Host "Ollama is not installed." -ForegroundColor Yellow
    Write-Host "1) Download/install: https://ollama.com/download/windows"
    Write-Host "2) Open Ollama once so the tray app is running"
    Write-Host "3) Re-run this script"
    exit 1
}

Write-Host "==> Pulling a good default model for gaming PCs (qwen2.5:7b)..." -ForegroundColor Cyan
ollama pull qwen2.5:7b

Write-Host ""
Write-Host "Setup complete." -ForegroundColor Green
Write-Host "Start the agent with:"
Write-Host "  .\.venv\Scripts\python -m pc_agent"
Write-Host ""
Write-Host "GPU tip: NVIDIA GPUs are used automatically by Ollama."
Write-Host "If you have lots of VRAM, try:"
Write-Host "  .\.venv\Scripts\python -m pc_agent --model qwen2.5:14b"
Write-Host "  .\.venv\Scripts\python -m pc_agent --model llama3.1:8b"
