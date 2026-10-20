#pragma once
// Hardware-independent parsing of the AT command responses and unsolicited
// result codes (URCs) emitted by SIMCom / Quectel style LTE modules.

#include <cstdint>
#include <string>

namespace phone {

enum class UrcType {
  None,
  Ok,
  Error,         // ERROR, +CME ERROR, +CMS ERROR (text holds the detail)
  SmsPrompt,     // "> " after AT+CMGS
  Ring,
  CallerId,      // +CLIP: "number",type
  CallStatus,    // +CLCC: id,dir,stat,mode,mpty,"number",type
  NoCarrier,
  Busy,
  NoAnswer,
  NewSms,        // +CMTI: "SM",index
  SmsHeader,     // +CMGR: "REC UNREAD","number",,"timestamp"  (body follows)
  SmsSent,       // +CMGS: mr
  SignalQuality, // +CSQ: rssi,ber
  Registration,  // +CREG / +CEREG (value = stat)
  Operator,      // +COPS: mode,format,"name"
  Other,
};

// +CLCC <stat> values (3GPP TS 27.007)
enum class ClccStat { Active = 0, Held = 1, Dialing = 2, Alerting = 3, Incoming = 4,
                      Waiting = 5, Disconnected = 6 };

struct Urc {
  UrcType type = UrcType::None;
  std::string number;   // phone number for CallerId / CallStatus / SmsHeader
  std::string text;     // timestamp, operator name or error detail
  int value = -1;       // index / rssi / stat / call id
  int value2 = -1;      // ber / clcc stat / registration stat
  int value3 = -1;      // clcc direction (0 = outgoing, 1 = incoming)
};

// Parses a single line (without CR/LF).
Urc parseLine(const std::string& line);

// Splits the raw serial byte stream into lines. The SMS prompt "> " is emitted
// as its own line because modules do not terminate it with CR/LF.
class LineAssembler {
 public:
  // Returns true when a complete line is available in `out`.
  bool push(char c, std::string& out);

 private:
  std::string buf_;
};

// Maps +CSQ rssi (0..31, 99 = unknown) to 0..4 signal bars.
int signalBars(int rssi);

// Estimates the state of charge of a 1S Li-ion cell from its resting voltage.
int batteryPercent(float volts);

}  // namespace phone
