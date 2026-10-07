# Vapez

## Minecraft server tooling (`server/`)

Scripts for the Fabric servers on `crazycraft-vm`. Each server lives in `~/<name>` (for example `~/lifesteal1`) and runs in a tmux session of the same name. Install the scripts into `~/server-tools`, symlink `mc` into `~/bin`, and load the schedule with `crontab ~/server-tools/crontab`.

| Command | What it does |
|---|---|
| `mc <name> start` / `stop` / `status` | Start, stop, or check the server, its mod watcher, and `~/<name>-geyser` if present. |
| `mc <name> restart [secs]` | Count down in chat (default 30s), then restart. |
| `mc <name> console` | Open the server console. Leave with Ctrl+B, then D. |
| `mc <name> cmd <command>` | Run a console command, e.g. `mc lifesteal1 cmd say hi`. |
| `mc <name> restart-geyser` | Restart Geyser Standalone after editing its `config.yml`. |
| `backup.sh <name> [label]` | Back up the server while it runs, into `~/backups`. |
| `reset-dimensions.sh <name> [secs]` | Warn players, back up, move players out of the Nether/End, and regenerate both. |

- **Adding mods:** copy the `.jar` into `~/<name>/mods`. When the folder has been unchanged for 30 seconds, the watcher checks that every jar is complete, warns players, and restarts the server. Fabric cannot load mods into a running server, so a restart is always needed.
- **Crashes:** `start.sh` restarts the server after a crash. If it crashes 3 times within 2 minutes of starting, it gives up so a broken mod can't cause an endless loop. Remove the mod and run `mc <name> start`.
- **RAM:** set `MIN_RAM` / `MAX_RAM` in `~/<name>/server.env`.
- **Schedule (`server/crontab`, UTC):** start on boot, daily backups at 05:00 (kept 14 days), and a Nether/End reset of `lifesteal1` at 06:00 on 1 Jan, 1 Apr, 1 Jul and 1 Oct, after a 30 minute countdown.
- **Reset safety:** `move_players_out.py` edits the saved player files (via `nbt.py`), so players who logged out in the Nether or End respawn at world spawn instead of inside new terrain.
