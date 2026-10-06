#!/usr/bin/env python3
"""Top-down Aether Citadel floorplan (same geometry as CitadelBuilder) for remote comparison."""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

Y = 64
COLORS = {
    "AIR": (11, 16, 32),
    "LODESTONE": (197, 205, 212),
    "BEACON": (123, 255, 233),
    "CRYING_OBSIDIAN": (75, 30, 109),
    "PURPUR_BLOCK": (201, 160, 220),
    "AMETHYST_BLOCK": (192, 132, 252),
    "POLISHED_DEEPSLATE": (74, 81, 88),
    "DEEPSLATE": (44, 48, 54),
    "DEEPSLATE_TILES": (58, 64, 72),
    "POLISHED_BLACKSTONE": (42, 36, 44),
    "GILDED_BLACKSTONE": (138, 106, 42),
    "SMOOTH_QUARTZ": (232, 224, 216),
    "QUARTZ_PILLAR": (242, 234, 224),
    "OXIDIZED_COPPER": (79, 163, 135),
    "WATER": (59, 111, 160),
    "POLISHED_ANDESITE": (122, 126, 122),
}


def ring(grid: list[list[str]], radius: int, floor: str, accent: str) -> None:
    r = len(grid) // 2
    for z, row in enumerate(grid):
        for x, _ in enumerate(row):
            dx, dz = x - r, z - r
            d2 = dx * dx + dz * dz
            if d2 <= radius * radius and d2 >= (radius - 3) * (radius - 3):
                grid[z][x] = floor if ((dx + dz) & 1) == 0 else accent


def build(radius: int = 72) -> list[list[str]]:
    size = radius * 2 + 1
    grid = [["DEEPSLATE"] * size for _ in range(size)]
    r = radius
    ring(grid, 12, "POLISHED_DEEPSLATE", "DEEPSLATE_TILES")
    ring(grid, 20, "DEEPSLATE_TILES", "AMETHYST_BLOCK")
    ring(grid, 28, "POLISHED_BLACKSTONE", "GILDED_BLACKSTONE")
    ring(grid, 36, "SMOOTH_QUARTZ", "QUARTZ_PILLAR")
    ring(grid, 48, "OXIDIZED_COPPER", "OXIDIZED_COPPER")
    for z in range(size):
        for x in range(size):
            dx, dz = x - r, z - r
            if abs(dx) <= 6 and abs(dz) <= 6:
                grid[z][x] = "AMETHYST_BLOCK"
            if abs(dx) <= 4 and abs(dz) <= 4:
                grid[z][x] = "PURPUR_BLOCK"
            if abs(dx) <= 2 and abs(dz) <= 2:
                grid[z][x] = "CRYING_OBSIDIAN"
            if abs(dx) <= 1 or abs(dz) <= 1:
                if abs(dx) <= 48 and abs(dz) <= 48:
                    grid[z][x] = "POLISHED_ANDESITE"
            if abs(dx) == 56 or abs(dz) == 56:
                if abs(dx) <= 56 and abs(dz) <= 56:
                    grid[z][x] = "DEEPSLATE_TILES"
            if dx * dx + dz * dz > 60 * 60:
                grid[z][x] = "WATER"
            if dx == 0 and dz == 0:
                grid[z][x] = "LODESTONE"
    return grid


def png(path: Path, grid: list[list[str]], scale: int = 3) -> None:
    h = len(grid)
    w = len(grid[0])
    img_w, img_h = w * scale, h * scale
    raw = bytearray()
    for z in range(h):
        for _sy in range(scale):
            raw.append(0)
            for x in range(w):
                rgb = COLORS.get(grid[z][x], (80, 80, 90))
                raw.extend(rgb * scale)
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", img_w, img_h, 8, 2, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    out = Path(__file__).resolve().parents[1] / "docs" / "images" / "citadel-expected-spawn.png"
    png(out, build(), scale=3)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
