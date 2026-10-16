#!/usr/bin/env python3
"""Generate the "Kit PvP" sky-plateau map as a datapack of fill/setblock commands.

A green mountain plateau just under the clouds with a walled castle (round
towers, gatehouse), a red-roofed palace with a clock tower, a village of small
timber houses, dense forest, and four leap-of-faith bridges with water pools
below so players can get down to the survival world.

  python3 tools/build_arena.py --out runtime/world/datapacks/vapez-arena [--preview out.png]

In game / console:  function vapez:arena/build {x:0,y:180,z:0}
(x,z = centre of the plateau, y = plateau surface; 180 sits right under the clouds)
"""
import argparse
import json
import math
import os
import re
import shutil

import numpy as np

R = 205                      # half-size of the modelled area (blocks)
YMIN, YMAX = -150, 80        # relative to the plateau surface
FORCE_FROM = -6              # at/above this, blocks overwrite the world; below, they only fill air/water
NX = NZ = 2 * R + 1
NY = YMAX - YMIN + 1
FILL_LIMIT = 32768
PART_VOLUME = 350_000        # blocks changed per tick while building
EDGE_R = 132                 # mean plateau radius
BRIDGE_LEN = 44

KEEP, AIR = 0, 1
ATTACH = re.compile(r"lantern|torch|door|bell|ladder|water|lightning_rod|chain|flower_pot|button|banner|campfire")

rng = np.random.default_rng(31943)
palette = ["", "minecraft:air"]
pal_index = {"": 0, "minecraft:air": 1}
G = np.zeros((NX, NY, NZ), dtype=np.int16)
H = np.zeros((NX, NZ), dtype=np.int32)      # surface height of each column (relative Y)
reserved = np.zeros((NX, NZ), dtype=bool)   # no trees here


def pid(name):
    if not name.startswith("minecraft:") and ":" not in name.split("[")[0]:
        name = "minecraft:" + name
    if name not in pal_index:
        pal_index[name] = len(palette)
        palette.append(name)
    return pal_index[name]


def ix(x):
    return x + R


def iy(y):
    return y - YMIN


def mat(spec, shape):
    """spec: palette id, block name or [(name, weight), ...] -> int16 array of `shape`."""
    if isinstance(spec, (int, np.integer)):
        return np.full(shape, spec, dtype=np.int16)
    if isinstance(spec, str):
        return np.full(shape, pid(spec), dtype=np.int16)
    ids = np.array([pid(n) for n, _ in spec], dtype=np.int16)
    w = np.array([w for _, w in spec], dtype=float)
    return rng.choice(ids, size=shape, p=w / w.sum())


def box(x0, y0, z0, x1, y1, z1, spec):
    x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
    x0, z0, y0 = max(x0, -R), max(z0, -R), max(y0, YMIN)
    x1, z1, y1 = min(x1, R), min(z1, R), min(y1, YMAX)
    if x0 > x1 or y0 > y1 or z0 > z1:
        return
    sl = (slice(ix(x0), ix(x1) + 1), slice(iy(y0), iy(y1) + 1), slice(ix(z0), ix(z1) + 1))
    G[sl] = mat(spec, G[sl].shape)


def put(x, y, z, spec):
    box(x, y, z, x, y, z, spec)


def noise2(scale, seed, octaves=3):
    """Smooth value noise in [-1, 1] over the model grid."""
    r = np.random.default_rng(seed)
    total = np.zeros((NX, NZ))
    amp_sum = 0
    for o in range(octaves):
        s = scale / (2 ** o)
        n = int(NX / s) + 3
        grid = r.uniform(-1, 1, (n, n))
        c = np.arange(NX) / s
        i0 = np.floor(c).astype(int)
        t = c - i0
        t = t * t * (3 - 2 * t)
        a = grid[i0][:, i0] * (1 - t)[None, :] + grid[i0][:, i0 + 1] * t[None, :]
        b = grid[i0 + 1][:, i0] * (1 - t)[None, :] + grid[i0 + 1][:, i0 + 1] * t[None, :]
        total += (a * (1 - t)[:, None] + b * t[:, None]) * 0.5 ** o
        amp_sum += 0.5 ** o
    return total / amp_sum


# ---------------------------------------------------------------- terrain
STONE_STRATA = ["minecraft:stone", "minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:stone", "minecraft:diorite"]
STRATA_IDS = np.array([pid(n) for n in STONE_STRATA], dtype=np.int16)
CLIFF = [("stone", 5), ("andesite", 2), ("cobblestone", 1), ("mossy_cobblestone", 1), ("tuff", 1)]

X, Z = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
RAD = np.hypot(X, Z)
edge = EDGE_R + 14 * noise2(70, 1)
D = RAD - edge                                   # <0 on the plateau
hills = np.clip(noise2(36, 2), -0.2, 1) * 4
castle_zone = (np.abs(X) <= 56) & (Z >= -82) & (Z <= 32)
flatten = np.clip((np.maximum(np.abs(X) - 56, 0) + np.maximum(-82 - Z, 0) + np.maximum(Z - 32, 0)) / 20, 0, 1)
top = np.round(hills * flatten).astype(int)
cliff_w = 26 + 8 * noise2(25, 3)
drop = -150 * np.clip(D / cliff_w, 0, 1) ** 0.6 + 6 * noise2(9, 4) * (D > 0)
step = np.where(drop > -45, 3, 6)
terraced = np.floor(drop / step) * step
surface = np.where(D <= 0, top, terraced).astype(int)
surface = np.clip(surface, YMIN, YMAX)
H[:] = surface

gx, gz = np.gradient(surface.astype(float))
steep = np.hypot(gx, gz) > 1.6
strata_shift = (np.floor((noise2(45, 7) + 1) * 1.5) * 2).astype(int)
cliff_pick = np.clip(((noise2(10, 5) + 1) / 2 * len(CLIFF)).astype(int), 0, len(CLIFF) - 1)
for xi in range(NX):
    for zi in range(NZ):
        s = surface[xi, zi]
        if s <= YMIN:
            continue
        col = G[xi, :, zi]
        ys = np.arange(YMIN, s + 1)
        col[: s - YMIN + 1] = STRATA_IDS[((ys + strata_shift[xi, zi]) // 6) % len(STONE_STRATA)]
        if steep[xi, zi] and D[xi, zi] > 0:
            col[s - YMIN] = pid("minecraft:" + CLIFF[cliff_pick[xi, zi]][0])
        else:
            col[max(s - YMIN - 3, 0): s - YMIN] = pid("minecraft:dirt")
            col[s - YMIN] = pid("minecraft:grass_block")
        if D[xi, zi] <= 2:
            col[s - YMIN + 1:] = AIR      # clear any natural terrain poking through


def flatten_area(x0, z0, x1, z1, y, top_spec="grass_block", ring=1):
    """Level the ground to Y=y over a rectangle (plus a ring), keeping fill below."""
    for x in range(x0 - ring, x1 + ring + 1):
        for z in range(z0 - ring, z1 + ring + 1):
            if abs(x) > R or abs(z) > R:
                continue
            box(x, y + 1, z, x, YMAX, z, AIR)
            box(x, y - 3, z, x, y - 1, z, "dirt")
            put(x, y, z, top_spec)
            H[ix(x), ix(z)] = y


# ---------------------------------------------------------------- building helpers
STONE = [("stone_bricks", 6), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("cobblestone", 1)]
PAVE = [("polished_andesite", 3), ("stone_bricks", 3), ("cobblestone", 2), ("mossy_cobblestone", 1), ("andesite", 1)]
ROOFS = {
    "red": ("red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab"),
    "brick": ("bricks", "brick_stairs", "brick_slab"),
    "dark": ("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab"),
    "spruce": ("spruce_planks", "spruce_stairs", "spruce_slab"),
}


def cylinder(cx, cz, r, y0, y1, spec, hollow=None):
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.3 and (hollow is None or d > hollow + 0.3):
                box(x, y0, z, x, y1, z, spec)


def cone(cx, cz, r, y0, height, roof="red"):
    solid, _, _ = ROOFS[roof]
    for h in range(height):
        rr = (r + 1) * (1 - h / height)
        if rr < 0.6:
            break
        cylinder(cx, cz, int(round(rr)), y0 + h, y0 + h, solid)
    put(cx, y0 + height, cz, solid)
    put(cx, y0 + height + 1, cz, "lightning_rod")


def round_tower(cx, cz, r, height, roof="red", door_dir=None):
    cylinder(cx, cz, r, 1, height, STONE)
    cylinder(cx, cz, r - 2, 1, height - 1, AIR)
    for fy in range(6, height - 1, 6):
        cylinder(cx, cz, r - 2, fy, fy, "spruce_planks")
        put(cx, fy, cz, AIR)
    box(cx, 1, cz, cx, height - 1, cz, AIR)
    for y in range(1, height):
        put(cx, y, cz + 1, "stone_bricks")
        put(cx, y, cz, "ladder[facing=north]")
    for a in range(0, 360, 45):
        for y in (4, 5, 10, 11, 16, 17):
            if y < height - 1:
                put(cx + round((r) * math.cos(math.radians(a))), y, cz + round((r) * math.sin(math.radians(a))), AIR)
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if r - 0.7 <= d <= r + 0.3 and (x + z) % 2 == 0:
                put(x, height + 1, z, STONE)
    cone(cx, cz, r - 1, height + 2, 2 * r + 2, roof)


def rot_facing(block, rot):
    order = ["north", "east", "south", "west"]
    return re.sub(r"facing=(\w+)", lambda m: "facing=" + order[(order.index(m.group(1)) + rot) % 4], block)


def gable_x(x0, x1, z0, z1, yb, roof, wall):
    """Gable roof over [x0,x1]x[z0,z1] with the ridge running along X."""
    solid, stairs, slab = ROOFS[roof]
    k = 0
    while True:
        zl, zr, y = z0 - 1 + k, z1 + 1 - k, yb + k
        if zl > zr:
            break
        if zl == zr:
            box(x0 - 1, y, zl, x1 + 1, y, zl, f"{slab}[type=bottom]")
            break
        box(x0 - 1, y, zl, x1 + 1, y, zl, f"{stairs}[facing=south]")
        box(x0 - 1, y, zr, x1 + 1, y, zr, f"{stairs}[facing=north]")
        if zl + 1 <= zr - 1:
            box(x0, y, zl + 1, x1, y, zr - 1, wall)
            if zl + 2 <= zr - 2:
                box(x0 + 1, y, zl + 2, x1 - 1, y, zr - 2, AIR)
        k += 1


def gable_z(x0, x1, z0, z1, yb, roof, wall):
    solid, stairs, slab = ROOFS[roof]
    k = 0
    while True:
        xl, xr, y = x0 - 1 + k, x1 + 1 - k, yb + k
        if xl > xr:
            break
        if xl == xr:
            box(xl, y, z0 - 1, xl, y, z1 + 1, f"{slab}[type=bottom]")
            break
        box(xl, y, z0 - 1, xl, y, z1 + 1, f"{stairs}[facing=east]")
        box(xr, y, z0 - 1, xr, y, z1 + 1, f"{stairs}[facing=west]")
        if xl + 1 <= xr - 1:
            box(xl + 1, y, z0, xr - 1, y, z1, wall)
            if xl + 2 <= xr - 2:
                box(xl + 2, y, z0 + 1, xr - 2, y, z1 - 1, AIR)
        k += 1


def pyramid(x0, x1, z0, z1, yb, roof="red"):
    solid, stairs, _ = ROOFS[roof]
    k = 0
    while x0 + k <= x1 - k and z0 + k <= z1 - k:
        a, b, c, d, y = x0 + k, x1 - k, z0 + k, z1 - k, yb + k
        box(a, y, c, b, y, d, solid)
        box(a, y, c, b, y, c, f"{stairs}[facing=south]")
        box(a, y, d, b, y, d, f"{stairs}[facing=north]")
        box(a, y, c + 1, a, y, d - 1, f"{stairs}[facing=east]")
        box(b, y, c + 1, b, y, d - 1, f"{stairs}[facing=west]")
        k += 1
    return yb + k


# ---------------------------------------------------------------- castle
CW_X, CW_Z0, CW_Z1 = 46, -72, 22      # curtain wall: x in [-46,46], z in [-72,22]
WALL_H = 13


def castle():
    flatten_area(-CW_X - 8, CW_Z0 - 8, CW_X + 8, CW_Z1 + 8, 0)
    reserved[ix(-CW_X - 8):ix(CW_X + 8) + 1, ix(CW_Z0 - 8):ix(CW_Z1 + 8) + 1] = True
    box(-CW_X + 3, 0, CW_Z0 + 3, CW_X - 3, 0, CW_Z1 - 3, PAVE)
    for x0, z0, x1, z1 in [(-CW_X, CW_Z0, CW_X, CW_Z0 + 2), (-CW_X, CW_Z1 - 2, CW_X, CW_Z1),
                           (-CW_X, CW_Z0, -CW_X + 2, CW_Z1), (CW_X - 2, CW_Z0, CW_X, CW_Z1)]:
        box(x0, 1, z0, x1, WALL_H - 1, z1, STONE)
    box(-CW_X + 1, WALL_H - 1, CW_Z0 + 1, CW_X - 1, WALL_H - 1, CW_Z0 + 1, "spruce_planks")
    for x in range(-CW_X, CW_X + 1):
        if x % 2 == 0:
            put(x, WALL_H, CW_Z0, STONE); put(x, WALL_H, CW_Z1, STONE)
    for z in range(CW_Z0, CW_Z1 + 1):
        if z % 2 == 0:
            put(-CW_X, WALL_H, z, STONE); put(CW_X, WALL_H, z, STONE)
    for x in range(-CW_X + 1, CW_X):
        put(x, WALL_H, CW_Z0 + 2, "stone_brick_wall"); put(x, WALL_H, CW_Z1 - 2, "stone_brick_wall")
    for z in range(CW_Z0 + 1, CW_Z1):
        put(-CW_X + 2, WALL_H, z, "stone_brick_wall"); put(CW_X - 2, WALL_H, z, "stone_brick_wall")
    # stairs up to the wall walk on both sides of the gate
    for i in range(WALL_H - 1):
        for side in (-1, 1):
            box(side * (14 + i), i + 1, CW_Z1 - 5, side * (14 + i), i + 1, CW_Z1 - 3,
                f"stone_brick_stairs[facing={'west' if side < 0 else 'east'}]")
            box(side * (14 + i), 1, CW_Z1 - 5, side * (14 + i), i, CW_Z1 - 3, STONE)
    for cx, cz in [(-CW_X, CW_Z0), (CW_X, CW_Z0), (-CW_X, CW_Z1), (CW_X, CW_Z1)]:
        round_tower(cx, cz, 7, 21)
    for cx, cz in [(-CW_X, (CW_Z0 + CW_Z1) // 2), (CW_X, (CW_Z0 + CW_Z1) // 2), (0, CW_Z0)]:
        round_tower(cx, cz, 5, 17)
    # gatehouse
    for gx in (-8, 8):
        round_tower(gx, CW_Z1 + 1, 5, 18)
    box(-4, 1, CW_Z1 - 3, 4, 15, CW_Z1 + 3, STONE)
    for x in range(-3, 4):
        arch_top = 8 + (1 if abs(x) <= 1 else 0) - (1 if abs(x) == 3 else 0)
        box(x, 1, CW_Z1 - 3, x, arch_top, CW_Z1 + 3, AIR)
        put(x, 1, CW_Z1 - 3, AIR)
    box(-3, 9, CW_Z1 + 3, 3, 9, CW_Z1 + 3, "iron_bars")
    for x in range(-4, 5, 2):
        put(x, 16, CW_Z1 + 3, STONE); put(x, 16, CW_Z1 - 3, STONE)
    for x in (-5, 5):
        put(x, 6, CW_Z1 + 4, "lantern")
        put(x, 5, CW_Z1 + 4, "stone_bricks")
    # fountain
    fx, fz = 0, -8
    cylinder(fx, fz, 6, 1, 1, "stone_bricks")
    cylinder(fx, fz, 5, 1, 1, "water")
    cylinder(fx, fz, 5, 0, 0, "stone_bricks")
    cylinder(fx, fz, 1, 1, 4, "chiseled_stone_bricks")
    put(fx, 5, fz, "water")
    for x, z in [(-20, -10), (20, -10), (-20, 10), (20, 10), (-10, 14), (10, 14)]:
        box(x, 1, z, x, 3, z, "stone_brick_wall")
        put(x, 4, z, "lantern")


def palace():
    wall = [("calcite", 6), ("smooth_stone", 1)]
    x0, x1, z0, z1 = -30, 30, -66, -38
    box(x0, 1, z0, x1, 3, z1, STONE)
    box(x0, 4, z0, x1, 16, z1, wall)
    box(x0 + 1, 1, z0 + 1, x1 - 1, 15, z1 - 1, AIR)
    for fy in (5, 10):
        box(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, "spruce_planks")
    for x in range(x0, x1 + 1, 6):
        box(x, 1, z0, x, 16, z0, "stone_bricks"); box(x, 1, z1, x, 16, z1, "stone_bricks")
    for x in range(x0 + 3, x1 - 1, 6):
        for wy in (2, 7, 12):
            box(x - 1, wy, z0, x + 1, wy + 2, z0, "glass_pane")
            box(x - 1, wy, z1, x + 1, wy + 2, z1, "glass_pane")
    for z in range(z0 + 4, z1 - 2, 6):
        for wy in (7, 12):
            box(x0, wy, z - 1, x0, wy + 2, z + 1, "glass_pane"); box(x1, wy, z - 1, x1, wy + 2, z + 1, "glass_pane")
    gable_x(x0, x1, z0, z1, 17, "red", "calcite")
    for wx0, wx1 in ((-42, -31), (31, 42)):
        box(wx0, 1, -62, wx1, 3, -42, STONE)
        box(wx0, 4, -62, wx1, 12, -42, wall)
        box(wx0 + 1, 1, -61, wx1 - 1, 11, -43, AIR)
        box(wx0 + 1, 6, -61, wx1 - 1, 6, -43, "spruce_planks")
        for z in range(-58, -44, 5):
            box(wx0, 8, z, wx0, 10, z + 1, "glass_pane"); box(wx1, 8, z, wx1, 10, z + 1, "glass_pane")
        gable_z(wx0, wx1, -62, -42, 13, "red", "calcite")
        box(wx0 + 4, 1, -42, wx0 + 6, 4, -42, AIR)
    for dx in (-24, 24):
        box(dx - 2, 1, -38, dx + 2, 4, -38, AIR)
    # grand entrance steps
    for i in range(3):
        box(-4, i, -32 + i, 4, i, -32 + i, "stone_brick_stairs[facing=north]")
    clock_tower()


def clock_tower():
    x0, x1, z0, z1, top = -5, 5, -44, -32, 44
    box(x0, 1, z0, x1, top, z1, STONE)
    box(x0 + 1, 1, z0 + 1, x1 - 1, top - 1, z1 - 1, AIR)
    box(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, "spruce_planks")
    box(-2, 1, z1, 2, 5, z1, AIR)
    box(-1, 6, z1, 1, 6, z1, AIR)
    for y in range(1, top + 4):
        put(0, y, z0 + 1, "stone_bricks")
        put(0, y, z0 + 2, "ladder[facing=south]")
    for x in (x0, x1):
        for y in range(8, 32, 6):
            box(x, y, -39, x, y + 2, -37, "glass_pane")
    for y in range(8, 32, 6):
        box(-1, y, z1, 1, y + 2, z1, "glass_pane")
    cy = 37
    # clock faces on all four sides (hands at 10:10)
    faces = [("z", z1 + 1, "north"), ("z", z0 - 1, "south"), ("x", x1 + 1, "west"), ("x", x0 - 1, "east")]
    for axis, c, _ in faces:
        for u in range(-5, 6):
            for v in range(-5, 6):
                d = math.hypot(u, v)
                b = None
                if d <= 4.3:
                    b = "quartz_block"
                if 4.3 < d <= 5.3:
                    b = "gold_block"
                if b and (u, v) in {(0, 0), (-1, 1), (-2, 2), (-3, 2), (1, 1), (2, 2), (3, 3)}:
                    b = "black_concrete"
                if b and d <= 4.3 and (abs(u), abs(v)) in {(0, 4), (4, 0)}:
                    b = "black_concrete"
                if b:
                    if axis == "x":
                        put(c, cy + v, -38 + u, b)
                    else:
                        put(u, cy + v, c, b)
    # belfry
    bel = top + 1
    box(x0, bel, z0, x1, bel + 6, z1, STONE)
    box(x0 + 1, bel, z0 + 1, x1 - 1, bel + 5, z1 - 1, AIR)
    box(-2, bel, z0, 2, bel + 4, z0, AIR); box(-2, bel, z1, 2, bel + 4, z1, AIR)
    box(x0, bel, -40, x0, bel + 4, -36, AIR); box(x1, bel, -40, x1, bel + 4, -36, AIR)
    box(x0 + 1, bel - 1, z0 + 1, x1 - 1, bel - 1, z1 - 1, "spruce_planks")
    put(0, bel - 1, z0 + 2, AIR)
    put(0, bel + 5, -38, "spruce_planks")
    put(0, bel + 4, -38, "bell[attachment=ceiling,facing=north]")
    peak = pyramid(x0 - 1, x1 + 1, z0 - 1, z1 + 1, bel + 7, "red")
    box(0, peak, -38, 0, peak + 2, -38, "red_nether_brick_wall")
    put(0, peak + 3, -38, "lightning_rod")


# ---------------------------------------------------------------- village
def house(x0, z0, w, d, rot, roof, rng_h):
    """Small timber house. Canonical frame: door on the north (front) side."""
    y0 = int(rng_h)
    fw, fd = (w, d) if rot in (0, 2) else (d, w)
    flatten_area(x0, z0, x0 + fw - 1, z0 + fd - 1, y0, "grass_block", ring=2)
    reserved[ix(x0 - 3):ix(x0 + fw + 2) + 1, ix(z0 - 3):ix(z0 + fd + 2) + 1] = True

    def P(lx, lz):
        if rot == 0: return x0 + lx, z0 + lz
        if rot == 2: return x0 + w - 1 - lx, z0 + d - 1 - lz
        if rot == 1: return x0 + d - 1 - lz, z0 + lx
        return x0 + lz, z0 + w - 1 - lx

    def lput(lx, ly, lz, b):
        x, z = P(lx, lz)
        put(x, y0 + ly, z, rot_facing(b, rot) if isinstance(b, str) else b)

    two = d >= 9
    top = 8 if two else 4
    solid, stairs, slab = ROOFS[roof]
    for lx in range(w):
        for lz in range(d):
            edge_cell = lx in (0, w - 1) or lz in (0, d - 1)
            corner = lx in (0, w - 1) and lz in (0, d - 1)
            lput(lx, 0, lz, "cobblestone" if edge_cell else "spruce_planks")
            for ly in range(1, top + 1):
                if not edge_cell:
                    lput(lx, ly, lz, "minecraft:air")
                elif corner:
                    lput(lx, ly, lz, "oak_log" if ly > 2 or not two else "cobblestone")
                elif ly <= 2 and two:
                    lput(lx, ly, lz, [("cobblestone", 3), ("stone_bricks", 2), ("mossy_cobblestone", 1)])
                elif ly == 5 and two:
                    lput(lx, ly, lz, "stripped_oak_log[axis=x]" if lz in (0, d - 1) else "stripped_oak_log[axis=z]")
                else:
                    lput(lx, ly, lz, "calcite")
            if two and not (lx in (0, w - 1) or lz in (0, d - 1)):
                lput(lx, 5, lz, "spruce_planks")
    for lx in range(2, w - 2, 3):
        for ly in ((2, 3) if not two else (3, 7)):
            lput(lx, ly, 0, "glass_pane"); lput(lx, ly, d - 1, "glass_pane")
    for lz in range(2, d - 2, 3):
        for ly in ((2,) if not two else (2, 7)):
            lput(0, ly, lz, "glass_pane"); lput(w - 1, ly, lz, "glass_pane")
    dx = w // 2
    lput(dx, 1, 0, "spruce_door[facing=north,half=lower]")
    lput(dx, 2, 0, "spruce_door[facing=north,half=upper]")
    lput(dx - 1, 1, -1, "oak_fence")
    lput(dx - 1, 2, -1, "lantern")
    if two:
        lput(1, 6, 1, "minecraft:air")
        for ly in range(1, 6):
            lput(1, ly, 1, "ladder[facing=south]")
    # gable roof, ridge front-to-back
    k = 0
    while True:
        lxl, lxr, ly = -1 + k, w - k, top + 1 + k
        if lxl > lxr:
            break
        for lz in range(-1, d + 1):
            if lxl == lxr:
                lput(lxl, ly, lz, f"{slab}[type=bottom]")
            else:
                lput(lxl, ly, lz, f"{stairs}[facing=east]")
                lput(lxr, ly, lz, f"{stairs}[facing=west]")
                if lz in (0, d - 1):
                    for lx in range(lxl + 1, lxr):
                        lput(lx, ly, lz, "calcite" if not (k == 0) else "stripped_oak_log[axis=x]")
        if lxl == lxr:
            break
        k += 1
    if rng.random() < 0.5:
        for ly in range(1, top + k + 3):
            lput(w - 2, ly, d - 2, "bricks" if ly > top else "minecraft:air")
    if rng.random() < 0.6:
        for lx in range(0, w):
            lput(lx, 1, -2, "oak_fence" if lx != dx else "minecraft:air")
        lput(-1, 1, -2, "oak_fence"); lput(w, 1, -2, "oak_fence")


def path(x0, z0, x1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if D[ix(x), ix(z)] < -4:
                y = H[ix(x), ix(z)]
                put(x, y, z, [("dirt_path", 6), ("coarse_dirt", 1), ("cobblestone", 1)])
                box(x, y + 1, z, x, y + 3, z, AIR)
                reserved[ix(x), ix(z)] = True


def village():
    houses = []
    streets = [("z", 0, CW_Z1 + 4, 124), ("x", 58, -96, 96), ("x", 96, -88, 88),
               ("z", -60, 30, 110), ("z", 60, 30, 110)]
    for axis, c, a, b in streets:
        if axis == "z":
            path(c - 2, a, c + 2, b)
        else:
            path(a, c - 1, b, c + 1)
    for x in range(-9, 10):
        for z in range(68, 87):
            if math.hypot(x, z - 77) <= 9 and D[ix(x), ix(z)] < -4:
                put(x, H[ix(x), ix(z)], z, PAVE)
                reserved[ix(x), ix(z)] = True
    wy = H[ix(0), ix(77)]
    cylinder(0, 77, 2, wy + 1, wy + 1, "cobblestone")
    put(0, wy + 1, 77, "water")
    for x, z in [(-2, 75), (2, 75), (-2, 79), (2, 79)]:
        box(x, wy + 2, z, x, wy + 3, z, "oak_fence")
    box(-2, wy + 4, 75, 2, wy + 4, 79, "spruce_slab[type=bottom]")
    roof_cycle = ["red", "brick", "dark", "red", "spruce", "brick"]
    n = 0
    for axis, c, a, b in streets:
        for t in range(a + 6, b - 6, 14):
            for side in (-1, 1):
                w, d = (7, 9) if rng.random() < 0.6 else (7, 7)
                if axis == "z":
                    rot = 1 if side < 0 else 3
                    fw, fd = d, w
                    hx = c - 5 - fw if side < 0 else c + 5
                    hz = t
                else:
                    rot = 2 if side < 0 else 0
                    fw, fd = w, d
                    hx = t
                    hz = c - 4 - fd if side < 0 else c + 4
                corners = [(hx - 3, hz - 3), (hx + fw + 2, hz - 3), (hx - 3, hz + fd + 2), (hx + fw + 2, hz + fd + 2)]
                if any(abs(px) > R or abs(pz) > R or D[ix(px), ix(pz)] > -6 for px, pz in corners):
                    continue
                if any(reserved[ix(px), ix(pz)] for px in range(hx - 1, hx + fw + 1) for pz in range(hz - 1, hz + fd + 1)):
                    continue
                if abs(hx + fw / 2) < 14 and 68 - 14 < hz + fd / 2 < 86 + 10:
                    continue
                gy = int(np.median(H[ix(hx):ix(hx + fw), ix(hz):ix(hz + fd)]))
                house(hx, hz, w, d, rot, roof_cycle[n % len(roof_cycle)], gy)
                houses.append((hx, hz))
                n += 1
    return houses


# ---------------------------------------------------------------- bridges & trees
def bridges():
    pools = []
    for dx, dz, facing in [(0, -1, "north"), (1, 0, "east"), (0, 1, "south"), (-1, 0, "west")]:
        # walk out from the centre to the plateau edge along this axis
        r = 60
        while D[ix(dx * r), ix(dz * r)] < -1:
            r += 1
        y = int(H[ix(dx * (r - 4)), ix(dz * (r - 4))])
        start, end = r - 8, r + BRIDGE_LEN
        for s in range(start, end + 1):
            for w in (-2, -1, 0, 1, 2):
                x, z = dx * s + (w if dz else 0), dz * s + (w if dx else 0)
                if abs(w) == 2:
                    put(x, y + 1, z, "stone_brick_wall")
                    if s % 6 == 0 and s < end - 2:
                        put(x, y + 2, z, "lantern")
                put(x, y, z, "stone_bricks" if abs(w) < 2 else "chiseled_stone_bricks")
                if abs(w) < 2:
                    box(x, y + 1, z, x, y + 4, z, AIR)
                reserved[ix(x), ix(z)] = True
            if s > r + 2 and s % 8 == 0:
                for w in (-2, 2):
                    x, z = dx * s + (w if dz else 0), dz * s + (w if dx else 0)
                    box(x, y - 8, z, x, y - 1, z, "stone_brick_wall")
        # diving board at the far end, no railing
        for s in range(end - 2, end + 1):
            for w in (-2, 2):
                x, z = dx * s + (w if dz else 0), dz * s + (w if dx else 0)
                put(x, y + 1, z, AIR)
        pools.append((dx * (end + 6), dz * (end + 6)))
    return pools


def trees():
    feats = [("minecraft:fancy_oak", 3), ("minecraft:oak", 4), ("minecraft:birch", 2),
             ("minecraft:spruce", 2), ("minecraft:dark_oak", 1)]
    names = [f for f, _ in feats]
    weights = np.array([w for _, w in feats], float)
    out = []
    taken = np.zeros_like(reserved)
    order = rng.permutation(NX * NZ)
    for k in order:
        xi, zi = divmod(int(k), NZ)
        if D[xi, zi] > -5 or reserved[xi, zi] or taken[xi, zi]:
            continue
        if G[xi, iy(H[xi, zi]), zi] != pid("minecraft:grass_block"):
            continue
        spacing = 3
        taken[max(xi - spacing, 0):xi + spacing + 1, max(zi - spacing, 0):zi + spacing + 1] = True
        f = names[rng.choice(len(names), p=weights / weights.sum())]
        out.append((xi - R, int(H[xi, zi]) + 1, zi - R, f))
    decor = []
    for _ in range(900):
        xi, zi = rng.integers(0, NX), rng.integers(0, NZ)
        if D[xi, zi] < -3 and not reserved[xi, zi]:
            decor.append((xi - R, int(H[xi, zi]) + 1, zi - R,
                          rng.choice(["minecraft:patch_grass_taiga_2", "minecraft:flower_default", "minecraft:patch_tall_grass"])))
    return out, decor


# ---------------------------------------------------------------- compile
def compile_boxes():
    """Greedy-merge the voxel grid into <=32768-block boxes: [(cat, y, cmd, volume)]."""
    out = []
    active = {}
    for yi in range(NY):
        layer = G[:, yi, :]
        rects = set()
        for v in np.unique(layer):
            if v == KEEP:
                continue
            mask = layer == v
            open_runs = {}
            for xi in range(NX + 1):
                runs = set()
                if xi < NX and mask[xi].any():
                    e = np.flatnonzero(np.diff(np.concatenate(([0], mask[xi].view(np.int8), [0]))))
                    runs = {(int(a), int(b) - 1) for a, b in zip(e[::2], e[1::2])}
                for key in [k for k in open_runs if k not in runs]:
                    rects.add((int(v), open_runs.pop(key), xi - 1, key[0], key[1]))
                for key in runs:
                    open_runs.setdefault(key, xi)
        for key in [k for k in active if k not in rects]:
            out.append((key, active.pop(key), yi - 1))
        for key in rects:
            active.setdefault(key, yi)
    for key, y0 in active.items():
        out.append((key, y0, NY - 1))

    cmds = []
    for (v, x0, x1, z0, z1), y0, y1 in out:
        name = palette[v]
        dx, dz = x1 - x0 + 1, z1 - z0 + 1
        xs = max(1, FILL_LIMIT // dz)
        for xa in range(x0, x1 + 1, xs):
            xb = min(xa + xs - 1, x1)
            ys = max(1, FILL_LIMIT // ((xb - xa + 1) * dz))
            for ya in range(y0, y1 + 1, ys):
                yb = min(ya + ys - 1, y1)
                ry0, ry1 = ya + YMIN, yb + YMIN
                force = ry0 >= FORCE_FROM
                if not force and ry1 >= FORCE_FROM:
                    # split at the force line so the upper part overwrites the world
                    parts = [(ry0, FORCE_FROM - 1, False), (FORCE_FROM, ry1, True)]
                else:
                    parts = [(ry0, ry1, force)]
                for pa, pb, f in parts:
                    a = (xa - R, pa, z0 - R)
                    b = (xb - R, pb, z1 - R)
                    vol = (xb - xa + 1) * (pb - pa + 1) * dz
                    if v == AIR and not f:
                        continue
                    suffix = "" if f else " replace #vapez:fillable"
                    if vol == 1:
                        cmd = f"setblock ~{a[0]} ~{a[1]} ~{a[2]} {name}"
                    else:
                        cmd = f"fill ~{a[0]} ~{a[1]} ~{a[2]} ~{b[0]} ~{b[1]} ~{b[2]} {name}{suffix}"
                    cat = 0 if v == AIR else (2 if ATTACH.search(name) else 1)
                    cmds.append((cat, pa, cmd, vol))
    cmds.sort(key=lambda c: (c[0], c[1]))
    return cmds


def write_pack(out_dir, cmds, tree_list, decor, pools):
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    fdir = os.path.join(out_dir, "data", "vapez", "function", "arena")
    os.makedirs(os.path.join(fdir, "p"))
    os.makedirs(os.path.join(out_dir, "data", "vapez", "tags", "block"))
    json.dump({"pack": {"pack_format": 48, "description": "Vapez Factions: Kit PvP sky plateau"}},
              open(os.path.join(out_dir, "pack.mcmeta"), "w"), indent=2)
    json.dump({"values": ["#minecraft:replaceable", "#minecraft:leaves", "#minecraft:logs",
                          "#minecraft:flowers", "#minecraft:saplings", "minecraft:water",
                          "minecraft:lava", "minecraft:bamboo", "minecraft:sugar_cane",
                          "minecraft:kelp", "minecraft:kelp_plant", "minecraft:cactus",
                          "minecraft:snow_block", "minecraft:ice", "minecraft:powder_snow"]},
              open(os.path.join(out_dir, "data", "vapez", "tags", "block", "fillable.json"), "w"), indent=2)

    parts, cur, vol = [], [], 0
    for _, _, cmd, v in cmds:
        if cur and (vol + v > PART_VOLUME or len(cur) >= 4000):
            parts.append(cur); cur, vol = [], 0
        cur.append(cmd); vol += v
    if cur:
        parts.append(cur)
    for i in range(0, len(tree_list), 120):
        parts.append([f"place feature {f} ~{x} ~{y} ~{z}" for x, y, z, f in tree_list[i:i + 120]])
    for i in range(0, len(decor), 300):
        parts.append([f"place feature {f} ~{x} ~{y} ~{z}" for x, y, z, f in decor[i:i + 300]])
    final = [f"execute positioned ~{px} 400 ~{pz} positioned over motion_blocking_no_leaves run fill ~-4 ~-3 ~-4 ~4 ~-1 ~4 minecraft:water"
             for px, pz in pools]
    final += ["setworldspawn ~ ~1 ~10", "gamerule spawnRadius 0"]
    parts.append(final)

    n = len(parts)
    for i, body in enumerate(parts):
        open(os.path.join(fdir, "p", f"{i}.mcfunction"), "w").write("\n".join(body) + "\n")
        open(os.path.join(fdir, "p", f"{i}_at.mcfunction"), "w").write(
            f"$execute positioned $(x) $(y) $(z) run function vapez:arena/p/{i}\n")
        nxt = f"schedule function vapez:arena/step_{i + 1} 1t" if i + 1 < n else "function vapez:arena/done"
        msg = f'tellraw @a [{{"text":"[Arena] ","color":"gold"}},{{"text":"building {100 * i // n}%","color":"gray"}}]\n' if i % 10 == 0 else ""
        open(os.path.join(fdir, f"step_{i}.mcfunction"), "w").write(
            f"{msg}function vapez:arena/p/{i}_at with storage vapez:arena origin\n{nxt}\n")

    c = R + 5
    quads = [(-c, -c, -1, -1), (0, -c, c, -1), (-c, 0, -1, c), (0, 0, c, c)]
    fl_add = "\n".join(f"$execute positioned $(x) $(y) $(z) run forceload add ~{a} ~{b} ~{c2} ~{d}" for a, b, c2, d in quads)
    fl_rem = "\n".join(f"$execute positioned $(x) $(y) $(z) run forceload remove ~{a} ~{b} ~{c2} ~{d}" for a, b, c2, d in quads)
    open(os.path.join(fdir, "build.mcfunction"), "w").write(
        "# function vapez:arena/build {x:0,y:180,z:0}\n"
        "$data modify storage vapez:arena origin set value {x:$(x),y:$(y),z:$(z)}\n"
        f"{fl_add}\n"
        "gamerule maxCommandChainLength 10000000\n"
        'tellraw @a [{"text":"[Arena] ","color":"gold"},{"text":"loading chunks, this can take a minute...","color":"gray"}]\n'
        "say Building the Kit PvP map, expect lag for a minute or two...\n"
        "schedule function vapez:arena/wait 40t\n")
    # Forceloaded chunks load (and generate) asynchronously; commands in a chunk that isn't
    # ready yet silently do nothing, so wait until every chunk in the area is loaded.
    probes = [(a, b) for a in range(-c, c + 1, 16) for b in range(-c, c + 1, 16)]
    open(os.path.join(fdir, "wait.mcfunction"), "w").write("function vapez:arena/wait_at with storage vapez:arena origin\n")
    open(os.path.join(fdir, "wait_at.mcfunction"), "w").write(
        "".join(f"$execute positioned $(x) $(y) $(z) unless loaded ~{a} ~ ~{b} run return run schedule function vapez:arena/wait 20t\n"
                for a, b in probes) + "schedule function vapez:arena/step_0 1t\n")
    open(os.path.join(fdir, "done.mcfunction"), "w").write("function vapez:arena/done_at with storage vapez:arena origin\n")
    open(os.path.join(fdir, "done_at.mcfunction"), "w").write(
        f"{fl_rem}\n"
        "gamerule maxCommandChainLength 65536\n"
        "say Kit PvP map finished.\n"
        'tellraw @a [{"text":"[Arena] ","color":"gold"},{"text":"Kit PvP map finished! World spawn is now the castle courtyard.","color":"green"}]\n')
    return n


def preview(path):
    from PIL import Image
    colors = {}
    def color(name):
        if name not in colors:
            base = name.split("[")[0].split(":")[-1]
            table = [("red_nether", (114, 2, 0)), ("stone_brick", (122, 122, 122)), ("brick", (150, 74, 58)),
                     ("grass_block", (95, 159, 53)), ("dirt_path", (148, 122, 65)), ("dirt", (134, 96, 67)),
                     ("calcite", (223, 224, 220)), ("dark_oak", (66, 43, 20)), ("spruce", (114, 84, 48)),
                     ("water", (64, 100, 220)), ("cobble", (110, 110, 110)), ("andesite", (136, 136, 136)),
                     ("quartz", (235, 229, 222)), ("gold", (246, 208, 61)), ("oak", (162, 130, 78)),
                     ("stone", (125, 125, 125))]
            colors[name] = next((v for k, v in table if k in base), (150, 150, 150))
        return colors[name]
    img = np.zeros((NZ, NX, 3), np.uint8) + np.array([140, 190, 240], np.uint8)
    for xi in range(NX):
        col = G[xi]
        for zi in range(NZ):
            nz = np.flatnonzero(col[:, zi] > AIR)
            if len(nz):
                yi = nz[-1]
                c = np.array(color(palette[col[yi, zi]]), float)
                shade = 0.6 + 0.4 * (yi / NY)
                img[zi, xi] = np.clip(c * shade, 0, 255)
    Image.fromarray(img).resize((NX * 2, NZ * 2), Image.NEAREST).save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True)
    ap.add_argument("--preview")
    a = ap.parse_args()
    castle(); palace(); village()
    pools = bridges()
    tree_list, decor = trees()
    cmds = compile_boxes()
    n = write_pack(a.out, cmds, tree_list, decor, pools)
    total = sum(c[3] for c in cmds)
    print(f"Arena: {len(cmds)} fill/setblock commands ({total:,} blocks), {len(tree_list)} trees, "
          f"{n} build steps -> {a.out}")
    if a.preview:
        preview(a.preview)


if __name__ == "__main__":
    main()
