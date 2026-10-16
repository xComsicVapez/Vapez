#!/usr/bin/env bash
# Attach to the live server console. Detach again with Ctrl+B then D (don't Ctrl+C).
# In the any-version edition, Ctrl+B then N switches between the server and the proxy.
exec tmux attach -t "=${SESSION:-factions}:server"
