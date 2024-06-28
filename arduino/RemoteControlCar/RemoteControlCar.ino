#define ENA 13
#define IN1 14
#define IN2 27
#define ENB 12
#define IN3 26
#define IN4 25

#define BUZZER 19
#define LEFT_LED_SIGNAL 18
#define RIGHT_LED_SIGNAL 5
#define HEADLIGHTS 17

#define NEUTRAL 0
#define PRIMERA 100
#define SEGUNDA 150
#define TRESIERA 200
#define QUARTA 255

#define PORT 8080

#include <WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>

const char *SSID = "*****";
const char *PASSWORD = "*****";

int motorSpeedRight = 0;
int motorSpeedLeft = 0;
String currentMovement = "NONE";

// Use when moving forward left and forward right
int prevMotorSpeedRight = 0;
int prevMotorSpeedLeft = 0;

static TaskHandle_t signalLeftTurnTaskHandle = NULL;
static TaskHandle_t signalRightTurnTaskHandle = NULL;
static TaskHandle_t signalHazardTaskHandle = NULL;

WiFiServer server(PORT);

void setup() {
  Serial.begin(9600);

  // Setup the motor driver
  pinMode(ENA, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(ENB, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  // Setup headlights, signal lights and buzzer
  pinMode(BUZZER, OUTPUT);
  pinMode(LEFT_LED_SIGNAL, OUTPUT);
  pinMode(RIGHT_LED_SIGNAL, OUTPUT);
  pinMode(HEADLIGHTS, OUTPUT);

  // Setup an access point
  WiFi.softAP(SSID, PASSWORD);
  IPAddress myIP = WiFi.softAPIP();
  Serial.print("setup: Smart car IP address: ");
  Serial.println(myIP);
  server.begin();
  Serial.println("setup: Server started");
}

// HEADLIGHTS, SIGNAL LIGHTS AND BUZZER CONTROLS

void taskSignalLeftTurn() {
  while (true) {
    digitalWrite(LEFT_LED_SIGNAL, HIGH);
    vTaskDelay(600 / portTICK_PERIOD_MS);
    digitalWrite(LEFT_LED_SIGNAL, LOW);
    vTaskDelay(600 / portTICK_PERIOD_MS);
  }
}

void startTaskSignalLeftTurn() {
  xTaskCreatePinnedToCore(
    (TaskFunction_t) taskSignalLeftTurn, "TASK SIGNAL LEFT TURN", 
    1024, NULL, 1, 
    &signalLeftTurnTaskHandle, 1
  );
}

void stopTaskSignalLeftTurn() {
  if (signalLeftTurnTaskHandle != NULL) {
    vTaskDelete(signalLeftTurnTaskHandle);
    signalLeftTurnTaskHandle = NULL;
    digitalWrite(LEFT_LED_SIGNAL, LOW);
  }
}

void taskSignalRightTurn() {
  while (true) {
    digitalWrite(RIGHT_LED_SIGNAL, HIGH);
    vTaskDelay(600 / portTICK_PERIOD_MS);
    digitalWrite(RIGHT_LED_SIGNAL, LOW);
    vTaskDelay(600 / portTICK_PERIOD_MS);
  }
}

void startTaskSignalRightTurn() {
  xTaskCreatePinnedToCore(
    (TaskFunction_t) taskSignalRightTurn, "TASK SIGNAL RIGHT TURN", 
    1024, NULL, 1, 
    &signalRightTurnTaskHandle, 1
  );
}

void stopTaskSignalRightTurn() {
  if (signalRightTurnTaskHandle != NULL) {
    vTaskDelete(signalRightTurnTaskHandle);
    signalRightTurnTaskHandle = NULL;
    digitalWrite(RIGHT_LED_SIGNAL, LOW);
  }
}

void taskSignalHazard() {
  while (true) {
    digitalWrite(LEFT_LED_SIGNAL, HIGH);
    digitalWrite(RIGHT_LED_SIGNAL, HIGH);
    vTaskDelay(600 / portTICK_PERIOD_MS);
    digitalWrite(LEFT_LED_SIGNAL, LOW);
    digitalWrite(RIGHT_LED_SIGNAL, LOW);
    vTaskDelay(600 / portTICK_PERIOD_MS);
  }
}

void startTaskHazardSignal() {
  xTaskCreatePinnedToCore(
    (TaskFunction_t) taskSignalHazard, "TASK SIGNAL HAZARD", 
    1024, NULL, 1, 
    &signalHazardTaskHandle, 1
  );
}

void stopTaskHazardSignal() {
  if (signalHazardTaskHandle != NULL) {
    vTaskDelete(signalHazardTaskHandle);
    signalHazardTaskHandle = NULL;
    digitalWrite(RIGHT_LED_SIGNAL, LOW);
    digitalWrite(LEFT_LED_SIGNAL, LOW);
  }
}

// MOTOR CONTROLS

void setMotorSpeed() {
  if (currentMovement == "RIGHT_TURN") {
    // If right turn is pressed then only increase the left motor speed
    analogWrite(ENB, motorSpeedLeft);
  } else if (currentMovement == "LEFT_TURN") {
    // If left turn is pressed then only increase the right motor speed
    analogWrite(ENA, motorSpeedRight);
  } else {
    analogWrite(ENA, motorSpeedRight);
    analogWrite(ENB, motorSpeedLeft);
  }
}

void changeGear(String log, int gearNumber) {
  Serial.println("changeGear: " + log);
  motorSpeedRight = gearNumber;
  motorSpeedLeft = gearNumber;
  setMotorSpeed();
}

void forwardLeftPressed() {
  Serial.println("forwardLeft: Forward left pressed...");
  currentMovement = "FORWARD_LEFT";
  prevMotorSpeedRight = motorSpeedRight;
  prevMotorSpeedLeft = motorSpeedLeft;

  if (motorSpeedRight == PRIMERA) {
    motorSpeedLeft = SEGUNDA + 20;
  } else if (motorSpeedRight == SEGUNDA) {
    motorSpeedLeft = TRESIERA + 20;
  } else if (motorSpeedRight == TRESIERA) {
    motorSpeedRight = TRESIERA - 20;
    motorSpeedLeft = QUARTA;
  } else if (motorSpeedRight == QUARTA) {
    motorSpeedRight = TRESIERA - 30;
    motorSpeedLeft = QUARTA;
  }
  setMotorSpeed();
}

void forwardLeftReleased() {
  Serial.println("forwardLeft: Forward left released...");
  motorSpeedRight = prevMotorSpeedRight;
  motorSpeedLeft = prevMotorSpeedLeft;
  setMotorSpeed();
}

void forwardRightPressed() {
  Serial.println("forwardLeft: Forward left pressed...");
  currentMovement = "FORWARD_RIGHT";
  prevMotorSpeedRight = motorSpeedRight;
  prevMotorSpeedLeft = motorSpeedLeft;

  if (motorSpeedLeft == PRIMERA) {
    motorSpeedRight = SEGUNDA + 20;
  } else if (motorSpeedLeft == SEGUNDA) {
    motorSpeedRight = TRESIERA + 20;
  } else if (motorSpeedLeft == TRESIERA) {
    motorSpeedLeft = TRESIERA - 20;
    motorSpeedRight = QUARTA;
  } else if (motorSpeedLeft == QUARTA) {
    motorSpeedLeft = TRESIERA - 30;
    motorSpeedRight = QUARTA;
  }
  setMotorSpeed();
}

void forwardRightReleased() {
  Serial.println("forwardLeft: Forward right released...");
  motorSpeedRight = prevMotorSpeedRight;
  motorSpeedLeft = prevMotorSpeedLeft;
  setMotorSpeed();
}

void moveForward() {
  Serial.println("moveForward: Moving forward...");
  currentMovement = "ACCELERATE";
  setMotorSpeed();
  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, HIGH);
}

void moveRight() {
  Serial.println("moveRight: Moving right...");
  analogWrite(ENA, 0);
  currentMovement = "RIGHT_TURN";
  analogWrite(ENB, motorSpeedLeft);
  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, HIGH);
}

void moveLeft() {
  Serial.println("moveLeft: Moving left...");
  currentMovement = "LEFT_TURN";
  analogWrite(ENA, motorSpeedRight);
  analogWrite(ENB, 0);
  digitalWrite(IN1, LOW); digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, HIGH);
}

void stopMotor() {
  Serial.println("stopMotor: Stopping motor...");
  currentMovement = "BREAK";
  digitalWrite(IN1, LOW); digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW); digitalWrite(IN4, LOW);
}

void spinCar() {
  Serial.println("spinCar: Spinning the car...");
  currentMovement = "REVERSE";
  setMotorSpeed();
  digitalWrite(IN1, HIGH); digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH); digitalWrite(IN4, HIGH);
}

// COMMAND INTERPRETER AND EXECUTOR

void readAndExecuteCommand(WiFiClient client) {

  while (client.connected()) {
    if (!client.available()) {
      continue;
    }

    // Read until newline character
    String command = client.readStringUntil('\n');
    Serial.print("readAndExecuteCommand: Received command -> ");
    Serial.println(command);

    if (command == "ACCELERATE") {
      moveForward();
    } else if (command == "BREAK") {
      stopMotor();
    } else if (command == "REVERSE") {
      spinCar();
    } else if (command == "LEFT_TURN") {
      moveLeft();
    } else if (command == "RIGHT_TURN") {
      moveRight();
    } else if (command == "FORWARD_LEFT_PRESSED") {
      forwardLeftPressed();
    } else if (command == "FORWARD_LEFT_RELEASED") {
      forwardLeftReleased();
    } else if (command == "FORWARD_RIGHT_PRESSED") {
      forwardRightPressed();
    } else if (command == "FORWARD_RIGHT_RELEASED") {
      forwardRightReleased();
    }
    
    else if (command == "GEAR_NEUTRAL") {
      changeGear("Shifting gear to neutral", NEUTRAL);
    } else if (command == "GEAR_PRIMERA") {
      changeGear("Shifting gear to primera", PRIMERA);
    } else if (command == "GEAR_SEGUNDA") {
      changeGear("Shifting gear to segunda", SEGUNDA);
    } else if (command == "GEAR_TRESIERA") {
      changeGear("Shifting gear to tresiera", TRESIERA);
    } else if (command == "GEAR_QUARTA") {
      changeGear("Shifting gear to quarta", QUARTA);
    }  
    
    else if (command == "HORN_PRESSED") {
      digitalWrite(BUZZER, HIGH);
    } else if (command == "HORN_RELEASED") {
      digitalWrite(BUZZER, LOW);
    } else if (command == "HEADLIGHTS_ON") {
      digitalWrite(HEADLIGHTS, HIGH);
    } else if (command == "HEADLIGHTS_OFF") {
      digitalWrite(HEADLIGHTS, LOW);
    } else if (command == "SIGNAL_LIGHTS_RIGHT_ON") {
      startTaskSignalRightTurn();
    } else if (command == "SIGNAL_LIGHTS_RIGHT_OFF") {
      stopTaskSignalRightTurn();
    } else if (command == "SIGNAL_LIGHTS_LEFT_ON") {
      startTaskSignalLeftTurn();
    } else if (command == "SIGNAL_LIGHTS_LEFT_OFF") {
      stopTaskSignalLeftTurn();
    } else if (command == "SIGNAL_LIGHTS_HAZARD_ON") {
      startTaskHazardSignal();
    } else if (command == "SIGNAL_LIGHTS_HAZARD_OFF") {
      stopTaskHazardSignal();
    }
  }
}

// MAIN LOOP

void loop() {
  WiFiClient client = server.available();
  if (client) {
    Serial.println("New client connected");
    // Send message to the client
    client.println("Connected successfully");
    readAndExecuteCommand(client);

    // Close the connection
    client.stop();
  }
}
