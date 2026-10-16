#!/usr/bin/env python3
"""CosmicPixel web store for the lifesteal2 server.

Players type /store in game for a one-time login code, sign in on the website and spend their in-game money (the
same balance as /bal) on kits, supplies, revives and spawn plot rentals. Every kit or supply purchase has a small
chance of a bonus heart above the lifesteal cap. Eliminated players wait a few days for an automatic revive or buy
one here (any player can also buy a revive for a friend).

Everything goes through RCON and the cosmic-store / spawn-guard mods:
  /cosmicstore charge|refund|balance|bonushearts ...   money and bonus hearts
  /plot admin rent <id> <uuid> <name> <days>            spawn plots
  /revive <name>                                        lifesteal revive
Items are only handed out while the buyer is online, so nothing ends up on the ground at spawn.

Standard library only. Run:  python3 web_store.py /home/cosmic/lifesteal2
"""
import base64
import hashlib
import hmac
import html
import json
import os
import re
import secrets
import socket
import struct
import sys
import threading
import time
import traceback
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SERVER = Path(sys.argv[1] if len(sys.argv) > 1 else "/home/cosmic/lifesteal2")
HERE = Path(__file__).resolve().parent
CONFIG_FILE = SERVER / "config" / "web-store.json"
DATA = SERVER / "config" / "web-store"
STORE_MOD_CONFIG = SERVER / "config" / "cosmic-store.json"
LOGIN_CODES = SERVER / "config" / "cosmic-store" / "login-codes.json"
PLOTS_FILE = SERVER / "config" / "spawn-plots.json"
BANS_FILE = SERVER / "banned-players.json"
CATALOG_FILE = HERE / "web-store" / "catalog.json"

DEFAULTS = {
    "bind": "127.0.0.1",
    "port": 8080,
    # Fixed public address (e.g. https://store.cosmicpixel.org). Empty: use the cloudflared quick tunnel address.
    "public_url": "",
    "tunnel_metrics": "http://127.0.0.1:20242",
    "revive_wait_days": 3,
    "revive_price": 5000,
    "plot_price_per_week": 2500,
    "plot_max_weeks": 4,
    "max_bonus_hearts": 5,
}
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
SESSION_SECONDS = 12 * 3600
COLOR_CODES = re.compile("§.")
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
ITEM_RE = re.compile(r"^[a-z0-9_]+(\[[a-z0-9_:={},\s]*\])?$")

LOCK = threading.RLock()      # all state files and every purchase
LOG_LOCK = threading.Lock()


def log(*parts):
    with LOG_LOCK:
        print(time.strftime("%Y-%m-%d %H:%M:%S"), *parts, flush=True)


def read_json(path, default):
    try:
        return json.loads(Path(path).read_text())
    except (OSError, ValueError):
        return default


def write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(data, indent=2))
    os.chmod(tmp, 0o600)
    tmp.replace(path)


def load_config():
    config = dict(DEFAULTS)
    config.update(read_json(CONFIG_FILE, {}))
    if not config.get("secret"):
        config["secret"] = secrets.token_hex(32)
        stored = read_json(CONFIG_FILE, {})
        stored.update({k: v for k, v in config.items() if k not in stored or k == "secret"})
        write_json(CONFIG_FILE, stored)
    return config


CONFIG = load_config()
SECRET = bytes.fromhex(CONFIG["secret"])


def server_properties():
    props = {}
    for line in (SERVER / "server.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


# ---------------------------------------------------------------------------------------------------- RCON

class Rcon:
    def __init__(self, port, password):
        self.address, self.password, self.sock = ("127.0.0.1", port), password, None
        self.lock = threading.Lock()
        self.next_id = 1

    def _packet(self, request_id, kind, body):
        data = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _read(self):
        def exact(n):
            buf = b""
            while len(buf) < n:
                chunk = self.sock.recv(n - len(buf))
                if not chunk:
                    raise ConnectionError("RCON connection closed")
                buf += chunk
            return buf
        (length,) = struct.unpack("<i", exact(4))
        payload = exact(length)
        request_id, _ = struct.unpack("<ii", payload[:8])
        return request_id, payload[8:-2].decode("utf-8", errors="replace")

    def _connect(self):
        self.sock = socket.create_connection(self.address, timeout=20)
        self._packet(1, 3, self.password)
        if self._read()[0] == -1:
            raise PermissionError("RCON password rejected")

    def run(self, command, retry=True):
        """Send a command. With retry=False (money), a lost connection is never re-sent, so nothing runs twice."""
        with self.lock:
            for attempt in range(2 if retry else 1):
                try:
                    if self.sock is None:
                        self._connect()
                    self.next_id = self.next_id % 1_000_000 + 2
                    self._packet(self.next_id, 2, command)
                    while True:
                        request_id, body = self._read()
                        if request_id == self.next_id:
                            return COLOR_CODES.sub("", body).strip()
                except (OSError, ConnectionError) as e:
                    self.sock = None
                    if attempt or not retry:
                        raise ConnectionError(f"RCON failed ({e})") from e
                    time.sleep(2)
            raise ConnectionError("RCON failed")


_props = server_properties()
RCON = Rcon(int(_props.get("rcon.port", 25575)), _props["rcon.password"])
del _props


def online_players():
    """{uuid: name} of players online right now."""
    text = RCON.run("list uuids")
    return {uuid: name for name, uuid in re.findall(r"([A-Za-z0-9_.\-]+) \(([0-9a-f\-]{36})\)", text)}


def money(amount):
    return f"${amount:,.0f}" if float(amount) == int(amount) else f"${amount:,.2f}"


def balance(uuid):
    match = re.search(r"BALANCE (-?[\d.]+)", RCON.run(f"cosmicstore balance {uuid}"))
    return float(match.group(1)) if match else None


def charge(uuid, amount):
    """(ok, balance or error text). Never retried: a resend could take the money twice."""
    try:
        reply = RCON.run(f"cosmicstore charge {uuid} {amount:.2f}", retry=False)
    except ConnectionError as e:
        return False, f"the server did not answer ({e}); check your /bal before trying again"
    match = re.match(r"(OK|NOFUNDS|ERROR) ?(.*)", reply)
    if match and match.group(1) == "OK":
        return True, match.group(2)
    if match and match.group(1) == "NOFUNDS":
        return False, f"not enough money (you have {money(float(match.group(2) or 0))})"
    return False, f"the server refused the payment ({reply or 'no reply'})"


def refund(uuid, amount, why):
    try:
        reply = RCON.run(f"cosmicstore refund {uuid} {amount:.2f}", retry=False)
    except ConnectionError as e:
        reply = f"ERROR {e}"
    log("REFUND", uuid, amount, why, "->", reply)
    record({"type": "refund", "uuid": uuid, "amount": amount, "why": why, "reply": reply})
    return reply.startswith("OK")


def record(entry):
    entry = {"at": int(time.time()), **entry}
    DATA.mkdir(parents=True, exist_ok=True)
    with LOCK, open(DATA / "purchases.jsonl", "a") as f:
        f.write(json.dumps(entry) + "\n")


def history(uuid, limit=25):
    rows = []
    try:
        with open(DATA / "purchases.jsonl") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except ValueError:
                    continue
                if entry.get("uuid") == uuid or entry.get("for") == uuid:
                    rows.append(entry)
    except OSError:
        pass
    return rows[-limit:][::-1]


# ---------------------------------------------------------------------------------------------------- catalog

def catalog():
    data = read_json(CATALOG_FILE, {"kits": [], "supplies": []})
    products = {}
    for group in ("kits", "supplies"):
        for product in data.get(group, []):
            if all(ITEM_RE.match(item) and 1 <= int(count) <= 64 for item, count in product["items"]):
                products[product["id"]] = {**product, "group": group}
            else:
                log("CATALOG skipping", product.get("id"), "(bad item)")
    return products


def item_label(item, count):
    name = item.split("[", 1)[0].replace("_", " ").title()
    enchants = re.search(r"enchantments=\{([^}]*)\}", item)
    if enchants:
        roman = {1: "I", 2: "II", 3: "III", 4: "IV", 5: "V"}
        parts = []
        for pair in enchants.group(1).split(","):
            key, _, level = pair.partition(":")
            parts.append(f"{key.strip().replace('_', ' ').title()} {roman.get(int(level or 1), level)}")
        name += f" ({', '.join(parts)})"
    return f"{count} x {name}" if count > 1 else name


# ---------------------------------------------------------------------------------------------------- deliveries

def queue_delivery(uuid, name, product):
    with LOCK:
        deliveries = read_json(DATA / "deliveries.json", [])
        deliveries.append({"id": secrets.token_hex(6), "uuid": uuid, "name": name, "product": product["name"],
                           "items": product["items"], "done": 0, "created": int(time.time())})
        write_json(DATA / "deliveries.json", deliveries)


def pending_deliveries(uuid):
    with LOCK:
        return [d for d in read_json(DATA / "deliveries.json", []) if d["uuid"] == uuid]


def deliver_loop():
    while True:
        time.sleep(5)
        try:
            with LOCK:
                deliveries = read_json(DATA / "deliveries.json", [])
            if not deliveries:
                continue
            online = online_players()
            for delivery in deliveries:
                if delivery["uuid"] not in online:
                    continue
                uuid = delivery["uuid"]
                while delivery["done"] < len(delivery["items"]):
                    item, count = delivery["items"][delivery["done"]]
                    reply = RCON.run(f"give {uuid} minecraft:{item} {int(count)}")
                    if not reply.startswith("Gave"):
                        log("DELIVERY give failed", uuid, item, reply)
                        break
                    delivery["done"] += 1
                    with LOCK:
                        current = read_json(DATA / "deliveries.json", [])
                        for entry in current:
                            if entry["id"] == delivery["id"]:
                                entry["done"] = delivery["done"]
                        write_json(DATA / "deliveries.json", current)
                if delivery["done"] >= len(delivery["items"]):
                    RCON.run(f'tellraw {uuid} {{"text":"[Store] Your {delivery["product"]} has been delivered. '
                             f'Thanks for your purchase!","color":"green"}}')
                    log("DELIVERED", delivery["name"], delivery["product"])
                    with LOCK:
                        current = [d for d in read_json(DATA / "deliveries.json", []) if d["id"] != delivery["id"]]
                        write_json(DATA / "deliveries.json", current)
        except Exception:
            log("DELIVERY error", traceback.format_exc(limit=3))


# ---------------------------------------------------------------------------------------------------- eliminations

def parse_ban_time(text):
    try:
        return int(datetime.strptime(text, "%Y-%m-%d %H:%M:%S %z").timestamp())
    except (TypeError, ValueError):
        return int(time.time())


def when(epoch):
    return datetime.fromtimestamp(epoch, timezone.utc).strftime("%b %d at %H:%M UTC")


def ban_reason(entry):
    url = public_url() or "the store website (type /store in game)"
    return (f"You ran out of hearts! You will be revived automatically on {when(entry['revive_at'])}. "
            f"Or buy a revive now at {url} with code {entry['code']} (friends can buy it for you too).")


def reban(entry):
    """Swap the lifesteal ban message for one with the revive date, website and code. The player stays banned."""
    name = entry["name"]
    RCON.run(f"pardon {name}")
    reply = RCON.run(f"ban {name} {ban_reason(entry)}")
    if not reply.startswith("Banned"):
        fallback = RCON.run(f"ban {name} You ran out of hearts! Wait to be revived.")
        log("ELIMINATION re-ban failed for", name, reply, "| fallback:", fallback)
        return False
    return True


def elimination_loop():
    last_url = None
    while True:
        try:
            check_eliminations()
            url = public_url()
            if last_url is not None and url != last_url:
                with LOCK:
                    waiting = [e for e in read_json(DATA / "eliminations.json", {}).values() if e["status"] == "waiting"]
                for entry in waiting:
                    reban(entry)
            last_url = url
        except Exception:
            log("ELIMINATION error", traceback.format_exc(limit=3))
        time.sleep(15)


def check_eliminations():
    bans = {b["uuid"]: b for b in read_json(BANS_FILE, []) if "uuid" in b}
    now = int(time.time())
    with LOCK:
        elims = read_json(DATA / "eliminations.json", {})
        changed = False
        for uuid, ban in bans.items():
            entry = elims.get(uuid)
            if ban.get("source") != "LifestealMod" or (entry and entry["status"] == "waiting"):
                continue
            created = parse_ban_time(ban.get("created"))
            entry = {"uuid": uuid, "name": ban["name"], "at": created,
                     "revive_at": created + int(CONFIG["revive_wait_days"] * 86400),
                     "code": "".join(secrets.choice(CODE_ALPHABET) for _ in range(8)), "status": "waiting"}
            elims[uuid] = entry
            changed = True
            log("ELIMINATED", entry["name"], "auto revive", when(entry["revive_at"]))
            RCON.run(f"cosmicstore bonushearts reset {uuid}")
            reban(entry)
        for uuid, entry in elims.items():
            if entry["status"] != "waiting":
                continue
            if uuid not in bans:
                entry["status"], entry["ended"] = "pardoned", now
                changed = True
                log("ELIMINATION ended outside the store for", entry["name"])
            elif now >= max(entry["revive_at"], entry.get("retry_at", 0)):
                reply = RCON.run(f"revive {entry['name']}")
                if "Successfully revived" in reply:
                    entry["status"], entry["ended"] = "revived", now
                    log("AUTO REVIVE", entry["name"])
                else:
                    entry["retry_at"] = now + 600
                    log("AUTO REVIVE failed for", entry["name"], reply)
                changed = True
        if changed:
            write_json(DATA / "eliminations.json", elims)


def waiting_eliminations():
    with LOCK:
        return sorted((e for e in read_json(DATA / "eliminations.json", {}).values() if e["status"] == "waiting"),
                      key=lambda e: e["revive_at"])


# ---------------------------------------------------------------------------------------------------- public URL

_tunnel_url = {"value": "", "checked": 0}


def public_url():
    if CONFIG.get("public_url"):
        return CONFIG["public_url"].rstrip("/")
    return _tunnel_url["value"]


def tunnel_loop():
    """Follow the cloudflared quick tunnel address and tell the cosmic-store mod, so /store links to the right place."""
    while True:
        try:
            url = CONFIG.get("public_url", "").rstrip("/")
            if not url:
                with urllib.request.urlopen(CONFIG["tunnel_metrics"].rstrip("/") + "/quicktunnel", timeout=5) as r:
                    host = json.loads(r.read().decode()).get("hostname", "")
                url = f"https://{host}" if host else ""
            if url and url != _tunnel_url["value"]:
                _tunnel_url["value"] = url
                mod = read_json(STORE_MOD_CONFIG, {})
                if mod.get("url") != url:
                    mod["url"] = url
                    mod.setdefault("maxBonusHearts", CONFIG["max_bonus_hearts"])
                    write_json(STORE_MOD_CONFIG, mod)
                log("PUBLIC URL", url)
        except Exception as e:
            if not _tunnel_url["value"]:
                log("PUBLIC URL not known yet:", e)
        time.sleep(30)


# ---------------------------------------------------------------------------------------------------- sessions

def sign(data):
    return hmac.new(SECRET, data.encode(), hashlib.sha256).hexdigest()[:40]


def make_session(uuid, name, kind):
    body = base64.urlsafe_b64encode(json.dumps({"u": uuid, "n": name, "k": kind,
                                                "e": int(time.time()) + SESSION_SECONDS}).encode()).decode()
    return f"{body}.{sign(body)}"


def read_session(cookie):
    try:
        body, sig = cookie.rsplit(".", 1)
        if not hmac.compare_digest(sign(body), sig):
            return None
        data = json.loads(base64.urlsafe_b64decode(body.encode()))
        if data["e"] < time.time() or not UUID_RE.match(data["u"]):
            return None
        return {"uuid": data["u"], "name": data["n"], "kind": data["k"], "cookie": cookie}
    except (ValueError, KeyError, TypeError):
        return None


def csrf_token(session):
    return sign("csrf:" + session["cookie"])[:32]


def flash(text, good=True):
    body = base64.urlsafe_b64encode(json.dumps([text, good]).encode()).decode()
    return f"{body}.{sign('flash:' + body)[:16]}"


def read_flash(value):
    try:
        body, sig = value.rsplit(".", 1)
        if hmac.compare_digest(sign("flash:" + body)[:16], sig):
            text, good = json.loads(base64.urlsafe_b64decode(body.encode()))
            return str(text), bool(good)
    except (ValueError, TypeError):
        pass
    return None


def redeem_code(code):
    """Session data for a /store code (single use) or an elimination code from a ban screen, else None."""
    code = re.sub(r"[^A-Z0-9]", "", code.upper())
    now = int(time.time())
    with LOCK:
        if len(code) == 6:
            used = {c: t for c, t in read_json(DATA / "used-codes.json", {}).items() if t > now}
            entry = read_json(LOGIN_CODES, {}).get(code)
            if entry and entry.get("expires", 0) > now and code not in used and UUID_RE.match(entry.get("uuid", "")):
                used[code] = entry["expires"]
                write_json(DATA / "used-codes.json", used)
                return entry["uuid"], entry["name"], "player"
        elif len(code) == 8:
            for entry in read_json(DATA / "eliminations.json", {}).values():
                if entry["status"] == "waiting" and hmac.compare_digest(entry["code"], code):
                    return entry["uuid"], entry["name"], "eliminated"
    return None


_attempts = {}


def rate_limited(ip, limit=10, window=600):
    now = time.time()
    with LOCK:
        recent = [t for t in _attempts.get(ip, []) if now - t < window]
        recent.append(now)
        _attempts[ip] = recent
        if len(_attempts) > 5000:
            for key in list(_attempts)[:1000]:
                _attempts.pop(key, None)
        return len(recent) > limit


# ---------------------------------------------------------------------------------------------------- purchases

def cooldown_left(uuid, product):
    hours = float(product.get("cooldown_hours", 0))
    if hours <= 0:
        return 0
    last = read_json(DATA / "cooldowns.json", {}).get(uuid, {}).get(product["id"], 0)
    return max(0, int(last + hours * 3600 - time.time()))


def buy_product(session, product_id):
    product = catalog().get(product_id)
    if not product:
        return "That item is not for sale.", False
    uuid, name = session["uuid"], session["name"]
    with LOCK:
        left = cooldown_left(uuid, product)
        if left:
            return f"You can buy the {product['name']} again in {duration(left)}.", False
        ok, detail = charge(uuid, float(product["price"]))
        if not ok:
            return f"Payment failed: {detail}.", False
        queue_delivery(uuid, name, product)
        cooldowns = read_json(DATA / "cooldowns.json", {})
        cooldowns.setdefault(uuid, {})[product["id"]] = int(time.time())
        write_json(DATA / "cooldowns.json", cooldowns)
        heart = ""
        chance = float(product.get("heart_chance", 0))
        if chance > 0 and secrets.randbelow(10000) < chance * 100:
            reply = RCON.run(f"cosmicstore bonushearts add {uuid} 1")
            if reply.startswith("OK"):
                heart = " LUCKY! You also won a bonus heart that goes past the 20 heart limit!"
                RCON.run(f'tellraw @a {{"text":"[Store] {name} won a bonus heart from the web store!","color":"light_purple"}}')
            elif reply.startswith("MAX"):
                heart = " You rolled a bonus heart, but you already have the most bonus hearts allowed."
            log("HEART", name, reply)
        record({"type": "buy", "uuid": uuid, "name": name, "item": product["id"], "amount": product["price"],
                "heart": bool(heart and heart.startswith(" LUCKY"))})
        log("BUY", name, product["id"], money(product["price"]), "->", detail)
    try:
        online = uuid in online_players()
    except ConnectionError:
        online = False
    where = "It will be put in your inventory now." if online else \
        "It will be put in your inventory the next time you join."
    return f"You bought the {product['name']} for {money(product['price'])}. {where}{heart}", True


def buy_revive(session, target):
    price = float(CONFIG["revive_price"])
    with LOCK:
        entry = read_json(DATA / "eliminations.json", {}).get(target)
        if not entry or entry["status"] != "waiting":
            return "That player is not waiting for a revive.", False
        if session["kind"] == "eliminated" and target != session["uuid"]:
            return "You can only buy your own revive with this code.", False
        ok, detail = charge(session["uuid"], price)
        if not ok:
            return f"Payment failed: {detail}.", False
        reply = RCON.run(f"revive {entry['name']}")
        if "Successfully revived" not in reply:
            refund(session["uuid"], price, f"revive of {entry['name']} failed: {reply}")
            return f"The revive did not go through ({reply}); your money was refunded.", False
        elims = read_json(DATA / "eliminations.json", {})
        elims[target].update(status="bought", ended=int(time.time()), buyer=session["name"])
        write_json(DATA / "eliminations.json", elims)
        record({"type": "revive", "uuid": session["uuid"], "name": session["name"], "for": target,
                "target": entry["name"], "amount": price})
        log("REVIVE bought by", session["name"], "for", entry["name"])
    if target == session["uuid"]:
        return f"You are revived! Join the server again. ({money(price)} paid)", True
    return f"You revived {entry['name']} for {money(price)}. They can join again now.", True


def plots():
    return read_json(PLOTS_FILE, {}).get("plots", [])


def rent_plot(session, plot_id, weeks):
    price = float(CONFIG["plot_price_per_week"]) * weeks
    if not 1 <= weeks <= int(CONFIG["plot_max_weeks"]):
        return "Pick between 1 and %d weeks." % CONFIG["plot_max_weeks"], False
    plot = next((p for p in plots() if p.get("id") == plot_id), None)
    if not plot:
        return "That plot does not exist.", False
    if plot.get("renter") and plot["renter"] != session["uuid"]:
        return "Someone else rents that plot.", False
    other = next((p for p in plots() if p.get("renter") == session["uuid"] and p.get("id") != plot_id), None)
    if other:
        return f"You already rent plot {other['id']}; one plot per player.", False
    with LOCK:
        ok, detail = charge(session["uuid"], price)
        if not ok:
            return f"Payment failed: {detail}.", False
        reply = RCON.run(f"plot admin rent {plot_id} {session['uuid']} {session['name']} {weeks * 7}")
        if not reply.startswith("OK"):
            refund(session["uuid"], price, f"plot {plot_id} rent failed: {reply}")
            reason = reply.removeprefix("ERROR ").strip() or "no reply from the server"
            return f"Could not rent that plot ({reason}); your money was refunded.", False
        until = int(reply.split()[1])
        record({"type": "plot", "uuid": session["uuid"], "name": session["name"], "plot": plot_id,
                "weeks": weeks, "amount": price, "until": until})
        log("PLOT", session["name"], plot_id, weeks, "weeks until", when(until))
    return (f"Plot {plot_id} ({plot.get('name', '')}) is yours until {when(until)}. In game: /plot home to go there, "
            f"/plot for info.", True)


def duration(seconds):
    seconds = int(seconds)
    days, rest = divmod(seconds, 86400)
    hours, rest = divmod(rest, 3600)
    minutes = rest // 60
    if days:
        return f"{days}d {hours}h"
    if hours:
        return f"{hours}h {minutes}m"
    return f"{max(minutes, 1)}m"


# ---------------------------------------------------------------------------------------------------- pages

e = html.escape


def page(title, body, session=None, message=None):
    nav = ""
    if session:
        links = [("/", "Shop"), ("/revive", "Revives"), ("/plots", "Spawn plots"), ("/history", "History")]
        if session["kind"] == "eliminated":
            links = [("/revive", "Revive")]
        nav = "".join(f'<a href="{href}">{label}</a>' for href, label in links)
        nav += (f'<form method="post" action="/logout" class="inline"><input type="hidden" name="csrf" '
                f'value="{csrf_token(session)}"><button class="link">Sign out {e(session["name"])}</button></form>')
    note = ""
    if message:
        text, good = message
        note = f'<div class="flash {"good" if good else "bad"}">{e(text)}</div>'
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(title)} - CosmicPixel Store</title><link rel="stylesheet" href="/static/style.css"></head>
<body><header><a class="brand" href="/"><span class="heart">&#10084;</span> CosmicPixel <b>Store</b></a>
<nav>{nav}</nav></header><main>{note}{body}</main>
<footer>play.cosmicpixel.org &middot; Lifesteal &middot; Everything costs in-game money (your /bal). Not affiliated with Mojang or Microsoft.</footer>
</body></html>"""


def product_card(product, session, bal):
    items = "".join(f"<li>{e(item_label(item, count))}</li>" for item, count in product["items"])
    tags = []
    if product.get("heart_chance"):
        tags.append(f'<span class="tag heart">{product["heart_chance"]:g}% bonus heart</span>')
    if product.get("cooldown_hours"):
        tags.append(f'<span class="tag">every {product["cooldown_hours"]:g}h</span>')
    button = ""
    if session and session["kind"] == "player":
        left = cooldown_left(session["uuid"], product)
        if left:
            button = f'<button disabled>Again in {duration(left)}</button>'
        else:
            short = bal is not None and bal < product["price"]
            button = (f'<form method="post" action="/buy"><input type="hidden" name="csrf" value="{csrf_token(session)}">'
                      f'<input type="hidden" name="item" value="{e(product["id"])}">'
                      f'<button{" class=\"muted\"" if short else ""}>Buy for {money(product["price"])}</button></form>')
    else:
        button = f'<div class="price">{money(product["price"])}</div>'
    return (f'<article class="card"><h3>{e(product["name"])}</h3><p>{e(product.get("blurb", ""))}</p>'
            f'<ul>{items}</ul><div class="tags">{"".join(tags)}</div>{button}</article>')


def login_box():
    return """<section class="login"><h2>Sign in</h2>
<p>Join <b>play.cosmicpixel.org</b> and type <code>/store</code> in chat. You get a 6-letter code that works once
for 10 minutes.</p>
<p>Out of hearts? Use the 8-letter code from your ban screen to buy a revive, or just wait for the automatic one.</p>
<form method="post" action="/login"><input name="code" maxlength="12" placeholder="CODE" autocomplete="off"
autocapitalize="characters" required><button>Sign in</button></form></section>"""


def home_page(session, message):
    products = catalog()
    bal = None
    if session and session["kind"] == "player":
        try:
            bal = balance(session["uuid"])
        except ConnectionError:
            bal = None
    if session and session["kind"] == "eliminated":
        return revive_page(session, message)
    intro = ""
    if session:
        pending = pending_deliveries(session["uuid"])
        waiting = (f'<p class="notice">{len(pending)} order(s) waiting for you to join the server.</p>'
                   if pending else "")
        intro = (f'<section class="hello"><h1>Hi {e(session["name"])}</h1><p class="balance">Balance: '
                 f'<b>{money(bal) if bal is not None else "unavailable"}</b> <span>(same money as /bal)</span></p>'
                 f'{waiting}</section>')
    else:
        intro = (f'<section class="hero"><h1>Gear up. Survive. Steal hearts.</h1><p>Kits and supplies for '
                 f'in-game money, spawn plots to rent and revives when you run out of hearts. Every kit has a '
                 f'chance to give a <b>bonus heart</b> that goes past the 20 heart limit (up to '
                 f'{CONFIG["max_bonus_hearts"]} extra).</p></section>{login_box()}')
    kits = "".join(product_card(p, session, bal) for p in products.values() if p["group"] == "kits")
    supplies = "".join(product_card(p, session, bal) for p in products.values() if p["group"] == "supplies")
    extras = (f'<section class="grid two"><article class="card"><h3>Revive</h3><p>Out of hearts? You are revived '
              f'automatically after {CONFIG["revive_wait_days"]:g} days, or right away for '
              f'{money(CONFIG["revive_price"])}. You can buy one for a friend too.</p><a class="button" href="/revive">'
              f'Revives</a></article><article class="card"><h3>Spawn plots</h3><p>Rent one of the 8 domes at spawn '
              f'for {money(CONFIG["plot_price_per_week"])} a week. Only you can build and break there.</p>'
              f'<a class="button" href="/plots">Spawn plots</a></article></section>')
    body = (f'{intro}<h2>Kits</h2><section class="grid">{kits}</section><h2>Supplies</h2>'
            f'<section class="grid">{supplies}</section>{extras}')
    return page("Shop", body, session, message)


def revive_page(session, message):
    waiting = waiting_eliminations()
    now = time.time()
    rows = []
    for entry in waiting:
        if session and session["kind"] == "eliminated" and entry["uuid"] != session["uuid"]:
            continue
        action = ""
        if session:
            action = (f'<form method="post" action="/revive"><input type="hidden" name="csrf" value="{csrf_token(session)}">'
                      f'<input type="hidden" name="uuid" value="{e(entry["uuid"])}"><button>Revive for '
                      f'{money(CONFIG["revive_price"])}</button></form>')
        rows.append(f'<tr><td>{e(entry["name"])}</td><td>{when(entry["at"])}</td>'
                    f'<td>in {duration(entry["revive_at"] - now)}</td><td>{action}</td></tr>')
    table = ('<table><tr><th>Player</th><th>Out of hearts</th><th>Free revive</th><th></th></tr>'
             + "".join(rows) + "</table>") if rows else "<p>Nobody is waiting for a revive right now.</p>"
    intro = (f"<h1>Revives</h1><p>When you lose your last heart you are banned until you are revived. Wait "
             f"{CONFIG['revive_wait_days']:g} days for a free automatic revive, or buy one now for "
             f"{money(CONFIG['revive_price'])}. Revived players come back with "
             f"{read_json(SERVER / 'world' / 'lifesteal_config.json', {}).get('reviveAtHearts', 10):g} hearts at spawn.</p>")
    if session and session["kind"] == "eliminated":
        intro += ("<p>You are signed in with your elimination code, so the revive is paid from your own balance. "
                  "A friend can also sign in with their /store code and buy it for you.</p>")
    body = intro + table + ("" if session else login_box())
    return page("Revives", body, session, message)


def plots_page(session, message):
    now = time.time()
    rows = []
    all_plots = plots()
    mine = next((p for p in all_plots if session and p.get("renter") == session["uuid"]), None)
    for plot in all_plots:
        renter = plot.get("renter")
        active = renter and plot.get("until", 0) > now
        if renter == (session or {}).get("uuid"):
            status = f'<b>Yours</b> until {when(plot["until"])}' if active else \
                '<b>Yours</b>, expired: extend now or your blocks are removed in a day'
        elif renter:
            status = f'Rented by {e(plot.get("renterName") or "someone")}'
        else:
            status = '<span class="free">Free</span>'
        action = ""
        can_rent = session and session["kind"] == "player" and (not renter or renter == session["uuid"]) \
            and (mine is None or mine is plot)
        if can_rent:
            weeks = "".join(f'<option value="{w}">{w} week{"s" if w > 1 else ""} - '
                            f'{money(CONFIG["plot_price_per_week"] * w)}</option>'
                            for w in range(1, int(CONFIG["plot_max_weeks"]) + 1))
            action = (f'<form method="post" action="/plot" class="row"><input type="hidden" name="csrf" '
                      f'value="{csrf_token(session)}"><input type="hidden" name="id" value="{int(plot["id"])}">'
                      f'<select name="weeks">{weeks}</select><button>{"Extend" if renter else "Rent"}</button></form>')
        rows.append(f'<tr><td>#{int(plot["id"])} {e(plot.get("name", ""))}</td><td>{status}</td><td>{action}</td></tr>')
    table = ('<table><tr><th>Plot</th><th>Status</th><th></th></tr>' + "".join(rows) + '</table>') if rows else \
        "<p>The spawn plots are not set up yet.</p>"
    body = (f"<h1>Spawn plots</h1><p>Spawn has 8 domes you can rent for {money(CONFIG['plot_price_per_week'])} a "
            f"week (one per player). Inside your dome only you can build and break blocks, you don't get mining "
            f"fatigue, and nobody else can open your chests. Spawn blocks themselves can't be broken. In game: "
            f"<code>/plot</code> for info, <code>/plot home</code> to go there.</p>"
            f"<p>When a rental runs out you get one extra day to renew before your blocks are removed.</p>{table}")
    return page("Spawn plots", body + ("" if session else login_box()), session, message)


def history_page(session, message):
    labels = {"buy": "Bought", "revive": "Revive", "plot": "Spawn plot", "refund": "Refund"}
    products = catalog()
    rows = []
    for entry in history(session["uuid"]):
        what = entry.get("item", "")
        if entry["type"] == "buy":
            what = products.get(what, {}).get("name", what) + (" + bonus heart!" if entry.get("heart") else "")
        elif entry["type"] == "revive":
            what = "for " + entry.get("target", "") if entry.get("for") != session["uuid"] else "your revive"
            if entry.get("uuid") != session["uuid"]:
                what = f"bought by {entry.get('name', 'a friend')}"
        elif entry["type"] == "plot":
            what = f"plot {entry.get('plot')} for {entry.get('weeks')} week(s)"
        elif entry["type"] == "refund":
            what = entry.get("why", "")
        rows.append(f'<tr><td>{when(entry["at"])}</td><td>{labels.get(entry["type"], entry["type"])}</td>'
                    f'<td>{e(str(what))}</td><td>{money(float(entry.get("amount", 0)))}</td></tr>')
    body = "<h1>Your purchases</h1>" + (
        '<table><tr><th>When</th><th>Type</th><th>What</th><th>Amount</th></tr>' + "".join(rows) + "</table>"
        if rows else "<p>No purchases yet.</p>")
    return page("History", body, session, message)


# ---------------------------------------------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "CosmicStore"
    sys_version = ""

    def log_message(self, fmt, *args):
        pass

    def client_ip(self):
        ip = self.client_address[0]
        if ip in ("127.0.0.1", "::1") and self.headers.get("CF-Connecting-IP"):
            return self.headers["CF-Connecting-IP"].strip()
        return ip

    def https(self):
        return "https" in (self.headers.get("CF-Visitor", "") + self.headers.get("X-Forwarded-Proto", ""))

    def session(self):
        for part in self.headers.get("Cookie", "").split(";"):
            name, _, value = part.strip().partition("=")
            if name == "cs":
                return read_session(value)
        return None

    def send(self, status, body, content_type="text/html; charset=utf-8", headers=()):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "same-origin")
        self.send_header("Content-Security-Policy", "default-src 'self'; form-action 'self'; frame-ancestors 'none'")
        if content_type.startswith("text/html"):
            self.send_header("Cache-Control", "no-store")
        for key, value in headers:
            self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def redirect(self, path, message=None, cookie=None):
        if message:
            path += ("&" if "?" in path else "?") + "m=" + flash(*message)
        headers = [("Location", path)]
        if cookie is not None:
            attrs = "; Path=/; HttpOnly; SameSite=Lax" + ("; Secure" if self.https() else "")
            headers.append(("Set-Cookie", f"cs={cookie}{attrs}; Max-Age={SESSION_SECONDS if cookie else 0}"))
        self.send(303, "", headers=headers)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        try:
            url = urllib.parse.urlsplit(self.path)
            query = urllib.parse.parse_qs(url.query)
            message = read_flash(query["m"][0]) if "m" in query else None
            session = self.session()
            if url.path == "/static/style.css":
                return self.send(200, (HERE / "web-store" / "style.css").read_bytes(), "text/css; charset=utf-8",
                                 [("Cache-Control", "public, max-age=300")])
            if url.path == "/healthz":
                return self.send(200, "ok", "text/plain")
            if url.path == "/":
                return self.send(200, home_page(session, message))
            if url.path == "/revive":
                return self.send(200, revive_page(session, message))
            if url.path == "/plots":
                return self.send(200, plots_page(session, message))
            if url.path == "/history" and session:
                return self.send(200, history_page(session, message))
            if url.path == "/history":
                return self.redirect("/")
            self.send(404, page("Not found", "<h1>Not found</h1><p><a href=\"/\">Back to the store</a></p>", session))
        except Exception:
            log("HTTP error", traceback.format_exc(limit=4))
            self.send(500, page("Error", "<h1>Something went wrong</h1><p>Try again in a minute.</p>"))

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length") or 0)
            if length > 4096:
                return self.send(413, "too large", "text/plain")
            form = {k: v[0] for k, v in urllib.parse.parse_qs(self.rfile.read(length).decode(errors="replace")).items()}
            path = urllib.parse.urlsplit(self.path).path
            if path == "/login":
                if rate_limited(self.client_ip()):
                    return self.redirect("/", ("Too many attempts. Wait a few minutes and try again.", False))
                found = redeem_code(form.get("code", ""))
                if not found:
                    return self.redirect("/", ("That code is wrong, used or expired. Type /store in game for a new one.", False))
                uuid, name, kind = found
                log("LOGIN", name, kind, self.client_ip())
                return self.redirect("/revive" if kind == "eliminated" else "/",
                                     (f"Signed in as {name}.", True), make_session(uuid, name, kind))
            session = self.session()
            if not session or not hmac.compare_digest(form.get("csrf", ""), csrf_token(session)):
                return self.redirect("/", ("Your session expired; sign in again.", False))
            if path == "/logout":
                return self.redirect("/", ("Signed out.", True), "")
            if path == "/buy" and session["kind"] == "player":
                return self.redirect("/", buy_product(session, form.get("item", "")))
            if path == "/revive":
                return self.redirect("/revive", buy_revive(session, form.get("uuid", "")))
            if path == "/plot" and session["kind"] == "player":
                try:
                    plot_id, weeks = int(form.get("id", "")), int(form.get("weeks", ""))
                except ValueError:
                    return self.redirect("/plots", ("Pick a plot and a number of weeks.", False))
                return self.redirect("/plots", rent_plot(session, plot_id, weeks))
            self.send(404, "not found", "text/plain")
        except Exception:
            log("HTTP error", traceback.format_exc(limit=4))
            self.redirect("/", ("Something went wrong; if money was taken it is refunded or check /bal.", False))


def main():
    DATA.mkdir(parents=True, exist_ok=True)
    os.chmod(DATA, 0o700)
    for target in (deliver_loop, elimination_loop, tunnel_loop):
        threading.Thread(target=target, daemon=True, name=target.__name__).start()
    server = ThreadingHTTPServer((CONFIG["bind"], int(CONFIG["port"])), Handler)
    server.daemon_threads = True
    log(f"web store on http://{CONFIG['bind']}:{CONFIG['port']} for {SERVER}")
    server.serve_forever()


if __name__ == "__main__":
    main()
