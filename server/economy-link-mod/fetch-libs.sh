#!/usr/bin/env bash
# Copies the economy mods this mod compiles against from the server into libs/.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p libs
scp "${1:-mc}:lifesteal2/mods/{ClassicGUIShop-*.jar,echoesauctionhouse-*.jar,savs-common-economy-*.jar,placeholder-api-*.jar}" libs/
