# Excavator 2.0 PCB — ESP32 5V vs 3V3 Power Check

## Verdict

**The fabricated copper already feeds the buck converter’s 5V output into the ESP32 DevKit `VIN` pins, not the `3V3` pins.**

You do **not** need a copper respins for “5V landed on 3V3” — assuming:

1. You are using **30-pin ESP32 DevKit boards** (USB + onboard 3.3V regulator), not bare ESP32-WROOM modules.
2. Each DevKit is oriented with the **USB end toward the VIN/5V header end** (the end the thick power rail hits).

Bare WROOM modules only accept **3.3V on VDD**. Putting 5V on a bare module will destroy it.

## What your assembled board shows

The photo matches the Professor Boots Excavator-style layout:

- Two ESP32 **DevKit** modules in female headers
- One mini **5V buck** module (`BC1`)
- Motor-driver headers above the ESP32s
- Battery input through fuse / switch into the buck

Professor Boots’ own parts list calls for a **5V buck** and an **ESP32 development board**, with USB aligned to the PCB orientation mark.

## Trace analysis (from your Gerbers)

Source: `Excavator_2.0_PCB` EasyEDA Gerbers (`docs/pcb/Excavator_2.0_Gerbers/`).

| Net / pad | Role |
|-----------|------|
| Buck pad ≈ `(118.36, 73.41)` | **5V OUT** — thick rail |
| Buck pad ≈ `(123.06, 73.41)` | **Buck IN** — from fuse/switch/battery |
| Buck pad ≈ `(127.76, 73.41)` | **GND** (pour / no stout track) |
| Left ESP upper-left pad `(36.83, 44.58)` | On 5V OUT rail → **VIN** |
| Right ESP upper-left pad `(92.58, 44.45)` | On same 5V OUT rail → **VIN** |
| Left/right ESP lower-left pads `(*.83/*.58, 19.05)` | **Not** on 5V rail → **3V3** |

Standard 30-pin DevKit pinout with USB at that left end:

- Upper-left = **VIN** (accepts ~5V into the onboard AMS1117)
- Lower-left = **3V3** (regulator output — do not drive with 5V)

Annotated copper path: `renders/pcb_5v_rail_annotation.png`  
Summary diagram: `esp32_power_pin_verdict.png`

## Why modules still fry (even with correct copper)

If copper is right, these are the usual kill switches:

1. **Wrong module type** — bare WROOM / bare chip instead of DevKit.
2. **USB flipped 180°** — seats `VIN` on the unpowered end and can put board power on the wrong corner; always match USB to the power-fed end / silk mark.
3. **Buck not actually at 5.0V** — adjustable modules can leave the factory above 5V; some “5V fixed” clones are mis-set. Measure **before** plugging ESP32s.
4. **Battery / VM shorted onto logic** — if the buck is installed backwards or bridged to the motor rail, ESP `VIN` can see pack voltage.
5. **5V on GPIO** — DevKit IO is **3.3V only**; a 5V signal into a GPIO can kill the chip even when power pins are correct.

## Bring-up checklist (do this before another ESP32)

1. Install fuse + buck only (no ESP32s seated).
2. Power the board and measure buck **OUT → GND = 5.0V ±0.2V**.
3. Measure the ESP header **upper-left pad → GND ≈ 5V**.
4. Measure the ESP header **lower-left pad → GND** — should be **open / not 5V** with ESP removed.
5. Seat DevKits with **USB toward that upper-left/VIN end**.
6. Power again and confirm on the DevKit silk:
   - `VIN` ≈ 5V
   - `3V3` ≈ 3.3V
7. Only then connect motors / drivers / USB programming.

## Can we “fix” the Gerbers here?

These uploads are **fabrication Gerbers only** (no EasyEDA schematic/PCB project). Copper for VIN vs 3V3 is already correct, so there is nothing safe to patch in the Gerber set without the source project.

Useful follow-ups if you still want a respins:

- Add silk labels `VIN` / `3V3` / `5V` at both ESP headers
- Add a USB orientation square at both footprints (2.0 board)
- Keep buck OUT and motor `VM` clearly separated on silk

If you export the EasyEDA project (schematic + PCB), we can add those labels and re-export Gerbers.

## Files

- `Excavator_2.0_Gerbers/` — original fab files
- `renders/` — layer renders + assembled reference photo
- `esp32_power_pin_verdict.png` — pin orientation summary
- `renders/pcb_5v_rail_annotation.png` — 5V rail overlaid on copper
