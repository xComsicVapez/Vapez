# Wootility setup — Apex 240 FPS DKS on `C`

Your binds: **Jump = Space**, **Crouch = Left Ctrl**, **DKS key = C**, **FPS = 240**.

At 240 FPS the trainer wants **Jump → Crouch exactly 1 frame later** ≈ **4.17 ms**.

Trainer: https://apexmovement.tech/superglidetrainer/

---

## 1. Apex in-game

| Action | Bind |
|--------|------|
| Jump | `Space` |
| Crouch / crouch-hold | `Left Ctrl` |

Leave `C` unbound for crouch. The keyboard will send Space then Ctrl when you use the DKS key.

Enable **Mantle Boost indicator** in Gameplay settings so you can see the last ~0.15 s window.

---

## 2. Wootility — Performance

1. Open [Wootility](https://wootility.io) (v5 recommended) with your **60HE** connected.
2. Create / select an **Apex** profile.
3. **Performance** tab:
   - **Tachyon Mode**: On
   - **Polling**: highest available (8 kHz on 60HE+)
   - **Rapid Trigger**: On for `W A S D` only (Continuous RT, ~0.2 mm)
   - **Do not** enable Rapid Trigger on `C`
   - Global actuation ~`0.4 mm` (WASD can be `0.2 mm`)

---

## 3. Wootility — DKS on `C` (recommended: release method)

This matches Wooting’s own Apex DKS example: hold during the climb, **release** to fire Jump then Crouch.

1. Open **Advanced Keys** → **Dynamic Keystroke**.
2. Click **`C`**.
3. Clear any binding that would still send the letter `C`.
4. Row 1 → bind **`Space`**
5. Row 2 → bind **`Left Ctrl`**
6. Use the **Key Release** (upstroke) section:
   - **Space**: single trigger at **`2.7 mm`**
   - **Left Ctrl**: single trigger at **`2.5 mm`**
7. Leave Key Press empty for these two actions (or no overlapping holds).
8. Save the profile to the keyboard.

### In-game motion

1. Sprint into a mantle.
2. **Hold `C`** through the climb (bottom the key).
3. In the last ~0.15 s of the mantle, **release `C`**.
4. Space fires first, Left Ctrl ~1 frame later.

### Tune with the trainer (240 FPS)

1. Open the [Superglide Trainer](https://apexmovement.tech/superglidetrainer/).
2. Set **FPS = 240**.
3. Jump bind = **SPACE**, Crouch bind = **Control** (whatever the site shows for Left Ctrl).
4. Hold `C` down, then release the same way you will in Apex.
5. Read the % chance:

| Trainer feedback | What to change on `C` |
|------------------|------------------------|
| Crouch later / frames &lt; 1 | Widen gap: move Ctrl shallower (`2.5` → `2.4` / `2.35`) or release slower |
| Crouch sooner / frames between 1–2 | Narrow gap: move Ctrl closer to Space (`2.5` → `2.55`–`2.6`) or release faster |
| 0% / too late | Gap too wide or release too slow — tighten toward `2.7 / 2.6` |
| ~90%+ | Lock it and practice mantle timing in firing range |

Adjust in **0.05 mm** steps. Finger speed changes the delay, so tune to *your* release, not someone else’s numbers.

---

## 4. Alternate: press / smash method

If you prefer tapping once at the apex instead of holding:

| Binding | Section | Point |
|---------|---------|-------|
| Space (Jump) | Key Press | `0.3 mm` single |
| Left Ctrl (Crouch) | Key Press | `0.55 mm` single |

Smash `C` in the last 0.15 s of the mantle. Tune the **0.25 mm** gap the same way with the trainer.

---

## 5. Why these numbers

Trainer math (from apexmovement.tech):

```
frame_time = 1 / fps
frames     = delay_seconds / frame_time
if frames < 1:   chance = frames * 100      # crouch later
if 1 ≤ frames < 2: chance = (2 - frames) * 100  # crouch sooner
else:            chance = 0
```

At **240 FPS**, perfect is **exactly 4.1667 ms** between Jump and Crouch (1.0 frame → peak chance).
