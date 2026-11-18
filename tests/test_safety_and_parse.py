from __future__ import annotations

import unittest

from pc_agent.agent import _extract_json
from pc_agent.safety import assess_shell_command
from pc_agent.tools import tool_system_info


class SafetyTests(unittest.TestCase):
    def test_safe_command(self) -> None:
        result = assess_shell_command("Get-Process | Select-Object -First 5")
        self.assertFalse(result.risky)

    def test_risky_command(self) -> None:
        result = assess_shell_command("Remove-Item C:\\Temp -Recurse -Force")
        self.assertTrue(result.risky)


class ParseTests(unittest.TestCase):
    def test_plain_json(self) -> None:
        payload = _extract_json('{"type":"final","message":"hi"}')
        self.assertEqual(payload["type"], "final")

    def test_fenced_json(self) -> None:
        text = """Sure.
```json
{"type":"tool","name":"system_info","args":{}}
```
"""
        payload = _extract_json(text)
        self.assertEqual(payload["name"], "system_info")


class ToolSmokeTests(unittest.TestCase):
    def test_system_info(self) -> None:
        result = tool_system_info({})
        self.assertTrue(result.ok)
        self.assertIn("os=", result.output)


if __name__ == "__main__":
    unittest.main()
