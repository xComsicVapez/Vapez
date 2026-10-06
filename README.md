# Vapez SMP

Original, high-performance **Purpur 1.21.8** Survival Multiplayer pack for Java 21+ with **60G Generational ZGC**, Java+Bedrock cross-play, and an original economy / lifesteal / progression framework.

This is **not** a DonutSMP clone, leak, or redistribution. Gameplay is in the same *genre* (economy SMP, auction house, player warps, lifesteal, crates, shop) and is implemented in original `VapezCore` code plus configs for **legally obtained** plugins. See [docs/LEGAL.md](docs/LEGAL.md).

## What you get

| Layer | Contents |
| --- | --- |
| Runtime | Purpur 1.21.8, `start.sh` with `-Xms60G -Xmx60G -XX:+UseZGC -XX:+ZGenerational`, Paper/Purpur tuning |
| Cross-play | GeyserMC + Floodgate + ViaVersion, Geyser mappings v2, Java + Bedrock resource packs |
| Gameplay | `VapezCore` — `/shop`, `/ah`, `/pwarp`, `/level`, `/lifesteal`, `/aetherforge`, `/worldborder`, RTP, bounties, crates, unique first-join shulker kit |
| Ore | **Aetherium**, rarer than diamonds, Y=-64 to -50, netherite-pick only, plugin populator (Terralith-safe) |
| Relic | **Sovereign Edge** — one craft per player lifetime via `PersistentDataContainer` |
| Spawn | Original **Aether Citadel**, WorldGuard + no-death/no-mob guard, phone dashboard on :25580, `/vapez spawnstatus` |
| Worldgen | Install path for Terralith + Structory + Towns and Towers (downloaded, not vendored) |
| Border | 10,000 × 10,000 initialized, Chunky pregen, live `/worldborder expand` out to vanilla max (~30M) with optional Far Lands overlay engine |

## Quick start

```bash
# 1. Java 21+
java -version

# 2. Assemble a runtime tree (downloads Purpur + official free plugins)
chmod +x scripts/*.sh pack/start.sh
./scripts/bootstrap.sh

# 3. Accept the Mojang EULA
sed -i 's/eula=false/eula=true/' pack/runtime/eula.txt

# 4. First boot (Citadel builds over a few seconds; 60G AlwaysPreTouch takes a while)
cd pack/runtime && ./start.sh
```

After first stop:

1. Copy `plugins/floodgate/key.pem` → `plugins/Geyser-Spigot/key.pem`
2. Host `pack/resourcepack/Vapez-Aetherium.zip` and set `resource-pack=` in `server.properties`
3. Paste [scripts/luckperms-bootstrap.txt](scripts/luckperms-bootstrap.txt) into console
4. `papi ecloud download Vault` then `papi ecloud download Player` then `papi reload`
5. Pregen: [scripts/pregen.sh](scripts/pregen.sh)

Full procedure: [docs/INSTALL.md](docs/INSTALL.md)

## Commands (VapezCore)

- `/shop` — Citadel market
- `/ah` / `/ah sell <price>` — auction house
- `/pwarp set\|list\|delete|<name>` — player warps
- `/level` — custom progression
- `/lifesteal` / `/lifesteal withdraw`
- `/aetherforge` — lifetime Sovereign ritual
- `/worldborder status\|set\|expand\|farlands\|pregen`
- `/crates give <player>` — ops
- `/spawn` `/rtp` `/bal` `/pay` `/bounty`
- `/vapez reload|give|buildspawn|spawnstatus|stats`

## Hardware

60G heap is the configured allocation. The machine needs **~64–72G RAM** total so the OS, native chunk compression, and Geyser have headroom. AlwaysPreTouch will stall 30–90s at boot; that is expected.

## Docs

- [Installation](docs/INSTALL.md)
- [Operations / TPS](docs/OPERATIONS.md)
- [Gameplay](docs/GAMEPLAY.md)
- [Cross-play & custom models](docs/CROSSPLAY.md)
- [Spawn citadel](docs/SPAWN.md)
- [Remote spawn check (phone)](docs/REMOTE.md)
- [Worldgen datapacks](docs/WORLDGEN.md)
- [World border & Far Lands](docs/FAR-LANDS.md)
- [Legal / licensing](docs/LEGAL.md)
