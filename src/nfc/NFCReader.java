package nfc;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

public class NFCReader implements Runnable {

    private static final String DEFAULT_PORT = "COM3";

    private SerialPort serialPort;
    private final String portName;
    private volatile boolean running = true;

    private static void logError(String context, Exception error) {
        System.err.println("NFCReader: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public NFCReader(String portName) {
        this.portName = portName;
    }

    public static String resolveConfiguredPortName() {
        String configured = System.getProperty("taska.nfc.port");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("TASKA_NFC_PORT");
        }
        if (configured == null || configured.isBlank()) {
            configured = DEFAULT_PORT;
        }

        configured = configured.trim();
        if (configured.equalsIgnoreCase("disabled") || configured.equalsIgnoreCase("none")) {
            return null;
        }

        return configured;
    }

    public static boolean isPortAvailable(String portName) {
        if (portName == null || portName.isBlank()) {
            return false;
        }

        for (SerialPort port : SerialPort.getCommPorts()) {
            String systemName = String.valueOf(port.getSystemPortName());
            String descriptiveName = String.valueOf(port.getDescriptivePortName());
            if (portName.equalsIgnoreCase(systemName) || portName.equalsIgnoreCase(descriptiveName)) {
                return true;
            }
        }
        return false;
    }

    public static String availablePortsSummary() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports == null || ports.length == 0) {
            return "<none>";
        }

        List<String> names = new ArrayList<>();
        for (SerialPort port : ports) {
            names.add(String.valueOf(port.getSystemPortName()));
        }
        return String.join(", ", names);
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {

            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(115200);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
            serialPort.setParity(0);

            if (serialPort.openPort()) {
                System.out.println("✅ Serial port " + portName + " opened successfully!");
            } else {
                System.out.println("❌ Failed to open serial port " + portName + "!");
                return;
            }

            System.out.println("📡 Listening for NFC tags...");

            try (InputStream in = serialPort.getInputStream()) {
                byte[] buffer = new byte[64];

                while (running) {
                    if (in.available() > 0) {
                        int len = in.read(buffer);
                        if (len > 0) {
                            String tagId = bytesToHex(buffer, len).trim();
                            if (tagId.length() >= 8 && tagId.length() <= 40) {
                                System.out.println("🏷️ Tag detected: " + tagId);
                                processTag(tagId);
                            } else {
                                System.out.println("⚠️ Ignored invalid tag: " + tagId);
                            }
                        }
                    }
                    Thread.sleep(300);
                }

            } catch (Exception e) {
                logError("serial reader loop failed", e);
            } finally {
                if (serialPort != null && serialPort.isOpen()) {
                    serialPort.closePort();
                    System.out.println("🔒 Serial port closed.");
                }
            }
        }
    }

    public void stopReading() {
        running = false;
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("✅ Serial port closed successfully.");
        }
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    private void processTag(String tagId) {
        try {
            FirestoreRestClient client = FirestoreRest.forCurrentUser();

            // 🔍 Step 1: Find the child document with this NFC UID
            List<FsDocument> matches = client.queryWhereEqual("children", "nfc_uid", tagId);

            if (matches == null || matches.isEmpty()) {
                System.out.println("❌ Unknown card detected!");
                Platform.runLater(() -> showAlert("❌ Unknown card detected!", Alert.AlertType.WARNING));
                return;
            }

            // Prefer the active child doc (skip redirect docs left behind by migration).
            FsDocument childDoc = null;
            for (FsDocument d : matches) {
                String migratedTo = d.getString("migratedToChildId");
                if (migratedTo == null || migratedTo.trim().isEmpty()) {
                    childDoc = d;
                    break;
                }
            }
            if (childDoc == null) {
                FsDocument legacy = matches.get(0);
                String migratedTo = legacy.getString("migratedToChildId");
                if (migratedTo != null && !migratedTo.trim().isEmpty()) {
                    childDoc = client.getDocument("children", migratedTo.trim());
                }
            }
            if (childDoc == null) {
                System.out.println("❌ Unknown card detected!");
                Platform.runLater(() -> showAlert("❌ Unknown card detected!", Alert.AlertType.WARNING));
                return;
            }

            String childDocId = childDoc.getId();
            Long numericChildId = childDoc.getLong("child_id");
            String childName = childDoc.getString("name");

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            // Use stable childDocId as the key (NFC UID is replaceable)
            String docId = today + "_" + childDocId;
            FsDocument attSnap = client.getDocument("attendance", docId);

            Date firestoreDate = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());

            String childRef = client.referenceValue("children", childDocId);

            // ⚙️ Case 1: No document — create new with check-in
            if (attSnap == null) {
                Map<String, Object> newData = new HashMap<>();
                // Canonical fields used by AttendanceView
                newData.put("childId", childDocId);
                newData.put("childRef", new FirestoreRestClient.ReferenceValue(childRef));
                newData.put("nfc_uid", tagId);
                newData.put("name", childName);
                newData.put("date", firestoreDate);
                newData.put("check_in_time", new Date());
                newData.put("check_out_time", null);
                newData.put("isPresent", true);
                newData.put("checkin_method", "NFC");

                // Back-compat fields (older code/data)
                if (numericChildId != null) {
                    newData.put("child_id", numericChildId.intValue());
                }
                newData.put("dateString", today.toString());
                newData.put("is_present", true);

                client.patchDocumentMerge("attendance", docId, newData);

                System.out.println("✅ Attendance recorded (check-in) for: " + childName);
                Platform.runLater(() -> showAlert("Check-in successful for " + childName, Alert.AlertType.INFORMATION));

                // 🟢 Instantly refresh dashboard + attendance UI
                Platform.runLater(() -> {
                    AttendanceView.refreshUI();
                    AttendanceView.updateChartFromStatic();
                });
                return;
            }

            // ⚙️ Case 2: Record exists — update check-in or check-out
            Date checkIn = attSnap.getDate("check_in_time");
            Date checkOut = attSnap.getDate("check_out_time");

            if (checkIn == null) {
                // Missing check-in: update it
                Map<String, Object> update = new HashMap<>();
                update.put("check_in_time", new Date());
                update.put("isPresent", true);
                update.put("is_present", true);
                update.put("date", firestoreDate);
                update.put("childId", childDocId);
                update.put("childRef", new FirestoreRestClient.ReferenceValue(childRef));
                update.put("nfc_uid", tagId);
                update.put("name", childName);
                update.put("checkin_method", "NFC");
                if (numericChildId != null) {
                    update.put("child_id", numericChildId.intValue());
                }
                update.put("dateString", today.toString());

                client.patchDocumentMerge("attendance", docId, update);
                System.out.println("✅ Check-in updated for: " + childName);
                Platform.runLater(() -> showAlert("Check-in updated for " + childName, Alert.AlertType.INFORMATION));

            } else if (checkOut == null) {
                // Normal check-out (no 8h limit)
                Map<String, Object> update = new HashMap<>();
                update.put("check_out_time", new Date());
                update.put("isPresent", true);
                update.put("is_present", true);
                update.put("date", firestoreDate);
                update.put("childId", childDocId);
                update.put("childRef", new FirestoreRestClient.ReferenceValue(childRef));
                update.put("nfc_uid", tagId);
                update.put("name", childName);
                if (numericChildId != null) {
                    update.put("child_id", numericChildId.intValue());
                }
                update.put("dateString", today.toString());

                client.patchDocumentMerge("attendance", docId, update);
                System.out.println("✅ Check-out updated for: " + childName);
                Platform.runLater(() -> showAlert("Check-out successful for " + childName, Alert.AlertType.INFORMATION));
            } else {
                System.out.println("ℹ️ Already checked out today for: " + childName);
                Platform.runLater(() -> showAlert("Already checked out today for " + childName, Alert.AlertType.INFORMATION));
            }

            // 🟢 Final unified refresh (runs once per scan)
            Platform.runLater(() -> {
                AttendanceView.refreshUI();
                AttendanceView.updateChartFromStatic();
            });

        } catch (Exception e) {
            logError("tag processing failed", e);
            Platform.runLater(() ->
                showAlert("Firestore error: " + e.getMessage(), Alert.AlertType.ERROR)
            );
        }
    }

    private static void showAlert(String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("NFC Attendance");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }
}