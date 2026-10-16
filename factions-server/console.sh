#!/usr/bin/env bash
# Attach to the live server console. Detach again with Ctrl+B then D (don't Ctrl+C).
# In the any-version edition, Ctrl+B then N switches between the server and the proxy.
SESSION="${SESSION:-factions}"
if tmux list-windows -t "=$SESSION" -F '#W' 2>/dev/null | grep -qx server; then
  exec tmux attach -t "=$SESSION:server"
fi
exec tmux attach -t "=$SESSION"
