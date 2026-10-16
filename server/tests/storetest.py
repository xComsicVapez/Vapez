"""Live end-to-end test of spawn rules, plots, the web store, bonus hearts and revives with throwaway Carpet bots."""
import http.cookiejar, json, re, sys, time, urllib.parse, urllib.request
from pathlib import Path

stage = sys.argv[1] if len(sys.argv) > 1 else "all"
sys.argv = ["web_store", "/home/cosmic/lifesteal2"]
sys.path.insert(0, "/home/cosmic/server-tools")
import web_store as ws

S = Path("/home/cosmic/lifesteal2")
URL = json.loads((S / "config" / "cosmic-store.json").read_text())["url"]
run = ws.RCON.run
BOT, VICTIM = "AITest", "StoreTest"


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def sc(expr):
    return re.sub(r"\s*\([\d.]+\S*s\)$", "", run(f"script run {expr}").removeprefix("=").strip())


def uuid_of(name):
    return re.search(r"\(([0-9a-f\-]{36})\)", run("list uuids").split(name, 1)[1]).group(1)


class Web:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.op.addheaders = [("User-Agent", "storetest")]

    def get(self, path):
        with self.op.open(URL + path, timeout=20) as r:
            return r.geturl(), r.read().decode()

    def post(self, path, **form):
        data = urllib.parse.urlencode(form).encode()
        with self.op.open(URL + path, data=data, timeout=30) as r:
            final, body = r.geturl(), r.read().decode()
        m = urllib.parse.parse_qs(urllib.parse.urlsplit(final).query).get("m")
        return ws.read_flash(m[0]) if m else (final, None)

    def csrf(self):
        return re.search(r'name="csrf" value="([^"]+)"', self.get("/")[1]).group(1)


def spawn(name, where="223.5 114 35.5"):
    if name not in run("list"):
        log(run(f"player {name} spawn at {where} facing 0 0 in minecraft:overworld in survival"))
        time.sleep(3)
    run(f"tp {name} {where}")
    time.sleep(1)


if stage in ("all", "spawn"):
    spawn(BOT)
    run(f"gamemode survival {BOT}")
    time.sleep(2)
    log("mining fatigue at spawn:", sc(f"query(player('{BOT}'), 'effect', 'mining_fatigue')"))
    run("scoreboard objectives add ecopen minecraft.custom:minecraft.open_enderchest")
    before = run(f"scoreboard players get {BOT} ecopen")
    run(f"tp {BOT} 207.5 114 30.5")
    time.sleep(1)
    run(f"player {BOT} look at 205.5 114.5 30.5")
    run(f"player {BOT} use once")
    time.sleep(1)
    log("ender chest opens: before:", before, "| after:", run(f"scoreboard players get {BOT} ecopen"))
    run(f"player {BOT} stop")
    floor = "210 113 30"
    block = sc(f"block({floor.replace(' ', ',')})")
    run(f"give {BOT} minecraft:diamond_pickaxe")
    run(f"player {BOT} hotbar 1")
    run(f"tp {BOT} 210.5 114 32.5")
    run(f"player {BOT} look at 210.5 113.9 30.5")
    run(f"player {BOT} attack continuous")
    time.sleep(6)
    run(f"player {BOT} stop")
    log(f"spawn floor {floor} after 6s of mining: {block} -> {sc('block(' + floor.replace(' ', ',') + ')')}")

if stage in ("all", "store"):
    spawn(BOT)
    uid = uuid_of(BOT)
    log("top up test money:", run(f"cosmicstore refund {uid} 20000"))
    run(f"execute as {BOT} run store")
    codes = json.loads((S / "config" / "cosmic-store" / "login-codes.json").read_text())
    code = next(c for c, v in codes.items() if v["uuid"] == uid)
    web = Web()
    log("public URL", URL, "| login:", web.post("/login", code=code.lower()))
    log("balance shown:", re.search(r"Balance: <b>([^<]+)", web.get("/")[1]).group(1))
    run(f"clear {BOT}")
    log("buy starter:", web.post("/buy", csrf=web.csrf(), item="starter"))
    log("buy starter again:", web.post("/buy", csrf=web.csrf(), item="starter"))
    time.sleep(8)
    log("inventory has iron sword:", sc(f"inventory_find(player('{BOT}'), 'iron_sword') != null"),
        "| bread:", sc(f"inventory_find(player('{BOT}'), 'bread') != null"))
    log("balance:", run(f"cosmicstore balance {uid}"))
    log("max health before bonus:", run(f"attribute {BOT} minecraft:max_health get"))
    log("bonus heart:", run(f"cosmicstore bonushearts add {uid} 1"))
    log("max health after bonus:", run(f"attribute {BOT} minecraft:max_health get"))
    log("rent plot 1:", web.post("/plot", csrf=web.csrf(), id="1", weeks="1"))
    log("rent plot 2 too:", web.post("/plot", csrf=web.csrf(), id="2", weeks="1"))
    (Path("/tmp/storetest-session.json")).write_text(json.dumps({c.name: c.value for c in web.jar}))

if stage in ("all", "plot"):
    spawn(BOT)
    log("plot home:", run(f"execute as {BOT} run plot home"))
    time.sleep(2)
    pos = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", sc(f"pos(player('{BOT}'))"))]
    log("bot at", [round(v, 1) for v in pos], "| fatigue in own plot:",
        sc(f"query(player('{BOT}'), 'effect', 'mining_fatigue')"))
    x, y, z = int(pos[0] // 1), int(pos[1] // 1), int(pos[2] // 1)
    run(f"clear {BOT}")
    run(f"give {BOT} minecraft:cobblestone 4")
    run(f"give {BOT} minecraft:diamond_pickaxe")
    run(f"player {BOT} hotbar 1")
    target = None
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2), (2, 1), (-2, 1), (1, 2), (1, -2), (3, 0), (0, 3), (-3, 0), (0, -3)):
        if sc(f"solid({x + dx},{y - 1},{z + dz})") == "true" and sc(f"air({x + dx},{y},{z + dz})") == "true" \
                and sc(f"air({x + dx},{y + 1},{z + dz})") == "true":
            target = (x + dx, y, z + dz)
            break
    log("test spot", target)
    run(f"player {BOT} look at {target[0] + 0.5} {y - 0.02} {target[2] + 0.5}")
    log("floor under target:", sc(f"block({target[0]},{y - 1},{target[2]})"))
    run(f"player {BOT} use once")
    time.sleep(1)
    log("placed cobblestone in plot:", sc(f"block({target[0]},{y},{target[2]})"))
    run(f"player {BOT} hotbar 2")
    run(f"player {BOT} look at {target[0] + 0.5} {y + 0.5} {target[2] + 0.5}")
    run(f"player {BOT} attack continuous")
    time.sleep(3)
    run(f"player {BOT} stop")
    log("own block after mining 3s:", sc(f"block({target[0]},{y},{target[2]})"))
    floor = (target[0], y - 1, target[2])
    before = sc(f"block({floor[0]},{floor[1]},{floor[2]})")
    run(f"player {BOT} look at {floor[0] + 0.5} {floor[1] + 0.98} {floor[2] + 0.5}")
    run(f"player {BOT} attack continuous")
    time.sleep(5)
    run(f"player {BOT} stop")
    log(f"dome floor {floor} after mining 5s: {before} -> {sc(f'block({floor[0]},{floor[1]},{floor[2]})')}")

if stage in ("all", "revive"):
    spawn(BOT)
    spawn(VICTIM, "223.5 114 40.5")
    vid = uuid_of(VICTIM)
    run(f"lifesteal set_hearts {VICTIM} 1")
    # Carpet's /kill only disconnects fake players; real damage makes the lifesteal mod see a death.
    log("victim killed:", run(f"damage {VICTIM} 1000 minecraft:out_of_world"))
    for _ in range(12):
        time.sleep(5)
        elim = json.loads((S / "config" / "web-store" / "eliminations.json").read_text()).get(vid, {})
        if elim.get("status") == "waiting" and not elim.get("rebanning", True):
            break
    ban = next((b for b in json.loads((S / "banned-players.json").read_text()) if b["uuid"] == vid), None)
    log("ban reason:", ban and ban["reason"])
    web2 = Web()
    log("victim login with ban code:", web2.post("/login", code=elim["code"]))
    page = web2.get("/revive")[1]
    log("victim sees revive button:", "Revive for" in page, "| shop hidden:", 'href="/history"' not in page)
    web = Web()
    for name, value in json.loads(Path("/tmp/storetest-session.json").read_text()).items():
        web.jar.set_cookie(http.cookiejar.Cookie(0, name, value, None, False, urllib.parse.urlsplit(URL).hostname,
                                                 False, False, "/", True, False, None, False, None, None, {}))
    log("AITest buys the revive:", web.post("/revive", csrf=web.csrf(), uuid=vid))
    time.sleep(2)
    log("victim still banned:", any(b["uuid"] == vid for b in json.loads((S / "banned-players.json").read_text())))
    log("elimination record:", json.loads((S / "config" / "web-store" / "eliminations.json").read_text()).get(vid))

if stage in ("all", "intruder"):
    spawn(BOT)
    run(f"execute as {BOT} run plot home")
    time.sleep(2)
    pos = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", sc(f"pos(player('{BOT}'))"))]
    x, y, z = int(pos[0] // 1), int(pos[1] // 1), int(pos[2] // 1)
    target = (x + 2, y, z)
    assert sc(f"air({x + 2},{y},{z})") == "true" and sc(f"air({x + 2},{y + 1},{z})") == "true", "test spot not empty"
    run(f"setblock {target[0]} {target[1]} {target[2]} minecraft:cobblestone")
    spawn(VICTIM, f"{x + 0.5} {y} {z + 0.5}")
    run(f"gamemode survival {VICTIM}")
    run(f"clear {VICTIM}")
    run(f"give {VICTIM} minecraft:diamond_pickaxe")
    run(f"player {VICTIM} hotbar 1")
    run(f"tp {VICTIM} {x + 0.5} {y} {z + 0.5}")
    run(f"tp {BOT} 223.5 114 35.5")
    run(f"player {VICTIM} look at {target[0] + 0.5} {y + 0.5} {target[2] + 0.5}")
    run(f"player {VICTIM} attack continuous")
    time.sleep(6)
    run(f"player {VICTIM} stop")
    log("renter's block after a non-renter mined it 6s:", sc(f"block({target[0]},{target[1]},{target[2]})"))
    run(f"player {VICTIM} look at {target[0] + 0.5} {y + 1.02} {target[2] + 0.5}")
    run(f"give {VICTIM} minecraft:cobblestone 4")
    run(f"player {VICTIM} hotbar 2")
    run(f"player {VICTIM} use once")
    time.sleep(1)
    log("non-renter placing on top:", sc(f"block({target[0]},{target[1] + 1},{target[2]})"))
    run(f"setblock {target[0]} {target[1]} {target[2]} minecraft:air")
    run(f"setblock {target[0]} {target[1] + 1} {target[2]} minecraft:air")

if stage == "cleanup":
    log(run("plot admin clear 1"))
    if BOT in run("list"):
        uid = uuid_of(BOT)
        log(run(f"cosmicstore bonushearts reset {uid}"))
        bal = float(re.search(r"BALANCE ([\d.]+)", run(f"cosmicstore balance {uid}")).group(1))
        if bal > 1000:
            log(run(f"cosmicstore charge {uid} {bal - 1000:.2f}"))
        run(f"clear {BOT}")
        log(run(f"player {BOT} kill"))
    for name in (VICTIM,):
        run(f"player {name} kill")
    run("scoreboard objectives remove ecopen")
