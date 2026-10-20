"""
DIY Phone mainboard - single source of truth for parts, nets and placement.

Everything JLCPCB needs (Gerbers, drill, BOM, CPL) is generated from this file
by build_board.py. Coordinates are in millimetres relative to the top-left
corner of the board outline, Y grows downward (KiCad convention).

Block diagram
-------------
  USB-C --+--> USBLC6 ESD --> ESP32-S3 native USB (flashing + serial console)
          +--> TP4056 1 A Li-ion charger --> BAT+ (JST-PH battery)
                                               |
                                    SW1 power slide switch
                                               |
                                             VSYS ---> cellular module socket (VBAT, 2 A peaks)
                                               +-----> ME6211 LDO --> 3V3 --> ESP32-S3 + display

  ESP32-S3-WROOM-1-N16R8
     SPI  ---> 2.8" ILI9341 TFT + XPT2046 touch module (14-pin socket, front)
     UART ---> LTE Cat-1 / 4G module breakout (14-pin socket, back)
     ADC  <--- battery and USB voltage dividers
"""

BOARD_W = 70.0
BOARD_H = 145.0
CORNER_R = 4.0

# Footprints that are hand-soldered after the board arrives (through-hole
# sockets). They are kept out of the JLCPCB assembly BOM/CPL so the order can
# use single-sided SMT assembly; set ASSEMBLE_THT = True to have JLCPCB fit them.
ASSEMBLE_THT = False

R0402 = "Resistor_SMD:R_0402_1005Metric"
C0402 = "Capacitor_SMD:C_0402_1005Metric"
C0603 = "Capacitor_SMD:C_0603_1608Metric"
C0805 = "Capacitor_SMD:C_0805_2012Metric"
LED0603 = "LED_SMD:LED_0603_1608Metric"
SOT23 = "Package_TO_SOT_SMD:SOT-23"
SOCKET14 = "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical"
TACT = "Button_Switch_SMD:SW_Push_1P1T_XKB_TS-1187A"
HOLE = "MountingHole:MountingHole_2.7mm_M2.5"


class Part:
    def __init__(self, ref, value, footprint, lcsc, x, y, rot=0, side="F",
                 smt=True, mpn=""):
        self.ref = ref
        self.value = value
        self.footprint = footprint
        self.lcsc = lcsc
        self.x = x
        self.y = y
        self.rot = rot
        self.side = side
        self.smt = smt
        self.mpn = mpn


PARTS = [
    # --- Brain -------------------------------------------------------------
    # Rotated so the PCB antenna points at the left board edge, outside the
    # area covered by the display, battery and cellular module.
    Part("U1", "ESP32-S3-WROOM-1-N16R8", "RF_Module:ESP32-S3-WROOM-1", "C2913202",
         12.75, 113.0, rot=90, mpn="ESP32-S3-WROOM-1-N16R8"),
    Part("C5", "100nF", C0402, "C1525", 29.0, 102.5, rot=90),
    Part("C8", "10uF", C0603, "C19702", 31.0, 102.5, rot=90),
    Part("R9", "10k", R0402, "C25744", 29.0, 124.0, rot=90),
    Part("C6", "1uF", C0402, "C52923", 31.0, 124.0, rot=90),

    # --- Buttons -----------------------------------------------------------
    Part("SW2", "BOOT", TACT, "C318884", 44.0, 100.0, mpn="TS-1187A-B-A-B"),
    Part("SW3", "RESET", TACT, "C318884", 54.0, 100.0, mpn="TS-1187A-B-A-B"),
    Part("SW4", "USER", TACT, "C318884", 64.0, 100.0, rot=0, mpn="TS-1187A-B-A-B"),

    # --- USB-C + ESD ---------------------------------------------------------
    Part("J1", "USB-C", "Connector_USB:USB_C_Receptacle_HRO_TYPE-C-31-M-12", "C165948",
         48.0, 140.5, mpn="TYPE-C-31-M-12"),
    Part("R1", "5.1k", R0402, "C25905", 42.0, 133.0, rot=90),
    Part("R2", "5.1k", R0402, "C25905", 52.5, 133.0, rot=90),
    Part("U4", "USBLC6-2SC6", "Package_TO_SOT_SMD:SOT-23-6", "C7519", 48.0, 130.0,
         rot=90, mpn="USBLC6-2SC6"),
    Part("C11", "100nF", C0402, "C1525", 44.5, 129.0, rot=90),

    # --- Charger -------------------------------------------------------------
    Part("U2", "TP4056", "Package_SO:SOIC-8-1EP_3.9x4.9mm_P1.27mm_EP2.41x3.3mm", "C16581",
         58.0, 121.5, mpn="TP4056-42-ESOP8"),
    Part("C1", "10uF", C0603, "C19702", 52.5, 121.5, rot=90),
    Part("C2", "10uF", C0603, "C19702", 63.5, 121.5, rot=90),
    Part("R3", "1.2k", R0402, "C25862", 58.0, 127.0),
    Part("D1", "CHRG red", LED0603, "C2286", 52.0, 114.0, mpn="KT-0603R"),
    Part("R4", "1k", R0402, "C11702", 52.0, 116.5),
    Part("D2", "FULL green", LED0603, "C12624", 57.0, 114.0, mpn="KT-0603G"),
    Part("R5", "1k", R0402, "C11702", 57.0, 116.5),

    # --- Battery, power switch, LDO ------------------------------------------
    Part("J2", "Battery JST-PH", "Connector_JST:JST_PH_S2B-PH-SM4-TB_1x02-1MP_P2.00mm_Horizontal",
         "C295747", 58.5, 134.5, mpn="S2B-PH-SM4-TB"),
    # Slider actuator faces the right board edge.
    Part("SW1", "POWER", "Button_Switch_SMD:SW_SPDT_Shouhan_MSK12C02", "C431540",
         67.1, 112.0, rot=270, mpn="MSK12C02"),
    Part("U3", "ME6211C33", "Package_TO_SOT_SMD:SOT-23-5", "C82942", 40.0, 118.0,
         mpn="ME6211C33M5G-N"),
    Part("C3", "10uF", C0603, "C19702", 36.5, 118.0, rot=90),
    Part("C4", "10uF", C0603, "C19702", 43.5, 118.0, rot=90),

    # --- Battery / USB sensing ----------------------------------------------
    Part("R13", "100k", R0402, "C25741", 36.0, 110.0, rot=90),
    Part("R14", "100k", R0402, "C25741", 38.0, 110.0, rot=90),
    Part("C7", "100nF", C0402, "C1525", 40.0, 110.0, rot=90),
    Part("R11", "100k", R0402, "C25741", 36.0, 126.0, rot=90),
    Part("R12", "100k", R0402, "C25741", 38.0, 126.0, rot=90),

    # --- Display -------------------------------------------------------------
    # 14-pin socket for the ubiquitous 2.8"/3.2" ILI9341 + XPT2046 SPI module.
    Part("J3", "TFT 1x14 socket", SOCKET14, "C2905420", 18.49, 6.0, rot=90, smt=False,
         mpn="KH-2.54FH-1X14P-H8.5"),
    Part("Q1", "AO3401A", SOT23, "C15127", 30.0, 16.0, mpn="AO3401A"),
    Part("R10", "10k", R0402, "C25744", 30.0, 20.0),

    # --- Cellular module (mounted on the back) ------------------------------
    Part("J4", "LTE 1x14 socket", SOCKET14, "C2905420", 64.0, 40.0, side="B", smt=False,
         mpn="KH-2.54FH-1X14P-H8.5"),
    Part("C9", "22uF", C0805, "C45783", 58.5, 42.0, rot=90),
    Part("C10", "22uF", C0805, "C45783", 58.5, 47.0, rot=90),
    Part("Q2", "MMBT3904", SOT23, "C20526", 56.0, 60.0, mpn="MMBT3904"),
    Part("R15", "1k", R0402, "C11702", 52.0, 60.0, rot=90),
    Part("R16", "10k", R0402, "C25744", 54.0, 63.0),
    Part("J5", "Earpiece/Mic JST-SH", "Connector_JST:JST_SH_SM04B-SRSS-TB_1x04-1MP_P1.00mm_Horizontal",
         "C160404", 33.0, 93.0, rot=0, mpn="SM04B-SRSS-TB"),

    # --- Mechanical ----------------------------------------------------------
    Part("H1", "M2.5", HOLE, "", 3.5, 3.5, smt=False),
    Part("H2", "M2.5", HOLE, "", BOARD_W - 3.5, 3.5, smt=False),
    Part("H3", "M2.5", HOLE, "", 3.5, BOARD_H - 3.5, smt=False),
    Part("H4", "M2.5", HOLE, "", BOARD_W - 3.5, BOARD_H - 3.5, smt=False),
]


# ESP32-S3-WROOM-1 module pad numbers (see RF_Module.kicad_sym)
ESP = {
    "GND": ["1", "40", "41"], "3V3": "2", "EN": "3",
    "IO4": "4", "IO5": "5", "IO6": "6", "IO7": "7", "IO15": "8", "IO16": "9",
    "IO17": "10", "IO18": "11", "IO8": "12", "IO19": "13", "IO20": "14",
    "IO3": "15", "IO46": "16", "IO9": "17", "IO10": "18", "IO11": "19",
    "IO12": "20", "IO13": "21", "IO14": "22", "IO21": "23", "IO47": "24",
    "IO48": "25", "IO45": "26", "IO0": "27", "IO1": "39", "IO2": "38",
}


def esp(name):
    return ("U1", ESP[name])


# Cellular socket J4 pinout (wire / choose a breakout to match - see docs)
MODEM_PINS = {
    1: "VSYS", 2: "VSYS", 3: "GND", 4: "GND",
    5: "MODEM_RXD",   # into the module  (ESP32 TX)
    6: "MODEM_TXD",   # out of the module (ESP32 RX)
    7: "MODEM_PWRKEY", 8: "MODEM_RI", 9: "MODEM_DTR", 10: "MODEM_STATUS",
    11: "MIC_P", 12: "MIC_N", 13: "SPK_P", 14: "SPK_N",
}

# Display socket J3 pinout - matches the common red 2.8" ILI9341 SPI modules
DISPLAY_PINS = {
    1: "3V3", 2: "GND", 3: "LCD_CS", 4: "LCD_RST", 5: "LCD_DC", 6: "SPI_MOSI",
    7: "SPI_SCK", 8: "LCD_LED", 9: "SPI_MISO", 10: "SPI_SCK", 11: "TOUCH_CS",
    12: "SPI_MOSI", 13: "SPI_MISO", 14: "TOUCH_IRQ",
}

USB_GND = [("J1", p) for p in ("A1", "A12", "B1", "B12", "S1")]

NETS = {
    "GND": USB_GND + [(("U1", p)) for p in ESP["GND"]] + [
        ("R1", "2"), ("R2", "2"), ("U4", "2"), ("C11", "2"),
        ("U2", "1"), ("U2", "3"), ("U2", "9"), ("R3", "2"), ("C1", "2"), ("C2", "2"),
        ("J2", "1"), ("U3", "2"), ("C3", "2"), ("C4", "2"), ("C5", "2"), ("C6", "2"),
        ("C7", "2"), ("C8", "2"), ("C9", "2"), ("C10", "2"),
        ("R12", "2"), ("R14", "2"), ("R16", "2"), ("Q2", "2"),
        ("SW2", "2"), ("SW3", "2"), ("SW4", "2"),
        ("J3", "2"), ("J4", "3"), ("J4", "4"),
    ],
    "VBUS": [("J1", p) for p in ("A4", "A9", "B4", "B9")] + [
        ("U4", "5"), ("C11", "1"), ("U2", "4"), ("U2", "8"), ("C1", "1"),
        ("R4", "1"), ("R5", "1"), ("R11", "1"),
    ],
    "USB_DP": [("J1", "A6"), ("J1", "B6"), ("U4", "1"), ("U4", "6"), esp("IO20")],
    "USB_DN": [("J1", "A7"), ("J1", "B7"), ("U4", "3"), ("U4", "4"), esp("IO19")],
    "CC1": [("J1", "A5"), ("R1", "1")],
    "CC2": [("J1", "B5"), ("R2", "1")],
    "PROG": [("U2", "2"), ("R3", "1")],
    "CHRG_N": [("U2", "7"), ("D1", "1")],
    "STDBY_N": [("U2", "6"), ("D2", "1")],
    "LED_CHRG_A": [("R4", "2"), ("D1", "2")],
    "LED_STDBY_A": [("R5", "2"), ("D2", "2")],
    "BAT+": [("U2", "5"), ("C2", "1"), ("J2", "2"), ("SW1", "2")],
    "VSYS": [("SW1", "3"), ("U3", "1"), ("U3", "3"), ("C3", "1"), ("R13", "1"),
             ("C9", "1"), ("C10", "1"), ("J4", "1"), ("J4", "2")],
    "3V3": [("U3", "5"), ("C4", "1"), ("C5", "1"), ("C8", "1"), esp("3V3"), ("R9", "1"),
            ("J3", "1"), ("Q1", "2"), ("R10", "1")],
    "EN": [esp("EN"), ("R9", "2"), ("C6", "1"), ("SW3", "1")],
    "BOOT": [esp("IO0"), ("SW2", "1")],
    "BTN_USER": [esp("IO1"), ("SW4", "1")],
    "VBAT_SENSE": [("R13", "2"), ("R14", "1"), ("C7", "1"), esp("IO4")],
    "VBUS_SENSE": [("R11", "2"), ("R12", "1"), esp("IO6")],
    # Display
    "SPI_SCK": [esp("IO12"), ("J3", "7"), ("J3", "10")],
    "SPI_MOSI": [esp("IO11"), ("J3", "6"), ("J3", "12")],
    "SPI_MISO": [esp("IO13"), ("J3", "9"), ("J3", "13")],
    "LCD_CS": [esp("IO10"), ("J3", "3")],
    "LCD_DC": [esp("IO9"), ("J3", "5")],
    "LCD_RST": [esp("IO14"), ("J3", "4")],
    "TOUCH_CS": [esp("IO47"), ("J3", "11")],
    "TOUCH_IRQ": [esp("IO48"), ("J3", "14")],
    "LCD_BL_N": [esp("IO21"), ("Q1", "1"), ("R10", "2")],
    "LCD_LED": [("Q1", "3"), ("J3", "8")],
    # Cellular
    "MODEM_RXD": [esp("IO17"), ("J4", "5")],
    "MODEM_TXD": [esp("IO18"), ("J4", "6")],
    "MODEM_PWRKEY": [("Q2", "3"), ("J4", "7")],
    "MODEM_PWR_ON": [esp("IO16"), ("R15", "1")],
    "Q2_BASE": [("R15", "2"), ("R16", "1"), ("Q2", "1")],
    "MODEM_RI": [esp("IO15"), ("J4", "8")],
    "MODEM_DTR": [esp("IO7"), ("J4", "9")],
    "MODEM_STATUS": [esp("IO5"), ("J4", "10")],
    "MIC_P": [("J4", "11"), ("J5", "1")],
    "MIC_N": [("J4", "12"), ("J5", "2")],
    "SPK_P": [("J4", "13"), ("J5", "3")],
    "SPK_N": [("J4", "14"), ("J5", "4")],
}

# Track widths (mm). VBUS is limited by the 0.25 mm gaps between USB-C pads;
# 0.4 mm on 1 oz copper is still fine for the 1 A charge current.
POWER_NETS = {"GND": 0.5, "VBUS": 0.4, "BAT+": 0.8, "VSYS": 0.8, "3V3": 0.5, "LCD_LED": 0.4}

# JLCPCB's placement machine uses a different zero-rotation for some
# packages than KiCad. These offsets are added to the KiCad rotation in the
# CPL file; always double-check the 3D placement preview when ordering.
JLC_ROTATION_OFFSETS = {
    "Package_TO_SOT_SMD:SOT-23": 180,
    "Package_TO_SOT_SMD:SOT-23-5": 180,
    "Package_TO_SOT_SMD:SOT-23-6": 180,
    "Package_SO:SOIC-8-1EP_3.9x4.9mm_P1.27mm_EP2.41x3.3mm": 270,
}
