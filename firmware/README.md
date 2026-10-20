# DIY Phone firmware

Arduino (PlatformIO) firmware for the ESP32-S3 on the DIY phone mainboard.

## Features

- Touchscreen home screen, dialer and in-call screen (call timer, DTMF keypad, volume)
- Incoming call screen with caller ID (answer / decline)
- SMS inbox (received messages are read from the SIM and then deleted
  from the SIM), message view, reply, and compose with an on-screen keyboard
- Status bar: operator name, signal bars, battery %, charging indicator,
  unread SMS badge
- Backlight turns off after 30 s idle; touch or the USER button wakes it
- USB serial console that forwards typed AT commands to the modem, for bring-up

The phone logic (AT parsing, call state machine, SMS handling) is in
`lib/PhoneCore`. It has no hardware dependencies and is unit tested on the PC.

## Build and flash

```bash
pip install platformio
cd firmware
pio run -e phone                 # build
pio run -e phone -t upload       # flash over USB-C
pio device monitor -e phone      # console: modem traffic is logged as << / >>
pio test -e native               # PhoneCore unit tests on your computer
```

If upload fails on a brand-new board: hold **BOOT**, tap **RESET**,
release **BOOT**, then upload again.

## First boot

1. On first start (or when **USER** is held at power-on) the touchscreen
   calibration runs: tap the arrows in each corner. The result is stored
   in flash.
2. The firmware pings the modem with `AT`. If it gets no reply, it pulses
   PWRKEY and waits up to 20 s for the module to boot.
3. It then configures caller ID, call status reports, SMS text mode and
   network registration reporting, and polls signal/operator every 15 s.

### Useful console commands

Type these in the serial monitor:

| Command | Purpose |
|---|---|
| `AT+CPIN?` | SIM status (`READY` expected) |
| `AT+CSQ` | signal quality |
| `AT+COPS?` | registered operator |
| `AT+CEREG?` | LTE registration (`,1` home or `,5` roaming) |
| `AT+CNMP=38` | SIMCom: LTE only |
| `AT+CVOLTE?` / `AT+CEVOLTE?` | SIMCom: check VoLTE |

Module vendors differ slightly. If a command returns `ERROR`, check the AT
command manual for your module.

## Changing pins

GPIOs are in `src/board_pins.h`; display/touch pins are TFT_eSPI build flags
in `platformio.ini`. They must match `hardware/phone_design.py`.
