# AutoMine

Fresh **Fabric 1.21.1** client mod — not a server plugin.

- **AutoMine** — holds break on the block under your crosshair with human-ish pacing (small look drift, random micro-pauses, short delay after each block).
- **AutoReply** — when someone whispers/messages you on a multiplayer server, sends a delayed casual reply from a configurable list.

## Controls

| Key | Action |
|-----|--------|
| **H** | Toggle AutoMine |
| **J** | Toggle AutoReply |

Remap under **Options → Controls → AutoMine**.

## Config

Created on first launch at `.minecraft/config/automine.json`:

- `replyMessages` — random lines used for auto-replies
- `whispersOnly` — only reply to private messages (default `true`)
- `replyDelayTicksMin` / `Max` — think-time before replying (20 ticks ≈ 1s)
- `pauseChancePerSecond`, look jitter, after-break delays — mining “feel”

## Requirements

- Minecraft **1.21.1**
- [Fabric Loader](https://fabricmc.net/use/) `0.20.1+`
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Build

Needs **JDK 25+** for the Fabric Loom used by this project (Minecraft still compiles to Java 21 bytecode).

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

Jar: `build/libs/automine-1.0.0.jar` (ignore `-sources`).

## Install

1. Install Fabric for 1.21.1 + Fabric API.
2. Drop the jar into `.minecraft/mods`.
3. Launch, press **H** to mine, **J** for auto-reply.
