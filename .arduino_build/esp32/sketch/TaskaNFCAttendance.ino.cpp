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
#include "AttendanceDocCandidate.h"
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
#define SAME_TAG_COOLDOWN_MS 5000

LiquidCrystal_I2C lcd(0x27, 16, 2);
Adafruit_PN532 nfc(SDA_PIN, SCL_PIN);
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
String lastScannedUid = "";
unsigned long lastScannedAtMs = 0;

// ---------------- Helper Functions ---------------
#line 59 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void lcdSplash();
#line 63 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String cleanString(String s);
#line 70 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String readFirestoreStringField(FirebaseJson &json, const String &path);
#line 79 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String readFirestoreTimestampField(FirebaseJson &json, const String &fieldPath);
#line 102 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool firestoreFieldHasTimestamp(FirebaseJson &json, const String &fieldPath);
#line 106 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String normalizedAttendanceStatus(FirebaseJson &json, bool &hasCheckIn, bool &hasCheckOut);
#line 149 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void setFirestoreNull(FirebaseJson &json, const String &fieldPath);
#line 155 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String buildFirestoreDocPath(const String &collectionId, const String &docId);
#line 162 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String buildAttendanceDocId(const String &dateKey, const String &identityKey);
#line 169 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool attendanceDocumentIsAdminCorrected(FirebaseJson &json);
#line 187 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String attendanceDocumentSortKey(FirebaseJson &json, const String &docId);
#line 227 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool loadAttendanceDocCandidate(const String &docId, AttendanceDocCandidate &candidate);
#line 256 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool shouldPreferAttendanceCandidate(const AttendanceDocCandidate &candidate, const AttendanceDocCandidate &current);
#line 283 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
AttendanceDocCandidate resolveAttendanceDocForScan(const String &dateKey, const String &childNfcUid, const String &canonicalChildId);
#line 305 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getUIDString(uint8_t *uid, uint8_t uidLength);
#line 315 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getIsoTimestamp();
#line 329 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getMidnightTimestamp();
#line 341 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getDateNow();
#line 352 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String encodeLegacyUidDocId(const String &uid);
#line 365 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String extractDocumentIdFromName(const String &documentName);
#line 373 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool loadChildDocumentByPath(const String &childDocPath, const String &expectedUid, FirebaseJson &json, String &resolvedDocId);
#line 404 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool queryChildDocumentByUid(const String &nfcUID, FirebaseJson &json, String &resolvedDocId);
#line 490 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool fetchChildDocumentByUid(const String &nfcUID, FirebaseJson &json, String &resolvedDocId);
#line 506 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
bool shouldIgnoreDuplicateScan(const String &nfcUID);
#line 520 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
String getActiveDate();
#line 528 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void setup();
#line 588 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
void loop();
#line 45 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\TaskaNFCAttendance.ino"
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

String readFirestoreStringField(FirebaseJson &json, const String &path) {
  FirebaseJsonData result;
  json.get(result, path);
  if (!result.success) {
    return "";
  }
  return cleanString(result.stringValue);
}

String readFirestoreTimestampField(FirebaseJson &json, const String &fieldPath) {
  FirebaseJsonData fieldResult;
  json.get(fieldResult, fieldPath);
  if (!fieldResult.success) {
    return "";
  }

  String fieldText = fieldResult.to<String>();
  if (fieldText == "") {
    return "";
  }

  FirebaseJson fieldJson;
  fieldJson.setJsonData(fieldText.c_str());

  FirebaseJsonData timestampResult;
  fieldJson.get(timestampResult, "timestampValue");
  if (!timestampResult.success) {
    return "";
  }
  return cleanString(timestampResult.stringValue);
}

bool firestoreFieldHasTimestamp(FirebaseJson &json, const String &fieldPath) {
  return readFirestoreTimestampField(json, fieldPath) != "";
}

String normalizedAttendanceStatus(FirebaseJson &json, bool &hasCheckIn, bool &hasCheckOut) {
  hasCheckIn = firestoreFieldHasTimestamp(json, "fields/checkInAt")
    || firestoreFieldHasTimestamp(json, "fields/check_in_time");
  hasCheckOut = firestoreFieldHasTimestamp(json, "fields/checkOutAt")
    || firestoreFieldHasTimestamp(json, "fields/check_out_time");

  String status = readFirestoreStringField(json, "fields/status/stringValue");
  status.toUpperCase();

  if (status == "ABSENT") {
    status = "NOT_CHECKED_IN";
  }

  if (status == "NOT_CHECKED_IN") {
    hasCheckIn = false;
    hasCheckOut = false;
    return status;
  }

  if (status == "CHECKED_OUT") {
    hasCheckIn = true;
    hasCheckOut = true;
    return status;
  }

  if (status == "CHECKED_IN") {
    hasCheckIn = true;
    hasCheckOut = false;
    return status;
  }

  if (hasCheckOut) {
    hasCheckIn = true;
    return "CHECKED_OUT";
  }

  if (hasCheckIn) {
    return "CHECKED_IN";
  }

  return "NOT_CHECKED_IN";
}

void setFirestoreNull(FirebaseJson &json, const String &fieldPath) {
  String nullPath = fieldPath;
  nullPath += "/nullValue";
  json.set(nullPath.c_str(), "NULL_VALUE");
}

String buildFirestoreDocPath(const String &collectionId, const String &docId) {
  String path = collectionId;
  path += "/";
  path += docId;
  return path;
}

String buildAttendanceDocId(const String &dateKey, const String &identityKey) {
  String docId = dateKey;
  docId += "_";
  docId += identityKey;
  return docId;
}

bool attendanceDocumentIsAdminCorrected(FirebaseJson &json) {
  String manualReason = readFirestoreStringField(json, "fields/manualEditReason/stringValue");
  if (manualReason == "") {
    manualReason = readFirestoreStringField(json, "fields/manual_edit_reason/stringValue");
  }
  if (manualReason != "") {
    return true;
  }

  String lastAction = readFirestoreStringField(json, "fields/auditMetadata/mapValue/fields/lastAction/stringValue");
  lastAction.toUpperCase();
  return lastAction == "MARK_ABSENT"
    || lastAction == "EDIT_RECORD"
    || lastAction == "MANUAL_CHECK_IN"
    || lastAction == "MANUAL_CHECK_OUT"
    || lastAction == "REOPEN_RECORD";
}

String attendanceDocumentSortKey(FirebaseJson &json, const String &docId) {
  String value = readFirestoreTimestampField(json, "fields/updatedAt");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/checkOutAt");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/check_out_time");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/checkOutTime");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/checkoutTime");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/checkInAt");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/check_in_time");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/checkInTime");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/createdAt");
  if (value != "") return value;

  value = readFirestoreTimestampField(json, "fields/date");
  if (value != "") return value;

  value = readFirestoreStringField(json, "fields/dateKey/stringValue");
  if (value != "") return value;

  if (docId.length() >= 10) {
    return cleanString(docId.substring(0, 10));
  }
  return "";
}

bool loadAttendanceDocCandidate(const String &docId, AttendanceDocCandidate &candidate) {
  candidate.exists = false;
  candidate.docId = docId;
  candidate.docPath = buildFirestoreDocPath("attendance", docId);
  candidate.payload = "";
  candidate.hasCheckIn = false;
  candidate.hasCheckOut = false;
  candidate.isAdminCorrected = false;
  candidate.status = "NOT_CHECKED_IN";
  candidate.sortKey = "";

  if (!Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, candidate.docPath.c_str())) {
    return false;
  }

  candidate.payload = fbdo.payload().c_str();
  if (candidate.payload.indexOf("fields") < 0) {
    return false;
  }

  FirebaseJson docJson;
  docJson.setJsonData(candidate.payload.c_str());
  candidate.status = normalizedAttendanceStatus(docJson, candidate.hasCheckIn, candidate.hasCheckOut);
  candidate.isAdminCorrected = attendanceDocumentIsAdminCorrected(docJson);
  candidate.sortKey = attendanceDocumentSortKey(docJson, candidate.docId);
  candidate.exists = true;
  return true;
}

bool shouldPreferAttendanceCandidate(const AttendanceDocCandidate &candidate, const AttendanceDocCandidate &current) {
  if (!candidate.exists) {
    return false;
  }
  if (!current.exists) {
    return true;
  }

  if (candidate.sortKey != current.sortKey) {
    return candidate.sortKey > current.sortKey;
  }

  if (candidate.isAdminCorrected != current.isAdminCorrected) {
    return candidate.isAdminCorrected;
  }

  if (candidate.hasCheckOut != current.hasCheckOut) {
    return candidate.hasCheckOut;
  }

  if (candidate.hasCheckIn != current.hasCheckIn) {
    return candidate.hasCheckIn;
  }

  return candidate.docId > current.docId;
}

AttendanceDocCandidate resolveAttendanceDocForScan(const String &dateKey, const String &childNfcUid, const String &canonicalChildId) {
  AttendanceDocCandidate best;
  best.exists = false;

  AttendanceDocCandidate candidate;
  String legacyDocId = buildAttendanceDocId(dateKey, childNfcUid);
  if (loadAttendanceDocCandidate(legacyDocId, candidate) && shouldPreferAttendanceCandidate(candidate, best)) {
    best = candidate;
  }

  if (canonicalChildId != "" && canonicalChildId != childNfcUid) {
    String canonicalDocId = buildAttendanceDocId(dateKey, canonicalChildId);
    if (canonicalDocId != legacyDocId) {
      if (loadAttendanceDocCandidate(canonicalDocId, candidate) && shouldPreferAttendanceCandidate(candidate, best)) {
        best = candidate;
      }
    }
  }

  return best;
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

String encodeLegacyUidDocId(const String &uid) {
  String encoded;
  encoded.reserve(uid.length() * 2 + 4);
  for (size_t i = 0; i < uid.length(); i++) {
    uint8_t value = static_cast<uint8_t>(uid.charAt(i));
    if (value < 0x10) encoded += "0";
    encoded += String(value, HEX);
  }
  encoded += "0D0A";
  encoded.toUpperCase();
  return encoded;
}

String extractDocumentIdFromName(const String &documentName) {
  int slashIndex = documentName.lastIndexOf('/');
  if (slashIndex < 0 || slashIndex + 1 >= documentName.length()) {
    return "";
  }
  return cleanString(documentName.substring(slashIndex + 1));
}

bool loadChildDocumentByPath(const String &childDocPath, const String &expectedUid, FirebaseJson &json, String &resolvedDocId) {
  if (!Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID, childDocPath.c_str())) {
    return false;
  }

  json.setJsonData(fbdo.payload().c_str());
  FirebaseJsonData storedUidResult;
  json.get(storedUidResult, "fields/nfc_uid/stringValue");
  String storedUid = storedUidResult.success ? cleanString(storedUidResult.stringValue) : "";
  storedUid.toUpperCase();
  if (storedUid != expectedUid) {
    return false;
  }

  FirebaseJsonData migratedResult;
  json.get(migratedResult, "fields/migratedToChildId/stringValue");
  String migratedToChildId = migratedResult.success ? cleanString(migratedResult.stringValue) : "";
  if (migratedToChildId != "") {
    String currentDocId = extractDocumentIdFromName(childDocPath);
    if (migratedToChildId != currentDocId) {
      return loadChildDocumentByPath(String("children/") + migratedToChildId, expectedUid, json, resolvedDocId);
    }
  }

  resolvedDocId = extractDocumentIdFromName(childDocPath);
  if (resolvedDocId == "") {
    resolvedDocId = childDocPath;
  }
  return true;
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
    Serial.println(String("⚠️ Firestore query failed for UID: ") + nfcUID);
    Serial.println(String("   Reason: ") + fbdo.errorReason());
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
    return loadChildDocumentByPath(String("children/") + migratedToChildId, nfcUID, json, resolvedDocId);
  }

  return false;
}

bool fetchChildDocumentByUid(const String &nfcUID, FirebaseJson &json, String &resolvedDocId) {
  String primaryDocId = nfcUID;
  String legacyDocId = encodeLegacyUidDocId(nfcUID);
  String candidates[2] = {primaryDocId, legacyDocId};

  for (int i = 0; i < 2; i++) {
    String docId = candidates[i];
    String childDocPath = String("children/") + docId;
    if (loadChildDocumentByPath(childDocPath, nfcUID, json, resolvedDocId)) {
      return true;
    }
  }

  return queryChildDocumentByUid(nfcUID, json, resolvedDocId);
}

bool shouldIgnoreDuplicateScan(const String &nfcUID) {
  unsigned long now = millis();
  if (nfcUID == lastScannedUid && now - lastScannedAtMs < SAME_TAG_COOLDOWN_MS) {
    return true;
  }
  lastScannedUid = nfcUID;
  lastScannedAtMs = now;
  return false;
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
  WiFi.setSleep(false);

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
  if (shouldIgnoreDuplicateScan(nfcUID)) {
    Serial.println(String("ℹ️ Duplicate scan ignored for UID: ") + nfcUID);
    delay(400);
    return;
  }

  Serial.println("\n================================");
  Serial.println(String("📇 Card UID: ") + nfcUID);
  showLCD("Card Detected!", nfcUID);
  beep(200);

  FirebaseJson json;
  String resolvedChildDocId = "";
  if (!fetchChildDocumentByUid(nfcUID, json, resolvedChildDocId)) {
    showLCD("No record", "Check card/rules");
    Serial.println(String("❌ Child lookup failed for UID: ") + nfcUID);
    Serial.println("   Reason: no matching child document found for direct or legacy UID path");
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

  String canonicalChildId = resolvedChildDocId;
  String canonicalChildRef = String("projects/") + FIREBASE_PROJECT_ID + "/databases/(default)/documents/children/" + canonicalChildId;

  Serial.println(String("✅ Found: ") + childName + " | Parent: " + parentName + " | Teacher: " + teacherName);

  String date = getActiveDate();
  String timestampNow = getIsoTimestamp();
  String midnightTimestamp = getMidnightTimestamp();
  String defaultDocId = buildAttendanceDocId(date, childNfcUid);
  AttendanceDocCandidate selectedAttendance = resolveAttendanceDocForScan(date, childNfcUid, canonicalChildId);
  String docID = selectedAttendance.exists ? selectedAttendance.docId : defaultDocId;
  String docPath = selectedAttendance.exists ? selectedAttendance.docPath : buildFirestoreDocPath("attendance", docID);
  docID.trim();  // ✅ Ensures no hidden spaces, newline, or trailing characters

  bool recordExists = selectedAttendance.exists;

  if (recordExists) {
  bool hasCheckIn = selectedAttendance.hasCheckIn;
  bool hasCheckOut = selectedAttendance.hasCheckOut;
  String normalizedStatus = selectedAttendance.status;

  if (selectedAttendance.docId != defaultDocId) {
    String resolvedMessage = "ℹ️ Resolved attendance doc: ";
    resolvedMessage += selectedAttendance.docId;
    Serial.println(resolvedMessage);
  }

  FirebaseJson canonicalPatch;
  canonicalPatch.set("fields/attendanceId/stringValue", docID);
  canonicalPatch.set("fields/childId/stringValue", canonicalChildId);
  canonicalPatch.set("fields/nfc_uid/stringValue", childNfcUid);
  canonicalPatch.set("fields/childRef/referenceValue", canonicalChildRef);
  canonicalPatch.set("fields/name/stringValue", childName);
  canonicalPatch.set("fields/parentName/stringValue", parentName);
  canonicalPatch.set("fields/teacher/stringValue", teacherName);
  canonicalPatch.set("fields/dateKey/stringValue", date);
  canonicalPatch.set("fields/isPresent/booleanValue", hasCheckIn || hasCheckOut);
  canonicalPatch.set("fields/is_present/booleanValue", hasCheckIn || hasCheckOut);
  canonicalPatch.set("fields/status/stringValue", normalizedStatus);

  if (!Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                       docPath.c_str(), canonicalPatch.raw(),
                                       "attendanceId,childId,nfc_uid,childRef,name,parentName,teacher,dateKey,isPresent,is_present,status")) {
    Serial.println(String("⚠️ Failed to canonicalize attendance identity for ") + docID);
    Serial.println(String("   Reason: ") + fbdo.errorReason());
  }

  if (!hasCheckIn) {
    Serial.println("🟢 No check-in found — performing CHECK-IN");
    FirebaseJson update;
    update.set("fields/attendanceId/stringValue", docID);
    update.set("fields/childId/stringValue", canonicalChildId);
    update.set("fields/nfc_uid/stringValue", childNfcUid);
    update.set("fields/childRef/referenceValue", canonicalChildRef);
    update.set("fields/name/stringValue", childName);
    update.set("fields/parentName/stringValue", parentName);
    update.set("fields/teacher/stringValue", teacherName);
    update.set("fields/date/timestampValue", midnightTimestamp);
    update.set("fields/dateKey/stringValue", date);
    update.set("fields/checkInAt/timestampValue", timestampNow);
    update.set("fields/checkInMethod/stringValue", "NFC");
    setFirestoreNull(update, "fields/checkOutAt");
    setFirestoreNull(update, "fields/checkOutMethod");
    update.set("fields/check_in_time/timestampValue", timestampNow);
    update.set("fields/checkin_method/stringValue", "NFC");
    setFirestoreNull(update, "fields/check_out_time");
    setFirestoreNull(update, "fields/checkout_method");
    update.set("fields/isPresent/booleanValue", true);
    update.set("fields/is_present/booleanValue", true);
    update.set("fields/status/stringValue", "CHECKED_IN");
    update.set("fields/manualCheckout/booleanValue", false);
    update.set("fields/manual_in/booleanValue", false);
    update.set("fields/manual_out/booleanValue", false);
    update.set("fields/checkout_approval/booleanValue", false);

    if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, FIRESTORE_DB_ID,
                                         docPath.c_str(), update.raw(),
                                         "attendanceId,childId,nfc_uid,childRef,name,parentName,teacher,date,dateKey,checkInAt,checkInMethod,checkOutAt,checkOutMethod,check_in_time,checkin_method,check_out_time,checkout_method,isPresent,is_present,status,manualCheckout,manual_in,manual_out,checkout_approval")) {
      showLCD("Checked In", childName);
      beep(250);
    } else {
      showLCD("⚠️ Failed", "Check-In Error");
    }
  } 
  else if (hasCheckIn && !hasCheckOut) {
    Serial.println("ℹ️ Already checked in. Use parent QR pickup in Teacher App, or use manual checkout override if needed.");
    showLCD("Already In", "QR or Manual");
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
  content.set("fields/attendanceId/stringValue", docID);
  content.set("fields/childId/stringValue", canonicalChildId);
  content.set("fields/nfc_uid/stringValue", childNfcUid);
  content.set("fields/childRef/referenceValue", canonicalChildRef);
  content.set("fields/name/stringValue", childName);
  content.set("fields/parentName/stringValue", parentName);
  content.set("fields/teacher/stringValue", teacherName);
  content.set("fields/date/timestampValue", midnightTimestamp);
  content.set("fields/dateKey/stringValue", date);
  content.set("fields/status/stringValue", "CHECKED_IN");
  content.set("fields/checkInAt/timestampValue", timestampNow);
  content.set("fields/checkInMethod/stringValue", "NFC");
  content.set("fields/check_in_time/timestampValue", timestampNow);
  content.set("fields/checkin_method/stringValue", "NFC");
  content.set("fields/isPresent/booleanValue", true);
  content.set("fields/is_present/booleanValue", true);
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
