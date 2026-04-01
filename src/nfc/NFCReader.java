package nfc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fazecast.jSerialComm.SerialPort;

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
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);

            if (serialPort.openPort()) {
                System.out.println("✅ Serial port " + portName + " opened successfully!");
            } else {
                System.out.println("❌ Failed to open serial port " + portName + "!");
                return;
            }

            System.out.println("📡 Listening for NFC tags...");

            try (InputStream in = serialPort.getInputStream()) {
                byte[] buffer = new byte[64];

                while (running && serialPort != null && serialPort.isOpen()) {
                    int len = in.read(buffer);
                    if (len <= 0) {
                        continue;
                    }

                    String tagId = bytesToHex(buffer, len).trim();
                    if (tagId.length() >= 8 && tagId.length() <= 40) {
                        System.out.println("🏷️ Tag detected: " + tagId);
                        processTag(tagId);
                    } else {
                        System.out.println("⚠️ Ignored invalid tag: " + tagId);
                    }
                }

            } catch (java.io.IOException | RuntimeException e) {
                logError("serial reader loop failed", e);
            } finally {
                if (serialPort != null && serialPort.isOpen()) {
                    serialPort.closePort();
                    System.out.println("🔒 Serial port closed.");
                }
                serialPort = null;
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
            NFCAttendanceSupport.AttendanceUpdateResult result = NFCAttendanceSupport.submitCheckIn(tagId, UserSession.getName());

            if (result.status == NFCAttendanceSupport.AttendanceUpdateResult.Status.UNKNOWN_CARD
                || result.status == NFCAttendanceSupport.AttendanceUpdateResult.Status.INVALID_UID) {
                System.out.println("❌ Unknown card detected!");
                Platform.runLater(() -> showAlert("❌ Unknown card detected!", Alert.AlertType.WARNING));
                return;
            }

            if (result.status == NFCAttendanceSupport.AttendanceUpdateResult.Status.CHECKED_IN) {
                System.out.println("✅ Attendance recorded (check-in) for: " + result.childName);
                Platform.runLater(() -> showAlert("Check-in successful for " + result.childName, Alert.AlertType.INFORMATION));
                Platform.runLater(() -> {
                    AttendanceView.refreshUI();
                    AttendanceView.updateChartFromStatic();
                });
                return;
            }

            switch (result.status) {
                case ALREADY_OPEN:
                    Platform.runLater(() -> showAlert(
                        result.childName + " is already checked in. Check-out now requires the parent QR scan in Teacher App.",
                        Alert.AlertType.INFORMATION
                    ));
                    break;
                case ALREADY_CLOSED:
                    Platform.runLater(() -> showAlert("Already checked out today for " + result.childName, Alert.AlertType.INFORMATION));
                    break;
                default:
                    Platform.runLater(() -> showAlert("Attendance update failed: " + result.reason, Alert.AlertType.ERROR));
                    break;
            }

        } catch (RuntimeException | java.io.IOException | InterruptedException e) {
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