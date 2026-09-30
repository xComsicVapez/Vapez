# Worldgen

Vanilla overworld is replaced in *new chunks* by Stardust Labs datapacks, which this pack **downloads** (it does not vendor their zip files):

| Datapack | Modrinth slug | Role |
| --- | --- | --- |
| Terralith | `terralith` | Unique overworld biomes / terrain |
| Structory | `structory` | Overworld structure variety |
| Towns and Towers | `towns-and-towers` | Villages / outposts / unique towns |

Install into `world/datapacks/` **before** the first chunk is generated for a full effect. On an existing world they only apply past the current generated border.

Incendium (nether) is optional and not enabled by default.

## Aetherium vs Terralith

Terralith replaces biome/feature JSON. A vanilla datapack that injects ores into `minecraft:` biomes will **miss** Terralith biomes.

`VapezCore` therefore generates aetherium with a **Bukkit `BlockPopulator`** after chunk population. That runs on every overworld generator, including Terralith. The included `pack/datapacks/vapez-aetherium` is documentation + a vanilla fallback (heavy_core ore feature at Y=-64..-50, rarity 72). Keep the plugin enabled.

## Custom structures

Structory + Towns and Towers provide the “non-vanilla structures and biomes” requirement. Do not dump copyrighted adventure maps into `world/generated/`.

## Seeds

`server.properties` `level-seed` is empty (random). Set a seed before first boot if you want a reproducible Citadel-adjacent landscape. The Citadel still flattens its own 17×17 chunk pad.

## Oraxen / ItemsAdder

YAML stubs live under `pack/plugins/Oraxen` and `pack/plugins/ItemsAdder`. They are **off** unless you install those plugins. Default ore visuals use `HEAVY_CORE` so the server is playable with zero paid resource-pack plugins.
