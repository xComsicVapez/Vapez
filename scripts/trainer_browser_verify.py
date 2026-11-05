#!/usr/bin/env python3
"""Drive apexmovement.tech trainer and measure Jump→Crouch chance at 240 FPS."""

from __future__ import annotations

import json
import time
from pathlib import Path

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import WebDriverWait

ARTIFACTS = Path("/opt/cursor/artifacts")
ARTIFACTS.mkdir(parents=True, exist_ok=True)


def make_driver() -> webdriver.Chrome:
    opts = Options()
    opts.add_argument("--window-size=1400,1000")
    opts.add_argument("--disable-gpu")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    # Prefer headed if DISPLAY exists so screenshots look real; fall back to headless.
    import os

    if not os.environ.get("DISPLAY"):
        opts.add_argument("--headless=new")
    return webdriver.Chrome(options=opts)


def set_fps(driver: webdriver.Chrome, fps: int) -> None:
    # Prefer UI button if present; also force via local store used by the app.
    driver.execute_script(
        """
        const fps = arguments[0];
        try {
          const raw = localStorage.getItem('settings');
          let s = raw ? JSON.parse(raw) : {};
          if (s && typeof s === 'object') {
            s.fps = fps;
            if (s.mnk) {
              s.mnk.jump = s.mnk.jump || {type: 0, bind: ' '};
              s.mnk.crouch = {type: 0, bind: 'Control'};
            } else {
              s.mnk = {
                jump: {type: 0, bind: ' '},
                crouch: {type: 0, bind: 'Control'}
              };
            }
            localStorage.setItem('settings', JSON.stringify(s));
          }
        } catch (e) {}
        // Also poke any svelte stores if exposed
        window.__TARGET_FPS = fps;
        """,
        fps,
    )
    driver.refresh()
    time.sleep(1.2)
    driver.execute_script(
        """
        const fps = arguments[0];
        // Click FPS button / set input if the UI exposes one
        const buttons = [...document.querySelectorAll('button')];
        const fpsBtn = buttons.find(b => /fps/i.test(b.textContent || ''));
        if (fpsBtn) fpsBtn.click();
        const inputs = [...document.querySelectorAll('input')];
        for (const inp of inputs) {
          if ((inp.type === 'number' || inp.type === 'text') && /fps|frame/i.test(
                (inp.getAttribute('aria-label')||'') + (inp.previousElementSibling?.textContent||'')
              )) {
            inp.value = String(fps);
            inp.dispatchEvent(new Event('input', {bubbles:true}));
            inp.dispatchEvent(new Event('change', {bubbles:true}));
          }
        }
        """,
        fps,
    )


def start_training(driver: webdriver.Chrome) -> None:
    buttons = driver.find_elements(By.CSS_SELECTOR, "button")
    for b in buttons:
        txt = (b.text or "").strip().lower()
        if txt in {"start", "start training", "train"} or "start" in txt:
            b.click()
            time.sleep(0.4)
            return
    # Some builds auto-listen; click body to focus.
    driver.find_element(By.TAG_NAME, "body").click()


def fire_pair(driver: webdriver.Chrome, delay_ms: float) -> None:
    # Use async JS so the trainer's Date() timestamps see a real gap.
    driver.execute_async_script(
        """
        const delayMs = arguments[0];
        const done = arguments[arguments.length - 1];
        const fire = (key) => {
          const opts = {key, code: key === ' ' ? 'Space' : 'ControlLeft', bubbles: true, cancelable: true};
          if (key === 'Control') opts.ctrlKey = true;
          window.dispatchEvent(new KeyboardEvent('keydown', opts));
        };
        fire(' ');
        setTimeout(() => {
          fire('Control');
          setTimeout(done, 30);
        }, delayMs);
        """,
        delay_ms,
    )


def read_stats(driver: webdriver.Chrome) -> dict:
    return driver.execute_script(
        """
        const text = document.body.innerText;
        const pick = (re) => {
          const m = text.match(re);
          return m ? m[1] : null;
        };
        return {
          bodySnippet: text.slice(0, 2500),
          consistency: pick(/Overall superglide consistency:\\s*([0-9.]+%)/i),
          attempts: pick(/Attempts:\\s*([0-9]+)/i),
          chanceLines: [...text.matchAll(/([0-9.]+)% chance to hit the superglide/gi)].map(m => m[0]),
          tips: [...text.matchAll(/Crouch (later|sooner) by[^\\n]+/gi)].map(m => m[0]),
          fpsHint: pick(/([0-9]+)\\s*FPS/i),
        };
        """
    )


def main() -> None:
    driver = make_driver()
    results = {"fps": 240, "attempts": []}
    try:
        driver.get("https://apexmovement.tech/superglidetrainer/")
        WebDriverWait(driver, 20).until(lambda d: d.find_element(By.TAG_NAME, "body"))
        time.sleep(1.5)

        # Force settings via localStorage then reload
        driver.execute_script(
            """
            localStorage.setItem('settings', JSON.stringify({
              fps: 240,
              mnk: {
                jump: {type: 0, bind: ' '},
                crouch: {type: 0, bind: 'Control'}
              },
              controller: {jump: 0, crouch: 1}
            }));
            """
        )
        driver.refresh()
        time.sleep(1.5)

        start_training(driver)
        driver.save_screenshot(str(ARTIFACTS / "trainer-240-ready.png"))

        delays = [2.0, 4.1667, 4.17, 6.0, 10.0]
        for d in delays:
            before = read_stats(driver)
            fire_pair(driver, d)
            time.sleep(0.35)
            after = read_stats(driver)
            results["attempts"].append({"delay_ms": d, "before": before, "after": after})
            print(f"delay={d}ms tips={after.get('tips')} chances={after.get('chanceLines')}")

        driver.save_screenshot(str(ARTIFACTS / "trainer-240-after-attempts.png"))

        # Open local calculator
        driver.get("file:///workspace/wooting-apex-superglide/timing-calculator.html")
        time.sleep(0.8)
        driver.save_screenshot(str(ARTIFACTS / "timing-calculator-verified.png"))

        out = ARTIFACTS / "trainer-selenium-results.json"
        out.write_text(json.dumps(results, indent=2))
        print("wrote", out)
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
