#!/usr/bin/env python3
"""
Check every LCSC part in the design against the live JLCPCB parts library:
stock level and whether it is a "basic" part (no extended-part setup fee).

    python3 hardware/check_parts.py [--boards 5]
"""
import argparse
import json
import os
import sys
import urllib.request
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import phone_design as D  # noqa: E402

API = "https://jlcpcb.com/api/overseas-pcb-order/v1/shoppingCart/smtGood/selectSmtComponentList"


def lookup(lcsc):
    body = json.dumps({"keyword": lcsc, "currentPage": 1, "pageSize": 5}).encode()
    req = urllib.request.Request(API, data=body, method="POST", headers={
        "Content-Type": "application/json", "User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=20) as r:
        data = json.load(r)
    items = ((data.get("data") or {}).get("componentPageInfo") or {}).get("list") or []
    for it in items:
        if it.get("componentCode") == lcsc:
            return it
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--boards", type=int, default=5, help="number of boards to assemble")
    args = ap.parse_args()

    qty = Counter(p.lcsc for p in D.PARTS if p.lcsc)
    names = {}
    for p in D.PARTS:
        if p.lcsc:
            names.setdefault(p.lcsc, []).append(p.ref)

    problems = 0
    extended = 0
    print(f"{'LCSC':<10} {'type':<9} {'stock':>9} {'need':>5}  part / designators")
    for lcsc, n in sorted(qty.items(), key=lambda kv: names[kv[0]][0]):
        try:
            it = lookup(lcsc)
        except Exception as e:  # network errors should not hide other results
            print(f"{lcsc:<10} lookup failed: {e}")
            problems += 1
            continue
        need = n * args.boards
        if not it:
            print(f"{lcsc:<10} NOT FOUND  {','.join(names[lcsc])}")
            problems += 1
            continue
        kind = "basic" if it.get("componentLibraryType") == "base" else "extended"
        extended += kind == "extended"
        stock = int(it.get("stockCount") or 0)
        flag = "" if stock >= need else "  <-- LOW STOCK"
        problems += bool(flag)
        print(f"{lcsc:<10} {kind:<9} {stock:>9} {need:>5}  {it.get('componentModelEn')} "
              f"({','.join(names[lcsc])}){flag}")
    print(f"\n{len(qty)} unique parts, {extended} extended "
          f"(JLCPCB charges a small setup fee per extended part type)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
