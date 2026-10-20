# Hardware guide

The mainboard is a 70 x 145 mm, 2-layer, 1.6 mm FR-4 PCB. Everything is
generated from [`hardware/phone_design.py`](../hardware/phone_design.py), which
is the single source of truth for parts, nets and placement.

## Why a plug-in cellular module?

Putting an LTE modem chip directly on your own PCB means controlled-impedance
RF layout, antenna matching, and regulatory (FCC/CE) and carrier
certification. That is a big leap for a first build. This board takes the
path most DIY phones use: a **ready-made, pre-certified LTE module breakout**
plugs into a socket on the back. The rest of the phone (power, charging, MCU,
display, buttons, audio routing) is on the JLCPCB-assembled mainboard.

2G/3G networks are switched off in many countries, so voice calls need an
LTE module with **VoLTE**. Before buying, check with your carrier that the
module (and its firmware) is VoLTE-certified on their network.

Modules known to expose the needed signals on breakout boards:

| Module | Network | Notes |
|---|---|---|
| SIMCom A7670E / A7670SA / A7670G | LTE Cat-1, VoLTE | small, cheap, analog audio on most breakouts |
| SIMCom SIM7600E-H / SIM7600G-H | LTE Cat-4, VoLTE | larger, higher power |
| Quectel EG915U / EC25 breakouts | LTE Cat-1/Cat-4 | check the analog audio is broken out |

The breakout must:

- accept **3.4-4.2 V** on its VBAT pin directly from the Li-ion cell
  (the board feeds it straight from the battery: no regulator in between);
- have **3.3 V tolerant UART** pins (most breakouts include level shifters;
  the bare SIMCom/Quectel modules are 1.8 V and need one);
- break out the **analog microphone and speaker** pins if you want voice;
- carry its own SIM holder and antenna connector (u.FL / IPEX).

### Cellular socket J4 (back side, 1x14, 2.54 mm)

| Pin | Signal | Direction | Notes |
|---|---|---|---|
| 1, 2 | VBAT (VSYS) | out | battery voltage after the power switch, 22 µF x2 local bulk |
| 3, 4 | GND | | |
| 5 | MODEM_RXD | ESP32 → module | ESP32 GPIO17 (UART TX) |
| 6 | MODEM_TXD | module → ESP32 | ESP32 GPIO18 (UART RX) |
| 7 | PWRKEY | open collector | pulled low by Q2 when GPIO16 is high |
| 8 | RI | module → ESP32 | GPIO15, ring indicator |
| 9 | DTR | ESP32 → module | GPIO7, held low to keep the module awake |
| 10 | STATUS | module → ESP32 | GPIO5 |
| 11, 12 | MIC+ / MIC- | | routed to J5 |
| 13, 14 | SPK+ / SPK- | | routed to J5 |

If your breakout's pin order differs, solder a short ribbon cable between
the socket and the breakout instead of plugging it in directly.

### Earpiece / microphone connector J5 (JST-SH 4-pin)

`1 MIC+`, `2 MIC-`, `3 SPK+`, `4 SPK-`. Connect an 8-32 Ω earpiece speaker
and an electret microphone. These go straight to the module's analog audio
pins, so the module does all the audio processing.

## Display

J3 on the front is a 1x14 socket for the very common **2.8" (or 3.2") SPI
TFT with ILI9341 driver and XPT2046 resistive touch** (the red boards with an
SD-card slot). The pin order matches those modules:

| Pin | Signal | ESP32-S3 |
|---|---|---|
| 1 | VCC | 3V3 |
| 2 | GND | |
| 3 | CS | GPIO10 |
| 4 | RESET | GPIO14 |
| 5 | DC | GPIO9 |
| 6 | SDI (MOSI) | GPIO11 |
| 7 | SCK | GPIO12 |
| 8 | LED | 3V3 via Q1 (GPIO21 low = backlight on) |
| 9 | SDO (MISO) | GPIO13 |
| 10 | T_CLK | GPIO12 (shared SPI) |
| 11 | T_CS | GPIO47 |
| 12 | T_DIN | GPIO11 (shared SPI) |
| 13 | T_DO | GPIO13 (shared SPI) |
| 14 | T_IRQ | GPIO48 |

Check your module's silkscreen before plugging it in. Some sellers ship
modules with a different pin order.

## Power

```
USB-C VBUS ─┬─ USBLC6 ESD
            ├─ TP4056 (1 A, R3 = 1.2 kΩ) ── BAT+ ── J2 LiPo
            └─ VBUS sense divider → GPIO6         │
                                         SW1 slide switch
                                                  │
                                                VSYS ─┬─ J4 module VBAT
                                                      ├─ VBAT sense divider → GPIO4
                                                      └─ ME6211 LDO → 3V3 (ESP32, display)
```

- Use a **single-cell 3.7 V LiPo with protection circuit** (≥ 2000 mAh
  recommended; LTE transmit bursts draw up to 2 A). JST-PH 2.0 mm connector.
- **Battery polarity is not standardised.** J2 pin 1 is `-`, pin 2 is `+`
  (Adafruit/SparkFun convention, marked on the silkscreen). Check yours with
  a multimeter before plugging it in, and swap the crimp pins if needed.
- The TP4056 charges the battery whenever USB is connected. The power switch
  only disconnects the system load, so the battery charges with the phone
  switched off too.
- Red LED = charging, green LED = charged.
- Charge current is 1 A. For batteries smaller than 1000 mAh, change R3 to
  2 kΩ (580 mA) or 3 kΩ (400 mA).

## Buttons

| Button | GPIO | Use |
|---|---|---|
| BOOT (SW2) | GPIO0 | hold while pressing RESET to enter the USB bootloader |
| RESET (SW3) | EN | reset |
| USER (SW4) | GPIO1 | wakes the screen; hold at power-on to recalibrate touch |
| POWER (SW1) | slide | disconnects the battery from the system |

## ESP32-S3 pin usage

| GPIO | Function | GPIO | Function |
|---|---|---|---|
| 0 | BOOT button | 13 | SPI MISO |
| 1 | USER button | 14 | LCD RESET |
| 4 | battery sense (ADC) | 15 | modem RI |
| 5 | modem STATUS | 16 | modem PWRKEY |
| 6 | USB sense (ADC) | 17 | modem UART TX |
| 7 | modem DTR | 18 | modem UART RX |
| 9 | LCD DC | 19/20 | USB D-/D+ |
| 10 | LCD CS | 21 | backlight (active low) |
| 11 | SPI MOSI | 47 | touch CS |
| 12 | SPI SCK | 48 | touch IRQ |

Free for expansion: GPIO2, 3, 8, 38-46 (GPIO35-37 are used by the
octal PSRAM in the N16R8 module; GPIO3/45/46 are strapping pins).

## Mechanical stack-up

```
  front  ┌──────────────────────────────┐
         │ 2.8" TFT module (in J3)      │  ~8.5 mm above the PCB
         ├──────────────────────────────┤
         │ mainboard (SMD parts, front) │
         ├──────────────────────────────┤
         │ LTE breakout (in J4) + LiPo  │  back side
  back   └──────────────────────────────┘
```

Four M2.5 mounting holes sit 3.5 mm in from each corner. Use 10-12 mm
standoffs to fix the display above the board. The ESP32-S3 antenna sits
flush with the left board edge and is kept clear of copper. Keep metal away
from it in your enclosure.

## Regenerating the board

```bash
# Ubuntu: KiCad 9 + Java 21
sudo add-apt-repository ppa:kicad/kicad-9.0-releases && sudo apt install kicad openjdk-21-jre
mkdir -p ~/tools && curl -L -o ~/tools/freerouting.jar \
  https://github.com/freerouting/freerouting/releases/download/v1.9.0/freerouting-1.9.0.jar

python3 hardware/build_board.py     # place, autoroute, DRC, export JLCPCB files
python3 hardware/check_parts.py     # live JLCPCB stock / basic-vs-extended check
```

Edit `phone_design.py` (parts, nets, positions), rerun, and re-upload the new
`hardware/output/jlcpcb/` files. You can also open
`hardware/output/diy-phone.kicad_pcb` in KiCad to hand-tune the routing.
