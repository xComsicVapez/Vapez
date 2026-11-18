from __future__ import annotations

import re
from dataclasses import dataclass


DANGEROUS_PATTERNS = [
    r"\brm\s+(-[a-zA-Z]*f[a-zA-Z]*\s+)?(/|~|\*)",
    r"\bdel\s+/[sSfFqQ]",
    r"\brmdir\s+/s",
    r"\bformat\b",
    r"\bmkfs\b",
    r"\bdd\s+if=",
    r"\bshutdown\b",
    r"\breboot\b",
    r"\bpoweroff\b",
    r"\bRemove-Item\b.*-Recurse",
    r"\bStop-Computer\b",
    r"\bRestart-Computer\b",
    r"\breg\s+delete\b",
    r"\bdiskpart\b",
    r":\(\)\s*\{\s*:\|:&\s*\};:",
]


@dataclass
class RiskAssessment:
    risky: bool
    reason: str


def assess_shell_command(command: str) -> RiskAssessment:
    text = command.strip()
    if not text:
        return RiskAssessment(True, "Empty command")
    for pattern in DANGEROUS_PATTERNS:
        if re.search(pattern, text, flags=re.IGNORECASE):
            return RiskAssessment(True, f"Matched dangerous pattern: {pattern}")
    return RiskAssessment(False, "")


def confirm(action: str, details: str) -> bool:
    print("\nConfirmation required")
    print(f"Action : {action}")
    print(f"Details: {details}")
    answer = input("Allow this? [y/N]: ").strip().lower()
    return answer in {"y", "yes"}
