# Operations

## JVM

`pack/start.sh` uses the requested flags plus production extras:

```
-Xms60G -Xmx60G
-XX:+UnlockExperimentalVMOptions
-XX:+UseZGC
-XX:+ZGenerational
-XX:+AlwaysPreTouch
```

Do **not** mix Aikar G1GC flags with ZGC. Paper’s vector module is enabled via `--add-modules=jdk.incubator.vector`.

Java 23+ treats generational ZGC as default; the `ZGenerational` flag may warn. Keep it for Java 21.

## Paper / Purpur knobs that matter

- `view-distance=8`, `simulation-distance=6`
- Anti-xray engine mode 1, including `heavy_core`
- Alternate Current redstone
- Per-player mob spawns
- Entity activation ranges tightened
- Villager lobotomy (`purpur.yml`)
- spark enabled on startup (`paper-global.yml`)

## Daily

```
spark tps
spark health
spark profiler --timeout 30
chunky continue     # if a pregen was paused
```

Watch `mspt`. If it climbs after a border expand, you skipped Chunky.

## Backups

Worlds: `world`, `world_nether`, `world_the_end`  
Plugin data: `plugins/VapezCore/*.yml` (stats, auctions, warps, lifetime-crafts)  
Player PDC lives inside `world/playerdata/` — a world backup includes kit/forge/lifesteal flags.

## Restart

`stop` from console. `start.sh` is also the watchdog restart script in `spigot.yml`.

## Security

- `online-mode=true`
- `enable-command-block=false`
- `rcon` disabled by default
- Anti-xray on
- WorldGuard spawn deny-break
- Packet limiter in Paper + ViaVersion

## When you outgrow 10k

1. `/worldborder expand 10000` (or `set`)
2. Chunky `worldborder` + `start`
3. Announce. Do not enable Far Lands until you actually want 12.5M+ chaos.
