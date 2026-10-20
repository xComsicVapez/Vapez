#pragma once
// Phone logic (call state, SMS inbox, network status) driven purely by modem
// response lines. It never touches hardware: the firmware feeds it lines from
// the UART and writes out whatever commands it asks for, which keeps it unit
// testable on a PC.

#include <cstdint>
#include <deque>
#include <string>
#include <vector>

#include "AtParser.h"

namespace phone {

enum class CallState { Idle, Dialing, Alerting, Incoming, Active };

struct SmsMessage {
  std::string from;
  std::string timestamp;
  std::string body;
};

struct OutgoingData {
  std::string data;
  bool raw = false;  // raw = write as-is (SMS body), otherwise append "\r"
};

class PhoneModel {
 public:
  static constexpr size_t kMaxInbox = 30;
  static constexpr uint32_t kStatusPollMs = 15000;

  PhoneModel();

  // --- inputs -----------------------------------------------------------
  void handleLine(const std::string& line, uint32_t nowMs);
  void tick(uint32_t nowMs);  // call regularly; handles timeouts and polling

  bool dial(const std::string& number);
  void answer();
  void hangup();
  bool sendSms(const std::string& number, const std::string& text);
  void sendDtmf(char digit);
  void setVolume(int level);  // 0..5
  // Queues an arbitrary AT command (used by the USB serial console).
  void command(const std::string& cmd) { enqueue(cmd); }

  // --- outputs ------------------------------------------------------------
  // Next chunk to write to the modem, respecting one-command-at-a-time.
  bool nextOutgoing(OutgoingData& out, uint32_t nowMs);

  // --- state ----------------------------------------------------------------
  CallState callState() const { return call_; }
  const std::string& remoteNumber() const { return remote_; }
  uint32_t callSeconds(uint32_t nowMs) const;
  int rssi() const { return rssi_; }
  bool registered() const { return registered_; }
  const std::string& operatorName() const { return operator_; }
  const std::vector<SmsMessage>& inbox() const { return inbox_; }
  int unreadCount() const { return unread_; }
  void markInboxRead();
  const std::string& lastError() const { return lastError_; }
  const std::string& lastEvent() const { return lastEvent_; }
  // Incremented whenever something user-visible changes (for UI redraws).
  uint32_t revision() const { return revision_; }
  bool busy() const { return inFlight_; }

  static bool isDialable(const std::string& number);

 private:
  struct Pending {
    std::string cmd;
    uint32_t timeoutMs;
    std::string smsBody;  // non-empty for AT+CMGS
  };

  void enqueue(const std::string& cmd, uint32_t timeoutMs = 5000, const std::string& sms = "");
  void setCall(CallState s, uint32_t nowMs);
  void finishCommand(bool ok, const std::string& err);
  void changed() { ++revision_; }

  std::deque<Pending> queue_;
  bool inFlight_ = false;
  Pending current_;
  uint32_t sentAt_ = 0;
  bool waitingPrompt_ = false;
  bool promptPending_ = false;

  CallState call_ = CallState::Idle;
  std::string remote_;
  uint32_t callStart_ = 0;

  int rssi_ = 99;
  bool registered_ = false;
  std::string operator_;
  uint32_t lastPoll_ = 0;
  bool polledOnce_ = false;

  bool readingSms_ = false;
  SmsMessage smsInProgress_;
  std::vector<SmsMessage> inbox_;
  int unread_ = 0;

  std::string lastError_;
  std::string lastEvent_;
  uint32_t revision_ = 0;
};

// Commands sent once after the modem powers up.
std::vector<std::string> modemInitCommands();

}  // namespace phone
