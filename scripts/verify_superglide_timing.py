#!/usr/bin/env python3
"""Verify Jump→Crouch delays against the apexmovement.tech chance formula."""

from __future__ import annotations


def chance(fps: float, delay_ms: float) -> tuple[float, float, str]:
    frame_s = 1.0 / fps
    delay_s = delay_ms / 1000.0
    frames = delay_s / frame_s
    if frames < 1:
        c = frames * 100.0
        tip = f"crouch later by {1 - frames:.2f} frames"
    elif frames < 2:
        c = (2.0 - frames) * 100.0
        tip = f"crouch sooner by {frames - 1:.2f} frames"
    else:
        c = 0.0
        tip = f"too late by {frames - 1:.2f} frames"
    return frames, c, tip


def main() -> None:
    fps = 240.0
    ideal = 1000.0 / fps
    samples = [0.0, 2.0, 3.5, ideal, 4.17, 5.0, 6.0, 8.0, 10.0]
    print(f"FPS={fps:.0f}  ideal_delay_ms={ideal:.4f}")
    print(f"{'delay_ms':>10} {'frames':>8} {'chance%':>9}  tip")
    for d in samples:
        frames, c, tip = chance(fps, d)
        print(f"{d:10.4f} {frames:8.3f} {c:9.2f}  {tip}")

    # Profile default travel estimate: 0.2mm @ 48mm/s
    gap_mm = 0.2
    speed = 48.0
    travel_ms = (gap_mm / speed) * 1000.0
    frames, c, tip = chance(fps, travel_ms)
    print()
    print(f"release gap {gap_mm} mm @ {speed} mm/s => {travel_ms:.3f} ms")
    print(f"  frames={frames:.3f} chance={c:.2f}% ({tip})")


if __name__ == "__main__":
    main()
