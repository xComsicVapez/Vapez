#!/usr/bin/env python3
"""Move offline players out of dimensions that are about to be reset.

Usage: move_players_out.py <world-dir> [dimension ...]

Players whose saved position is in one of the given dimensions (default: the
Nether and the End) are moved to the world spawn. Respawn points (beds,
respawn anchors) in those dimensions are cleared. Must run while the server is
stopped, otherwise the server overwrites the edits on save.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

DEFAULT_DIMENSIONS = ("minecraft:the_nether", "minecraft:the_end")


def world_spawn(world):
    _, root = nbt.load(world / "level.dat")
    spawn = root.value["Data"].value["spawn"].value
    x, y, z = spawn["pos"].value
    return spawn["dimension"].value, (x + 0.5, float(y), z + 0.5)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    world = Path(sys.argv[1])
    targets = set(sys.argv[2:]) or set(DEFAULT_DIMENSIONS)
    spawn_dimension, spawn_pos = world_spawn(world)

    moved = 0
    for path in sorted((world / "players" / "data").glob("*.dat")):
        name, root = nbt.load(path)
        data = root.value
        changed = False

        if data.get("Dimension") and data["Dimension"].value in targets:
            data["Dimension"].value = spawn_dimension
            data["Pos"] = nbt.Tag(nbt.LIST, [nbt.Tag(nbt.DOUBLE, v) for v in spawn_pos], nbt.DOUBLE)
            data["Motion"] = nbt.Tag(nbt.LIST, [nbt.Tag(nbt.DOUBLE, 0.0) for _ in range(3)], nbt.DOUBLE)
            data["fall_distance"] = nbt.Tag(nbt.DOUBLE, 0.0)
            changed = True

        respawn = data.get("respawn")
        if respawn and respawn.type == nbt.COMPOUND:
            dimension = respawn.value.get("dimension")
            if dimension and dimension.value in targets:
                del data["respawn"]
                changed = True

        if changed:
            nbt.save(path, name, root)
            moved += 1
            print(f"moved {path.stem} to spawn {spawn_pos}")

    print(f"{moved} player file(s) updated")


if __name__ == "__main__":
    main()
