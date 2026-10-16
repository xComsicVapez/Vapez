#!/bin/bash
# Back up a server folder (world, mods, config) while it keeps running.
#
#   backup.sh <name> [label]
#
# Backups go to ~/backups/<name>-<label>-<timestamp>.tar.gz. Backups labelled
# "daily" older than KEEP_DAYS (default 14) are deleted; other labels are kept.

set -u
TOOLS_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
NAME="$1"
LABEL="${2:-manual}"
DIR="$HOME/$NAME"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
MC="$TOOLS_DIR/mc"

mkdir -p "$BACKUP_DIR"
target="$BACKUP_DIR/$NAME-$LABEL-$(date +%Y%m%d-%H%M%S).tar.gz"
was_running=0
tmux has-session -t "=$NAME" 2>/dev/null && was_running=1

count_saves() { local n; n=$(grep -c "Saved the game" "$DIR/logs/latest.log" 2>/dev/null || true); echo "${n:-0}"; }

if [ "$was_running" = 1 ]; then
  saves_before=$(count_saves)
  "$MC" "$NAME" cmd "save-off"
  "$MC" "$NAME" cmd "save-all flush"
  for _ in $(seq 1 120); do
    [ "$(count_saves)" -gt "$saves_before" ] && break
    sleep 1
  done
fi

tar czf "$target" -C "$HOME" \
  --exclude="$NAME/libraries" --exclude="$NAME/versions" --exclude="$NAME/.fabric" \
  --exclude="$NAME/logs" --exclude="$NAME/crash-reports" --exclude="$NAME/world/session.lock" \
  "$NAME"
status=$?

[ "$was_running" = 1 ] && "$MC" "$NAME" cmd "save-on"

if [ "$status" -gt 1 ]; then
  echo "Backup FAILED: $target"
  exit 1
fi
echo "Backup written: $target ($(du -h "$target" | cut -f1))"

find "$BACKUP_DIR" -maxdepth 1 -name "$NAME-daily-*.tar.gz" -mtime +"$KEEP_DAYS" -print -delete
