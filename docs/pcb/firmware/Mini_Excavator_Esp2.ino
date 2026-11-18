#include <Arduino.h>
#include <ESP32Servo.h>

#define RX0 3
#define TX0 1

#define rightTrack0 12
#define rightTrack1 13
#define leftTrack0 18
#define leftTrack1 19
#define dipper0 25
#define dipper1 26
#define curl0 33
#define curl1 32
#define aux2 2
#define aux3 4
#define aux4 16
#define aux5 17

String receivedDataStr = "";
String part1 = "";
String part2 = "";

void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);

  pinMode(rightTrack0, OUTPUT);
  pinMode(rightTrack1, OUTPUT);
  pinMode(leftTrack0, OUTPUT);
  pinMode(leftTrack1, OUTPUT);
  pinMode(dipper0, OUTPUT);
  pinMode(dipper1, OUTPUT);
  pinMode(curl0, OUTPUT);
  pinMode(curl1, OUTPUT);
  pinMode(aux2, OUTPUT);
  pinMode(aux3, OUTPUT);
  pinMode(aux4, OUTPUT);
  pinMode(aux5, OUTPUT);
  digitalWrite(rightTrack0, LOW);
  digitalWrite(rightTrack1, LOW);
  digitalWrite(leftTrack0, LOW);
  digitalWrite(leftTrack1, LOW);
  digitalWrite(dipper0, LOW);
  digitalWrite(dipper1, LOW);
  digitalWrite(curl0, LOW);
  digitalWrite(curl1, LOW);
  digitalWrite(aux2, LOW);
  digitalWrite(aux3, LOW);
  digitalWrite(aux4, LOW);
  digitalWrite(aux5, LOW);
}

void loop() {
  if (Serial.available() > 0) {
    receivedDataStr = Serial.readStringUntil('\n');
    int commaIndex = receivedDataStr.indexOf(',');
    Serial.print("Received: ");

    if (commaIndex != -1) {
      // Extract the first part (before the comma)
      part1 = receivedDataStr.substring(0, commaIndex);

      // Extract the second part (after the comma)
      part2 = receivedDataStr.substring(commaIndex + 1);

      // Convert part2 to an integer
      int mtr = part1.toInt();
      int velocity = part2.toInt();
      if (mtr == 7) {
        analogWrite(dipper0, LOW);
        analogWrite(dipper1, velocity);
      } else if (mtr == 8) {
        analogWrite(dipper0, -1 * velocity);
        analogWrite(dipper1, LOW);
      }
      if (mtr == 10) {
        analogWrite(curl0, LOW);
        analogWrite(curl1, velocity);
      } else if (mtr == 11) {
        analogWrite(curl0, -1 * velocity);
        analogWrite(curl1, LOW);
      }

      if (mtr == 16) {
        analogWrite(aux4, LOW);
        analogWrite(aux5, velocity);
      } else if (mtr == 17) {
        analogWrite(aux4, -1 * velocity);
        analogWrite(aux5, LOW);
      }
    } else {
      int mtr = receivedDataStr.toInt();
      if (mtr == 1) {
        digitalWrite(rightTrack0, LOW);
        digitalWrite(rightTrack1, HIGH);
      } else if (mtr == 2) {
        digitalWrite(rightTrack0, HIGH);
        digitalWrite(rightTrack1, LOW);
      } else if (mtr == 3) {
        digitalWrite(rightTrack0, LOW);
        digitalWrite(rightTrack1, LOW);
      }
      if (mtr == 4) {
        digitalWrite(leftTrack0, LOW);
        digitalWrite(leftTrack1, HIGH);
      } else if (mtr == 5) {
        digitalWrite(leftTrack0, HIGH);
        digitalWrite(leftTrack1, LOW);
      } else if (mtr == 6) {
        digitalWrite(leftTrack0, LOW);
        digitalWrite(leftTrack1, LOW);
      }
      if (mtr == 9) {
        analogWrite(dipper0, LOW);
        analogWrite(dipper1, LOW);
      }
      if (mtr == 12) {
        analogWrite(curl0, LOW);
        analogWrite(curl1, LOW);
      }
      if (mtr == 13) {
        digitalWrite(aux2, LOW);
        digitalWrite(aux3, HIGH);
      } else if (mtr == 14) {
        digitalWrite(aux2, HIGH);
        digitalWrite(aux3, LOW);
      } else if (mtr == 15) {
        digitalWrite(aux2, LOW);
        digitalWrite(aux3, LOW);
      }
      if (mtr == 18) {
        analogWrite(aux4, LOW);
        analogWrite(aux5, LOW);
      }
    }
  }
}
