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
- **Schedule (`server/crontab`, UTC):** start `lifesteal2` (the live server) on boot, daily backups at 05:00 (kept 14 days), and a Nether/End reset at 06:00 on 1 Jan, 1 Apr, 1 Jul and 1 Oct, after a 30 minute countdown. `lifesteal1` is retired and kept only as a fallback.
- **In-game AI admin:** `server/ai-admin-mod` is a server-side Fabric mod that adds `/ai <request>`, usable only by the UUIDs listed in `config/ai-admin.json` (`{"owners": ["<uuid>"]}`); everyone else gets "Unknown command". `ai_admin_bridge.py` reads the requests, asks a local Ollama model, and acts through RCON and the server files. Read-only actions run straight away. Risky ones (non-allowlisted commands, installing/removing mods, editing files, restarts) wait for `/ai yes` or `/ai no`. `/ai reset` clears the conversation. Mods are installed from Modrinth with their required dependencies, and client-required mods are refused. If a new mod stops the server from booting, it is moved to `~/<name>-mods-disabled` and the server is started again. `mc <name> start` launches the bridge when `config/ai-admin-bridge.json` exists (`ollama_url`, `model`, `minecraft_version`, `rcon_port`, `rcon_password`). Everything is logged to `ai-admin/audit.log`. Build the mod with `./gradlew build` (Java 25).
- **Reset safety:** `move_players_out.py` edits the saved player files (via `nbt.py`), so players who logged out in the Nether or End respawn at world spawn instead of inside new terrain.
