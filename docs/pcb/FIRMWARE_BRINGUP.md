# Excavator firmware bring-up (ProfBoots dual ESP32)

Upstream sketches (copied for reference):

- Source: https://github.com/ProfBoots/3D-Printed-RC-Excavator
- Local copies: `firmware/Mini_Excavator_ESP1.ino`, `firmware/Mini_Excavator_Esp2.ino`

## Important: nothing “homes” a gear on power-up

Reading both sketches:

| Board | On `setup()` | When motors/servos move |
|-------|--------------|-------------------------|
| **ESP1** (`Mini_Excavator_ESP1.ino`) | All motor pins **LOW**; attachment + aux servos attach at **90°**; Bluepad32 starts and waits for a gamepad | Only after a **controller** is connected and you move sticks/buttons |
| **ESP2** (`Mini_Excavator_Esp2.ino`) | All motor pins **LOW** | Only when it receives Serial commands from ESP1 (`1`–`18`) |

There is **no** routine that turns a gear to a fixed direction just because power was applied.

So if you powered the board on low voltage and expected a gear to spin by itself: that behavior is **not in this firmware**. What you saw instead (fast red blink) was the ESP **brownout reboot loop** — the chip never stayed alive long enough to run the control loop.

Diagram: `renders/firmware_no_auto_gear.png`

## Why low voltage never moved anything

1. **Fast blink** = brownout. ESP1 never stably finishes Bluetooth setup / never sends Serial to ESP2.
2. **Logic rail ≠ motor rail.** Even a living ESP only toggles driver inputs. Motors still need pack voltage on the driver **VM** / battery input. Undervoltage on the pack side = no torque / no motion.
3. **No controller = no motion.** ESP1 only drives outputs inside `processGamepad()` after Bluepad32 has a connected gamepad with data.
4. **ESP2 is a slave.** Tracks / dipper / curl / aux on ESP2 stay stopped until Serial lines arrive from ESP1.

## What *does* move what (once power + controller are good)

**ESP1 (local pins):** boom (`16`/`17`), pivot (`13`/`12`), thumb (`4`/`2`), aux1 (`25`/`26`), lights, attachment servo (`23`), aux servo (`22`).

**ESP2 (via Serial from ESP1):**

| Cmd | Action |
|-----|--------|
| 1 / 2 / 3 | Right track back / fwd / stop |
| 4 / 5 / 6 | Left track back / fwd / stop |
| 7 / 8 / 9 | Dipper up / down / stop (+ velocity) |
| 10 / 11 / 12 | Bucket/curl up / down / stop |
| 13–15 | Aux2 |
| 16–18 | Aux3 |

Attachment servo end positions (`10` or `170`) only happen on **triple-tap** D-pad left/right — not at boot.

If a motor runs the **wrong way** after controls work: swap that motor’s two wires at its terminal block (ProfBoots assembly guidance).

## Flash notes (easy to get wrong)

1. **ESP1** must be flashed with the **Bluepad32** Arduino core / board package (sketch `#include <Bluepad32.h>`). Vanilla ESP32 core will not build/run this sketch correctly.
2. **ESP2** is a normal ESP32 Arduino sketch (no Bluepad32).
3. Load `Mini_Excavator_ESP1.ino` on the Bluetooth/controller ESP; `Mini_Excavator_Esp2.ino` on the daughter/motor ESP.
4. Boards talk over hardware Serial at **115200** — the PCB must have the ESP1↔ESP2 TX/RX link populated as designed.
5. ESP1 calls `BP32.forgetBluetoothKeys()` every boot — re-pair the controller after each power cycle if pairing feels sticky.

## Power + first-motion checklist (avoid the fast blink)

1. After any mains/monitor-cord event: replace fried ESPs; replace fuse; inspect/replace buck.
2. DC pack only into board power in. Never 100–240V AC.
3. ESPs removed: set buck **OUT = 5.0V ±0.2V**. Confirm header VIN ≈ 5V, 3V3 not 5V.
4. Seat DevKits **USB toward VIN end**. Power up: red LED **solid**, not strobing.
5. USB serial on ESP1 should print firmware / BD address (not endless reboot spam).
6. Confirm motor pack / VM is present for the drivers.
7. Pair PS/Xbox-style controller; move one stick; expect that axis to move.
8. Wrong direction → swap that motor’s two leads at the block — do not “fix” it with wall voltage.
