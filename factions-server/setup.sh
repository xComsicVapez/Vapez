#!/usr/bin/env bash
# Install or update the factions server into ./runtime (safe to re-run; the world is kept).
#   EDITION=any-version ./setup.sh   -> vanilla-client edition in ./runtime-any-version
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
EDITION="${EDITION:-full}"
if [ "$EDITION" = full ]; then
  RUNTIME="${RUNTIME:-$ROOT/runtime}"
else
  RUNTIME="${RUNTIME:-$ROOT/runtime-$EDITION}"
fi

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
python3 tools/modpack.py --edition "$EDITION" server --mods-dir "$RUNTIME/mods"

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
if [ "$EDITION" = any-version ]; then
  VELOCITY_VERSION=4.2.0
  echo "==> Velocity $VELOCITY_VERSION proxy with ViaVersion"
  [ -n "$(ls /usr/lib/jvm/{java-25-openjdk*,jdk-25*}/bin/java 2>/dev/null)" ] ||
    echo "  ! Velocity needs Java 25: sudo apt install openjdk-25-jre-headless (the server itself stays on 21)"
  mkdir -p "$RUNTIME/proxy"
  read -r vurl vsha < <(curl -fsSL "https://fill.papermc.io/v3/projects/velocity/versions/$VELOCITY_VERSION/builds/latest" |
    python3 -c 'import json,sys; d=json.load(sys.stdin)["downloads"]["server:default"]; print(d["url"], d["checksums"]["sha256"])')
  if ! echo "$vsha  $RUNTIME/proxy/velocity.jar" | sha256sum -c --status 2>/dev/null; then
    curl -fsSL -o "$RUNTIME/proxy/velocity.jar.part" "$vurl"
    echo "$vsha  $RUNTIME/proxy/velocity.jar.part" | sha256sum -c --status || { echo "Velocity download corrupt" >&2; exit 1; }
    mv "$RUNTIME/proxy/velocity.jar.part" "$RUNTIME/proxy/velocity.jar"
  fi
  python3 tools/modpack.py --edition "$EDITION" proxy --plugins-dir "$RUNTIME/proxy/plugins"
  # Without this, 1.17-1.20.1 players hang on "Joining world" (see compat/config-ping-timeout).
  cp compat/config-ping-timeout/vapez-config-ping-timeout.jar "$RUNTIME/mods/"
  [ -e "$RUNTIME/proxy/velocity.toml" ] || cp proxy-config/velocity.toml "$RUNTIME/proxy/velocity.toml"
  [ -s "$RUNTIME/proxy/forwarding.secret" ] || head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$RUNTIME/proxy/forwarding.secret"
  # The Fabric server only listens on localhost; the proxy does the Mojang login and
  # forwards each player's real UUID and skin, signed with the shared secret.
  python3 - "$RUNTIME" <<'EOF'
import os, sys
rt = sys.argv[1]
want = {"server-ip": "127.0.0.1", "server-port": "25566", "online-mode": "false",
        "enforce-secure-profile": "false", "prevent-proxy-connections": "false"}
path = os.path.join(rt, "server.properties")
lines = open(path).read().splitlines()
for i, line in enumerate(lines):
    k = line.split("=", 1)[0]
    if k in want:
        lines[i] = f"{k}={want.pop(k)}"
lines += [f"{k}={v}" for k, v in want.items()]
open(path, "w").write("\n".join(lines) + "\n")
secret = open(os.path.join(rt, "proxy", "forwarding.secret")).read().strip()
os.makedirs(os.path.join(rt, "config"), exist_ok=True)
open(os.path.join(rt, "config", "FabricProxy-Lite.toml"), "w").write(
    'hackOnlineMode = true\nhackEarlySend = false\nhackMessageChain = true\n'
    'disconnectMessage = "Join through the main server address."\n'
    f'secret = "{secret}"\n')
EOF
fi

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
