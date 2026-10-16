#!/usr/bin/env bash
# Pre-generate the overworld around spawn so exploring doesn't lag the server.
# ./pregen.sh 10000   -> square of radius 10,000 blocks (20k x 20k). Rough disk use:
# 5k ~ 5 GB, 10k ~ 20 GB, 25k ~ 120 GB with this many worldgen mods.
set -euo pipefail
SESSION="${SESSION:-factions}"
RADIUS="${1:-10000}"
WIN=server
tmux list-windows -t "=$SESSION" -F '#W' | grep -qx server || WIN=""   # session from an older start.sh
send() { tmux send-keys -t "=$SESSION:$WIN" "$1" Enter; }
send "chunky world minecraft:overworld"
send "chunky center 0 0"
send "chunky radius $RADIUS"
send "chunky start"
echo "Pre-generating radius $RADIUS. Progress shows in ./console.sh; 'chunky pause' / 'chunky continue' to control."
