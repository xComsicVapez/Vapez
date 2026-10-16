#!/usr/bin/env bash
# Saves the world and shuts the server down cleanly (no auto-restart).
set -euo pipefail
cd "$(dirname "$0")"
SESSION="${SESSION:-factions}"
if ! tmux has-session -t "=$SESSION" 2>/dev/null; then
  echo "Server is not running."
  exit 0
fi
touch .stop
if ! tmux list-windows -t "=$SESSION" -F '#W' | grep -qx server; then
  tmux kill-session -t "=$SESSION"
  rm -f .stop
  echo "Stopped (only the proxy was still running)."
  exit 0
fi
tmux send-keys -t "=$SESSION:server" "say Server shutting down in 10 seconds" Enter
sleep 10
if tmux list-windows -t "=$SESSION" -F '#W' | grep -qx proxy; then
  tmux send-keys -t "=$SESSION:proxy" "shutdown" Enter
  for _ in $(seq 1 30); do
    tmux list-windows -t "=$SESSION" -F '#W' | grep -qx proxy || break
    sleep 1
  done
fi
tmux send-keys -t "=$SESSION:server" "stop" Enter
for _ in $(seq 1 120); do
  tmux has-session -t "=$SESSION" 2>/dev/null || { echo "Stopped."; exit 0; }
  sleep 1
done
echo "Still running after 2 minutes (it may be stuck generating spawn). Check ./console.sh;" >&2
echo "to force it:  kill \$(pgrep -P \$(tmux list-panes -t '=$SESSION:server' -F '#{pane_pid}') -f fabric-server-launch)" >&2
exit 1
