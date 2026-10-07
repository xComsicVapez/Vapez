#!/bin/bash
# Reset the Nether and the End of a server. The Overworld is never touched.
#
#   reset-dimensions.sh <name> [warning-seconds]
#
# Warns players (default 30 minutes), stops the server, takes a "pre-reset"
# backup, moves players who logged out in the Nether/End to world spawn, deletes
# both dimensions (they regenerate fresh, with a new Ender Dragon), and starts
# the server again. World borders set in those dimensions are kept.

set -u
TOOLS_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
NAME="$1"
WARNING="${2:-1800}"
DIR="$HOME/$NAME"
MC="$TOOLS_DIR/mc"
LEVEL="$(grep -E '^level-name=' "$DIR/server.properties" | cut -d= -f2-)"
WORLD="$DIR/${LEVEL:-world}"
log() { echo "[$(date '+%F %T')] $*" | tee -a "$DIR/logs/manager.log"; }

was_running=0
if tmux has-session -t "=$NAME" 2>/dev/null; then
  was_running=1
  log "Nether/End reset: warning players for $WARNING seconds"
  "$MC" "$NAME" warn "$WARNING" "The Nether and End will be reset"
  "$MC" "$NAME" stop
fi

"$TOOLS_DIR/backup.sh" "$NAME" pre-reset || { log "backup failed, reset cancelled"; [ "$was_running" = 1 ] && "$MC" "$NAME" start; exit 1; }

python3 "$TOOLS_DIR/move_players_out.py" "$WORLD" minecraft:the_nether minecraft:the_end | tee -a "$DIR/logs/manager.log"

for dim in the_nether the_end; do
  path="$WORLD/dimensions/minecraft/$dim"
  [ -d "$path" ] || continue
  border="$path/data/minecraft/world_border.dat"
  [ -f "$border" ] && cp "$border" "/tmp/$NAME-$dim-world_border.dat"
  rm -rf "$path"
  if [ -f "/tmp/$NAME-$dim-world_border.dat" ]; then
    mkdir -p "$path/data/minecraft"
    mv "/tmp/$NAME-$dim-world_border.dat" "$border"
  fi
  log "reset $dim"
done

[ "$was_running" = 1 ] && "$MC" "$NAME" start
log "Nether/End reset complete"
