package nfc;

import org.opencv.core.Core;

public class TestOpenCV {
    public static void main(String[] args) {
        System.load("C:\\dev\\opencv\\build\\install\\java\\opencv_java4100.dll");
        System.out.println("OpenCV: " + Core.getVersionString());
    }
}