#!/usr/bin/env python3
"""Carry the parts of an old server over to this one.

  python3 tools/import-old-server.py /path/to/old/server [--runtime runtime] [--dry-run]

Copies network/identity/admin settings from the old server.properties plus the
ops, whitelist, ban lists and server icon. Gameplay and world settings stay as
this pack sets them: the old world type, height, seed and difficulty don't fit a
new factions world, and the performance values are tuned for these mods.
"""
import argparse
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TRANSFER = [
    # how players reach the server
    "server-port", "max-players", "online-mode", "prevent-proxy-connections",
    "enforce-secure-profile", "accepts-transfers", "enable-status", "hide-online-players",
    # who's allowed in / who's in charge
    "white-list", "enforce-whitelist", "op-permission-level", "function-permission-level",
    "player-idle-timeout", "rate-limit", "log-ips",
    # remote admin / server lists
    "enable-rcon", "rcon.port", "rcon.password", "broadcast-rcon-to-ops",
    "enable-query", "query.port", "broadcast-console-to-ops",
    # server resource pack
    "resource-pack", "resource-pack-sha1", "resource-pack-id", "resource-pack-prompt",
    "require-resource-pack",
]

FILES = ["ops.json", "whitelist.json", "banned-players.json", "banned-ips.json", "server-icon.png"]


def read_props(path):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if s and not s.startswith("#") and "=" in s:
                k, v = s.split("=", 1)
                props[k.strip()] = v
    return props


def write_props(path, updates):
    lines = open(path, encoding="utf-8").read().splitlines() if os.path.exists(path) else []
    seen = set()
    for i, line in enumerate(lines):
        k = line.split("=", 1)[0].strip()
        if "=" in line and not line.lstrip().startswith("#") and k in updates:
            lines[i] = f"{k}={updates[k]}"
            seen.add(k)
    extra = [f"{k}={v}" for k, v in updates.items() if k not in seen]
    if extra:
        lines += ["", "# imported from the old server"] + extra
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("old_server", help="folder of the old server (the one with server.properties)")
    p.add_argument("--runtime", default=os.path.join(ROOT, "runtime"))
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    old_props_path = os.path.join(args.old_server, "server.properties")
    if not os.path.exists(old_props_path):
        sys.exit(f"No server.properties in {args.old_server}")
    new_props_path = os.path.join(args.runtime, "server.properties")
    if not os.path.exists(new_props_path):
        sys.exit(f"No {new_props_path}; run ./setup.sh first")

    old, new = read_props(old_props_path), read_props(new_props_path)
    updates = {k: old[k] for k in TRANSFER if k in old and old[k] != new.get(k)}
    if old.get("rcon.password") == "" and updates.get("enable-rcon") == "true":
        print("! old server had RCON on with no password; leaving RCON off")
        updates.pop("enable-rcon")

    print("server.properties:")
    for k, v in updates.items():
        shown = "********" if k == "rcon.password" else v
        print(f"  {k}: {new.get(k, '(unset)')} -> {shown}")
    if not updates:
        print("  nothing to change")
    skipped = sorted(k for k in old if k not in TRANSFER and old[k] != new.get(k))
    if skipped:
        print("kept this pack's value for: " + ", ".join(skipped))

    if old.get("server-ip"):
        print(f"note: old server bound to server-ip={old['server-ip']}; left blank here so it listens on "
              "every address (set it only if this VM really has that IP)")

    print("files:")
    for name in FILES:
        src = os.path.join(args.old_server, name)
        if os.path.exists(src) and open(src, "rb").read().strip() not in (b"", b"[]"):
            print(f"  {name}")
            if not args.dry_run:
                shutil.copy2(src, os.path.join(args.runtime, name))

    if args.dry_run:
        print("(dry run, nothing written)")
    else:
        write_props(new_props_path, updates)
        print("Done. Restart the server to apply.")
    if os.path.isdir(os.path.join(args.old_server, "plugins", "LuckPerms")):
        print("\nLuckPerms ranks: on the OLD server run `lp export ranks`, copy "
              "plugins/LuckPerms/ranks.json.gz to runtime/config/luckperms/, then `lp import ranks` here.")


if __name__ == "__main__":
    main()
