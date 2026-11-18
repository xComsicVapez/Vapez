#include <Arduino.h>
#include <ESP32Servo.h>  // by Kevin Harrington
#include <Bluepad32.h>
//25,26,32,33,21,19,22,23,2,4,17,16

ControllerPtr myControllers[BP32_MAX_GAMEPADS];

/*Serial commands for Excavator esp32 daughter board
1- Left Track Backward
2- Left Track Forward
3- Left Track Stop
4- Right Track Backward
5- Right Track Forward
6- Right Track Stop
7- Dipper Up
8- Dipper Down
9- Dipper Stop
10- Bucket/Curl Up
11- Bucket/Curl Down
12- Bucket/Curl Stop
13- Aux2 Up
14- Aux2 Down
15- Aux2 Stop
16- Aux3 Up
17- Aux3 Down
18- Aux3 Stop
19-
20-
*/
#define cabLights 32
#define auxLights 33

#define attachmentServoPin 23
#define auxServoPin 22

#define aux0 25  // Used for controlling Aux1 terminal block
#define aux1 26
#define pivot0 13  // Used for controlling pivot motion
#define pivot1 12

#define mBoom0 16  // Used for controlling the main boom
#define mBoom1 17
#define thumb0 4  // Used for controlling rear drive motor movement
#define thumb1 2

unsigned long lastInputTime = 0;
const unsigned long INPUT_TIMEOUT = 250;  // ms — adjust if needed


Servo attachmentServo;
Servo auxServo;
int attachmentValue = 90;
int auxServoValue = 90;
int servoDelay = 0;
int lightSwitchTime = 0;
int adjustedSteeringValue = 86;


bool lightsOn = false;
bool lightsOn2 = false;
bool rightTrackMoving = false;
bool leftTrackMoving = true;
bool aux1Moving = false;
bool aux2Moving = false;

unsigned long servoTimer = 0;
bool servoActive = false;
// Triple-tap tracking
int tapCount = 0;
unsigned long firstTapTime = 0;
const unsigned long tapWindow = 800;  // time window to count taps (ms)
int lastDpadValue = 0;

void onConnectedController(ControllerPtr ctl) {
  bool foundEmptySlot = false;
  for (int i = 0; i < BP32_MAX_GAMEPADS; i++) {
    if (myControllers[i] == nullptr) {
      Serial.printf("CALLBACK: Controller is connected, index=%d\n", i);
      // Additionally, you can get certain gamepad properties like:
      // Model, VID, PID, BTAddr, flags, etc.
      ControllerProperties properties = ctl->getProperties();
      Serial.printf("Controller model: %s, VID=0x%04x, PID=0x%04x\n", ctl->getModelName().c_str(), properties.vendor_id,
                    properties.product_id);
      myControllers[i] = ctl;
      foundEmptySlot = true;
      break;
    }
  }
  if (!foundEmptySlot) {
    Serial.println("CALLBACK: Controller connected, but could not found empty slot");
  }
}



void moveServo(int movement, Servo &servo, int &servoValue) {
  switch (movement) {
    case 1:
      if (servoValue >= 10 && servoValue < 170) {
        servoValue = servoValue + 5;
        servo.write(servoValue);
        delay(10);
      }
      break;
    case -1:
      if (servoValue <= 170 && servoValue > 10) {
        servoValue = servoValue - 5;
        servo.write(servoValue);
        delay(10);
      }
      break;
  }
}

void onDisconnectedController(ControllerPtr ctl) {
  bool foundController = false;

  for (int i = 0; i < BP32_MAX_GAMEPADS; i++) {
    if (myControllers[i] == ctl) {
      Serial.printf("CALLBACK: Controller disconnected from index=%d\n", i);
      myControllers[i] = nullptr;
      foundController = true;
      break;
    }
  }

  if (!foundController) {
    Serial.println("CALLBACK: Controller disconnected, but not found in myControllers");
  }
}

void processGamepad(ControllerPtr ctl) {
  // Record that we received fresh input
  lastInputTime = millis();
  //Throttle and Boom
  processBoom(ctl->axisRY());
  //Pivot
  processPivot(ctl->axisX());
  //Boom Extension
  processDipper(ctl->axisY());
  //Steering
  processCurl(ctl->axisRX());
  //Au
  processCabLights(ctl->thumbR());
  //Aux2
  processAuxLights(ctl->thumbL());

  //Track Controls
  processLeftTrackBackward(ctl->l2());
  processRightTrackBackward(ctl->r2());
  processLeftTrackForward(ctl->l1());
  processRightTrackForward(ctl->r1());

  int dpadValue = ctl->dpad();  // get current D-pad value
  int targetPosition = -1;
  if (dpadValue == 1) {
    digitalWrite(thumb0, LOW);
    digitalWrite(thumb1, HIGH);
  } else if (dpadValue == 2) {
    digitalWrite(thumb0, HIGH);
    digitalWrite(thumb1, LOW);
  } else {
    digitalWrite(thumb0, LOW);
    digitalWrite(thumb1, LOW);
  }

  // Detect edge (new press)
  if (dpadValue != lastDpadValue && dpadValue != 0) {
    // Check if tap window expired
    if (millis() - firstTapTime > tapWindow) {
      tapCount = 0;  // reset taps
      firstTapTime = millis();
    }

    tapCount++;                                  // increment tap count
    if (tapCount == 1) firstTapTime = millis();  // start timer on first tap

    // Triple-tap detected
    if (tapCount >= 3) {
      if (dpadValue == 4) targetPosition = 170;
      else if (dpadValue == 8) targetPosition = 10;

      if (targetPosition != -1) {
        attachmentServo.attach(attachmentServoPin);
        attachmentServo.write(targetPosition);
        servoTimer = millis();
        servoActive = true;
      }

      // Reset tap count after activation
      tapCount = 0;
      firstTapTime = 0;
    }
  }
  // Save last D-pad value
  lastDpadValue = dpadValue;

  processAux1Forward(ctl->x());
  processAux1Backward(ctl->b());
  if (servoDelay == 2) {
    processAux2Forward(ctl->y());
    processAux2Backward(ctl->a());
    servoDelay = 0;
  }
  servoDelay++;
}
void processAux1Forward(int xValue) {
  if (xValue == 1) {
    digitalWrite(aux0, LOW);
    digitalWrite(aux1, HIGH);
    aux1Moving = true;
  } else if (!aux1Moving) {
    digitalWrite(aux0, LOW);
    digitalWrite(aux1, LOW);
  } else {
    aux1Moving = false;
  }
}
void processAux1Backward(int bValue) {
  if (bValue == 1) {
    digitalWrite(aux0, HIGH);
    digitalWrite(aux1, LOW);
    aux1Moving = true;
  } else if (!aux1Moving) {
    digitalWrite(aux0, LOW);
    digitalWrite(aux1, LOW);
  } else {
    aux1Moving = false;
  }
}
void processAux2Forward(int yValue) {
  if (yValue == 1 && auxServoValue <= 170) {
    auxServoValue = auxServoValue + 2;
    auxServo.write(auxServoValue);
  }
}
void processAux2Backward(int aValue) {
  if (aValue == 1 && auxServoValue >= 10) {
    auxServoValue = auxServoValue - 2;
    auxServo.write(auxServoValue);
  }
}
void processBoom(int axisRYValue) {
  int adjustedThrottleValue = axisRYValue / 2;
  if (axisRYValue >= 90 || axisRYValue <= -90) {
    moveMotor(mBoom0, mBoom1, adjustedThrottleValue);
  } else {
    moveMotor(mBoom0, mBoom1, 0);
  }
}
void processPivot(int axisXValue) {
  int adjustedThrottleValue = axisXValue / 2;
  if (axisXValue >= 90 || axisXValue <= -90) {
    moveMotor(pivot0, pivot1, adjustedThrottleValue);
  } else {
    moveMotor(pivot0, pivot1, 0);
  }
}
void processDipper(int axisYValue) {
  int adjustedThrottleValue = axisYValue / 2;
  if (axisYValue > 90) {
    Serial.print("7,");
    Serial.println(adjustedThrottleValue);
    delay(10);
  } else if (axisYValue < -90) {
    Serial.print("8,");
    Serial.println(adjustedThrottleValue);
    delay(10);
  } else {
    Serial.println(9);
    delay(10);
  }
  //Serial.println(axisRYValue);
}

void processLeftTrackBackward(bool value) {
  if (value) {
    Serial.println(1);
    leftTrackMoving = true;
    delay(10);
  } else if (!leftTrackMoving) {
    Serial.println(3);
  } else {
    leftTrackMoving = false;
  }
}
void processLeftTrackForward(bool value) {
  if (value) {
    Serial.println(2);
    leftTrackMoving = true;
    delay(10);
  } else if (!leftTrackMoving) {
    Serial.println(3);
  } else {
    leftTrackMoving = false;
  }
}
void processRightTrackBackward(bool value) {
  if (value) {
    Serial.println(4);
    rightTrackMoving = true;
    delay(10);
  } else if (!rightTrackMoving) {
    Serial.println(6);
  } else {
    rightTrackMoving = false;
  }
}
void processRightTrackForward(bool value) {
  if (value) {
    Serial.println(5);
    rightTrackMoving = true;
    delay(10);
  } else if (!rightTrackMoving) {
    Serial.println(6);
  } else {
    rightTrackMoving = false;
  }
}

void processCurl(int axisRXValue) {
  int adjustedThrottleValue = axisRXValue / 2;
  if (axisRXValue > 90) {
    Serial.print("10,");
    Serial.println(adjustedThrottleValue);
    delay(10);
  } else if (axisRXValue < -90) {
    Serial.print("11,");
    Serial.println(adjustedThrottleValue);
    delay(10);
  } else {
    Serial.println(12);
    delay(10);
  }
}

void processCabLights(bool buttonValue) {
  if (buttonValue && (millis() - lightSwitchTime) > 200) {
    if (lightsOn) {
      digitalWrite(cabLights, LOW);
      lightsOn = false;
    } else {
      digitalWrite(cabLights, HIGH);
      lightsOn = true;
    }
    lightSwitchTime = millis();
  }
}
void processAuxLights(bool buttonValue) {
  if (buttonValue && (millis() - lightSwitchTime) > 200) {
    if (lightsOn2) {
      digitalWrite(auxLights, LOW);
      lightsOn2 = false;
    } else {
      digitalWrite(auxLights, HIGH);
      lightsOn2 = true;
    }

    lightSwitchTime = millis();
  }
}
void moveMotor(int motorPin0, int motorPin1, int velocity) {
  if (velocity > 15) {
    analogWrite(motorPin0, velocity);
    analogWrite(motorPin1, LOW);
  } else if (velocity < -15) {
    analogWrite(motorPin0, LOW);
    analogWrite(motorPin1, (-1 * velocity));
  } else {
    analogWrite(motorPin0, 0);
    analogWrite(motorPin1, 0);
  }
}
void processControllers() {
  for (auto myController : myControllers) {
    if (myController && myController->isConnected() && myController->hasData()) {
      if (myController->isGamepad()) {
        processGamepad(myController);
      } else {
        Serial.println("Unsupported controller");
      }
    }
  }
}

// Arduino setup function. Runs in CPU 1
void setup() {
  Serial.begin(115200);
  //   put your setup code here, to run once:
  Serial.printf("Firmware: %s\n", BP32.firmwareVersion());
  const uint8_t *addr = BP32.localBdAddress();
  Serial.printf("BD Addr: %2X:%2X:%2X:%2X:%2X:%2X\n", addr[0], addr[1], addr[2], addr[3], addr[4], addr[5]);

  // Setup the Bluepad32 callbacks
  BP32.setup(&onConnectedController, &onDisconnectedController);

  BP32.forgetBluetoothKeys();

  BP32.enableVirtualDevice(false);

  pinMode(mBoom1, OUTPUT);
  pinMode(thumb0, OUTPUT);
  pinMode(pivot0, OUTPUT);
  pinMode(pivot1, OUTPUT);
  pinMode(mBoom0, OUTPUT);
  pinMode(thumb1, OUTPUT);
  pinMode(aux0, OUTPUT);
  pinMode(aux1, OUTPUT);

  digitalWrite(mBoom0, LOW);
  digitalWrite(mBoom1, LOW);
  digitalWrite(pivot0, LOW);
  digitalWrite(pivot1, LOW);
  digitalWrite(thumb0, LOW);
  digitalWrite(thumb1, LOW);
  digitalWrite(aux0, LOW);
  digitalWrite(aux1, LOW);

  pinMode(cabLights, OUTPUT);
  pinMode(auxLights, OUTPUT);
  digitalWrite(cabLights, LOW);
  digitalWrite(auxLights, LOW);


  attachmentServo.attach(attachmentServoPin);
  attachmentServo.write(attachmentValue);
  auxServo.attach(auxServoPin);
  auxServo.write(auxServoValue);
}



// Arduino loop function. Runs in CPU 1.
void loop() {

  // This call fetches all the controllers' data.
  // Call this function in your main loop.
  bool dataUpdated = BP32.update();
  if (dataUpdated) {
    processControllers();
  }
  // The main loop must have some kind of "yield to lower priority task" event.
  // Otherwise, the watchdog will get triggered.
  // If your main loop doesn't have one, just add a simple `vTaskDelay(1)`.
  // Detailed info here:
  // https://stackoverflow.com/questions/66278271/task-watchdog-got-triggered-the-tasks-did-not-reset-the-watchdog-in-time

  //     vTaskDelay(1);
  else { vTaskDelay(1); }
  // Failsafe check: if no input for too long, stop motors
  if (millis() - lastInputTime > INPUT_TIMEOUT) {
    digitalWrite(mBoom0, LOW);
    digitalWrite(mBoom1, LOW);
    digitalWrite(pivot0, LOW);
    digitalWrite(pivot1, LOW);
    digitalWrite(thumb0, LOW);
    digitalWrite(thumb1, LOW);
    digitalWrite(aux0, LOW);
    digitalWrite(aux1, LOW);
    Serial.println(3);
    Serial.println(6);
    Serial.println(9);
    Serial.println(12);
    Serial.println(15);
    Serial.println(18);
  }
}
