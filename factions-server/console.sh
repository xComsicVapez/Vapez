#!/usr/bin/env bash
# Attach to the live server console. Detach again with Ctrl+B then D (don't Ctrl+C).
exec tmux attach -t "=${SESSION:-factions}"
