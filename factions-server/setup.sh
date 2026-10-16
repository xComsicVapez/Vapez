#!/usr/bin/env bash
# Install or update the factions server into ./runtime (safe to re-run; the world is kept).
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
RUNTIME="${RUNTIME:-$ROOT/runtime}"

need() { command -v "$1" >/dev/null || { echo "Missing '$1'. Install it first (e.g. sudo apt install $2)." >&2; exit 1; }; }
need python3 python3
need curl curl
need tmux tmux
need java openjdk-21-jre-headless
python3 -c 'import numpy' 2>/dev/null || { echo "Missing numpy for the arena generator: sudo apt install python3-numpy" >&2; exit 1; }
java_major=$(java -version 2>&1 | awk -F'"' '/version/{split($2,v,"."); print v[1]}')
if [ "${java_major:-0}" -lt 21 ]; then
  echo "Java 21+ is required (found $java_major). sudo apt install openjdk-21-jre-headless" >&2
  exit 1
fi

MC=$(python3 -c 'import json;print(json.load(open("pack.json"))["minecraft"])')
LOADER=$(python3 -c 'import json;print(json.load(open("pack.json"))["fabric_loader"])')
INSTALLER=1.1.2

mkdir -p "$RUNTIME"
echo "==> Fabric $LOADER server launcher for Minecraft $MC"
curl -fsSL -o "$RUNTIME/fabric-server-launch.jar" \
  "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"

echo "==> Mods"
python3 tools/modpack.py server --mods-dir "$RUNTIME/mods"

echo "==> Config"
# Only seed files that don't exist yet so your own edits survive updates.
(cd server-config && find . -type f) | while read -r f; do
  dest="$RUNTIME/${f#./}"
  if [ ! -e "$dest" ]; then
    mkdir -p "$(dirname "$dest")"
    cp "server-config/$f" "$dest"
    echo "  + ${f#./}"
  fi
done
echo "==> Kit PvP arena datapack"
python3 tools/build_arena.py --out "$RUNTIME/world/datapacks/vapez-arena"
cp start.sh stop.sh console.sh pregen.sh arena.sh "$RUNTIME/"
chmod +x "$RUNTIME"/*.sh

if ! grep -qs '^eula=true' "$RUNTIME/eula.txt"; then
  if [ "${ACCEPT_EULA:-}" = "true" ]; then
    echo "eula=true" > "$RUNTIME/eula.txt"
  else
    echo
    echo "Read https://aka.ms/MinecraftEULA, then run:  echo eula=true > $RUNTIME/eula.txt"
  fi
fi

echo
echo "Done. Start with:  $RUNTIME/start.sh   (uses 100G RAM; MEMORY=64G $RUNTIME/start.sh to change)"
