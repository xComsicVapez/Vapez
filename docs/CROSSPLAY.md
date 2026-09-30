# Cross-play (Java + Bedrock)

Geyser runs **in-process** on Purpur (not standalone) so Bedrock players share the same worlds, economy, and PDC data.

```
Bedrock client  --UDP 19132-->  Geyser-Spigot  --localhost 25565-->  Purpur (online-mode)
                                         \
                                          +-- Floodgate key.pem  (auth)
ViaVersion handles newer *Java* protocol versions than 1.21.8.
```

## Auth

`server.properties` keeps `online-mode=true`. Floodgate authenticates Xbox Live users and prefixes Bedrock names with `.` (see `plugins/floodgate/config.yml`). Java and Bedrock accounts can be linked (`/linkaccount`).

After first boot, **the same** `key.pem` must exist in:

- `plugins/floodgate/key.pem` (created by Floodgate)
- `plugins/Geyser-Spigot/key.pem` (copy it)

## Custom item mapping (Geyser v2)

Java 1.21.4+ custom items use `minecraft:item_model`. VapezCore sets `item_model` to `vapez:<id>` on every custom stack.

Mapping file (already in the pack):

`plugins/Geyser-Spigot/custom_mappings/vapez.json`

Each definition binds:

| Java base item | `item_model` | Bedrock identifier | icon shorthand |
| --- | --- | --- | --- |
| amethyst_shard | vapez:aetherium_crystal | vapez:aetherium_crystal | vapez.aetherium_crystal |
| netherite_ingot | vapez:aetherium_ingot | vapez:aetherium_ingot | vapez.aetherium_ingot |
| netherite_sword | vapez:sovereign_edge / aetherium_sword | matching | handheld |
| netherite tools/armor | vapez:aetherium_* | matching | equipment |
| nether_star | vapez:heart | vapez:stolen_heart | vapez.heart |
| tripwire_hook | vapez:crate_key | vapez:crate_key | vapez.crate_key |

Bedrock pack: `plugins/Geyser-Spigot/packs/vapez-aetherium.mcpack`  
Java pack: `pack/resourcepack/Vapez-Aetherium.zip`

Regenerate both:

```bash
python3 scripts/generate_assets.py
```

## Why Bedrock still needs a pack

Geyser does **not** convert Java resource packs. Without `vapez-aetherium.mcpack`, Bedrock players see the vanilla netherite/amethyst item with a custom name. With the pack + mappings, they get the teal/violet Aetherium icons.

Armor on Bedrock is best-effort: icons map; 3D humanoid overlays require extra attachables if you outgrow the generated pack. The included `item_texture.json` covers hotbar/inventory.

## Rainbow (optional)

For future items, [Rainbow](https://github.com/summerofstart/Rainbow) can emit Geyser v2 mappings from a Java pack. Merge into `custom_mappings/` rather than replacing `vapez.json`.

## Ports

| Protocol | Port | Config |
| --- | --- | --- |
| Java | 25565/tcp | server.properties |
| Bedrock | 19132/udp | plugins/Geyser-Spigot/config.yml `bedrock.port` |

Do not run another Geyser-standalone on the same UDP port.

## ViaVersion

`plugins/ViaVersion/config.yml` is a conservative 1.21.8 host config. Add ViaBackwards if you must accept 1.21.1–1.21.7 Java clients. Bedrock versions follow Geyser’s current mapping set — keep Geyser updated.
