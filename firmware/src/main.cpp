// DIY Phone firmware: touchscreen dialer, calls and SMS over an LTE module.
#include <Arduino.h>
#include <Preferences.h>
#include <TFT_eSPI.h>

#include <vector>

#include "AtParser.h"
#include "PhoneModel.h"
#include "board_pins.h"

using phone::CallState;

namespace {

TFT_eSPI tft;
Preferences prefs;
HardwareSerial modemSerial(1);
phone::PhoneModel model;
phone::LineAssembler assembler;

constexpr uint16_t COL_BG = TFT_BLACK;
constexpr uint16_t COL_FG = TFT_WHITE;
constexpr uint16_t COL_KEY = 0x2124;     // dark grey
constexpr uint16_t COL_ACCENT = 0x05FF;  // light blue
constexpr uint16_t COL_GREEN = 0x07E6;
constexpr uint16_t COL_RED = 0xE8C4;
constexpr int STATUS_H = 22;
constexpr uint32_t BACKLIGHT_TIMEOUT_MS = 30000;

enum class Screen { Home, Dialer, Call, Incoming, Inbox, Message, ComposeNumber, ComposeText };

struct Button {
  int16_t x, y, w, h;
  String label;
  uint16_t color;
  int id;
};

// Button ids
enum : int {
  B_PHONE = 1, B_MESSAGES, B_BACK, B_CALL, B_DEL, B_HANGUP, B_ANSWER, B_VOL_UP, B_VOL_DN,
  B_NEW_SMS, B_REPLY, B_NEXT, B_SEND, B_SPACE, B_SHIFT,
  B_DIGIT = 100,   // + ASCII
  B_CHAR = 300,    // + ASCII
  B_MSG = 500,     // + inbox index
};

Screen screen = Screen::Home;
std::vector<Button> buttons;
String dialBuffer;
String smsNumber;
String smsText;
int openMessage = -1;
int volume = 4;
bool shift = false;
bool needRedraw = true;
uint32_t lastModelRev = 0;
uint32_t lastTouchMs = 0;
uint32_t lastStatusDraw = 0;
bool backlightOn = true;

// ---------------------------------------------------------------- hardware --

void setBacklight(bool on) {
  backlightOn = on;
  digitalWrite(PIN_LCD_BL_N, on ? LOW : HIGH);
}

float batteryVolts() {
  return analogReadMilliVolts(PIN_VBAT_SENSE) * SENSE_DIVIDER / 1000.0f;
}

bool usbPowered() {
  return analogReadMilliVolts(PIN_VBUS_SENSE) * SENSE_DIVIDER > 4000;
}

bool waitForModem(uint32_t timeoutMs) {
  phone::LineAssembler a;
  std::string line;
  uint32_t start = millis();
  uint32_t lastAt = 0;
  while (millis() - start < timeoutMs) {
    if (millis() - lastAt > 500) {
      modemSerial.print("AT\r");
      lastAt = millis();
    }
    while (modemSerial.available()) {
      if (a.push(static_cast<char>(modemSerial.read()), line) && line == "OK") return true;
    }
    delay(10);
  }
  return false;
}

void powerOnModem() {
  if (waitForModem(1500)) {
    Serial.println("modem already on");
    return;
  }
  Serial.println("pulsing PWRKEY");
  digitalWrite(PIN_MODEM_PWRKEY, HIGH);  // NPN on -> PWRKEY low
  delay(1200);
  digitalWrite(PIN_MODEM_PWRKEY, LOW);
  if (waitForModem(20000)) {
    Serial.println("modem ready");
  } else {
    Serial.println("modem not responding - check the module, SIM and wiring");
  }
}

void pumpModem() {
  std::string line;
  while (modemSerial.available()) {
    if (assembler.push(static_cast<char>(modemSerial.read()), line)) {
      Serial.printf("<< %s\n", line.c_str());
      model.handleLine(line, millis());
    }
  }
  model.tick(millis());
  phone::OutgoingData out;
  while (model.nextOutgoing(out, millis())) {
    if (out.raw) {
      modemSerial.write(reinterpret_cast<const uint8_t*>(out.data.data()), out.data.size());
      Serial.println(">> [sms body]");
    } else {
      modemSerial.print(out.data.c_str());
      modemSerial.print('\r');
      Serial.printf(">> %s\n", out.data.c_str());
    }
  }
}

// USB console: type AT commands to talk to the modem during bring-up.
void pumpConsole() {
  static String line;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\r' || c == '\n') {
      line.trim();
      if (line.length()) model.command(line.c_str());
      line = "";
    } else {
      line += c;
    }
  }
}

// ---------------------------------------------------------------- touch --

void calibrateTouch(bool force) {
  uint16_t cal[5];
  prefs.begin("phone", false);
  if (!force && prefs.getBytesLength("touchcal") == sizeof(cal)) {
    prefs.getBytes("touchcal", cal, sizeof(cal));
    tft.setTouch(cal);
    prefs.end();
    return;
  }
  setBacklight(true);
  tft.fillScreen(COL_BG);
  tft.setTextColor(COL_FG, COL_BG);
  tft.drawCentreString("Touch the arrows", 120, 140, 2);
  tft.calibrateTouch(cal, TFT_MAGENTA, COL_BG, 15);
  prefs.putBytes("touchcal", cal, sizeof(cal));
  prefs.end();
  tft.setTouch(cal);
}

// ---------------------------------------------------------------- drawing --

void addButton(int16_t x, int16_t y, int16_t w, int16_t h, const String& label, uint16_t color,
               int id) {
  buttons.push_back({x, y, w, h, label, color, id});
}

void drawButtons() {
  for (const auto& b : buttons) {
    tft.fillRoundRect(b.x + 2, b.y + 2, b.w - 4, b.h - 4, 8, b.color);
    tft.setTextColor(COL_FG, b.color);
    tft.drawCentreString(b.label, b.x + b.w / 2, b.y + b.h / 2 - 8, 2);
  }
}

void drawStatusBar() {
  tft.fillRect(0, 0, 240, STATUS_H, 0x1082);
  tft.setTextColor(COL_FG, 0x1082);
  String op = model.registered() ? String(model.operatorName().c_str()) : String("No service");
  if (op.isEmpty()) op = "Registered";
  tft.drawString(op.substring(0, 16), 4, 3, 2);

  int bars = phone::signalBars(model.rssi());
  for (int i = 0; i < 4; ++i) {
    int h = 4 + i * 4;
    uint16_t c = i < bars ? COL_FG : 0x4208;
    tft.fillRect(140 + i * 6, 18 - h, 4, h, c);
  }

  int pct = phone::batteryPercent(batteryVolts());
  String bat = String(pct) + "%" + (usbPowered() ? "+" : "");
  tft.drawRightString(bat, 236, 3, 2);
  if (model.unreadCount()) {
    tft.fillCircle(176, 11, 6, COL_ACCENT);
    tft.setTextColor(COL_BG, COL_ACCENT);
    tft.drawCentreString(String(model.unreadCount()), 176, 7, 1);
  }
}

String formatDuration(uint32_t s) {
  char buf[12];
  snprintf(buf, sizeof(buf), "%02u:%02u", static_cast<unsigned>(s / 60), static_cast<unsigned>(s % 60));
  return buf;
}

void layoutKeypad(int top, bool withCall) {
  const char* keys = "123456789*0#";
  const int w = 80, h = 46;
  for (int i = 0; i < 12; ++i) {
    addButton((i % 3) * w, top + (i / 3) * h, w, h, String(keys[i]), COL_KEY, B_DIGIT + keys[i]);
  }
  if (withCall) {
    int y = top + 4 * h;
    addButton(0, y, 80, h, "Back", COL_KEY, B_BACK);
    addButton(80, y, 80, h, "Call", COL_GREEN, B_CALL);
    addButton(160, y, 80, h, "Del", COL_KEY, B_DEL);
  }
}

void layoutKeyboard(int top) {
  const char* rows[] = {"1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm,.?"};
  for (int r = 0; r < 4; ++r) {
    for (int c = 0; c < 10; ++c) {
      char ch = rows[r][c];
      if (shift && ch >= 'a' && ch <= 'z') ch = ch - 'a' + 'A';
      addButton(c * 24, top + r * 30, 24, 30, String(ch), COL_KEY, B_CHAR + ch);
    }
  }
  int y = top + 4 * 30;
  addButton(0, y, 48, 34, shift ? "ABC" : "abc", COL_KEY, B_SHIFT);
  addButton(48, y, 96, 34, "space", COL_KEY, B_SPACE);
  addButton(144, y, 48, 34, "Del", COL_KEY, B_DEL);
  addButton(192, y, 48, 34, "Send", COL_GREEN, B_SEND);
}

void drawWrapped(const String& text, int x, int y, int w, int maxLines, int font = 2) {
  int lineH = tft.fontHeight(font);
  String line;
  int lines = 0;
  auto flush = [&]() {
    tft.drawString(line, x, y + lines * lineH, font);
    ++lines;
    line = "";
  };
  for (size_t i = 0; i < text.length() && lines < maxLines; ++i) {
    char c = text[i];
    if (c == '\n') {
      flush();
      continue;
    }
    if (tft.textWidth(line + c, font) > w) flush();
    if (lines < maxLines) line += c;
  }
  if (lines < maxLines && line.length()) flush();
}

void render() {
  buttons.clear();
  tft.fillScreen(COL_BG);
  drawStatusBar();
  tft.setTextColor(COL_FG, COL_BG);

  switch (screen) {
    case Screen::Home: {
      tft.drawCentreString("DIY Phone", 120, 50, 4);
      tft.setTextColor(0x8410, COL_BG);
      tft.drawCentreString(model.lastEvent().c_str(), 120, 85, 2);
      addButton(20, 120, 200, 70, "Phone", COL_GREEN, B_PHONE);
      String msgs = "Messages";
      if (model.unreadCount()) msgs += " (" + String(model.unreadCount()) + ")";
      addButton(20, 200, 200, 70, msgs, COL_ACCENT, B_MESSAGES);
      if (!model.lastError().empty()) {
        tft.setTextColor(COL_RED, COL_BG);
        drawWrapped(model.lastError().c_str(), 4, 280, 232, 2, 1);
      }
      break;
    }
    case Screen::Dialer:
    case Screen::ComposeNumber: {
      tft.fillRect(0, STATUS_H, 240, 42, 0x0841);
      tft.setTextColor(COL_FG, 0x0841);
      String shown = dialBuffer.length() > 13 ? dialBuffer.substring(dialBuffer.length() - 13) : dialBuffer;
      tft.drawCentreString(shown.length() ? shown : String(screen == Screen::Dialer ? "Enter number" : "SMS to..."),
                           120, STATUS_H + 10, 4);
      layoutKeypad(STATUS_H + 44, true);
      if (screen == Screen::ComposeNumber) {
        buttons[buttons.size() - 2].label = "Next";
        buttons[buttons.size() - 2].id = B_NEXT;
        buttons[buttons.size() - 2].color = COL_ACCENT;
      }
      break;
    }
    case Screen::Call: {
      const char* state = "Calling...";
      if (model.callState() == CallState::Alerting) state = "Ringing...";
      if (model.callState() == CallState::Active) state = "In call";
      tft.drawCentreString(state, 120, 30, 2);
      tft.drawCentreString(model.remoteNumber().c_str(), 120, 50, 4);
      tft.drawCentreString(formatDuration(model.callSeconds(millis())), 120, 80, 2);
      layoutKeypad(100, false);
      addButton(0, 284, 60, 36, "Vol-", COL_KEY, B_VOL_DN);
      addButton(60, 284, 120, 36, "Hang up", COL_RED, B_HANGUP);
      addButton(180, 284, 60, 36, "Vol+", COL_KEY, B_VOL_UP);
      break;
    }
    case Screen::Incoming: {
      tft.drawCentreString("Incoming call", 120, 70, 4);
      String who = model.remoteNumber().empty() ? "Unknown" : model.remoteNumber().c_str();
      tft.drawCentreString(who, 120, 120, 4);
      addButton(10, 220, 105, 80, "Decline", COL_RED, B_HANGUP);
      addButton(125, 220, 105, 80, "Answer", COL_GREEN, B_ANSWER);
      break;
    }
    case Screen::Inbox: {
      model.markInboxRead();
      const auto& inbox = model.inbox();
      if (inbox.empty()) tft.drawCentreString("No messages", 120, 120, 2);
      for (size_t i = 0; i < inbox.size() && i < 6; ++i) {
        int y = STATUS_H + 4 + i * 42;
        tft.drawFastHLine(0, y + 41, 240, 0x2104);
        tft.setTextColor(COL_ACCENT, COL_BG);
        tft.drawString(inbox[i].from.c_str(), 6, y + 2, 2);
        tft.setTextColor(0xAD55, COL_BG);
        String body = inbox[i].body.c_str();
        body.replace("\n", " ");
        tft.drawString(body.substring(0, 34), 6, y + 22, 1);
        buttons.push_back({0, static_cast<int16_t>(y), 240, 42, "", COL_BG, B_MSG + static_cast<int>(i)});
      }
      addButton(0, 280, 120, 40, "Back", COL_KEY, B_BACK);
      addButton(120, 280, 120, 40, "New SMS", COL_ACCENT, B_NEW_SMS);
      break;
    }
    case Screen::Message: {
      const auto& m = model.inbox()[openMessage];
      tft.setTextColor(COL_ACCENT, COL_BG);
      tft.drawString(m.from.c_str(), 6, STATUS_H + 6, 4);
      tft.setTextColor(0x8410, COL_BG);
      tft.drawString(m.timestamp.c_str(), 6, STATUS_H + 34, 1);
      tft.setTextColor(COL_FG, COL_BG);
      drawWrapped(m.body.c_str(), 6, STATUS_H + 52, 228, 13);
      addButton(0, 280, 80, 40, "Back", COL_KEY, B_BACK);
      addButton(80, 280, 80, 40, "Call", COL_GREEN, B_CALL);
      addButton(160, 280, 80, 40, "Reply", COL_ACCENT, B_REPLY);
      break;
    }
    case Screen::ComposeText: {
      tft.setTextColor(COL_ACCENT, COL_BG);
      tft.drawString("To: " + smsNumber, 4, STATUS_H + 2, 2);
      tft.setTextColor(COL_FG, COL_BG);
      drawWrapped(smsText + "_", 4, STATUS_H + 20, 232, 6);
      tft.setTextColor(0x8410, COL_BG);
      tft.drawRightString(String(smsText.length()) + "/160", 236, STATUS_H + 2, 1);
      layoutKeyboard(130);
      break;
    }
  }
  drawButtons();
  lastStatusDraw = millis();
}

// ---------------------------------------------------------------- input --

void go(Screen s) {
  screen = s;
  needRedraw = true;
}

void onButton(int id) {
  if (id >= B_MSG) {
    openMessage = id - B_MSG;
    go(Screen::Message);
    return;
  }
  if (id >= B_CHAR) {
    if (smsText.length() < 160) smsText += static_cast<char>(id - B_CHAR);
    if (shift) shift = false;
    needRedraw = true;
    return;
  }
  if (id >= B_DIGIT) {
    char d = static_cast<char>(id - B_DIGIT);
    if (screen == Screen::Call) {
      model.sendDtmf(d);
    } else if (dialBuffer.length() < 20) {
      // long-press style '+' : typing '*' as the first key gives '+'
      if (d == '*' && dialBuffer.isEmpty()) d = '+';
      dialBuffer += d;
    }
    needRedraw = true;
    return;
  }
  switch (id) {
    case B_PHONE: dialBuffer = ""; go(Screen::Dialer); break;
    case B_MESSAGES: go(Screen::Inbox); break;
    case B_BACK:
      go(screen == Screen::Message ? Screen::Inbox : Screen::Home);
      break;
    case B_DEL:
      if (screen == Screen::ComposeText) {
        if (smsText.length()) smsText.remove(smsText.length() - 1);
      } else if (dialBuffer.length()) {
        dialBuffer.remove(dialBuffer.length() - 1);
      }
      needRedraw = true;
      break;
    case B_CALL: {
      String number = screen == Screen::Message ? String(model.inbox()[openMessage].from.c_str()) : dialBuffer;
      if (model.dial(number.c_str())) go(Screen::Call);
      break;
    }
    case B_HANGUP: model.hangup(); go(Screen::Home); break;
    case B_ANSWER: model.answer(); go(Screen::Call); break;
    case B_VOL_UP: model.setVolume(++volume > 5 ? (volume = 5) : volume); break;
    case B_VOL_DN: model.setVolume(--volume < 0 ? (volume = 0) : volume); break;
    case B_NEW_SMS: dialBuffer = ""; go(Screen::ComposeNumber); break;
    case B_REPLY:
      smsNumber = model.inbox()[openMessage].from.c_str();
      smsText = "";
      go(Screen::ComposeText);
      break;
    case B_NEXT:
      if (phone::PhoneModel::isDialable(dialBuffer.c_str())) {
        smsNumber = dialBuffer;
        smsText = "";
        go(Screen::ComposeText);
      }
      break;
    case B_SPACE: smsText += ' '; needRedraw = true; break;
    case B_SHIFT: shift = !shift; needRedraw = true; break;
    case B_SEND:
      if (model.sendSms(smsNumber.c_str(), smsText.c_str())) go(Screen::Home);
      break;
  }
}

void pollTouch() {
  static bool wasDown = false;
  uint16_t x, y;
  bool down = tft.getTouch(&x, &y, 400);
  if (down) {
    lastTouchMs = millis();
    if (!backlightOn) {
      setBacklight(true);
      wasDown = true;  // swallow the wake-up touch
      return;
    }
  }
  if (down && !wasDown) {
    for (const auto& b : buttons) {
      if (x >= b.x && x < b.x + b.w && y >= b.y && y < b.y + b.h) {
        onButton(b.id);
        break;
      }
    }
  }
  wasDown = down;
}

}  // namespace

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LCD_BL_N, OUTPUT);
  setBacklight(true);
  pinMode(PIN_MODEM_PWRKEY, OUTPUT);
  digitalWrite(PIN_MODEM_PWRKEY, LOW);
  pinMode(PIN_MODEM_DTR, OUTPUT);
  digitalWrite(PIN_MODEM_DTR, LOW);  // keep the module awake
  pinMode(PIN_MODEM_RI, INPUT_PULLUP);
  pinMode(PIN_MODEM_STATUS, INPUT_PULLDOWN);
  pinMode(PIN_BTN_USER, INPUT_PULLUP);
  pinMode(PIN_BTN_BOOT, INPUT_PULLUP);
  pinMode(PIN_TOUCH_IRQ, INPUT_PULLUP);
  analogSetAttenuation(ADC_11db);

  tft.init();
  tft.setRotation(0);
  tft.fillScreen(COL_BG);
  tft.setTextColor(COL_FG, COL_BG);
  tft.drawCentreString("DIY Phone", 120, 120, 4);
  tft.drawCentreString("Starting modem...", 120, 160, 2);

  // Hold USER while powering on to recalibrate the touchscreen.
  calibrateTouch(digitalRead(PIN_BTN_USER) == LOW);

  modemSerial.begin(MODEM_BAUD, SERIAL_8N1, PIN_MODEM_RX, PIN_MODEM_TX);
  powerOnModem();
  lastTouchMs = millis();
}

void loop() {
  pumpConsole();
  pumpModem();
  pollTouch();

  // Follow call state changes driven by the network.
  CallState cs = model.callState();
  if (cs == CallState::Incoming && screen != Screen::Incoming) {
    setBacklight(true);
    lastTouchMs = millis();
    go(Screen::Incoming);
  } else if (cs == CallState::Idle && (screen == Screen::Call || screen == Screen::Incoming)) {
    go(Screen::Home);
  } else if ((cs == CallState::Active || cs == CallState::Dialing || cs == CallState::Alerting) &&
             screen == Screen::Incoming) {
    go(Screen::Call);
  }

  if (model.revision() != lastModelRev) {
    lastModelRev = model.revision();
    needRedraw = true;
  }
  if (needRedraw && backlightOn) {
    needRedraw = false;
    render();
  } else if (backlightOn && millis() - lastStatusDraw > 1000) {
    // Partial refresh avoids flicker: status bar and call timer only.
    lastStatusDraw = millis();
    drawStatusBar();
    if (screen == Screen::Call) {
      tft.setTextColor(COL_FG, COL_BG);
      tft.setTextPadding(80);
      tft.drawCentreString(formatDuration(model.callSeconds(millis())), 120, 80, 2);
      tft.setTextPadding(0);
    }
  }

  bool inCall = cs != CallState::Idle;
  if (backlightOn && !inCall && millis() - lastTouchMs > BACKLIGHT_TIMEOUT_MS) setBacklight(false);
  if (!backlightOn && digitalRead(PIN_BTN_USER) == LOW) {
    setBacklight(true);
    lastTouchMs = millis();
    needRedraw = true;
  }
  delay(5);
}
