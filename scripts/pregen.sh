#!/usr/bin/env bash
# Pregenerate the 10,000 × 10,000 overworld (and nether/end if desired) with Chunky.
# Requires a running server with RCON or wrap these as console commands.
set -euo pipefail

cat <<'EOF'
Paste into the Purpur console (or send via RCON) AFTER the Citadel finishes building:

  chunky pause
  chunky world world
  chunky center 0 0
  chunky radius 5000
  chunky shape square
  chunky confirm
  chunky start

  # Optional nether (radius scaled ~8:1 vanilla portal ratio → 625)
  chunky world world_nether
  chunky center 0 0
  chunky radius 625
  chunky shape square
  chunky start

  # Optional end
  chunky world world_the_end
  chunky center 0 0
  chunky radius 500
  chunky start

  spark tps
  spark profiler --timeout 60

When expanding the border later:
  /worldborder expand 10000
  chunky worldborder
  chunky start

Far Lands (after /worldborder farlands and a diameter past 25,101,642):
  /worldborder set 29999984
  # Do NOT pregen 30 million blocks. Pregen only the populated ring you actually need.
EOF
