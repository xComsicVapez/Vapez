#!/usr/bin/env bash
# Build the Kit PvP sky-plateau map into the running server.
#   ./arena.sh            -> centred on 0,0 with the plateau surface at Y=180 (just under the clouds)
#   ./arena.sh 3000 180 0 -> somewhere else
# Pre-generates the area with Chunky first, then runs the datapack build. The world
# spawn is moved to the castle courtyard when it finishes.
set -euo pipefail
cd "$(dirname "$0")"
SESSION="${SESSION:-factions}"
X="${1:-0}"; Y="${2:-180}"; Z="${3:-0}"
LOG=logs/console.log
send() { tmux send-keys -t "=$SESSION:server" "$1" Enter; }
tmux has-session -t "=$SESSION" 2>/dev/null || { echo "Start the server first (./start.sh)"; exit 1; }
[ -d world/datapacks/vapez-arena ] || { echo "Missing world/datapacks/vapez-arena; run ../setup.sh and restart"; exit 1; }

wait_for() {
  local pattern="$1" start="$2" limit="$3"
  for _ in $(seq 1 "$limit"); do
    tail -n +"$start" "$LOG" | grep -q "$pattern" && return 0
    sleep 5
  done
  return 1
}

echo "==> Pre-generating the area around $X,$Z"
start=$(($(wc -l < "$LOG") + 1))
send "chunky world minecraft:overworld"
send "chunky shape square"
send "chunky center $X $Z"
send "chunky radius 216"
send "chunky start"
wait_for "Task finished for minecraft:overworld" "$start" 720 || { echo "Pre-generation didn't finish; check ./console.sh"; exit 1; }

echo "==> Building (the server will lag for a minute or two)"
start=$(($(wc -l < "$LOG") + 1))
send "function vapez:arena/build {x:$X,y:$Y,z:$Z}"
wait_for "Kit PvP map finished" "$start" 720 || { echo "Build didn't report finishing; check ./console.sh"; exit 1; }
send "save-all"
echo "Done. Spawn is now the castle courtyard at $X $((Y + 1)) $((Z + 10))."
