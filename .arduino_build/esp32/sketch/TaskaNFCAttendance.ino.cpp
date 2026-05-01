#line 1 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
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

// ---------------- Helper Functions ---------------
#line 55 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void lcdSplash();
#line 59 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String cleanString(String s);
#line 66 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String extractDocumentIdFromName(const String &documentName);
#line 74 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool queryChildDocumentByUid(const String &nfcUID, FirebaseJson &json, String &resolvedDocId);
#line 165 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getUIDString(uint8_t *uid, uint8_t uidLength);
#line 175 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getIsoTimestamp();
#line 189 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getMidnightTimestamp();
#line 201 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getDateNow();
#line 212 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void publishLatestScan(const String &nfcUID);
#line 238 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getActiveDate();
#line 246 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void setup();
#line 305 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void loop();
#line 41 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
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

String cleanString(String s) {
  s.replace("\\n", "");
  s.replace("\n", "");
  s.trim();
  return s;
}

String extractDocumentIdFromName(const String &documentName) {
  int slashIndex = documentName.lastIndexOf('/');
  if (slashIndex < 0 || slashIndex + 1 >= documentName.length()) {
    return "";
  }
  return cleanString(documentName.substring(slashIndex + 1));
}

bool queryChildDocumentByUid(const String &nfcUID, FirebaseJson &json, String &resolvedDocId) {
  FirebaseJson query;
  query.set("from/collectionId", "children");
  query.set("from/allDescendants", false);
  query.set("where/fieldFilter/field/fieldPath", "nfc_uid");
  query.set("where/fieldFilter/op", "EQUAL");
  query.set("where/fieldFilter/value/stringValue", nfcUID);
  query.set("limit", 5);

  if (!Firebase.Firestore.runQuery(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, "/", &query)) {
    Serial.println("❌ Child lookup query failed for UID: " + nfcUID);
    Serial.println("   Reason: " + fbdo.errorReason());
    return false;
  }

  FirebaseJsonArray rows;
  if (!rows.setJsonArrayData(fbdo.payload().c_str())) {
    return false;
  }

  String migratedToChildId = "";

  for (size_t i = 0; i < rows.size(); i++) {
    FirebaseJsonData rowData;
    rows.get(rowData, i);

    String rowText = rowData.to<String>();
    if (rowText == "") {
      continue;
    }

    FirebaseJson rowJson;
    rowJson.setJsonData(rowText.c_str());

    FirebaseJsonData docResult;
    rowJson.get(docResult, "document");
    if (!docResult.success) {
      continue;
    }

    String docText = docResult.to<String>();
    if (docText == "") {
      continue;
    }

    FirebaseJson docJson;
    docJson.setJsonData(docText.c_str());

    FirebaseJsonData storedUidResult;
    docJson.get(storedUidResult, "fields/nfc_uid/stringValue");
    String storedUid = storedUidResult.success ? cleanString(storedUidResult.stringValue) : "";
    storedUid.toUpperCase();
    if (storedUid != nfcUID) {
      continue;
    }

    FirebaseJsonData migratedResult;
    docJson.get(migratedResult, "fields/migratedToChildId/stringValue");
    String migratedTo = migratedResult.success ? cleanString(migratedResult.stringValue) : "";
    if (migratedTo != "") {
      if (migratedToChildId == "") {
        migratedToChildId = migratedTo;
      }
      continue;
    }

    FirebaseJsonData docNameResult;
    docJson.get(docNameResult, "name");
    String documentName = docNameResult.success ? cleanString(docNameResult.stringValue) : "";
    String documentId = extractDocumentIdFromName(documentName);
    if (documentId == "") {
      continue;
    }

    resolvedDocId = documentId;
    json.setJsonData(docText.c_str());
    return true;
  }

  if (migratedToChildId != "") {
    String childDocPath = "children/" + migratedToChildId;
    if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, childDocPath.c_str())) {
      json.setJsonData(fbdo.payload().c_str());
      resolvedDocId = migratedToChildId;
      return true;
    }
  }

  return false;
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

void publishLatestScan(const String &nfcUID) {
  String bridgePath = "nfcCapture/latest";
  FirebaseJson content;
  content.set("fields/uid/stringValue", nfcUID);
  content.set("fields/scannedAt/timestampValue", getIsoTimestamp());
  content.set("fields/source/stringValue", "esp32");

  if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                       bridgePath.c_str(), content.raw(),
                                       "uid,scannedAt,source")) {
    return;
  }

  if (Firebase.Firestore.createDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                        bridgePath.c_str(), content.raw())) {
    return;
  }

  Serial.println("⚠️ Failed to publish latest NFC scan bridge.");
  Serial.println("   Reason: " + fbdo.errorReason());
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

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  showLCD("Connecting WiFi...");
  while (WiFi.status() != WL_CONNECTED) delay(300);
  Serial.println("\n✅ Wi-Fi Connected");

  configTime(28800, 0, "pool.ntp.org", "time.nist.gov");
  while (time(nullptr) < 100000) delay(500);
  Serial.println("✅ Time OK!");

  config.api_key = API_KEY;
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;
  config.token_status_callback = tokenStatusCallback;  // required for Firebase.ready() to work
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // Wait until Firebase auth token is ready before accepting scans
  showLCD("Firebase Auth...", "Please wait");
  Serial.print("⏳ Waiting for Firebase auth token");
  unsigned long authTimeout = millis();
  while (!Firebase.ready()) {
    Serial.print(".");
    delay(300);
    if (millis() - authTimeout > 20000) {
      Serial.println("\n❌ Firebase auth timed out — restarting");
      showLCD("Auth timeout!", "Restarting...");
      delay(2000);
      ESP.restart();
    }
  }
  Serial.println("\n✅ Firebase Ready");

  lcdSplash();
}

// ---------------- Loop ------------------
void loop() {
  uint8_t uid[7];
  uint8_t uidLength;

  // Keep Firebase token alive; show brief notice if not ready yet
  if (!Firebase.ready()) {
    showLCD("Reconnecting...", "Please wait");
    delay(500);
    return;
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
  publishLatestScan(nfcUID);

  FirebaseJson json;
  String childId = "";
  if (!queryChildDocumentByUid(nfcUID, json, childId)) {
    showLCD("No record", "Check card/rules");
    Serial.println("❌ Child lookup failed for UID: " + nfcUID);
    Serial.println("   Reason: no matching child document found for nfc_uid");
    delay(2000);
    lcdSplash();
    return;
  }

  FirebaseJsonData result;
  String childNfcUid, childName, parentName, teacherName;

  json.get(result, "fields/name/stringValue");
  if (result.success) childName = cleanString(result.stringValue);
  json.get(result, "fields/parentName/stringValue");
  if (result.success) parentName = cleanString(result.stringValue);
  json.get(result, "fields/teacher_username/stringValue");
  if (result.success) teacherName = cleanString(result.stringValue);
  json.get(result, "fields/nfc_uid/stringValue");
  if (result.success) childNfcUid = cleanString(result.stringValue);

  if (childName == "") childName = "Unknown";
  if (parentName == "") parentName = "Unknown";
  if (teacherName == "") teacherName = "Unknown";
  if (childNfcUid == "") childNfcUid = nfcUID;
  if (childId == "") childId = childNfcUid;

  String canonicalChildRef = String("projects/") + FIREBASE_PROJECT_ID + "/databases/(default)/documents/children/" + childId;

  Serial.println("✅ Found: " + childName + " | Parent: " + parentName + " | Teacher: " + teacherName);

  String date = getActiveDate();
  String timestampNow = getIsoTimestamp();
  String midnightTimestamp = getMidnightTimestamp();
  String docID = date + "_" + childId;
  String docPath = "attendance/" + docID;
  String legacyDocID = date + "_" + childNfcUid;
  String legacyDocPath = "attendance/" + legacyDocID;
  docID.trim();  // ✅ Ensures no hidden spaces, newline, or trailing characters

  bool recordExists = false;
  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, docPath.c_str())) {
    String payload = fbdo.payload().c_str();
    if (payload.indexOf("fields") > 0) recordExists = true;
  } else if (legacyDocID != docID && Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, legacyDocPath.c_str())) {
    String payload = fbdo.payload().c_str();
    if (payload.indexOf("fields") > 0) {
      recordExists = true;
      docID = legacyDocID;
      docPath = legacyDocPath;
    }
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
    update.set("fields/childId/stringValue", childId);
    update.set("fields/nfc_uid/stringValue", childNfcUid);
    update.set("fields/childRef/referenceValue", canonicalChildRef);
    update.set("fields/check_in_time/timestampValue", timestampNow);
    update.set("fields/checkin_method/stringValue", "NFC");
    update.set("fields/isPresent/booleanValue", true);

    if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                         docPath.c_str(), update.raw(),
                                         "childId,nfc_uid,childRef,check_in_time,checkin_method,isPresent")) {
      showLCD("Checked In", childName);
      beep(250);
    } else {
      showLCD("⚠️ Failed", "Check-In Error");
    }
  } 
  else if (hasCheckIn && !hasCheckOut) {
    Serial.println("ℹ️ Already checked in — NFC scan will not check out. Use parent QR pickup or manual override.");
    showLCD("Already In", "Use QR/manual");
    beep(100);
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
  content.set("fields/nfc_uid/stringValue", childNfcUid);
  content.set("fields/childRef/referenceValue", canonicalChildRef);
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
