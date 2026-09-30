# World border and Far Lands

## Initial season border

Vanilla `WorldBorder.setSize` is a **diameter**. VapezCore sets it to **10000** on first load of a fresh world (the vanilla default ~60 million is treated as “unset”).

Playable area: **10,000 × 10,000** blocks, centered at 0,0.

Chunky pregen uses **radius 5000** (square) which matches that diameter. See `scripts/pregen.sh`.

## Toggle / expand

`/worldborder` is registered by VapezCore (vanilla ops still have `/minecraft:worldborder`).

| Subcommand | Who | Effect |
| --- | --- | --- |
| `status` | everyone | diameter, Far Lands flag, threshold |
| `set <diameter>` | `vapez.border` | live resize, clamped to `border.max-diameter` (29,999,984) |
| `expand <blocks>` | `vapez.border` | add to current diameter |
| `farlands` / `toggle` | `vapez.border` | enable overlay generator for **new** chunks past 12,550,821 |
| `pregen` | `vapez.border` | prints Chunky commands |

After expanding:

```
chunky world world
chunky worldborder
chunky start
```

Never pregenerate a 20,000,000-radius world. Pregen only the **newly opened ring**.

## Why 20,000,000+ is possible

Vanilla `max-world-size` / Paper allows up to **29,999,984**. Geyser Bedrock clients historically dislike extreme coordinates; test before a public Far Lands event. Java clients are fine.

## Far Lands restoration

Modern 1.18+ noise is 64-bit. The Beta 1.7 Far Lands **do not exist** unless something overflows 32-bit math again.

`FarLandsEngine` (a `BlockPopulator`) is that something. When `border.farlands-enabled` is true **and** a chunk’s block X or Z is past **12,550,821**, it applies an overlay that:

1. Multiplies coordinates by 2^24
2. Stores them in a Java `int` (intentional wrap)
3. Turns the wrap into stacked stone strips, shredded air corridors, and occasional crying obsidian

It does **not** rewrite already generated chunks. Enable the engine **before** anyone explores past the threshold.

`/worldborder set 29999984` then `/worldborder farlands` is the “open the Far Lands” sequence. Keep the 10k border for the public season until you intend that event.

## TPS

Pregen the 10k square **offline or empty**. `spark profiler` after pregen should sit near 20 TPS with view-distance 8 / simulation-distance 6. Expanding without Chunky forces live generation and **will** hitch.
