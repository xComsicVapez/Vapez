#!/usr/bin/env python3
"""Resolve, download and package the factions server mod list from Modrinth.

  lock      resolve modlist.txt (+ required deps) into mods.lock.json
  server    download every server-compatible mod in the lock into a mods dir
  mrpack    build a client .mrpack (import into Prism / Modrinth App / ATLauncher)
"""
import argparse
import hashlib
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
UA = "xComsicVapez/factions-server (github.com/xComsicVapez/Vapez)"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODLIST = os.path.join(ROOT, "modlist.txt")
LOCK = os.path.join(ROOT, "mods.lock.json")


def get(path, params=None):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode({k: json.dumps(v) for k, v in params.items()})
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code != 429 and e.code < 500:
                raise
        except urllib.error.URLError:
            pass
        time.sleep(2 ** attempt)
    raise RuntimeError(f"Modrinth request failed: {url}")


def read_modlist():
    """Returns [(slug, pinned_version_or_None, client_only)].

    `slug==1.2.3` pins an exact Modrinth version number; a trailing `@client`
    keeps the mod (but not its libraries) off the server.
    """
    entries = []
    with open(MODLIST) as f:
        for line in f:
            words = line.split("#", 1)[0].split()
            if words:
                slug, _, pin = words[0].partition("==")
                entries.append((slug, pin or None, "@client" in words[1:]))
    return entries


def pick_version(project_id, loader, mc, pin=None):
    versions = get(f"/project/{project_id}/version",
                   {"loaders": [loader], "game_versions": [mc]})
    if not versions:
        return None
    if pin:
        return next((v for v in versions if v["version_number"] == pin), None)
    rank = {"release": 0, "beta": 1, "alpha": 2}
    best = min(rank.get(v["version_type"], 3) for v in versions)
    stable = [v for v in versions if rank.get(v["version_type"], 3) == best]
    return max(stable, key=lambda v: v["date_published"])


def env_of(project):
    def norm(side):
        return {"required": "required", "optional": "optional"}.get(side, "unsupported")
    return {"client": norm(project["client_side"]), "server": norm(project["server_side"])}


def cmd_lock(args):
    meta = json.load(open(os.path.join(ROOT, "pack.json")))
    loader, mc = meta["loader"], meta["minecraft"]
    entries = read_modlist()
    pins = {slug: pin for slug, pin, _ in entries if pin}
    client_only = {slug for slug, _, flag in entries if flag}
    queue = [(slug, None) for slug, _, _ in entries]
    resolved, deps, missing = {}, {}, []
    while queue:
        ref, parent = queue.pop(0)
        project = get(f"/project/{ref}")
        if project is None:
            missing.append(f"{ref} (not found)")
            continue
        if project["id"] in resolved:
            continue
        pin = pins.get(ref) or pins.get(project["slug"])
        version = pick_version(project["id"], loader, mc, pin)
        if version is None:
            why = f"version {pin} not found" if pin else f"no {loader} {mc} build"
            missing.append(f"{project['slug']} ({why})" + (f", needed by {parent}" if parent else ""))
            continue
        primary = next((f for f in version["files"] if f["primary"]), version["files"][0])
        env = env_of(project)
        if ref in client_only or project["slug"] in client_only:
            env["server"] = "unsupported"
        resolved[project["id"]] = {
            "slug": project["slug"],
            "title": project["title"],
            "version": version["version_number"],
            "version_id": version["id"],
            "dependency_of": parent,
            "env": env,
            "file": {
                "name": primary["filename"],
                "url": primary["url"],
                "size": primary["size"],
                "sha1": primary["hashes"]["sha1"],
                "sha512": primary["hashes"]["sha512"],
            },
        }
        deps[project["id"]] = [d["project_id"] for d in version["dependencies"]
                               if d["dependency_type"] == "required" and d.get("project_id")]
        queue.extend((d, project["slug"]) for d in deps[project["id"]])
        print(f"  {project['slug']:<45} {version['version_number']}" + (f"   (dep of {parent})" if parent else ""))

    # Modrinth side tags are often wrong for libraries (e.g. athena is tagged
    # client-only but Chipped needs it on the server), so a server mod's
    # required libraries are always installed on the server too.
    changed = True
    while changed:
        changed = False
        for pid, mod in resolved.items():
            if mod["env"]["server"] == "unsupported":
                continue
            for dep in deps[pid]:
                if dep in resolved and resolved[dep]["env"]["server"] == "unsupported":
                    resolved[dep]["env"]["server"] = "required"
                    print(f"  {resolved[dep]['slug']} forced onto server (needed by {mod['slug']})")
                    changed = True

    mods = sorted(resolved.values(), key=lambda m: m["slug"])
    with open(LOCK, "w") as f:
        json.dump({"minecraft": mc, "loader": loader, "mods": mods}, f, indent=2)
        f.write("\n")
    print(f"\nLocked {len(mods)} mods ({sum(m['dependency_of'] is not None for m in mods)} pulled in as dependencies) -> {LOCK}")
    if missing:
        print("Skipped:\n  " + "\n  ".join(missing))
        if args.strict:
            sys.exit(1)


def download(entry, dest_dir):
    dest = os.path.join(dest_dir, entry["file"]["name"])
    if os.path.exists(dest) and sha512(dest) == entry["file"]["sha512"]:
        return False
    tmp = dest + ".part"
    req = urllib.request.Request(entry["file"]["url"], headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as out:
        while chunk := r.read(1 << 20):
            out.write(chunk)
    if sha512(tmp) != entry["file"]["sha512"]:
        os.remove(tmp)
        raise RuntimeError(f"hash mismatch for {entry['file']['name']}")
    os.replace(tmp, dest)
    return True


def sha512(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def cmd_server(args):
    lock = json.load(open(LOCK))
    os.makedirs(args.mods_dir, exist_ok=True)
    wanted = [m for m in lock["mods"] if m["env"]["server"] != "unsupported"]
    wanted_names = {m["file"]["name"] for m in wanted}
    managed = os.path.join(args.mods_dir, ".managed.json")
    previous = set(json.load(open(managed))) if os.path.exists(managed) else set()
    for stale in previous - wanted_names:
        path = os.path.join(args.mods_dir, stale)
        if os.path.exists(path):
            os.remove(path)
            print(f"  removed {stale}")
    fetched = 0
    for m in wanted:
        if download(m, args.mods_dir):
            fetched += 1
            print(f"  got {m['file']['name']}")
    json.dump(sorted(wanted_names), open(managed, "w"), indent=1)
    print(f"{len(wanted)} server mods in {args.mods_dir} ({fetched} downloaded, "
          f"{len(lock['mods']) - len(wanted)} client-only mods skipped)")


def cmd_mrpack(args):
    lock = json.load(open(LOCK))
    meta = json.load(open(os.path.join(ROOT, "pack.json")))
    files = []
    for m in lock["mods"]:
        if m["env"]["client"] == "unsupported":
            continue
        files.append({
            "path": f"mods/{m['file']['name']}",
            "hashes": {"sha1": m["file"]["sha1"], "sha512": m["file"]["sha512"]},
            "env": m["env"],
            "downloads": [m["file"]["url"]],
            "fileSize": m["file"]["size"],
        })
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": meta["version"],
        "name": meta["name"],
        "summary": meta["summary"],
        "files": files,
        "dependencies": {"minecraft": meta["minecraft"], "fabric-loader": meta["fabric_loader"]},
    }
    out = args.output or os.path.join(ROOT, "dist", f"{meta['slug']}-{meta['version']}.mrpack")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        overrides = os.path.join(ROOT, "client-overrides")
        for base, _, names in os.walk(overrides):
            for n in names:
                full = os.path.join(base, n)
                z.write(full, os.path.join("overrides", os.path.relpath(full, overrides)))
    print(f"Client pack with {len(files)} mods -> {out}")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    lk = sub.add_parser("lock")
    lk.add_argument("--strict", action="store_true", help="fail if any listed mod can't be resolved")
    sv = sub.add_parser("server")
    sv.add_argument("--mods-dir", default=os.path.join(ROOT, "runtime", "mods"))
    mp = sub.add_parser("mrpack")
    mp.add_argument("--output")
    args = p.parse_args()
    {"lock": cmd_lock, "server": cmd_server, "mrpack": cmd_mrpack}[args.cmd](args)


if __name__ == "__main__":
    main()
