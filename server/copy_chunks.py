#!/usr/bin/env python3
"""Copy a rectangle of chunks (blocks, entities and POI) from one dimension folder to another.

Usage: copy_chunks.py <src-dim-dir> <dst-dim-dir> <x1> <z1> <x2> <z2> [--replace old=new ...]

<x1> <z1> <x2> <z2> are block coordinates of opposite corners. A dimension folder is the one
holding region/, entities/ and poi/ (e.g. world/dimensions/minecraft/overworld). Copied chunks
overwrite whatever the destination has at those positions. --replace swaps a block id in the
copied chunks' palettes (e.g. note_block=stone); blocks below y=0 become deepslate instead of stone.
The server must be stopped.
"""

import struct
import sys
import time
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402


def read_region(path):
    chunks = {}
    if not path.exists() or path.stat().st_size < 8192:
        return chunks
    data = path.read_bytes()
    for index in range(1024):
        offset = int.from_bytes(data[index * 4:index * 4 + 3], "big") * 4096
        if offset:
            (length,) = struct.unpack_from(">i", data, offset)
            chunks[index] = data[offset + 4:offset + 4 + length]
    return chunks


def write_region(path, chunks):
    header, body = bytearray(8192), bytearray()
    now = int(time.time())
    for index in sorted(chunks):
        payload = struct.pack(">i", len(chunks[index])) + chunks[index]
        payload += b"\x00" * (-len(payload) % 4096)
        sector = 2 + len(body) // 4096
        header[index * 4:index * 4 + 4] = (sector << 8 | len(payload) // 4096).to_bytes(4, "big")
        header[4096 + index * 4:4096 + index * 4 + 4] = now.to_bytes(4, "big")
        body += payload
    tmp = path.with_suffix(".mca.tmp")
    tmp.write_bytes(bytes(header) + bytes(body))
    tmp.replace(path)


def replace_blocks(raw, replacements):
    if raw[0] != 2:
        return raw, 0
    reader = nbt._Reader(zlib.decompress(raw[1:]))
    (root_type,) = reader.take(">b")
    name = reader.string()
    root = reader.payload(root_type)
    changed = 0
    for section in root.value.get("sections", nbt.Tag(nbt.LIST, [])).value:
        states = section.value.get("block_states")
        if not states or "palette" not in states.value:
            continue
        for entry in states.value["palette"].value:
            new = replacements.get(entry.value["Name"].value)
            if new:
                if new == "minecraft:stone" and section.value["Y"].value < 0:
                    new = "minecraft:deepslate"
                entry.value = {"Name": nbt.Tag(nbt.STRING, new)}
                changed += 1
    out = bytearray(struct.pack(">b", root_type))
    nbt._write_string(out, name)
    nbt._write_payload(out, root)
    return b"\x02" + zlib.compress(bytes(out)), changed


def main():
    args = sys.argv[1:]
    replacements = {}
    if "--replace" in args:
        at = args.index("--replace")
        for pair in args[at + 1:]:
            old, new = (part if ":" in part else "minecraft:" + part for part in pair.split("="))
            replacements[old] = new
        args = args[:at]
    src, dst = Path(args[0]), Path(args[1])
    x1, z1, x2, z2 = (int(v) for v in args[2:6])
    cx1, cx2 = sorted((x1 // 16, x2 // 16))
    cz1, cz2 = sorted((z1 // 16, z2 // 16))
    for kind in ("region", "entities", "poi"):
        (dst / kind).mkdir(parents=True, exist_ok=True)
        copied = cleaned = 0
        for rx in range(cx1 // 32, cx2 // 32 + 1):
            for rz in range(cz1 // 32, cz2 // 32 + 1):
                name = f"r.{rx}.{rz}.mca"
                source = read_region(src / kind / name)
                if not source:
                    continue
                target = read_region(dst / kind / name)
                for index, raw in source.items():
                    x, z = rx * 32 + index % 32, rz * 32 + index // 32
                    if not (cx1 <= x <= cx2 and cz1 <= z <= cz2):
                        continue
                    if raw[0] & 0x80:
                        oversized = f"c.{x}.{z}.mcc"
                        (dst / kind / oversized).write_bytes((src / kind / oversized).read_bytes())
                    elif kind == "region" and replacements:
                        raw, changed = replace_blocks(raw, replacements)
                        cleaned += changed
                    target[index] = raw
                    copied += 1
                write_region(dst / kind / name, target)
        print(f"{kind}: copied {copied} chunks" + (f", replaced {cleaned} palette entries" if cleaned else ""))


if __name__ == "__main__":
    main()
