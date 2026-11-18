# Vapez

Professor Boots Excavator 2.0 PCB notes and power verification.

## ESP32 power (5V vs 3V3)

The Excavator 2.0 Gerbers route the buck **5V output to ESP32 DevKit `VIN`**, not `3V3`.

See: [`docs/pcb/ESP32_POWER_ANALYSIS.md`](docs/pcb/ESP32_POWER_ANALYSIS.md)
