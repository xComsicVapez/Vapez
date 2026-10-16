#!/usr/bin/env python3
"""Merge ClassicGUIShop and Echoes Auction House balances into Savs Common Economy (one wallet).

Usage: merge_economy.py <server-dir> [--apply]

Run with the server stopped. Each player's new balance is their savs balance plus whatever they gained or
lost in the shop and the auction house relative to those mods' starting balances, never below zero.
Without --apply it only prints the table. With --apply it backs up the savs balances file first.
"""

import json
import shutil
import sqlite3
import sys
import time
from pathlib import Path

server = Path(sys.argv[1])
apply = "--apply" in sys.argv
savs_file = server / "config/savs-common-economy/balances.json"
savs = json.loads(savs_file.read_text())
savs_start = json.loads((server / "config/savs-common-economy/config.json").read_text()).get("defaultBalance", 0)
shop = json.loads((server / "config/guishop/balances.json").read_text())
shop_start = json.loads((server / "config/guishop/settings.json").read_text())["economy"]["startingBalance"]
ah_dir = server / "world/echoesauctionhouse"
ah_start = json.loads((ah_dir / "config.json").read_text()).get("startingBalance", 0)
with sqlite3.connect(f"file:{ah_dir / 'auctionhouse.sqlite'}?mode=ro", uri=True) as db:
    ah = {uuid: (name, balance) for uuid, name, balance in db.execute(
        "select player_uuid, player_name, balance from balances")}

print(f"starting balances: savs {savs_start}, shop {shop_start}, auction house {ah_start}")
print(f"{'player':20} {'savs':>10} {'shop':>10} {'auction':>10} {'merged':>10}")
for uuid in sorted(set(savs) | set(shop) | set(ah), key=lambda u: (savs.get(u, {}).get("name") or ah.get(u, ("?",))[0]).lower()):
    account = savs.get(uuid)
    name = account["name"] if account else ah.get(uuid, ("?", 0))[0]
    base = float(account["balance"]) if account else float(savs_start)
    shop_delta = float(shop[uuid]) - shop_start if uuid in shop else 0.0
    ah_delta = float(ah[uuid][1]) - ah_start if uuid in ah else 0.0
    merged = round(max(0.0, base + shop_delta + ah_delta), 2)
    print(f"{name:20} {base:>10.2f} {shop.get(uuid, '-')!s:>10} {ah.get(uuid, (0, '-'))[1]!s:>10} {merged:>10.2f}")
    if account:
        account["balance"] = merged
        account["version"] = int(account.get("version", 0)) + 1
    elif name != "?":
        savs[uuid] = {"name": name, "balance": merged, "version": 1}

if apply:
    backup = savs_file.with_name(f"balances.json.pre-merge-{time.strftime('%Y%m%d-%H%M%S')}")
    shutil.copy2(savs_file, backup)
    savs_file.write_text(json.dumps(savs, indent=2))
    print(f"wrote {savs_file} (backup: {backup.name})")
else:
    print("dry run: nothing written (add --apply)")
