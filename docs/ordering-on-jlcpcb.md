# Ordering the phone mainboard from JLCPCB

Everything you upload lives in [`hardware/output/jlcpcb/`](../hardware/output/jlcpcb):

| File | What it is |
|---|---|
| `gerbers.zip` | board copper, solder mask, silkscreen, outline and drill files |
| `bom.csv` | bill of materials with LCSC part numbers, for SMT assembly |
| `cpl.csv` | component placement list (position + rotation of each part) |
| `bom-hand-solder.csv` | the two through-hole sockets you solder yourself |

Before ordering, run `python3 hardware/check_parts.py` to confirm every part
is still in stock.

## 1. Upload the PCB

1. Go to <https://jlcpcb.com> and click **Order now** (or "Instant Quote").
2. Click **Add gerber file** and upload `hardware/output/jlcpcb/gerbers.zip`.
3. JLCPCB detects the board automatically: **2 layers, 70 x 145 mm**.
4. Keep the defaults, except:
   - **PCB Qty:** 5 (the minimum)
   - **PCB Thickness:** 1.6 mm
   - **PCB Color:** any. Green is cheapest and fastest; black looks the most "phone".
   - **Surface Finish:** HASL (lead-free recommended) or ENIG
   - **Remove Order Number:** "Specify a location" is not needed; choose
     *Yes* if you don't want JLCPCB's number printed on the board (small fee).

## 2. Turn on PCB Assembly

1. Scroll down and switch on **PCB Assembly**.
2. **PCBA Type:** *Economic*. All assembled parts are on the top side.
3. **Assembly Side:** *Top Side*.
4. **PCBA Qty:** 2 is enough to start (you can assemble just 2 of the 5 boards).
5. **Tooling holes:** *Added by JLCPCB*.
6. Click **Next** until you reach the file upload page.

## 3. Upload BOM and CPL

1. **Add BOM File:** upload `bom.csv`.
2. **Add CPL File:** upload `cpl.csv`.
3. Click **Process BOM & CPL**.
4. On the parts list, every line should be matched to the LCSC number from
   the file. If a part shows as out of stock, pick an equivalent (same
   value/package) with the search box, or run `check_parts.py` and update
   `phone_design.py`.
5. Click **Next**.

## 4. Check the placement preview (important)

JLCPCB shows a rendering of every part on the board. Check:

- **Polarised parts** (LEDs D1/D2, TP4056 U2, regulator U3, transistors Q1/Q2,
  ESD U4): the pin-1 dot or marking on the part must match the dot on the
  PCB silkscreen. If one is rotated, click it and rotate it by 90° or 180°.
  The CPL already applies JLCPCB's usual corrections for SOT-23 and SOIC
  packages, but always double-check.
- **ESP32-S3 module U1**: the antenna end must point at the **left board edge**.
- **USB-C J1**: the opening faces the bottom edge.

Then click **Next**, choose a product description (e.g. *"DIY - Consumer
Electronics"*), and **Save to cart**.

## 5. What it costs (approximate)

- 5 bare PCBs: a few dollars.
- Assembly of 2 boards: setup + stencil fee, plus parts. The ESP32-S3
  module is the most expensive part, and each "extended" part type adds a
  small loading fee (`check_parts.py` lists which parts are extended).
- Shipping: depends on the method you pick.

Expect roughly USD 40-70 before shipping for 2 assembled boards, depending on
current part prices and promotions. JLCPCB shows the exact total in the cart.

## 6. Parts to buy separately

| Item | Notes |
|---|---|
| 2 x 1x14 female header, 2.54 mm, 8.5 mm tall (LCSC C2905420) | J3 (front) and J4 (back), or add them to the JLCPCB order as through-hole assembly |
| 2.8" SPI TFT ILI9341 + XPT2046 touch module, 14-pin | the display |
| LTE breakout with VoLTE (e.g. SIMCom A7670E / SIM7600) | see [hardware.md](hardware.md) |
| LTE antenna (u.FL) | usually comes with the breakout |
| 1S LiPo, 2000-3000 mAh, JST-PH 2.0 connector, with protection | check polarity! |
| 8-32 Ω earpiece speaker + electret mic, JST-SH 4-pin cable | for voice calls |
| M2.5 standoffs and screws | to stack display / board |
| Nano SIM card with VoLTE enabled | |

## 7. When the boards arrive

1. Solder J3 on the **front** and J4 on the **back**: the socket body sits on
   the side marked by the silkscreen outline.
2. **Without the battery**, plug in USB-C. The red charge LED should flash
   or turn on, and the PC should detect a "USB JTAG/serial debug unit".
3. Flash the firmware (see [firmware/README.md](../firmware/README.md)).
4. Plug in the display and check the screen comes up.
5. Check the battery polarity with a multimeter, then plug in the battery.
6. Insert the SIM into the LTE breakout, attach the antenna, plug the
   breakout into J4, and switch on.

If you need to resend files later: the order page has a *"Re-upload"*
option, and the files in this repository are always regenerated from
`hardware/phone_design.py`.
