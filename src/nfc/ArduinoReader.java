package nfc;

import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;

public class ArduinoReader {
	public static SerialPort serialPort;

	public static void startReading() {
        String portName = NFCReader.resolveConfiguredPortName();
        if (portName == null) {
            System.out.println("ℹ️ Arduino reader disabled by configuration.");
            return;
        }

        if (!NFCReader.isPortAvailable(portName)) {
            System.out.println("ℹ️ Arduino reader not started. Port " + portName + " is unavailable. Available ports: " + NFCReader.availablePortsSummary());
            return;
        }

        SerialPort comPort = SerialPort.getCommPort(portName);
        comPort.setBaudRate(115200);

        if (comPort.openPort()) {
            System.out.println("✅ Arduino connected!");

            Scanner scanner = new Scanner(comPort.getInputStream());
            new Thread(() -> {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    System.out.println("📥 Arduino says: " + line);

                    if (line.startsWith("NFC Tag Detected:")) {
                        String uid = line.replace("NFC Tag Detected:", "").trim();
                        System.out.println("🎯 UID Read: " + uid);
                        saveAttendance(uid);
                    }
                }
            }).start();

        } else {
            System.out.println("❌ Cannot open Arduino COM port.");
        }
    }
	public static void safelyCloseSerialPort() {
	    if (serialPort != null) {
	        if (serialPort.isOpen()) {
	            serialPort.closePort();
	            System.out.println("✅ Serial port closed successfully!");
	        }
	        serialPort = null; // clear from memory
	    }
	}

    private static void saveAttendance(String uid) {
        System.out.println("⚠️ MySQL code disabled — Firestore integration coming soon.");
    }
}