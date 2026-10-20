#include "AtParser.h"

#include <cstdlib>
#include <vector>

namespace phone {
namespace {

bool startsWith(const std::string& s, const char* prefix) {
  return s.rfind(prefix, 0) == 0;
}

std::string trim(const std::string& s) {
  size_t b = s.find_first_not_of(" \t\r\n");
  if (b == std::string::npos) return "";
  size_t e = s.find_last_not_of(" \t\r\n");
  return s.substr(b, e - b + 1);
}

// Splits comma separated AT parameters, honouring double quotes, and strips
// the quotes from each field.
std::vector<std::string> splitParams(const std::string& s) {
  std::vector<std::string> out;
  std::string cur;
  bool quoted = false;
  for (char c : s) {
    if (c == '"') {
      quoted = !quoted;
    } else if (c == ',' && !quoted) {
      out.push_back(trim(cur));
      cur.clear();
    } else {
      cur += c;
    }
  }
  out.push_back(trim(cur));
  return out;
}

int toInt(const std::string& s, int fallback = -1) {
  if (s.empty()) return fallback;
  char* end = nullptr;
  long v = std::strtol(s.c_str(), &end, 10);
  return (end && *end == '\0') ? static_cast<int>(v) : fallback;
}

std::string after(const std::string& line, const char* prefix) {
  return trim(line.substr(std::string(prefix).size()));
}

}  // namespace

Urc parseLine(const std::string& raw) {
  Urc u;
  const std::string line = trim(raw);
  if (line.empty()) return u;

  if (line == "OK") {
    u.type = UrcType::Ok;
  } else if (line == "ERROR") {
    u.type = UrcType::Error;
  } else if (startsWith(line, "+CME ERROR:") || startsWith(line, "+CMS ERROR:")) {
    u.type = UrcType::Error;
    u.text = trim(line.substr(line.find(':') + 1));
  } else if (line == ">") {
    u.type = UrcType::SmsPrompt;
  } else if (line == "RING") {
    u.type = UrcType::Ring;
  } else if (line == "NO CARRIER") {
    u.type = UrcType::NoCarrier;
  } else if (line == "BUSY") {
    u.type = UrcType::Busy;
  } else if (line == "NO ANSWER") {
    u.type = UrcType::NoAnswer;
  } else if (startsWith(line, "+CLIP:")) {
    auto p = splitParams(after(line, "+CLIP:"));
    u.type = UrcType::CallerId;
    u.number = p[0];
  } else if (startsWith(line, "+CLCC:")) {
    auto p = splitParams(after(line, "+CLCC:"));
    if (p.size() < 5) {
      u.type = UrcType::Other;
      u.text = line;
      return u;
    }
    u.type = UrcType::CallStatus;
    u.value = toInt(p[0]);
    u.value3 = toInt(p[1]);
    u.value2 = toInt(p[2]);
    if (p.size() > 5) u.number = p[5];
  } else if (startsWith(line, "+CMTI:")) {
    auto p = splitParams(after(line, "+CMTI:"));
    u.type = UrcType::NewSms;
    u.value = p.size() > 1 ? toInt(p[1]) : -1;
  } else if (startsWith(line, "+CMGR:")) {
    // +CMGR: "REC UNREAD","+15551234567","","24/05/01,10:30:00+08"
    auto p = splitParams(after(line, "+CMGR:"));
    u.type = UrcType::SmsHeader;
    if (p.size() > 1) u.number = p[1];
    // The timestamp itself contains a comma, so re-join the trailing fields.
    if (p.size() > 3) {
      u.text = p[3];
      for (size_t i = 4; i < p.size(); ++i) u.text += "," + p[i];
    }
  } else if (startsWith(line, "+CMGS:")) {
    u.type = UrcType::SmsSent;
    u.value = toInt(after(line, "+CMGS:"));
  } else if (startsWith(line, "+CSQ:")) {
    auto p = splitParams(after(line, "+CSQ:"));
    u.type = UrcType::SignalQuality;
    u.value = toInt(p[0]);
    u.value2 = p.size() > 1 ? toInt(p[1]) : -1;
  } else if (startsWith(line, "+CREG:") || startsWith(line, "+CEREG:")) {
    // Unsolicited form is "+CREG: <stat>", query response "+CREG: <n>,<stat>"
    auto p = splitParams(line.substr(line.find(':') + 1));
    u.type = UrcType::Registration;
    u.value2 = toInt(p.size() >= 2 ? p[1] : p[0]);
    u.value = u.value2;
  } else if (startsWith(line, "+COPS:")) {
    auto p = splitParams(after(line, "+COPS:"));
    u.type = UrcType::Operator;
    if (p.size() > 2) u.text = p[2];
  } else {
    u.type = UrcType::Other;
    u.text = line;
  }
  return u;
}

bool LineAssembler::push(char c, std::string& out) {
  if (c == '\r' || c == '\n') {
    if (buf_.empty()) return false;
    out.swap(buf_);
    buf_.clear();
    return true;
  }
  buf_ += c;
  if (buf_ == "> ") {
    out = ">";
    buf_.clear();
    return true;
  }
  if (buf_.size() > 512) buf_.clear();  // runaway line, drop it
  return false;
}

int signalBars(int rssi) {
  if (rssi < 0 || rssi == 99) return 0;
  if (rssi >= 20) return 4;
  if (rssi >= 15) return 3;
  if (rssi >= 10) return 2;
  if (rssi >= 2) return 1;
  return 0;
}

int batteryPercent(float v) {
  static const float kVolts[] = {3.30f, 3.50f, 3.60f, 3.70f, 3.75f, 3.80f,
                                 3.85f, 3.90f, 4.00f, 4.10f, 4.20f};
  static const int kPct[] = {0, 5, 10, 20, 30, 40, 50, 60, 75, 90, 100};
  const int n = sizeof(kPct) / sizeof(kPct[0]);
  if (v <= kVolts[0]) return 0;
  if (v >= kVolts[n - 1]) return 100;
  for (int i = 1; i < n; ++i) {
    if (v < kVolts[i]) {
      float t = (v - kVolts[i - 1]) / (kVolts[i] - kVolts[i - 1]);
      return kPct[i - 1] + static_cast<int>(t * (kPct[i] - kPct[i - 1]) + 0.5f);
    }
  }
  return 100;
}

}  // namespace phone
