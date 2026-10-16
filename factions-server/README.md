# Vapez Factions server

A Fabric 1.21.1 factions server with 118 mods: tech, storage, furniture, food, mobs,
combat, and over a dozen structure and biome mods. The overworld is as big as
Minecraft allows:

| | Vanilla | This server |
|---|---|---|
| World border | 59,999,968 blocks wide (already the maximum) | same, enforced by `world/datapacks/vapez-world` |
| Build height | Y -64 to 320 | **Y -128 to 512** (Tectonic) |
| Continents | normal | **~2x wider**, wider oceans between them |
| Biomes | normal | **~2x larger**, plus Terralith, Biomes O' Plenty and Nature's Spirit biomes |
| Mountains | up to ~250 | **50% taller**, thicker ranges |

Factions comes from the [Factions](https://modrinth.com/mod/factions) mod: `/f create <name>`,
`/f claim`, `/f invite`, `/f declare ally|enemy`, power that drops when you die, faction
homes and safes. Its settings are in `runtime/config/factions.json`.

## Install on your Proxmox VM (Ubuntu/Debian)

```bash
sudo apt install -y openjdk-21-jre-headless python3 curl tmux git
git clone https://github.com/xComsicVapez/Vapez && cd Vapez/factions-server
./setup.sh
echo eula=true > runtime/eula.txt     # after reading https://aka.ms/MinecraftEULA
runtime/start.sh
```

`setup.sh` is safe to re-run after `git pull`. It updates the mods and never touches the
world or any config you've edited.

## RAM

`start.sh` gives the server **100 GB** by default. Because of `-XX:+AlwaysPreTouch`, the
whole 100 GB is claimed at boot, so in Proxmox give the VM **at least 104 GB, ideally
110 GB** (Hardware > Memory, with ballooning off). If the VM has less, `start.sh` refuses to start
and tells you what will fit. To use a different amount: `MEMORY=64G runtime/start.sh`.

## Day-to-day

| Do this | Command |
|---|---|
| Start (in the background, auto-restarts after a crash) | `runtime/start.sh` |
| Stop cleanly (warns players, saves) | `runtime/stop.sh` |
| Watch or type in the console | `runtime/console.sh`, then detach with **Ctrl+B, D** |
| Pre-generate land so exploring doesn't lag | `runtime/pregen.sh 10000` (radius in blocks) |

Pre-generating everything is impossible: 60 million blocks across is petabytes. Pre-generate
the area players will actually use. Rough disk use with these mods: radius 5k is about 5 GB,
10k about 20 GB, and 25k about 120 GB.

**Run `/f ...` commands in-game only.** Factions 2.8.0 crashes the server if a factions
command is typed in the server console (its bug, not ours). The auto-restart brings the
server back, but players get kicked. Admins in-game can use `/f admin ...`.

## Ports to forward on the ASUS router

| Port | Protocol | What |
|---|---|---|
| 25565 | TCP | Minecraft |
| 24454 | UDP | Simple Voice Chat (optional) |

## Players: install the client pack

Players need the same mods. Send them `dist/vapez-factions-1.0.0.mrpack`. In the
[Modrinth App](https://modrinth.com/app) or Prism Launcher, choose **Add instance > Import
> File**, pick the `.mrpack`, then join your server address. It's Minecraft 1.21.1 with
Fabric, plus Sodium, Iris shaders and Distant Horizons so the huge terrain is visible far
into the distance.

## Changing the mod list

1. Edit `modlist.txt` (Modrinth slugs). Use `slug==version` to pin a version, or add `@client`
   to keep a mod off the server.
2. Run `python3 tools/modpack.py lock`. This resolves every mod and its dependencies into
   `mods.lock.json`.
3. Run `./setup.sh` to update the server, and `python3 tools/modpack.py mrpack` to rebuild the
   client pack.
4. Test-boot on a **copy** of the world before going live.

Known incompatibilities, found by test-booting:

- **Oh The Biomes We've Gone:** world generation crashes ("Feature order cycle") together
  with Terralith.
- **C2ME 0.4.x:** needs Java 25, so it's pinned to 0.3.0.
