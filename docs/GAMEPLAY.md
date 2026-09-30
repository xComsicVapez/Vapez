# Gameplay

VapezCore is an original SMP framework. It is **inspired by the economy-SMP genre** (shops, auctions, warps, lifesteal, crates, levels) but does not copy DonutSMP items, maps, textures, NPCs, or plugin dumps.

## Economy

Vault + EssentialsX are the ledger. VapezCore GUIs sit on top.

- `/shop` — buy/sell categories (blocks, food, combat, ores, spawner-fuel, farming)
- `/ah` — browse; `/ah sell <price>` lists the held item (listing fee from config)
- `/bal` `/pay`
- `/pwarp set <name>` costs money; `/pwarp <name>` warms up 3s
- `/bounty add <player> <amount>` — killer of that player receives the pot

## Progression `/level`

XP from playtime (per minute), player kills, diamond/debris/emerald mining, and aetherium. Curve: `base * growth^(level-1)` (defaults 100 and 1.18, cap 100).

Placeholders: `%vapez_level%` `%vapez_xp%`.

## Lifesteal

Default 10 hearts (20 HP). Kill a player: steal 1 heart. Min 1, max 20. Overflow drops a **Stolen Heart** item (`/lifesteal withdraw` also produces one). Hearts live in `PersistentDataContainer` key `vapez:lifesteal_hearts`.

## First-join kit

Every new player receives **one** shulker box whose **dye color is derived from their UUID** (all 16 vanilla colors). Contents come from `config.yml` `starter-kit.contents`, plus one crate key. PDC flag `vapez:unique_kit` prevents re-issue. Vault deposit `starter-kit.money`.

## Aetherium (late-game ore)

- Generates in the overworld only, **Y=-64 to -50**
- **1 attempt per 72 chunks** (diamonds are ~1 vein per chunk at peak bands) with 1–3 blocks/vein
- Placed as `HEAVY_CORE` (does not naturally appear in overworld caves), so it is visually unique without ItemsAdder
- Requires **netherite pickaxe** (or the Aetherium Drill)
- Drops **Aetherium Crystal** → blast-furnace **Aetherium Ingot**
- Skips the 160×160 spawn footprint
- Optional Oraxen/ItemsAdder configs are included if you want a fully custom block model later

Craft netherite-tier recipes from ingots for saber / drill / cleaver / armor (all strictly better than netherite via attributes).

## Sovereign Edge — one craft per lifetime

`/aetherforge` or the west-wing smith NPC. Ingredients (must be in inventory):

- 8 Aetherium Ingots
- 1 Nether Star
- 1 Netherite Ingot
- 1 Heavy Core
- 1 Dragon Head
- 1 Beacon
- 1 Enchanted Golden Apple
- 1 Elytra
- 1 Totem of Undying
- 1 Heart of the Sea
- 4 Echo Shards

On success:

1. Player PDC `vapez:sovereign_crafted = 1`
2. Backup YAML `plugins/VapezCore/lifetime-crafts.yml`
3. Relic granted: Sharpness 7, extra damage/speed/HP/knockback resist, Void Rend dash, 18% void-lightning on hit, end-rod particles

A second craft is refused for that player forever (even if they drop the sword). Optional Skript mirror: `pack/skript/vapez-lifetime-forge.sk` (keep disabled while VapezCore is installed).

## Crates

North pavilion chests. Right-click with a **Citadel Crate Key**. Ops: `/crates give <player> [n]`.

## NPCs

Invulnerable, no-AI villagers at the Citadel (shop, auction, warps, forge, crates). ZNPCsPlus can replace them with player skins; coordinates in `pack/plugins/ZNPCsPlus/config.yml`.

## Holograms

DecentHolograms files under `plugins/DecentHolograms/holograms/`:

- `%vapez_kills_top_n%`
- `%vapez_bal_top_n%`
- `%vapez_playtime_top_n%`
- `%vapez_border%`

## Admin

`/vapez give aetherium_crystal|aetherium_ingot|sovereign_edge|...`
`/vapez buildspawn` — rebuild the Citadel
`/vapez reload`
