#!/usr/bin/env bash
# Rebuilds vapez-config-ping-timeout.jar (needs a JDK 21 and a runtime that setup.sh has
# already populated, for the Mixin library). The built jar is committed, so servers
# only need a JRE.
set -euo pipefail
cd "$(dirname "$0")"
MIXIN=$(ls ../../runtime*/libraries/net/fabricmc/sponge-mixin/*/sponge-mixin-*.jar 2>/dev/null | head -1 || true)
[ -n "$MIXIN" ] || { echo "Run ../../setup.sh first (need the sponge-mixin library)" >&2; exit 1; }
rm -rf build && mkdir -p build/stubs build/classes
javac --release 21 -d build/stubs stubs/net/minecraft/server/MinecraftServer.java
javac --release 21 -proc:none -cp "$MIXIN:build/stubs" -d build/classes $(find src -name '*.java')
cp resources/* build/classes/
jar --create --file vapez-config-ping-timeout.jar -C build/classes .
rm -rf build
echo "Built $(pwd)/vapez-config-ping-timeout.jar"
