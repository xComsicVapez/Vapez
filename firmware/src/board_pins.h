#pragma once
// GPIO map of the DIY phone mainboard (see hardware/phone_design.py).
// Display SPI pins are configured for TFT_eSPI in platformio.ini.

// Cellular module socket J4
constexpr int PIN_MODEM_TX = 17;      // ESP32 TX -> module RXD
constexpr int PIN_MODEM_RX = 18;      // ESP32 RX <- module TXD
constexpr int PIN_MODEM_PWRKEY = 16;  // drives an NPN that pulls PWRKEY low
constexpr int PIN_MODEM_RI = 15;
constexpr int PIN_MODEM_DTR = 7;
constexpr int PIN_MODEM_STATUS = 5;
constexpr uint32_t MODEM_BAUD = 115200;

// Display backlight: P-MOSFET high side switch, LOW = on
constexpr int PIN_LCD_BL_N = 21;
constexpr int PIN_TOUCH_IRQ = 48;

// Buttons (active low, internal pull-ups)
constexpr int PIN_BTN_BOOT = 0;
constexpr int PIN_BTN_USER = 1;

// 100k/100k dividers
constexpr int PIN_VBAT_SENSE = 4;
constexpr int PIN_VBUS_SENSE = 6;
constexpr float SENSE_DIVIDER = 2.0f;
