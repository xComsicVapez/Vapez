# Installation

Target: **Purpur 1.21.8**, **Java 21+**, **60G Generational ZGC**.

## 0. Legal gate

Read [LEGAL.md](LEGAL.md). Do not drop leaked `UltimateDonutSMP` jars or PlanetMinecraft builds you do not own into this tree.

## 1. Host requirements

- 64-bit Linux (or Windows via `start.bat`) with **Java 21** (`java -version`)
- 64–72 GB RAM if you keep `-Xms60G -Xmx60G`
- Open **25565/tcp** (Java) and **19132/udp** (Bedrock / Geyser)
- Optional: Maven 3.8+ if you rebuild `VapezCore` (bootstrap runs `mvn`)

## 2. Bootstrap

From the repo root:

```bash
chmod +x scripts/*.sh pack/start.sh
./scripts/bootstrap.sh
sed -i 's/eula=false/eula=true/' pack/runtime/eula.txt
```

`bootstrap.sh` will:

1. Compile `plugin/` → `VapezCore-1.0.0.jar`
2. Generate original item textures, Geyser mappings pack, and `vapez-citadel.schem`
3. Download **Purpur 1.21.8** from `api.purpurmc.org`
4. Download official free plugins (Geyser, Floodgate, ViaVersion, ViaBackwards, PlaceholderAPI, DecentHolograms, Chunky, LuckPerms, spark, CoreProtect, Vault, EssentialsX, WorldEdit) plus Stardust Labs datapacks from Modrinth
5. Copy all configs into `pack/runtime/`

If a Modrinth lookup fails for your exact game version, the script prints `SKIP` — grab that jar from the project page listed in the table below.

## 3. Plugin catalog

### Required for the designed feature set

| Plugin | Role | Where to get it |
| --- | --- | --- |
| VapezCore | Economy, lifesteal, ore, forge, kit, border, spawn, hologram placeholders | Built in this repo |
| Vault | Economy API | GitHub MilkBowl / Spigot |
| EssentialsX | Economy provider, homes, tpa | Modrinth / Hangar |
| PlaceholderAPI | `%vapez_*%` + Vault/Player expansions | Modrinth |
| DecentHolograms | Leaderboards at spawn | Modrinth |
| LuckPerms | Groups | luckperms.net |
| Geyser-Spigot | Bedrock listener | download.geysermc.org |
| floodgate | Bedrock auth on online-mode | download.geysermc.org |
| ViaVersion | Newer Java clients | Modrinth |
| Chunky | Pregen | Modrinth |
| spark | TPS / profiler | Modrinth |

### Strongly recommended

| Plugin | Role | Where to get it |
| --- | --- | --- |
| ViaBackwards | Older 1.21.x Java clients | Modrinth |
| WorldGuard + WorldEdit or **FAWE** | Regions, schematic import | EngineHub / FastAsyncWorldEdit Jenkins |
| ProtocolLib | Dependency of several plugins | Spigot / Modrinth |
| CoreProtect | Rollbacks | Modrinth |
| Citizens or ZNPCsPlus | Player-skin NPCs (VapezCore already spawns villagers) | Modrinth / Spigot |

### Optional paid / premium (not bundled, not required)

VapezCore already covers shop, AH, warps, crates, lifesteal, first-join kit. Install these **only** if you want their extra UI and have a license:

- ShopGUIPlus / EconomyShopGUI
- AuctionHouse / zAuctionHouse
- PlayerWarps
- ExcellentCrates / CrazyCrates
- LifeStealZ
- Oraxen / ItemsAdder / Nexo (true custom ore *block* model)
- FirstJoinCommands (redundant)

**UltimateDonutSMP** is a third-party commercial product. Do not pirate it. This pack does not include it and does not need it.

## 4. First boot

```bash
cd pack/runtime
./start.sh
```

Expect:

- 30–90s **AlwaysPreTouch** of 60G
- `VapezCore` registering aetherium + Far Lands populators
- Aether Citadel generating around 0,0 over several ticks
- World border clamped to **diameter 10000** (10,000 × 10,000)

Stop the server once (`stop`) after the citadel log line appears.

## 5. Post-boot wiring

```text
cp plugins/floodgate/key.pem plugins/Geyser-Spigot/key.pem
```

Console:

```text
lp import file ../../../scripts/luckperms-bootstrap.txt
papi ecloud download Vault
papi ecloud download Player
papi ecloud download Statistic
papi reload
dh reload
```

If LuckPerms import-from-file is unavailable, paste `scripts/luckperms-bootstrap.txt` line by line (skip comments).

Copy WorldGuard regions if WG overwrote them:

```text
plugins/WorldGuard/worlds/world/regions.yml  ← from pack/plugins/WorldGuard/worlds/world/regions.yml
```

Then `/rg reload` (ops).

## 6. Resource pack

Java clients: host `pack/resourcepack/Vapez-Aetherium.zip` over HTTPS and set in `server.properties`:

```properties
resource-pack=https://your-cdn.example/Vapez-Aetherium.zip
resource-pack-sha1=<sha1>
require-resource-pack=false
```

Bedrock: Geyser serves `plugins/Geyser-Spigot/packs/vapez-aetherium.mcpack` automatically when the mapping file is present.

## 7. Pregen

See [FAR-LANDS.md](FAR-LANDS.md) and `scripts/pregen.sh`. Do this before opening the server: Chunky at radius 5000 on `world`.

## 8. Datapacks (before first worldgen, ideally)

Bootstrap already drops Terralith / Structory / Towns and Towers into `world/datapacks/` when Modrinth resolves them. If you already generated `world/`, they only affect **new chunks** (border expansions). For a clean map, delete `world/`, `world_nether/`, `world_the_end/` **before** first start after datapacks are in place.

Keep `world/datapacks/vapez-aetherium` — it is original. The plugin populator is still the primary aetherium generator (Terralith-safe).

## 9. Firewall

```bash
ufw allow 25565/tcp
ufw allow 19132/udp
```

## 10. Verify

Join from Java 1.21.8 and from a Bedrock client pointing at `host:19132`.

- First join: uniquely colored shulker kit
- `/shop`, `/ah`, `/level` work
- `/worldborder status` shows 10000
- Spawn is the Citadel, not a dirt hut
- `spark tps` after pregen
