#!/usr/bin/env python3
"""Generate original Vapez textures, item models, Bedrock icons, and a Sponge schematic."""
from __future__ import annotations

import json
import math
import os
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RP = ROOT / "pack" / "resourcepack"
BP = ROOT / "pack" / "bedrock-pack"
SCHEM = ROOT / "pack" / "schematics"


def chunk(tag: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", ihdr))
        fh.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        fh.write(chunk(b"IEND", b""))


def lerp(a, b, t):
    return int(a + (b - a) * t)


def gradient_item(c1, c2, sparkle, w=16, h=16):
    px = []
    for y in range(h):
        row = []
        for x in range(w):
            t = (x + y) / (w + h)
            r, g, b = (lerp(c1[i], c2[i], t) for i in range(3))
            # diamond-ish silhouette
            cx, cy = x - 7.5, y - 7.5
            if abs(cx) + abs(cy) > 8.2:
                row.append((0, 0, 0, 0))
                continue
            n = (math.sin(x * 1.7 + y * 0.9) + math.cos(y * 2.1)) * 12
            r = max(0, min(255, r + int(n)))
            g = max(0, min(255, g + int(n * 0.6)))
            b = max(0, min(255, b + int(n * 0.4)))
            if (x + y * 3) % sparkle == 0:
                r = min(255, r + 70)
                g = min(255, g + 70)
                b = min(255, b + 40)
            row.append((r, g, b, 255))
        px.append(row)
    return px


def tool_item(c1, c2, kind: str):
    px = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    # handle
    for i in range(8):
        px[8 + i // 2][i] = (92, 64, 38, 255)
        px[9 + i // 2][i] = (74, 50, 28, 255)
    head = {
        "sword": [(11, 1), (12, 2), (13, 3), (12, 3), (11, 3), (10, 4), (11, 4), (12, 4), (10, 5), (11, 5)],
        "pick": [(8, 1), (9, 1), (10, 1), (11, 1), (12, 1), (8, 2), (12, 2), (7, 3), (13, 3)],
        "axe": [(9, 1), (10, 1), (11, 1), (12, 1), (10, 2), (11, 2), (12, 2), (11, 3), (12, 3)],
        "key": [(4, 4), (5, 4), (6, 4), (5, 5), (5, 6), (5, 7), (5, 8), (6, 8), (7, 8), (5, 10), (6, 10)],
        "heart": [(5, 4), (6, 3), (7, 4), (8, 3), (9, 4), (9, 5), (8, 6), (7, 7), (6, 6), (5, 5)],
        "ingot": [(3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
                  (3, 7), (11, 7), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8)],
        "helm": [(4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (4, 4), (10, 4), (4, 5), (5, 5), (9, 5), (10, 5)],
        "chest": [(5, 3), (10, 3), (4, 4), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4),
                  (4, 5), (11, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6)],
        "legs": [(5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (5, 4), (9, 4), (5, 6), (6, 6), (8, 6), (9, 6), (5, 8), (9, 8)],
        "boots": [(4, 9), (5, 9), (6, 9), (9, 9), (10, 9), (11, 9), (4, 10), (5, 10), (6, 10), (9, 10), (10, 10), (11, 10)],
    }
    pts = head.get(kind, head["sword"])
    for x, y in pts:
        t = (x + y) / 24
        px[y][x] = (lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t), 255)
        if y + 1 < 16:
            px[y + 1][x] = (min(255, px[y][x][0] + 20), min(255, px[y][x][1] + 20), min(255, px[y][x][2] + 20), 255)
    return px


def armor_layer(c1, c2, w=64, h=32):
    px = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]
    # very small humanoid layer: fill the vanilla helmet/chest/leg/boot uv boxes with gradient
    boxes = [
        (8, 0, 16, 8),   # helm top
        (0, 8, 32, 16),  # helm sides
        (16, 16, 40, 32),  # chest
        (0, 16, 16, 32),  # right arm / boot-ish
        (40, 16, 56, 32),
    ]
    for x0, y0, x1, y1 in boxes:
        for y in range(y0, y1):
            for x in range(x0, x1):
                t = (x + y) / (w + h)
                px[y][x] = (
                    lerp(c1[0], c2[0], t),
                    lerp(c1[1], c2[1], t),
                    lerp(c1[2], c2[2], t),
                    220,
                )
    return px


def ore_block():
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            base = (28, 24, 36) if (x + y) % 3 else (18, 16, 26)
            if (x * 3 + y * 7) % 11 == 0:
                base = (123, 255, 233)
            elif (x * 5 + y) % 9 == 0:
                base = (192, 132, 252)
            row.append((*base, 255))
        px.append(row)
    return px


TEAL = (123, 255, 233)
VIOLET = (192, 132, 252)
GOLD = (251, 191, 36)
RED = (248, 113, 113)


def item_model_def(model: str, handheld: bool = False) -> dict:
    parent = "minecraft:item/handheld" if handheld else "minecraft:item/generated"
    return {
        "model": {
            "type": "minecraft:model",
            "model": model,
        }
    }, {
        "parent": parent,
        "textures": {"layer0": model.replace("vapez:item/", "vapez:item/") if False else model.replace("vapez:", "vapez:")},
    }


def write_item(name: str, texture_path: str, handheld: bool = False) -> None:
    item_def = {
        "model": {
            "type": "minecraft:model",
            "model": f"vapez:item/{name}",
        }
    }
    model = {
        "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
        "textures": {"layer0": f"vapez:item/{name}"},
    }
    (RP / "assets/vapez/items" / f"{name}.json").write_text(json.dumps(item_def, indent=2) + "\n")
    (RP / "assets/vapez/models/item" / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")


def nbt_string(s: str) -> bytes:
    enc = s.encode("utf-8")
    return struct.pack(">H", len(enc)) + enc


def nbt_compound(pairs: list[tuple[int, str, bytes]]) -> bytes:
    out = b""
    for tag, name, payload in pairs:
        out += bytes([tag]) + nbt_string(name) + payload
    return out + b"\x00"


def write_schematic() -> None:
    """Minimal Sponge schematic v2 of a 21x12x21 lodestone plaza (import with FAWE)."""
    width, height, length = 21, 12, 21
    palette = {
        "minecraft:air": 0,
        "minecraft:polished_deepslate": 1,
        "minecraft:amethyst_block": 2,
        "minecraft:crying_obsidian": 3,
        "minecraft:lodestone": 4,
        "minecraft:beacon": 5,
        "minecraft:iron_block": 6,
        "minecraft:soul_lantern": 7,
        "minecraft:deepslate_bricks": 8,
    }
    blocks = bytearray()

    def pal_id(name: str) -> int:
        return palette[name]

    def set_block(x, y, z, name):
        idx = (y * length + z) * width + x
        blocks[idx] = pal_id(name)

    blocks.extend(b"\x00" * (width * height * length))
    for x in range(width):
        for z in range(length):
            set_block(x, 0, z, "minecraft:polished_deepslate")
            if min(x, z, width - 1 - x, length - 1 - z) == 0:
                set_block(x, 1, z, "minecraft:deepslate_bricks")
            if 8 <= x <= 12 and 8 <= z <= 12:
                set_block(x, 0, z, "minecraft:amethyst_block")
            if 9 <= x <= 11 and 9 <= z <= 11:
                set_block(x, 0, z, "minecraft:crying_obsidian")
            if x in (0, 20) and z in (0, 20):
                for y in range(1, 8):
                    set_block(x, y, z, "minecraft:deepslate_bricks")
                set_block(x, 8, z, "minecraft:soul_lantern")
    for x in range(8, 13):
        for z in range(8, 13):
            set_block(x, 0, z, "minecraft:iron_block")
    set_block(10, 0, 10, "minecraft:lodestone")
    set_block(10, 1, 10, "minecraft:beacon")

    pal_compound = nbt_compound(
        [(3, k, struct.pack(">i", v)) for k, v in palette.items()]  # TAG_Int = 3
    )
    # TAG_Short=2, TAG_Int=3, TAG_IntArray=11, TAG_Compound=10, TAG_ByteArray=7
    # Sponge v2 uses BlockData as byte array of palette indices (varint, here all < 128)
    payload = nbt_compound(
        [
            (2, "Width", struct.pack(">h", width)),
            (2, "Height", struct.pack(">h", height)),
            (2, "Length", struct.pack(">h", length)),
            (3, "Version", struct.pack(">i", 2)),
            (3, "DataVersion", struct.pack(">i", 4189)),  # 1.21.8-ish
            (3, "PaletteMax", struct.pack(">i", len(palette))),
            (10, "Palette", pal_compound),
            (7, "BlockData", struct.pack(">i", len(blocks)) + bytes(blocks)),
            (10, "Offset", nbt_compound([
                (3, "x", struct.pack(">i", -10)),
                (3, "y", struct.pack(">i", 0)),
                (3, "z", struct.pack(">i", -10)),
            ])),
            (8, "Name", nbt_string("vapez-citadel-plaza")),
            (8, "Author", nbt_string("Vapez")),
        ]
    )
    root = bytes([10]) + nbt_string("Schematic") + payload
    SCHEM.mkdir(parents=True, exist_ok=True)
    with (SCHEM / "vapez-citadel.schem").open("wb") as fh:
        fh.write(zlib.compress(root, 9))
    # FAWE also accepts uncompressed .schem in some builds; keep gzip-like zlib
    # Write a gzip wrapper which Sponge uses.
    import gzip
    with gzip.open(SCHEM / "vapez-citadel.schem", "wb") as fh:
        fh.write(root)


def bedrock_item_texture(names: list[str]) -> None:
    data = {
        "resource_pack_name": "vapez",
        "texture_name": "atlas.items",
        "texture_data": {
            name: {"textures": f"textures/items/{name.split('.', 1)[-1]}"} for name in names
        },
    }
    (BP / "textures" / "item_texture.json").write_text(json.dumps(data, indent=2) + "\n")


def zip_packs() -> None:
    rp_zip = ROOT / "pack" / "resourcepack" / "Vapez-Aetherium.zip"
    # zip contents of resourcepack excluding the zip itself
    with zipfile.ZipFile(rp_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in RP.rglob("*"):
            if path.suffix == ".zip" or not path.is_file():
                continue
            zf.write(path, path.relative_to(RP))
    mcpack = ROOT / "pack" / "plugins" / "Geyser-Spigot" / "packs" / "vapez-aetherium.mcpack"
    mcpack.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(mcpack, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in BP.rglob("*"):
            if not path.is_file():
                continue
            zf.write(path, path.relative_to(BP))


def main() -> None:
    items = {
        "aetherium_crystal": gradient_item(TEAL, VIOLET, 5),
        "aetherium_ingot": tool_item(TEAL, VIOLET, "ingot"),
        "sovereign_edge": tool_item(GOLD, TEAL, "sword"),
        "aetherium_sword": tool_item(TEAL, VIOLET, "sword"),
        "aetherium_pickaxe": tool_item(TEAL, VIOLET, "pick"),
        "aetherium_axe": tool_item(VIOLET, TEAL, "axe"),
        "aetherium_helmet": tool_item(TEAL, VIOLET, "helm"),
        "aetherium_chestplate": tool_item(TEAL, VIOLET, "chest"),
        "aetherium_leggings": tool_item(TEAL, VIOLET, "legs"),
        "aetherium_boots": tool_item(TEAL, VIOLET, "boots"),
        "heart": tool_item(RED, GOLD, "heart"),
        "crate_key": tool_item(GOLD, TEAL, "key"),
    }
    handheld = {"sovereign_edge", "aetherium_sword", "aetherium_pickaxe", "aetherium_axe"}
    for name, pixels in items.items():
        write_png(RP / "assets/vapez/textures/item" / f"{name}.png", pixels)
        write_png(BP / "textures/items" / f"{name}.png", pixels)
        write_item(name, f"vapez:item/{name}", name in handheld)
    write_png(RP / "assets/vapez/textures/block/aetherium_ore.png", ore_block())
    write_png(RP / "assets/vapez/textures/entity/equipment/humanoid/aetherium.png", armor_layer(TEAL, VIOLET))
    (RP / "assets/vapez/equipment/aetherium.json").write_text(json.dumps({
        "layers": {
            "humanoid": [{"texture": "vapez:aetherium"}],
            "humanoid_leggings": [{"texture": "vapez:aetherium"}],
        }
    }, indent=2) + "\n")
    bedrock_item_texture(["vapez." + n for n in items])
    write_schematic()
    zip_packs()
    print("Generated textures, models, schematic, Java zip, Bedrock mcpack.")


if __name__ == "__main__":
    main()
