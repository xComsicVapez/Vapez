# Remote spawn check (phone / away from home)

You do **not** need to sit at the PC. VapezCore now publishes spawn status three ways.

## 1. Phone web map (best)

On the home router, forward **25580 TCP** to the Minecraft VM (same way you forwarded 25565).

Then on your phone open:

```
http://YOUR.PUBLIC.IP:25580/
```

You get a live top-down picture of spawn, whether the Citadel (or your transferred build) is there, hostile mob count, players in the zone, and whether break / death / mobs are blocked. The page refreshes every 15 seconds.

JSON: `http://YOUR.PUBLIC.IP:25580/api/spawn`  
PNG: `http://YOUR.PUBLIC.IP:25580/map.png`

Optional lock: set `inspect.dashboard-token` in `plugins/VapezCore/config.yml` and add `?token=...` to the URL.

## 2. Server list ping (no extra port)

If **25565** already works from the internet, open [mcsrvstat.us](https://mcsrvstat.us) (or any server-list app) and paste `YOUR.PUBLIC.IP:25565`.

The second MOTD line is live spawn status, for example:

```
Citadel OK · mobs 0 · safe
```

or

```
Vanilla spawn · mobs 3 · UNPROTECTED
```

Same info from this repo:

```bash
python3 scripts/query-server.py YOUR.PUBLIC.IP 25565
```

## 3. Console while you have any remote shell

```
vapez spawnstatus
```

Works from the server console (not only in-game). It scans immediately and prints structure, mobs, players, and protection flags.

Files on disk after each scan:

- `plugins/VapezCore/spawn-map.png`
- `plugins/VapezCore/spawn-status.json`

## What “safe spawn” means now

Inside `spawn.protection-radius` (default 128 blocks of **world spawn**, so a transferred world’s spawn is covered):

| Check | Default |
| --- | --- |
| Players break / place / bucket | blocked (ops with `vapez.admin` bypass) |
| Players take damage / die / starve / void | blocked; void teleports back |
| Hostile mobs spawn | blocked |
| Hostiles already in the zone | removed each scan |
| Explosions, fire, enderman grief, item frames | blocked |

## Transferred worlds

If you copied an old world in, VapezCore **will not flatten it into the Aether Citadel** when the spawn chunk already has inhabited time. Protection still centers on that world’s spawn.

- Want the original Citadel anyway? `/vapez buildspawn` (ops) — this **does** flatten.
- Just checking the old spawn? Open the dashboard; signature will read `CUSTOM` instead of `CITADEL`. That is OK.

## Bedrock from your phone

If Geyser’s **19132 UDP** is forwarded, you can also join with Minecraft on the phone and look around. The dashboard is for when you cannot or do not want to join.

## Discord (optional)

Set `inspect.discord-webhook` to a webhook URL. VapezCore posts when spawn status changes (mobs appeared, structure missing, etc.).
