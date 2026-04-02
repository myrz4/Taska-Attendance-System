package nfc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fazecast.jSerialComm.SerialPort;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public class NFCReader implements Runnable {
    private static final String DEFAULT_PORT = "auto";

    private SerialPort serialPort;
    private final String requestedPortName;
    private volatile boolean running = true;
    private long lastWaitLogMs = 0L;

    private static void logError(String context, Exception error) {
        System.err.println("NFCReader: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    public NFCReader(String portName) {
        this.requestedPortName = portName;
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

    public static boolean isAutoPortSelection(String portName) {
        return portName == null || portName.isBlank() || "auto".equalsIgnoreCase(portName.trim());
    }

    public static String resolveUsablePortName(String requestedPortName) {
        if (requestedPortName == null) {
            return null;
        }

        String configured = requestedPortName.trim();
        if (configured.isEmpty()) {
            configured = DEFAULT_PORT;
        }
        if (configured.equalsIgnoreCase("disabled") || configured.equalsIgnoreCase("none")) {
            return null;
        }
        if (isAutoPortSelection(configured)) {
            return detectPreferredPortName();
        }

        for (SerialPort port : SerialPort.getCommPorts()) {
            String systemName = String.valueOf(port.getSystemPortName());
            String descriptiveName = String.valueOf(port.getDescriptivePortName());
            if (configured.equalsIgnoreCase(systemName) || configured.equalsIgnoreCase(descriptiveName)) {
                return systemName;
            }
        }
        return null;
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
            names.add(String.valueOf(port.getSystemPortName()) + " (" + String.valueOf(port.getDescriptivePortName()) + ")");
        }
        return String.join(", ", names);
    }

    private static String detectPreferredPortName() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports == null || ports.length == 0) {
            return null;
        }

        SerialPort fallback = null;
        for (SerialPort port : ports) {
            String systemName = String.valueOf(port.getSystemPortName()).toLowerCase(Locale.ROOT);
            String descriptiveName = String.valueOf(port.getDescriptivePortName()).toLowerCase(Locale.ROOT);
            String combined = systemName + " " + descriptiveName;
            if (fallback == null) {
                fallback = port;
            }
            if (combined.contains("cp210")
                || combined.contains("ch340")
                || combined.contains("usb serial")
                || combined.contains("arduino")
                || combined.contains("esp32")
                || combined.contains("uart")) {
                return String.valueOf(port.getSystemPortName());
            }
        }
        return fallback == null ? null : String.valueOf(fallback.getSystemPortName());
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            String activePortName = resolveUsablePortName(requestedPortName);
            if (activePortName == null) {
                if (!isAutoPortSelection(requestedPortName)) {
                    logWaiting("Configured NFC port unavailable: " + requestedPortName + ". Available ports: " + availablePortsSummary());
                } else {
                    logWaiting("Waiting for USB NFC scanner. Available ports: " + availablePortsSummary());
                }
                sleepQuietly(1500);
                continue;
            }

            serialPort = SerialPort.getCommPort(activePortName);
            serialPort.setBaudRate(115200);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
            serialPort.setParity(0);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);

            if (serialPort.openPort()) {
                System.out.println("✅ Serial port " + activePortName + " opened successfully!");
            } else {
                System.out.println("❌ Failed to open serial port " + activePortName + "!");
                sleepQuietly(1500);
                continue;
            }

            System.out.println("📡 Listening for NFC tags...");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(serialPort.getInputStream(), StandardCharsets.UTF_8))) {

                while (running && serialPort != null && serialPort.isOpen()) {
                    String line = in.readLine();
                    if (line == null) {
                        continue;
                    }

                    String tagId = extractUidFromLine(line);
                    if (tagId == null || tagId.isBlank()) {
                        if (!line.isBlank()) {
                            System.out.println("📥 NFC serial: " + line.trim());
                        }
                        continue;
                    }

                    System.out.println("🏷️ Tag detected: " + tagId);
                    processTag(tagId);
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

            sleepQuietly(300);
        }
    }

    public void stopReading() {
        running = false;
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("✅ Serial port closed successfully.");
        }
    }

    private void logWaiting(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWaitLogMs >= 4000) {
            System.out.println("ℹ️ " + message);
            lastWaitLogMs = now;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractUidFromLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] prefixes = {"TAG:", "UID:", "NFC Tag Detected:", "Card UID:", "📇 Card UID:"};
        for (String prefix : prefixes) {
            int idx = trimmed.indexOf(prefix);
            if (idx >= 0) {
                String candidate = trimmed.substring(idx + prefix.length()).trim();
                String normalized = normalizeUidCandidate(candidate);
                if (normalized != null) {
                    return normalized;
                }
            }
        }

        return normalizeUidCandidate(trimmed);
    }

    private String normalizeUidCandidate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        if (normalized.length() < 8 || normalized.length() > 40 || (normalized.length() % 2) != 0) {
            return null;
        }
        return normalized;
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