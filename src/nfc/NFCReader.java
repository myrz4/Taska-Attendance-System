package nfc;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;

public class NFCReader implements Runnable {

    private SerialPort serialPort;
    private final String portName;
    private volatile boolean running = true;

    // Firestore helper
    private static Firestore db() {
        return FirestoreService.db(); // unified Firestore connection
    }

    public NFCReader(String portName) {
        this.portName = portName;
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
                e.printStackTrace();
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
            Firestore fdb = db();

            // 🔍 Step 1: Find the child document with this NFC UID
            ApiFuture<QuerySnapshot> queryFuture = fdb.collection("children")
                    .whereEqualTo("nfc_uid", tagId)
                    .limit(1)
                    .get();
            QuerySnapshot querySnapshot = queryFuture.get();

            if (querySnapshot.isEmpty()) {
                System.out.println("❌ Unknown card detected!");
                Platform.runLater(() -> AdminDashboard.handleNfcAttendance(tagId));
                return;
            }

            DocumentSnapshot childDoc = querySnapshot.getDocuments().get(0);
            int childId = childDoc.getLong("child_id").intValue();
            String childName = childDoc.getString("name");

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            String docId = today + "_" + childId;
            DocumentReference attRef = fdb.collection("attendance").document(docId);
            DocumentSnapshot attSnap = attRef.get().get();

            // ⚙️ Case 1: No document — create new with check-in
            if (!attSnap.exists()) {
                Map<String, Object> newData = new HashMap<>();
                newData.put("child_id", childId);
                newData.put("name", childName);
                newData.put("date", today.toString());
                newData.put("check_in_time", new Date());
                newData.put("is_present", true);
                attRef.set(newData).get();

                System.out.println("✅ Attendance recorded (check-in) for: " + childName);
                Platform.runLater(() -> showAlert("Check-in successful for " + childName, Alert.AlertType.INFORMATION));

                // 🟢 Instantly refresh dashboard + attendance UI
                FirestoreService.forceFullRefresh();
                return;
            }

            // ⚙️ Case 2: Record exists — update check-in or check-out
            Date checkIn = attSnap.getDate("check_in_time");
            Date checkOut = attSnap.getDate("check_out_time");

            if (checkIn == null) {
                // Missing check-in: update it
                attRef.update("check_in_time", new Date(), "is_present", true).get();
                System.out.println("✅ Check-in updated for: " + childName);
                Platform.runLater(() -> showAlert("Check-in updated for " + childName, Alert.AlertType.INFORMATION));

            } else if (checkOut == null) {
                // Normal check-out (no 8h limit)
                attRef.update("check_out_time", new Date(), "is_present", true).get();
                System.out.println("✅ Check-out updated for: " + childName);
                Platform.runLater(() -> showAlert("Check-out successful for " + childName, Alert.AlertType.INFORMATION));
            } else {
                System.out.println("ℹ️ Already checked out today for: " + childName);
                Platform.runLater(() -> showAlert("Already checked out today for " + childName, Alert.AlertType.INFORMATION));
            }

            // 🟢 Final unified refresh (runs once per scan)
            Platform.runLater(FirestoreService::forceFullRefresh);

        } catch (Exception e) {
            e.printStackTrace();
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