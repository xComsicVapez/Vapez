#include "PhoneModel.h"

namespace phone {

std::vector<std::string> modemInitCommands() {
  return {
      "AT",
      "ATE0",            // no command echo
      "AT+CMEE=2",       // verbose errors
      "AT+CLIP=1",       // caller id on incoming calls
      "AT+CLCC=1",       // unsolicited call status reports
      "AT+CMGF=1",       // SMS text mode
      "AT+CSCS=\"GSM\"",
      "AT+CNMI=2,1,0,0,0",  // +CMTI notification for new SMS
      "AT+CREG=1",
      "AT+CEREG=1",
      "AT+COPS=3,0",     // operator name in long alphanumeric format
  };
}

PhoneModel::PhoneModel() {
  for (const auto& c : modemInitCommands()) enqueue(c);
}

bool PhoneModel::isDialable(const std::string& number) {
  if (number.empty() || number.size() > 20) return false;
  for (size_t i = 0; i < number.size(); ++i) {
    char c = number[i];
    bool ok = (c >= '0' && c <= '9') || c == '*' || c == '#' || (c == '+' && i == 0);
    if (!ok) return false;
  }
  return true;
}

void PhoneModel::enqueue(const std::string& cmd, uint32_t timeoutMs, const std::string& sms) {
  queue_.push_back({cmd, timeoutMs, sms});
}

bool PhoneModel::dial(const std::string& number) {
  if (call_ != CallState::Idle || !isDialable(number)) return false;
  remote_ = number;
  call_ = CallState::Dialing;
  lastEvent_ = "Calling " + number;
  enqueue("ATD" + number + ";", 20000);
  changed();
  return true;
}

void PhoneModel::answer() {
  if (call_ != CallState::Incoming) return;
  enqueue("ATA", 10000);
}

void PhoneModel::hangup() {
  // Jump the queue so a hang-up is never stuck behind status polling.
  queue_.push_front({"AT+CHUP", 10000, ""});
  if (call_ != CallState::Idle) setCall(CallState::Idle, 0);
}

bool PhoneModel::sendSms(const std::string& number, const std::string& text) {
  if (!isDialable(number) || text.empty() || text.size() > 160) return false;
  enqueue("AT+CMGS=\"" + number + "\"", 60000, text);
  lastEvent_ = "Sending SMS...";
  changed();
  return true;
}

void PhoneModel::sendDtmf(char digit) {
  if (call_ != CallState::Active || !isDialable(std::string(1, digit)) || digit == '+') return;
  enqueue(std::string("AT+VTS=") + digit);
}

void PhoneModel::setVolume(int level) {
  if (level < 0) level = 0;
  if (level > 5) level = 5;
  enqueue("AT+CLVL=" + std::to_string(level));
}

void PhoneModel::markInboxRead() {
  if (unread_) {
    unread_ = 0;
    changed();
  }
}

uint32_t PhoneModel::callSeconds(uint32_t nowMs) const {
  return call_ == CallState::Active ? (nowMs - callStart_) / 1000 : 0;
}

void PhoneModel::setCall(CallState s, uint32_t nowMs) {
  if (s == call_) return;
  if (s == CallState::Active) callStart_ = nowMs;
  if (s == CallState::Idle) lastEvent_ = remote_.empty() ? "Call ended" : "Call ended: " + remote_;
  call_ = s;
  changed();
}

bool PhoneModel::nextOutgoing(OutgoingData& out, uint32_t nowMs) {
  if (promptPending_) {
    promptPending_ = false;
    out.data = current_.smsBody + '\x1A';  // Ctrl-Z terminates the message
    out.raw = true;
    return true;
  }
  if (inFlight_ || queue_.empty()) return false;
  current_ = queue_.front();
  queue_.pop_front();
  inFlight_ = true;
  waitingPrompt_ = !current_.smsBody.empty();
  sentAt_ = nowMs;
  out.data = current_.cmd;
  out.raw = false;
  return true;
}

void PhoneModel::finishCommand(bool ok, const std::string& err) {
  if (!inFlight_) return;
  const std::string cmd = current_.cmd;
  inFlight_ = false;
  waitingPrompt_ = false;
  if (!ok) {
    lastError_ = cmd + ": " + (err.empty() ? "ERROR" : err);
    if (cmd.rfind("ATD", 0) == 0) setCall(CallState::Idle, 0);
    if (cmd.rfind("AT+CMGS", 0) == 0) lastEvent_ = "SMS failed";
    changed();
  } else if (cmd.rfind("AT+CMGS", 0) == 0) {
    lastEvent_ = "SMS sent";
    changed();
  }
}

void PhoneModel::tick(uint32_t nowMs) {
  if (inFlight_ && nowMs - sentAt_ > current_.timeoutMs) {
    finishCommand(false, "timeout");
  }
  if (!polledOnce_ || nowMs - lastPoll_ >= kStatusPollMs) {
    polledOnce_ = true;
    lastPoll_ = nowMs;
    if (queue_.size() < 4) {
      enqueue("AT+CSQ");
      enqueue("AT+CREG?");
      enqueue("AT+COPS?");
    }
  }
}

void PhoneModel::handleLine(const std::string& line, uint32_t nowMs) {
  if (readingSms_) {
    Urc end = parseLine(line);
    if (end.type == UrcType::Ok || end.type == UrcType::Error) {
      readingSms_ = false;
      inbox_.insert(inbox_.begin(), smsInProgress_);
      if (inbox_.size() > kMaxInbox) inbox_.pop_back();
      ++unread_;
      lastEvent_ = "New SMS from " + smsInProgress_.from;
      changed();
      finishCommand(true, "");
      return;
    }
    if (!smsInProgress_.body.empty()) smsInProgress_.body += "\n";
    smsInProgress_.body += line;
    return;
  }

  Urc u = parseLine(line);
  switch (u.type) {
    case UrcType::Ok:
      finishCommand(true, "");
      break;
    case UrcType::Error:
      finishCommand(false, u.text);
      break;
    case UrcType::SmsPrompt:
      if (waitingPrompt_) {
        waitingPrompt_ = false;
        promptPending_ = true;
      }
      break;
    case UrcType::Ring:
      if (call_ == CallState::Idle) {
        remote_.clear();
        setCall(CallState::Incoming, nowMs);
      }
      break;
    case UrcType::CallerId:
      if (remote_ != u.number) {
        remote_ = u.number;
        changed();
      }
      if (call_ == CallState::Idle) setCall(CallState::Incoming, nowMs);
      break;
    case UrcType::CallStatus: {
      if (!u.number.empty() && remote_ != u.number) {
        remote_ = u.number;
        changed();
      }
      switch (static_cast<ClccStat>(u.value2)) {
        case ClccStat::Active: setCall(CallState::Active, nowMs); break;
        case ClccStat::Dialing: setCall(CallState::Dialing, nowMs); break;
        case ClccStat::Alerting: setCall(CallState::Alerting, nowMs); break;
        case ClccStat::Incoming:
        case ClccStat::Waiting:
          if (call_ == CallState::Idle) setCall(CallState::Incoming, nowMs);
          break;
        case ClccStat::Disconnected: setCall(CallState::Idle, nowMs); break;
        case ClccStat::Held: break;
      }
      break;
    }
    case UrcType::NoCarrier:
    case UrcType::Busy:
    case UrcType::NoAnswer:
      setCall(CallState::Idle, nowMs);
      break;
    case UrcType::NewSms:
      if (u.value >= 0) {
        enqueue("AT+CMGR=" + std::to_string(u.value));
        enqueue("AT+CMGD=" + std::to_string(u.value));
      }
      break;
    case UrcType::SmsHeader:
      readingSms_ = true;
      smsInProgress_ = SmsMessage{u.number, u.text, ""};
      break;
    case UrcType::SignalQuality:
      if (u.value != rssi_) {
        rssi_ = u.value;
        changed();
      }
      break;
    case UrcType::Registration: {
      bool reg = (u.value2 == 1 || u.value2 == 5);  // home or roaming
      if (reg != registered_) {
        registered_ = reg;
        changed();
      }
      break;
    }
    case UrcType::Operator:
      if (u.text != operator_) {
        operator_ = u.text;
        changed();
      }
      break;
    default:
      break;
  }
}

}  // namespace phone
