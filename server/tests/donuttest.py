"""Live test of /bounty and silk-touch spawner mining with throwaway Carpet bots on a sky platform far from players."""
import re, sys, time

sys.argv = ["web_store", "/home/cosmic/lifesteal2"]
sys.path.insert(0, "/home/cosmic/server-tools")
import web_store as ws

run = ws.RCON.run
A, B = "AITest", "StoreTest"
X, Y, Z = 3000, 200, 3000


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def uuid_of(name):
    return re.search(r"\(([0-9a-f\-]{36})\)", run("list uuids").split(name, 1)[1]).group(1)


def bal(uuid):
    return float(re.search(r"BALANCE (-?[\d.]+)", run(f"cosmicstore balance {uuid}")).group(1))


def sc(expr):
    return re.sub(r"\s*\([\d.]+\S*s\)$", "", run(f"script run {expr}").removeprefix("=").strip())


run(f"forceload add {X - 16} {Z - 16} {X + 16} {Z + 16}")
time.sleep(2)
run(f"fill {X - 4} {Y - 1} {Z - 4} {X + 4} {Y - 1} {Z + 4} minecraft:glass")
for name, dx in ((A, -2), (B, 2)):
    if name not in run("list"):
        log(run(f"player {name} spawn at {X + dx + 0.5} {Y} {Z + 0.5} facing 0 0 in minecraft:overworld in survival"))
        time.sleep(3)
    run(f"gamemode survival {name}")
    run(f"tp {name} {X + dx + 0.5} {Y} {Z + 0.5}")
    run(f"clear {name}")
time.sleep(1)
ua, ub = uuid_of(A), uuid_of(B)
run(f"cosmicstore refund {ua} 2000")
hearts = {n: float(re.search(r"([\d.]+)\s*$", run(f"attribute {n} minecraft:max_health base get")).group(1)) / 2
          for n in (A, B)}

before = bal(ua)
log("place $50 (below minimum):", run(f"execute as {A} run bounty {B} 50"))
log("bounty on self:", run(f"execute as {A} run bounty {A} 500"))
run(f"execute as {A} run bounty {B} 500")
time.sleep(1)
run(f"execute as {A} run bounty {B} 250")
time.sleep(1)
log("payer balance:", before, "->", bal(ua))
log("show:", run(f"execute as {A} run bounty {B}"))
log("top:", run(f"execute as {A} run bounty").replace("\n", " | "))
killer_before = bal(ua)
log("kill:", run(f"damage {B} 1000 minecraft:player_attack by {A}"))
time.sleep(2)
log("killer balance:", killer_before, "->", bal(ua))
log("bounty after kill:", run(f"execute as {A} run bounty").replace("\n", " | "))

log("--- spawner")
sx, sy, sz = X, Y, Z + 2
for tool, label in (("minecraft:diamond_pickaxe", "plain pickaxe"),
                    ("minecraft:diamond_pickaxe[enchantments={silk_touch:1}]", "silk touch pickaxe")):
    run(f"kill @e[type=item,x={X},y={Y},z={Z},distance=..12]")
    run(f'setblock {sx} {sy} {sz} minecraft:spawner{{SpawnData:{{entity:{{id:"minecraft:zombie"}}}}}}')
    run(f"clear {A}")
    run(f"give {A} {tool}")
    run(f"player {A} hotbar 1")
    run(f"tp {A} {sx + 0.5} {sy} {sz - 1.5}")
    run(f"player {A} look at {sx + 0.5} {sy + 0.5} {sz + 0.5}")
    run(f"player {A} attack continuous")
    time.sleep(7)
    run(f"player {A} stop")
    time.sleep(1)
    block = sc(f"block({sx},{sy},{sz})")
    drops = run(f"execute positioned {sx} {sy} {sz} run data get entity @e[type=item,distance=..4,limit=1,sort=nearest] Item.id")
    inv = sc(f"[inventory_find(player('{A}'), 'spawner') != null, inventory_find(player('{A}'), 'zombie_spawn_egg') != null]")
    log(f"{label}: block now {block} | nearest drop: {drops} | bot has [spawner, zombie egg]: {inv}")

log("--- cleanup")
run(f"setblock {sx} {sy} {sz} minecraft:air")
run(f"kill @e[type=item,x={X},y={Y},z={Z},distance=..12]")
for name, h in hearts.items():
    if name not in run("list"):
        run(f"player {name} spawn at {X + 0.5} {Y} {Z + 0.5} facing 0 0 in minecraft:overworld in survival")
        time.sleep(3)
    run(f"lifesteal set_hearts {name} {h:g}")
    run(f"clear {name}")
    run(f"player {name} kill")
run(f"fill {X - 4} {Y - 1} {Z - 4} {X + 4} {Y - 1} {Z + 4} minecraft:air")
run(f"forceload remove {X - 16} {Z - 16} {X + 16} {Z + 16}")
b = bal(ua)
if b > 1000:
    run(f"cosmicstore charge {ua} {b - 1000:.2f}")
log("AITest balance reset to", bal(ua), "| lifesteal hearts restored to", hearts)
log(run("list"))
