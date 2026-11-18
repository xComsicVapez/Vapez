from __future__ import annotations

import argparse
import sys

from rich.console import Console
from rich.panel import Panel

from .agent import PCAgent
from .config import SETTINGS
from .ollama_client import OllamaError, ensure_model, list_models


console = Console()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="pc-agent",
        description="Local AI agent that can control your gaming PC via Ollama.",
    )
    parser.add_argument(
        "--model",
        default=SETTINGS.model,
        help=f"Ollama model name (default: {SETTINGS.model})",
    )
    parser.add_argument(
        "--once",
        metavar="PROMPT",
        help="Run one prompt and exit",
    )
    parser.add_argument(
        "--list-models",
        action="store_true",
        help="List local Ollama models and exit",
    )
    parser.add_argument(
        "--no-confirm",
        action="store_true",
        help="Disable confirmation prompts (dangerous)",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if args.no_confirm:
        SETTINGS.require_confirm = False

    try:
        if args.list_models:
            models = list_models()
            for name in models:
                console.print(f"- {name}")
            return 0

        ensure_model(args.model)
        agent = PCAgent(model=args.model)

        console.print(
            Panel.fit(
                f"[bold]Vapez PC Agent[/bold]\nModel: {args.model}\n"
                f"Confirmations: {'ON' if SETTINGS.require_confirm and not args.no_confirm else 'OFF'}\n"
                "Type your request. Commands: /quit /clear",
                title="Ready on your gaming PC",
            )
        )

        if args.once:
            answer = agent.ask(args.once)
            console.print(answer)
            return 0

        while True:
            try:
                user = console.input("[bold cyan]you>[/bold cyan] ").strip()
            except (EOFError, KeyboardInterrupt):
                console.print("\nBye.")
                return 0
            if not user:
                continue
            if user in {"/quit", "/exit", "quit", "exit"}:
                console.print("Bye.")
                return 0
            if user == "/clear":
                agent = PCAgent(model=args.model)
                console.print("Conversation cleared.")
                continue

            with console.status("Thinking / controlling PC..."):
                try:
                    answer = agent.ask(user)
                except OllamaError as exc:
                    console.print(f"[red]{exc}[/red]")
                    continue
            console.print(f"[bold green]agent>[/bold green] {answer}")
    except OllamaError as exc:
        console.print(f"[red]{exc}[/red]")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
