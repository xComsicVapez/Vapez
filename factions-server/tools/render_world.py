#!/usr/bin/env python3
"""Render a top-down map and a side view of an area straight from the world's region files.

  python3 tools/render_world.py runtime/world --center 0 0 --radius 220 --out arena.png
"""
import argparse
import io
import os
import struct
import zlib

import nbtlib
import numpy as np
from PIL import Image

COLORS = [
    ("water", (52, 92, 210)), ("grass_block", (96, 160, 56)), ("leaves", (48, 110, 36)),
    ("red_nether", (130, 18, 16)), ("stone_brick", (122, 122, 122)), ("brick", (156, 78, 60)),
    ("calcite", (225, 226, 222)), ("dark_oak", (70, 46, 22)), ("spruce", (110, 80, 46)),
    ("birch", (210, 200, 150)), ("oak", (160, 128, 76)), ("dirt_path", (150, 124, 66)),
    ("dirt", (134, 96, 67)), ("quartz", (236, 230, 224)), ("gold", (246, 208, 61)),
    ("cobble", (106, 106, 106)), ("andesite", (138, 138, 138)), ("tuff", (108, 110, 102)),
    ("diorite", (190, 190, 190)), ("lantern", (250, 200, 90)), ("sand", (218, 206, 150)),
    ("snow", (240, 245, 250)), ("stone", (125, 125, 125)), ("log", (100, 80, 50)),
]


def color(name):
    for key, c in COLORS:
        if key in name:
            return c
    return (150, 150, 150)


def read_chunk(region_path, cx, cz):
    with open(region_path, "rb") as f:
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        f.seek(i)
        loc = struct.unpack(">I", f.read(4))[0]
        if loc == 0:
            return None
        f.seek((loc >> 8) * 4096)
        length, comp = struct.unpack(">IB", f.read(5))
        data = f.read(length - 1)
    raw = zlib.decompress(data) if comp == 2 else data
    return nbtlib.File.parse(io.BytesIO(raw))


def chunk_blocks(nbt):
    """-> dict section_y -> (palette names, 4096 indices yzx) for non-empty sections."""
    out = {}
    for sec in nbt.get("sections", []):
        bs = sec.get("block_states")
        if not bs:
            continue
        pal = [str(p["Name"]) for p in bs["palette"]]
        if "data" not in bs:
            idx = np.zeros(4096, dtype=np.int64)
        else:
            bits = max(4, (len(pal) - 1).bit_length())
            per = 64 // bits
            longs = np.array([int(v) & 0xFFFFFFFFFFFFFFFF for v in bs["data"]], dtype=np.uint64)
            shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
            vals = (longs[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)
            idx = vals.reshape(-1)[:4096].astype(np.int64)
        out[int(sec["Y"])] = (pal, idx)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("world")
    ap.add_argument("--center", type=int, nargs=2, default=(0, 0))
    ap.add_argument("--radius", type=int, default=220)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    x0, z0 = a.center[0] - a.radius, a.center[1] - a.radius
    n = 2 * a.radius
    top = np.zeros((n, n, 3), np.uint8) + np.array([140, 190, 240], np.uint8)
    topy = np.full((n, n), -999)
    ymin, ymax = -128, 512
    side = np.zeros((ymax - ymin, n, 3), np.uint8) + np.array([140, 190, 240], np.uint8)
    side_depth = np.full((ymax - ymin, n), 10 ** 9)
    for cx in range(x0 >> 4, (x0 + n - 1 >> 4) + 1):
        for cz in range(z0 >> 4, (z0 + n - 1 >> 4) + 1):
            rp = os.path.join(a.world, "region", f"r.{cx >> 5}.{cz >> 5}.mca")
            if not os.path.exists(rp):
                continue
            nbt = read_chunk(rp, cx, cz)
            if nbt is None:
                continue
            for sy, (pal, idx) in sorted(chunk_blocks(nbt).items()):
                solid = np.array([not p.endswith(("air", "void_air")) for p in pal])
                cols = np.array([color(p) for p in pal], np.uint8)
                grid = idx.reshape(16, 16, 16)              # y, z, x
                for ly in range(16):
                    y = sy * 16 + ly
                    layer = grid[ly]
                    m = solid[layer]
                    if not m.any():
                        continue
                    zs, xs = np.nonzero(m)
                    wx, wz = cx * 16 + xs - x0, cz * 16 + zs - z0
                    ok = (wx >= 0) & (wx < n) & (wz >= 0) & (wz < n)
                    wx, wz, zs, xs = wx[ok], wz[ok], zs[ok], xs[ok]
                    hi = y > topy[wz, wx]
                    topy[wz[hi], wx[hi]] = y
                    top[wz[hi], wx[hi]] = cols[layer[zs[hi], xs[hi]]]
                    if ymin <= y < ymax:
                        row = ymax - 1 - y
                        near = wz < side_depth[row, wx]
                        side_depth[row, wx[near]] = wz[near]
                        side[row, wx[near]] = cols[layer[zs[near], xs[near]]]
    shade = np.clip(0.55 + 0.45 * (topy - 60) / 200.0, 0.4, 1.1)[..., None]
    top = np.clip(top * np.where(topy[..., None] > -999, shade, 1), 0, 255).astype(np.uint8)
    t = Image.fromarray(top).resize((n * 2, n * 2), Image.NEAREST)
    s = Image.fromarray(side[ymax - 300 - 0: ymax - 20]).resize((n * 2, 280 * 2), Image.NEAREST)
    canvas = Image.new("RGB", (n * 2, n * 2 + s.height + 10), (30, 30, 30))
    canvas.paste(t, (0, 0)); canvas.paste(s, (0, n * 2 + 10))
    canvas.save(a.out)
    print(f"wrote {a.out} (top view {n}x{n}, side view from the north, Y 20..300)")


if __name__ == "__main__":
    main()
