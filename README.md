# AutoMine

Fresh **Fabric 1.21.1** client mod aimed at prison-style grinding (not a server plugin).

## Features

- **Circle mine** — looks around you in a ring and breaks blocks (great with Efficiency picks) instead of one column
- **Crosshair mode** — optional classic “mine what I’m looking at”
- **Block whitelist / blacklist** — only break mine blocks you care about
- **Inventory full** — pause + optional `/sell all` (and optional deposit command)
- **Tool guard** — pause before your pick breaks
- **Session limits** — auto-stop after time or block count
- **Humanized pacing** — micro pauses, look jitter, short re-aim delays
- **AutoReply** — delayed casual whisper replies; skips staff-like names

## Controls

| Key | Action |
|-----|--------|
| **H** | Toggle AutoMine |
| **J** | Toggle AutoReply |
| **K** | Toggle circle mine ↔ crosshair |

## Config

`.minecraft/config/automine.json` (created on first launch). Important knobs:

| Key | Default | Notes |
|-----|---------|--------|
| `circleMine` | `true` | Ring look-mine |
| `circleRadius` | `4.2` | Reach around you |
| `circleStepDegrees` | `12` | Sweep density |
| `blockWhitelist` | common ores/stone | Empty list = all non-blacklisted |
| `sellCommand` | `sell all` | Change to your prison’s sell cmd |
| `depositCommand` | `""` | e.g. `deposit all` if the server has it |
| `minToolDurability` | `15` | Stop before pick dies |
| `maxSessionTicks` | `12000` | ~10 minutes (`0` = off) |
| `maxBlocksPerSession` | `4000` | `0` = off |
| `replyMessages` | prison-ish lines | Random whisper replies |

## Requirements

- Minecraft **1.21.1**
- Fabric Loader `0.20.1+`
- Fabric API

## Build

Needs **JDK 25+** for Fabric Loom (bytecode still targets 21):

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

Jar: `build/libs/automine-1.0.0.jar`

## Install

1. Fabric 1.21.1 + Fabric API  
2. Drop jar into `.minecraft/mods`  
3. Join world, press **H** (and **K** if you want to switch modes)

Many prison servers disallow automation — check rules before using online.
