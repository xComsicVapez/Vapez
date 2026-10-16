#!/usr/bin/env python3
"""Report where given blocks appear in a world's region files (block palettes only).

Usage: region_scan.py <region-dir> <block-id> [block-id ...]
       region_scan.py <region-dir> --built <center-x> <center-z> <radius-chunks>

The first form prints, per region file, how many 16x16x16 sections contain each block.
The second form prints the bounding box of chunks near a point that contain
player-made blocks (planks, glass, stairs, ...), to find the extent of a build.
"""

import re
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

BUILT = re.compile(r"minecraft:(.*_planks|.*glass.*|.*_stairs|.*_slab|.*_fence.*|.*_wall|.*wool|.*concrete.*|"
                   r".*bricks?|lantern|.*lantern|torch|wall_torch|.*_door|.*_trapdoor|bookshelf|.*_carpet|"
                   r"chest|barrel|crafting_table|furnace|.*_bed|.*_sign|.*hanging_sign|smooth_.*|polished_.*|"
                   r"chiseled_.*|quartz_.*|.*_log|stripped_.*|.*_terracotta|note_block|.*_banner|flower_pot|potted_.*)$")
NATURAL_LOGS = re.compile(r"minecraft:(oak|birch|spruce|jungle|acacia|dark_oak|mangrove|cherry|pale_oak)_log$")


def chunks(region_path):
    data = region_path.read_bytes()
    if len(data) < 8192:
        return
    rx, rz = map(int, region_path.stem.split(".")[1:3])
    for index in range(1024):
        offset = int.from_bytes(data[index * 4:index * 4 + 3], "big") * 4096
        if not offset:
            continue
        length, compression = struct.unpack_from(">ib", data, offset)
        raw = data[offset + 5:offset + 4 + length]
        if compression == 2:
            raw = zlib.decompress(raw)
        elif compression != 3:
            continue
        reader = nbt._Reader(raw)
        (root_type,) = reader.take(">b")
        reader.string()
        yield rx * 32 + index % 32, rz * 32 + index // 32, reader.payload(root_type)


def palettes(chunk):
    for section in chunk.value.get("sections", nbt.Tag(nbt.LIST, [])).value:
        states = section.value.get("block_states")
        if states and "palette" in states.value:
            yield section.value["Y"].value, [entry.value["Name"].value for entry in states.value["palette"].value]


def main():
    region_dir = Path(sys.argv[1])
    if sys.argv[2] == "--built":
        cx, cz, radius = int(sys.argv[3]) // 16, int(sys.argv[4]) // 16, int(sys.argv[5])
        found = []
        for path in sorted(region_dir.glob("r.*.mca")):
            for x, z, chunk in chunks(path):
                if abs(x - cx) > radius or abs(z - cz) > radius:
                    continue
                score = sum(1 for _, names in palettes(chunk) for name in names
                            if BUILT.match(name) and not NATURAL_LOGS.match(name))
                if score >= 3:
                    found.append((x, z, score))
        if not found:
            print("no built chunks found")
            return
        xs, zs = [f[0] for f in found], [f[1] for f in found]
        print(f"{len(found)} built chunks; blocks x {min(xs) * 16}..{max(xs) * 16 + 15}, z {min(zs) * 16}..{max(zs) * 16 + 15}")
        return
    targets = {name if ":" in name else "minecraft:" + name for name in sys.argv[2:]}
    for path in sorted(region_dir.glob("r.*.mca")):
        counts = dict.fromkeys(targets, 0)
        example = {}
        for x, z, chunk in chunks(path):
            for y, names in palettes(chunk):
                for name in targets.intersection(names):
                    counts[name] += 1
                    example.setdefault(name, (x * 16, y * 16, z * 16))
        if any(counts.values()):
            print(path.name, {k.split(":")[1]: v for k, v in counts.items()}, "e.g.", example)


if __name__ == "__main__":
    main()
