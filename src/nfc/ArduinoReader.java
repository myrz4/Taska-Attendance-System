package nfc;

import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;

public class ArduinoReader {
	public static SerialPort serialPort;

	public static void startReading() {
        SerialPort comPort = SerialPort.getCommPort("COM3"); // <-- set to your Arduino COM
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