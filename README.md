# AutoMine

Fabric **1.21.1** client mod for prison-style grinding on **servers that allow automation**.

Defaults use a **Complex-like** command profile (`/sell all`, 10s sell cooldown, `/rankupmax`, `/mine`) so allowlisted prisons with the same command names work with little config.

> **Not for Complex Gaming.** Complex bans AFK mining / macros / autoclickers. This profile only copies their *public command names* as a template. Use it on servers that explicitly allow bots/automation.

## Controls

| Key | Action |
|-----|--------|
| **H** | Toggle AutoMine |
| **J** | Toggle AutoReply |
| **K** | Circle mine ↔ crosshair |
| **L** | Force `/sell all` (respects cooldown) |

## Complex-like loop (defaults)

1. Circle-mine nearby blocks (good with high Efficiency picks)
2. Every **10s** (and when inventory is full) run **`/sell all`**
3. Shortly after sell, run **`/rankupmax`**
4. Optional: set `teleportToMineOnStart` + `mineTeleportCommand` (e.g. `mine` or `mine k`)

Copy `config-examples/complex-like.json` → `.minecraft/config/automine.json` or edit the file AutoMine creates on first launch.

| Setting | Default | Meaning |
|---------|---------|---------|
| `sellCommand` | `sell all` | Inv + backpack sell on Complex-style prisons |
| `sellCooldownTicks` | `200` | 10 seconds |
| `periodicSellTicks` | `200` | Sell while mining on that cadence (`0` = off) |
| `rankupCommand` | `rankupmax` | After each sell |
| `mineTeleportCommand` | `mine` | Used only if `teleportToMineOnStart` is true |
| `blockWhitelist` | `[]` | Empty = any non-blacklisted block |

## Other features

- Circle look-mine or crosshair mode
- Block blacklist (bedrock, chests, etc.)
- Tool durability pause
- Session limits (optional; off by default in this profile)
- Whisper AutoReply (off by default)

## Requirements

- Minecraft **1.21.1**
- Fabric Loader `0.20.1+`
- Fabric API

## Build

Needs **JDK 25+** for Fabric Loom (bytecode targets 21):

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

Jar: `build/libs/automine-1.1.0.jar`
