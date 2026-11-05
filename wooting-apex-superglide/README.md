# Wooting 60HE — Apex Superglide DKS Profile (240 FPS)

One-key **superglide** on **`C`** for Apex Legends.

| Setting | Value |
|---------|-------|
| Jump (in-game + DKS output) | `Space` |
| Crouch (in-game + DKS output) | `Left Ctrl` |
| DKS key | `C` |
| Target FPS | **240** |
| Ideal Jump → Crouch gap | **1 frame ≈ 4.17 ms** |
| Trainer | [apexmovement.tech/superglidetrainer](https://apexmovement.tech/superglidetrainer/) |

## Quick start

1. Follow **[`WOOTILITY_SETUP.md`](./WOOTILITY_SETUP.md)** in Wootility (v5).
2. Machine-readable defaults: **[`profile.json`](./profile.json)**.
3. Optional local timing helper: open **[`timing-calculator.html`](./timing-calculator.html)** in a browser.
4. Validate releases/presses on the [Superglide Trainer](https://apexmovement.tech/superglidetrainer/) at **240 FPS**.

## Recommended DKS (release method)

Hold `C` while mantling, **release** at the end of the mantle:

- **Space** @ **2.7 mm** on Key Release (fires first)
- **Left Ctrl** @ **2.5 mm** on Key Release (fires second)

This is the approach Wooting demonstrates for Apex DKS superglides. Fine-tune the **0.2 mm** gap on the trainer until you sit near 1.0 frame.

## Alternate DKS (press method)

Smash `C` at the apex:

- **Space** @ **0.3 mm** on Key Press
- **Left Ctrl** @ **0.55 mm** on Key Press

## Important

- Keep Apex binds as Space / Left Ctrl. `C` only exists as the DKS trigger on the keyboard.
- Turn **Rapid Trigger off on `C`**.
- Browser trainers have ~1 kHz poll uncertainty — use them to get close, then confirm in firing range.
- Apex’s engine still randomizes same-frame packaging; ~99% is the practical ceiling, not 100%.
