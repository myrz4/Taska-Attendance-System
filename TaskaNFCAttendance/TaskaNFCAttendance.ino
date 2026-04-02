// 🍋 ESP32 NFC Firestore Attendance (Unified Manual + NFC Edition)
// ✅ Fixes: Duplicates, unified ID system, method tags, smooth integration with desktop app

#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include "driver/ledc.h"
#include "esp32-hal-ledc.h"
#include <Firebase_ESP_Client.h>
#include <LiquidCrystal_I2C.h>
#include <Adafruit_PN532.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ---------------- Wi-Fi ----------------
#define WIFI_SSID "AnakAnakSyurga"
#define WIFI_PASSWORD "Roundabout29"

// ---------------- Firebase --------------
#define API_KEY "AIzaSyBiuQTwMUfk-rpgp3I6GZ2-AZ6viNjaZq0"
#define FIREBASE_PROJECT_ID "taskazurah"
#define FIRESTORE_DB_ID "(default)"
#define USER_EMAIL "esp32@taska.com"
#define USER_PASSWORD "12345678"

// ---------------- Scanner Mode ----------
// Desktop fallback mode:
// The device will try Wi-Fi + Firebase first. If cloud is unavailable,
// it will fall back to USB scanner mode and emit `TAG:<UID>` on Serial
// for the desktop app to process.
#define USB_SERIAL_FALLBACK_ENABLED true
#define WIFI_CONNECT_TIMEOUT_MS 15000
#define FIREBASE_AUTH_TIMEOUT_MS 20000
#define CLOUD_RETRY_INTERVAL_MS 10000

// ---------------- Hardware --------------
#define SDA_PIN 21
#define SCL_PIN 22
#define BUZZER_PIN 26
#define BUZZER_CH 0
#define BUZZER_FREQ 2000
#define BUZZER_RES 8

LiquidCrystal_I2C lcd(0x27, 16, 2);
Adafruit_PN532 nfc(SDA_PIN, SCL_PIN);
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
bool firebaseInitialized = false;
bool cloudReady = false;
unsigned long lastCloudRetryMs = 0;

// ---------------- Helper Functions ---------------
void beep(int ms = 120, int duty = 180) {
  ledcWriteTone(BUZZER_CH, BUZZER_FREQ);
  delay(ms);
  ledcWriteTone(BUZZER_CH, 0);
}

void showLCD(const String &line1, const String &line2 = "") {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(line1);
  lcd.setCursor(0, 1);
  lcd.print(line2);
}

void lcdSplash() {
  showLCD("Taska NFC Ready", "Scan your card");
}

void lcdUsbFallback() {
  showLCD("USB Scanner", "Scan your card");
}

String cleanString(String s) {
  s.replace("\\n", "");
  s.replace("\n", "");
  s.trim();
  return s;
}

String getUIDString(uint8_t *uid, uint8_t uidLength) {
  String uidString;
  for (uint8_t i = 0; i < uidLength; i++) {
    if (uid[i] < 0x10) uidString += "0";
    uidString += String(uid[i], HEX);
  }
  uidString.toUpperCase();
  return uidString;
}

String getIsoTimestamp() {
  time_t now = time(nullptr);
  struct tm *tm_struct = localtime(&now);
  char buf[30];
  sprintf(buf, "%04d-%02d-%02dT%02d:%02d:%02d+08:00",
          tm_struct->tm_year + 1900,
          tm_struct->tm_mon + 1,
          tm_struct->tm_mday,
          tm_struct->tm_hour,
          tm_struct->tm_min,
          tm_struct->tm_sec);
  return String(buf);
}

String getMidnightTimestamp() {
  time_t now = time(nullptr);
  struct tm *tm_struct = localtime(&now);
  tm_struct->tm_hour = 0;
  tm_struct->tm_min = 0;
  tm_struct->tm_sec = 0;
  time_t midnight = mktime(tm_struct);
  char buf[30];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S+08:00", localtime(&midnight));
  return String(buf);
}

String getDateNow() {
  time_t now = time(nullptr);
  struct tm *tm_struct = localtime(&now);
  char buf[11];
  sprintf(buf, "%04d-%02d-%02d",
          tm_struct->tm_year + 1900,
          tm_struct->tm_mon + 1,
          tm_struct->tm_mday);
  return String(buf);
}

// 🧩 Manual override for debugging or admin correction (optional)
String selectedDate = ""; // leave empty for auto (today)

// Utility to get selected or current date
String getActiveDate() {
  if (selectedDate != "" && selectedDate.length() == 10) {
    return selectedDate; // use manually set date (yyyy-MM-dd)
  }
  return getDateNow(); // default to today
}

bool waitForTimeSync(unsigned long timeoutMs) {
  configTime(28800, 0, "pool.ntp.org", "time.nist.gov");
  unsigned long started = millis();
  while (time(nullptr) < 100000) {
    delay(500);
    if (millis() - started > timeoutMs) {
      return false;
    }
  }
  return true;
}

bool connectCloud(bool showStatus) {
  if (WiFi.status() != WL_CONNECTED) {
    if (showStatus) {
      showLCD("Connecting WiFi...");
    }
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    unsigned long wifiStarted = millis();
    while (WiFi.status() != WL_CONNECTED) {
      delay(300);
      if (millis() - wifiStarted > WIFI_CONNECT_TIMEOUT_MS) {
        Serial.println("❌ Wi-Fi connection timeout");
        return false;
      }
    }
    Serial.println("✅ Wi-Fi Connected");
  }

  if (!waitForTimeSync(10000)) {
    Serial.println("❌ Time sync timeout");
    return false;
  }
  Serial.println("✅ Time OK!");

  if (!firebaseInitialized) {
    config.api_key = API_KEY;
    auth.user.email = USER_EMAIL;
    auth.user.password = USER_PASSWORD;
    config.token_status_callback = tokenStatusCallback;
    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
    firebaseInitialized = true;
  }

  if (showStatus) {
    showLCD("Firebase Auth...", "Please wait");
  }
  Serial.print("⏳ Waiting for Firebase auth token");
  unsigned long authStarted = millis();
  while (!Firebase.ready()) {
    Serial.print(".");
    delay(300);
    if (millis() - authStarted > FIREBASE_AUTH_TIMEOUT_MS) {
      Serial.println("\n❌ Firebase auth timeout");
      return false;
    }
  }
  Serial.println("\n✅ Firebase Ready");
  return true;
}

// ---------------- Setup -----------------
void setup() {
  Serial.begin(115200);
  delay(1000);

  // Initialize buzzer (PWM)
  ledcAttach(BUZZER_PIN, BUZZER_FREQ, BUZZER_RES);
  beep(150);

  Wire.begin(SDA_PIN, SCL_PIN);
  lcd.begin(16, 2);
  lcd.backlight();
  lcdSplash();
  delay(500);

  nfc.begin();
  if (!nfc.getFirmwareVersion()) {
    showLCD("❌ NFC not found!");
    Serial.println("❌ PN532 not detected");
    while (true) delay(10);
  }
  nfc.SAMConfig();
  Serial.println("✅ NFC Ready!");

  cloudReady = connectCloud(true);
  if (!cloudReady) {
    Serial.println("ℹ️ Cloud unavailable. Falling back to USB scanner mode.");
    if (USB_SERIAL_FALLBACK_ENABLED) {
      Serial.println("✅ USB serial scanner fallback enabled");
      Serial.println("ℹ️ Connect this device by USB; the desktop app will auto-detect the serial port.");
      lcdUsbFallback();
      delay(1200);
    } else {
      showLCD("Cloud Offline", "Check WiFi");
      delay(1500);
    }
  }

  lcdSplash();
}

// ---------------- Loop ------------------
void loop() {
  uint8_t uid[7];
  uint8_t uidLength;

  if (cloudReady && !Firebase.ready()) {
    cloudReady = false;
    lastCloudRetryMs = millis();
    Serial.println("⚠️ Firebase not ready. Switching to USB fallback until cloud returns.");
  }

  if (!cloudReady && USB_SERIAL_FALLBACK_ENABLED && millis() - lastCloudRetryMs >= CLOUD_RETRY_INTERVAL_MS) {
    lastCloudRetryMs = millis();
    Serial.println("🔄 Retrying Wi-Fi/Firebase connection...");
    cloudReady = connectCloud(false);
    if (cloudReady) {
      showLCD("Cloud Restored", "Ready to scan");
      delay(1200);
      lcdSplash();
    }
  }

  if (!nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength)) {
    delay(200);
    return;
  }

  String nfcUID = getUIDString(uid, uidLength);
  Serial.println("\n================================");
  Serial.println("📇 Card UID: " + nfcUID);
  showLCD("Card Detected!", nfcUID);
  beep(200);

  if (!cloudReady) {
    Serial.println("TAG:" + nfcUID);
    showLCD("USB Scan Sent", nfcUID);
    delay(1200);
    lcdSplash();
    return;
  }

  String childDocPath = "children/" + nfcUID;
  if (!Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, childDocPath.c_str())) {
    showLCD("No record", "Check card/rules");
    Serial.println("❌ Child lookup failed for UID: " + nfcUID);
    Serial.println("   Reason: " + fbdo.errorReason());
    delay(2000);
    lcdSplash();
    return;
  }

  FirebaseJson json;
  json.setJsonData(fbdo.payload().c_str());
  FirebaseJsonData result;
  String childId, childName, parentName, teacherName;

  json.get(result, "fields/name/stringValue");
  if (result.success) childName = cleanString(result.stringValue);
  json.get(result, "fields/parentName/stringValue");
  if (result.success) parentName = cleanString(result.stringValue);
  json.get(result, "fields/teacher_username/stringValue");
  if (result.success) teacherName = cleanString(result.stringValue);
  json.get(result, "fields/nfc_uid/stringValue");
  if (result.success) childId = cleanString(result.stringValue);

  if (childName == "") childName = "Unknown";
  if (parentName == "") parentName = "Unknown";
  if (teacherName == "") teacherName = "Unknown";
  if (childId == "") childId = nfcUID;

  Serial.println("✅ Found: " + childName + " | Parent: " + parentName + " | Teacher: " + teacherName);

  String date = getActiveDate();
  String timestampNow = getIsoTimestamp();
  String midnightTimestamp = getMidnightTimestamp();
  String docID = date + "_" + childId;
  String docPath = "attendance/" + docID;
  docID.trim();  // ✅ Ensures no hidden spaces, newline, or trailing characters

  bool recordExists = false;
  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, docPath.c_str())) {
    String payload = fbdo.payload().c_str();
    if (payload.indexOf("fields") > 0) recordExists = true;
  }

  if (recordExists) {
  // Parse current attendance document
  String payload = fbdo.payload().c_str();
  FirebaseJson existing;
  existing.setJsonData(payload);

  FirebaseJsonData checkInVal, checkOutVal;
  existing.get(checkInVal, "fields/check_in_time/timestampValue");
  existing.get(checkOutVal, "fields/check_out_time/timestampValue");

  bool hasCheckIn = checkInVal.success && checkInVal.stringValue != "";
  bool hasCheckOut = checkOutVal.success && checkOutVal.stringValue != "";

  if (!hasCheckIn) {
    Serial.println("🟢 No check-in found — performing CHECK-IN");
    FirebaseJson update;
    update.set("fields/check_in_time/timestampValue", timestampNow);
    update.set("fields/checkin_method/stringValue", "NFC");
    update.set("fields/isPresent/booleanValue", true);

    if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                         docPath.c_str(), update.raw(),
                                         "check_in_time,checkin_method,isPresent")) {
      showLCD("Checked In", childName);
      beep(250);
    } else {
      showLCD("⚠️ Failed", "Check-In Error");
    }
  } 
  else if (hasCheckIn && !hasCheckOut) {
    Serial.println("🔵 Check-in found — performing CHECK-OUT");
    FirebaseJson update;
    update.set("fields/check_out_time/timestampValue", timestampNow);
    update.set("fields/checkout_method/stringValue", "NFC");
    update.set("fields/manualCheckout/booleanValue", false);

    if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                         docPath.c_str(), update.raw(),
                                         "check_out_time,checkout_method,manualCheckout")) {
      showLCD("Checked Out", childName);
      beep(200);
    } else {
      showLCD("⚠️ Failed", "Check-Out Error");
    }
  } 
  else if (hasCheckIn && hasCheckOut) {
    Serial.println("⚠️ Already checked out — new day or reset required");
    showLCD("Already Done", "Next Scan Tomorrow");
    beep(100);
  }
} 
else {
  Serial.println("🆕 No record — performing CHECK-IN");
  FirebaseJson content;
  content.set("fields/childId/stringValue", childId);
  content.set("fields/childRef/referenceValue", "projects/" FIREBASE_PROJECT_ID "/databases/(default)/documents/children/" + nfcUID);
  content.set("fields/name/stringValue", childName);
  content.set("fields/parentName/stringValue", parentName);
  content.set("fields/teacher/stringValue", teacherName);
  content.set("fields/date/timestampValue", midnightTimestamp);
  content.set("fields/check_in_time/timestampValue", timestampNow);
  content.set("fields/checkin_method/stringValue", "NFC");
  content.set("fields/isPresent/booleanValue", true);
  content.set("fields/manualCheckout/booleanValue", false);
  content.set("fields/manual_in/booleanValue", false);
  content.set("fields/manual_out/booleanValue", false);
  content.set("fields/reason/stringValue", "Default");

  if (Firebase.Firestore.createDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                        docPath.c_str(), content.raw())) {
    showLCD("Checked In", childName);
    beep(250);
  } else {
    showLCD("⚠️ Firebase Error");
  }
}

  delay(3000);
  lcdSplash();
}