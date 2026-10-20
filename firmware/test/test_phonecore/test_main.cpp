#include <unity.h>

#include "AtParser.h"
#include "PhoneModel.h"

using namespace phone;

void setUp() {}
void tearDown() {}

// Sends every queued command and acknowledges it with OK.
static void drain(PhoneModel& m, uint32_t now = 0) {
  OutgoingData out;
  while (m.nextOutgoing(out, now)) m.handleLine("OK", now);
}

static void test_parse_basic_results() {
  TEST_ASSERT_EQUAL(UrcType::Ok, parseLine("OK").type);
  TEST_ASSERT_EQUAL(UrcType::Error, parseLine("ERROR").type);
  Urc e = parseLine("+CME ERROR: SIM not inserted");
  TEST_ASSERT_EQUAL(UrcType::Error, e.type);
  TEST_ASSERT_EQUAL_STRING("SIM not inserted", e.text.c_str());
  TEST_ASSERT_EQUAL(UrcType::None, parseLine("   ").type);
  TEST_ASSERT_EQUAL(UrcType::Ring, parseLine("RING\r").type);
}

static void test_parse_clip_and_clcc() {
  Urc c = parseLine("+CLIP: \"+447700900123\",145,\"\",0,\"\",0");
  TEST_ASSERT_EQUAL(UrcType::CallerId, c.type);
  TEST_ASSERT_EQUAL_STRING("+447700900123", c.number.c_str());

  Urc l = parseLine("+CLCC: 1,0,3,0,0,\"5551234\",129");
  TEST_ASSERT_EQUAL(UrcType::CallStatus, l.type);
  TEST_ASSERT_EQUAL(1, l.value);
  TEST_ASSERT_EQUAL(0, l.value3);
  TEST_ASSERT_EQUAL(static_cast<int>(ClccStat::Alerting), l.value2);
  TEST_ASSERT_EQUAL_STRING("5551234", l.number.c_str());
}

static void test_parse_sms_and_network() {
  Urc h = parseLine("+CMGR: \"REC UNREAD\",\"+15551234567\",\"\",\"24/05/01,10:30:00+08\"");
  TEST_ASSERT_EQUAL(UrcType::SmsHeader, h.type);
  TEST_ASSERT_EQUAL_STRING("+15551234567", h.number.c_str());
  TEST_ASSERT_EQUAL_STRING("24/05/01,10:30:00+08", h.text.c_str());

  Urc n = parseLine("+CMTI: \"SM\",7");
  TEST_ASSERT_EQUAL(UrcType::NewSms, n.type);
  TEST_ASSERT_EQUAL(7, n.value);

  Urc q = parseLine("+CSQ: 23,99");
  TEST_ASSERT_EQUAL(23, q.value);
  TEST_ASSERT_EQUAL(4, signalBars(q.value));
  TEST_ASSERT_EQUAL(0, signalBars(99));

  TEST_ASSERT_EQUAL(1, parseLine("+CREG: 0,1").value2);   // query response
  TEST_ASSERT_EQUAL(5, parseLine("+CEREG: 5").value2);    // unsolicited
  TEST_ASSERT_EQUAL_STRING("Vodafone UK", parseLine("+COPS: 0,0,\"Vodafone UK\",7").text.c_str());
}

static void test_line_assembler_handles_prompt_and_crlf() {
  LineAssembler a;
  std::string line;
  std::vector<std::string> lines;
  for (char c : std::string("\r\nOK\r\n\r\n> ")) {
    if (a.push(c, line)) lines.push_back(line);
  }
  TEST_ASSERT_EQUAL(2, lines.size());
  TEST_ASSERT_EQUAL_STRING("OK", lines[0].c_str());
  TEST_ASSERT_EQUAL_STRING(">", lines[1].c_str());
}

static void test_battery_percent() {
  TEST_ASSERT_EQUAL(0, batteryPercent(3.2f));
  TEST_ASSERT_EQUAL(100, batteryPercent(4.25f));
  TEST_ASSERT_EQUAL(50, batteryPercent(3.85f));
  int prev = -1;
  for (float v = 3.3f; v <= 4.2f; v += 0.01f) {
    int p = batteryPercent(v);
    TEST_ASSERT_TRUE(p >= prev);
    prev = p;
  }
}

static void test_init_commands_sent_one_at_a_time() {
  PhoneModel m;
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT", out.data.c_str());
  TEST_ASSERT_FALSE(m.nextOutgoing(out, 0));  // waits for OK
  m.handleLine("OK", 0);
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("ATE0", out.data.c_str());
}

static void test_outgoing_call_lifecycle() {
  PhoneModel m;
  drain(m);
  TEST_ASSERT_FALSE(m.dial("12ab"));
  TEST_ASSERT_TRUE(m.dial("+15551234"));
  TEST_ASSERT_EQUAL(CallState::Dialing, m.callState());
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("ATD+15551234;", out.data.c_str());
  m.handleLine("OK", 0);
  m.handleLine("+CLCC: 1,0,3,0,0,\"+15551234\",145", 100);
  TEST_ASSERT_EQUAL(CallState::Alerting, m.callState());
  m.handleLine("+CLCC: 1,0,0,0,0,\"+15551234\",145", 1000);
  TEST_ASSERT_EQUAL(CallState::Active, m.callState());
  TEST_ASSERT_EQUAL(61, m.callSeconds(62000));
  m.handleLine("NO CARRIER", 70000);
  TEST_ASSERT_EQUAL(CallState::Idle, m.callState());
}

static void test_failed_dial_returns_to_idle() {
  PhoneModel m;
  drain(m);
  m.dial("911999");
  OutgoingData out;
  m.nextOutgoing(out, 0);
  m.handleLine("+CME ERROR: no network service", 10);
  TEST_ASSERT_EQUAL(CallState::Idle, m.callState());
  TEST_ASSERT_TRUE(m.lastError().find("no network service") != std::string::npos);
}

static void test_incoming_call_answer_and_hangup_priority() {
  PhoneModel m;
  drain(m);
  m.tick(0);  // queues status polling commands
  m.handleLine("RING", 0);
  m.handleLine("+CLIP: \"07700900123\",129", 0);
  TEST_ASSERT_EQUAL(CallState::Incoming, m.callState());
  TEST_ASSERT_EQUAL_STRING("07700900123", m.remoteNumber().c_str());
  m.hangup();
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+CHUP", out.data.c_str());
  TEST_ASSERT_EQUAL(CallState::Idle, m.callState());
}

static void test_receive_sms() {
  PhoneModel m;
  drain(m);
  m.handleLine("+CMTI: \"SM\",3", 0);
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+CMGR=3", out.data.c_str());
  m.handleLine("+CMGR: \"REC UNREAD\",\"+4412345\",\"\",\"24/05/01,10:30:00+04\"", 0);
  m.handleLine("Hello from the", 0);
  m.handleLine("DIY phone!", 0);
  m.handleLine("OK", 0);
  TEST_ASSERT_EQUAL(1, m.inbox().size());
  TEST_ASSERT_EQUAL(1, m.unreadCount());
  TEST_ASSERT_EQUAL_STRING("+4412345", m.inbox()[0].from.c_str());
  TEST_ASSERT_EQUAL_STRING("Hello from the\nDIY phone!", m.inbox()[0].body.c_str());
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+CMGD=3", out.data.c_str());
  m.markInboxRead();
  TEST_ASSERT_EQUAL(0, m.unreadCount());
}

static void test_send_sms_waits_for_prompt() {
  PhoneModel m;
  drain(m);
  TEST_ASSERT_TRUE(m.sendSms("+15550000", "hi"));
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+CMGS=\"+15550000\"", out.data.c_str());
  TEST_ASSERT_FALSE(m.nextOutgoing(out, 0));
  m.handleLine(">", 0);
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_TRUE(out.raw);
  TEST_ASSERT_EQUAL_STRING("hi\x1A", out.data.c_str());
  m.handleLine("+CMGS: 12", 0);
  m.handleLine("OK", 0);
  TEST_ASSERT_EQUAL_STRING("SMS sent", m.lastEvent().c_str());
  TEST_ASSERT_FALSE(m.busy());
}

static void test_command_timeout_unblocks_queue() {
  PhoneModel m;
  OutgoingData out;
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_FALSE(m.nextOutgoing(out, 4000));
  m.tick(6000);
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 6000));
  TEST_ASSERT_EQUAL_STRING("ATE0", out.data.c_str());
}

static void test_network_status_updates_revision() {
  PhoneModel m;
  uint32_t r = m.revision();
  m.handleLine("+CSQ: 18,99", 0);
  m.handleLine("+CREG: 0,5", 0);
  m.handleLine("+COPS: 0,0,\"T-Mobile\",7", 0);
  TEST_ASSERT_EQUAL(18, m.rssi());
  TEST_ASSERT_TRUE(m.registered());
  TEST_ASSERT_EQUAL_STRING("T-Mobile", m.operatorName().c_str());
  TEST_ASSERT_TRUE(m.revision() > r);
}

static void test_dtmf_only_during_active_call_and_volume_clamped() {
  PhoneModel m;
  drain(m);
  OutgoingData out;
  m.sendDtmf('5');
  TEST_ASSERT_FALSE(m.nextOutgoing(out, 0));  // no call in progress
  m.dial("123");
  m.nextOutgoing(out, 0);
  m.handleLine("OK", 0);
  m.handleLine("+CLCC: 1,0,0,0,0,\"123\",129", 0);
  m.sendDtmf('#');
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+VTS=#", out.data.c_str());
  m.handleLine("OK", 0);
  m.setVolume(9);
  TEST_ASSERT_TRUE(m.nextOutgoing(out, 0));
  TEST_ASSERT_EQUAL_STRING("AT+CLVL=5", out.data.c_str());
}

int main() {
  UNITY_BEGIN();
  RUN_TEST(test_parse_basic_results);
  RUN_TEST(test_parse_clip_and_clcc);
  RUN_TEST(test_parse_sms_and_network);
  RUN_TEST(test_line_assembler_handles_prompt_and_crlf);
  RUN_TEST(test_battery_percent);
  RUN_TEST(test_init_commands_sent_one_at_a_time);
  RUN_TEST(test_outgoing_call_lifecycle);
  RUN_TEST(test_failed_dial_returns_to_idle);
  RUN_TEST(test_incoming_call_answer_and_hangup_priority);
  RUN_TEST(test_receive_sms);
  RUN_TEST(test_send_sms_waits_for_prompt);
  RUN_TEST(test_command_timeout_unblocks_queue);
  RUN_TEST(test_network_status_updates_revision);
  RUN_TEST(test_dtmf_only_during_active_call_and_volume_clamped);
  return UNITY_END();
}
