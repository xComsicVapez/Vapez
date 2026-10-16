"""Live test of the survival tools with a throwaway Carpet bot far from players; cleans up after itself."""
import json, re, sys, time
from pathlib import Path

stage = sys.argv[1] if len(sys.argv) > 1 else "all"
sys.argv = ["rc", "/home/cosmic/lifesteal2"]
sys.path.insert(0, "/home/cosmic/server-tools")
import ai_admin_bridge as b
from ai_player import AIPlayer, base_plan

SERVER = Path("/home/cosmic/lifesteal2")
NAME = "AITest"
X, Z = 2648, 2840


def log(*parts):
    print(time.strftime("%H:%M:%S"), *parts, flush=True)


def run(cmd):
    return b.run(cmd)


bot = AIPlayer({"bot": {"name": NAME, "spawn": "223.5 114 35.5", "enabled": True}}, run, None,
               lambda m: log("AUDIT", m), SERVER / "logs" / "latest.log")
bot.base_file = SERVER / "config" / f"ai-player-base-{NAME}.json"
bot.enabled = True


def surface():
    top = int(bot.scarpet(f"top('terrain', [{X}, 0, {Z}])"))
    return top


def spawn():
    if not bot.online():
        log(run(f"player {NAME} spawn at {X}.5 {surface() + 1} {Z}.5 facing 0 0 in minecraft:overworld in survival"))
        time.sleep(3)
    run(f"effect clear {NAME}")
    run(f"clear {NAME}")
    log("position", bot.position())


def give(item, count=1):
    log(run(f"give {NAME} minecraft:{item} {count}"))


if stage in ("all", "tunnel"):
    spawn()
    give("iron_pickaxe")
    give("cooked_beef", 16)
    run(f"player {NAME} hotbar 1")
    log("TUNNEL down:", bot.tunnel("west", 10, "down"))
    log("TUNNEL straight:", bot.tunnel("north", 12, "straight"))
    log("inventory:", bot.counts())

if stage in ("all", "smelt"):
    spawn()
    give("furnace")
    give("raw_iron", 3)
    give("coal", 1)
    x, y, z = (int(v // 1) for v in bot.position())
    spot = None
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        if bot.block_at(x + dx, y, z + dz) in ("air", "short_grass") and bot.scarpet(f"solid({x + dx},{y - 1},{z + dz})") == "true":
            spot = (x + dx, y, z + dz)
            break
    log("place furnace:", bot.place_block(*spot, "furnace"))
    log("smelt:", bot.smelt("raw_iron", 3))
    log("inventory:", bot.counts())
    log("cleanup furnace:", run(f"setblock {spot[0]} {spot[1]} {spot[2]} air"))

if stage in ("all", "base"):
    spawn()
    if not bot.base_file.exists():
        X, Z = 2571, 2791
        log(run(f"tp {NAME} {X}.5 {surface()} {Z}.5"))
        time.sleep(2)
        log("position", bot.position())
    give("cobblestone", 64)
    give("cobblestone", 64)
    give("oak_door")
    give("torch", 4)
    give("chest")
    give("crafting_table")
    give("furnace")
    give("iron_pickaxe")
    give("iron_shovel")
    give("iron_axe")
    for attempt in range(6):
        result = bot.build_base()
        log(f"BUILD {attempt + 1}:", result)
        if "finished" in result or result.startswith(("too", "the ground", "someone", "stand", "this spot")):
            break
    log("inventory:", bot.counts())

if stage == "check":
    base = json.loads(bot.base_file.read_text())
    x0, g, z0 = base["origin"]
    blocks = bot._survey(x0 - 1, g - 1, z0 - 1, x0 + 7, g + 4, z0 + 7)
    for y in range(g + 4, g - 2, -1):
        rows = []
        for z in range(z0 - 1, z0 + 8):
            rows.append("".join({"air": ".", "cobblestone": "#", "oak_door": "D", "torch": "t", "chest": "C",
                                 "crafting_table": "T", "furnace": "F", "grass_block": "g", "dirt": "d",
                                 "short_grass": ","}.get(blocks.get((x, y, z), "?"), "?")
                                for x in range(x0 - 1, x0 + 8)))
        log(f"y={y - g:+d}  " + "   ".join(rows))

if stage == "grief":
    base = json.loads(bot.base_file.read_text())
    x0, g, z0 = base["origin"]
    griefer = "AIGrief"
    log(run(f"player {griefer} spawn at {x0 - 1}.5 {g} {z0 + 3}.5 facing 0 0 in minecraft:overworld in survival"))
    time.sleep(3)
    log(run(f"give {griefer} minecraft:diamond_pickaxe"))
    run(f"player {griefer} hotbar 1")
    run(f"player {griefer} look at {x0 + 0.5} {g + 1.5} {z0 + 3.5}")
    run(f"player {griefer} attack continuous")
    time.sleep(4)
    run(f"player {griefer} stop")
    log("wall block after griefer mined 4s:", bot.block_at(x0, g + 1, z0 + 3))
    log(run(f"player {griefer} kill"))

if stage == "cleanup":
    base = json.loads(bot.base_file.read_text()) if bot.base_file.exists() else None
    if base:
        x0, g, z0 = base["origin"]
        log(run(f"fill {x0} {g} {z0} {x0 + 6} {g + 3} {z0 + 6} air"))
        bot.base_file.unlink()
    areas = SERVER / "config" / "spawn-guard-areas.json"
    if areas.exists():
        data = json.loads(areas.read_text())
        data["areas"] = [a for a in data["areas"] if a.get("owners") != [NAME]]
        areas.write_text(json.dumps(data, indent=2))
    run(f"clear {NAME}")
    log(run(f"player {NAME} kill"))
