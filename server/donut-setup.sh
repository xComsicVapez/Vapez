#!/usr/bin/env bash
# DonutSMP-style settings for a server folder. Safe to run again; backs up each file it changes.
#
#   donut-setup.sh <server-name>      e.g. donut-setup.sh lifesteal2
#
# Mods this expects in <server>/mods (install them first): fabric-carpet, styled-sidebars, allied,
# economy-link (economy-link-mod/), command-aliases 1.1+ (command-aliases-mod/). simpleskills is not used.
# To merge old shop/auction balances into the one wallet, stop the server and run merge_economy.py once.
set -euo pipefail
TOOLS_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
NAME="${1:?usage: donut-setup.sh <server-name>}"
DIR="$HOME/$NAME"
BACKUPS="$HOME/backups/config-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUPS"
backup() { [ -f "$1" ] && cp "$1" "$BACKUPS/$(basename "$1")"; }

# Teleports: 5 second warmup that is cancelled by moving or taking damage; 3 homes by default.
EC="$DIR/config/EssentialCommands.properties"
backup "$EC"
sed -i -e 's/^teleport_delay=.*/teleport_delay=5.0/' \
       -e 's/^teleport_interrupt_on_move=.*/teleport_interrupt_on_move=true/' \
       -e 's/^teleport_interrupt_on_damaged=.*/teleport_interrupt_on_damaged=true/' \
       -e 's/^home_limit=.*/home_limit=[3, 5, 10]/' "$EC"

# Combat tag: 15 seconds, no teleporting/escaping while tagged, logging out kills.
CL="$DIR/config/combatlog-common.json5"
backup "$CL"
sed -i -e 's/^\tcombatTime: .*/\tcombatTime: 15,/' \
       -e 's/^\tblockedCommands: .*/\tblockedCommands: "spawn,rtp,randomteleport,home,tpa,tpahere,tpaccept,back,warp,suicide,afk",/' \
       -e 's/^\tdisconnectKill: .*/\tdisconnectKill: true,/' "$CL"

# /pay belongs to Savs Common Economy; the auction house's own /pay would be a second command for the same wallet.
AH="$DIR/world/echoesauctionhouse/config.json"
if [ -f "$AH" ]; then
  backup "$AH"
  sed -i 's/"payCommandEnabled": true/"payCommandEnabled": false/' "$AH"
fi

# Sidebar with money, kills, deaths and playtime for everyone.
SB="$DIR/config/styled-sidebars"
if [ -d "$SB/styles" ]; then
  cp "$TOOLS_DIR/styled-sidebars/donut.json" "$SB/styles/donut.json"
  backup "$SB/config.json"
  sed -i 's/"default_style": "[^"]*"/"default_style": "donut"/' "$SB/config.json"
else
  echo "styled-sidebars has not created its config yet: start the server once with the mod, then run this again."
fi

# Carpet commands that would let players read block data, run scarpet scripts or profile the server: ops only.
# These are console commands, so the server has to be running; carpet saves them in world/carpet.conf.
if tmux has-session -t "=$NAME" 2>/dev/null; then
  for rule in commandScript commandInfo commandLog commandDistance commandPerimeterInfo commandProfile commandSpawn; do
    "$TOOLS_DIR/mc" "$NAME" cmd "carpet setDefault $rule ops"
  done
else
  echo "Server is not running: start it and run this again to lock down Carpet's commands."
fi

echo "Done. Backups of changed files: $BACKUPS. Restart the server (mc $NAME restart) to apply config changes."
