package nfc;

import com.fazecast.jSerialComm.SerialPort;

public class SerialTest {
  public static void main(String[] args) {
    for (SerialPort p : SerialPort.getCommPorts()) {
      System.out.println(p.getSystemPortName() + "  " + p.getDescriptivePortName());
    }
  }
}