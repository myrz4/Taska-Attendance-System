package nfc;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class OpenCVLoader {
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;

        // Try common library names first (varies by version)
        List<String> libNames = Arrays.asList(
            // Windows (OpenCV 4.10.0 / 4.9.0 / 4.8.0 etc.)
            "opencv_java4100", "opencv_java490", "opencv_java480", "opencv_java470",
            // Linux/macOS often use "opencv_java4100" style too (maps to .so/.dylib)
            "opencv_java"
        );

        // 1) Try System.loadLibrary on common names
        for (String n : libNames) {
            try {
                System.loadLibrary(n);
                System.out.println("[OpenCV] Loaded via System.loadLibrary: " + n);
                loaded = true;
                return;
            } catch (Throwable ignore) {}
        }

        System.err.println("[OpenCV] System.loadLibrary failed for common names. Trying explicit paths…");

        // 2) Try explicit paths (no hardcoding to C:\\dev\\…)
        // Put your DLL/SO/DYLIB inside one of these folders to avoid editing code:
        //   ./opencv/bin, ./opencv, ./lib, ./native
        String base = System.getProperty("user.dir");
        String[] guessDirs = new String[] {
            base + File.separator + "opencv" + File.separator + "bin",
            base + File.separator + "opencv",
            base + File.separator + "lib",
            base + File.separator + "native"
        };

        for (String dir : guessDirs) {
            for (String n : libNames) {
                String f = dir + File.separator + libFileName(n);
                File dll = new File(f);
                if (dll.exists()) {
                    try {
                        System.load(dll.getAbsolutePath());
                        System.out.println("[OpenCV] Loaded via explicit path: " + dll);
                        loaded = true;
                        return;
                    } catch (Throwable t) {
                        System.err.println("[OpenCV] Failed loading " + dll + " -> " + t);
                    }
                }
            }
        }

        // 3) Respect OPENCV_DIR if set (e.g., OPENCV_DIR=C:\opencv\build\install\x64\vc17\bin)
        String envDir = System.getenv("OPENCV_DIR");
        if (envDir != null) {
            for (String n : libNames) {
                File dll = new File(envDir, libFileName(n));
                if (dll.exists()) {
                    try {
                        System.load(dll.getAbsolutePath());
                        System.out.println("[OpenCV] Loaded via OPENCV_DIR: " + dll);
                        loaded = true;
                        return;
                    } catch (Throwable t) {
                        System.err.println("[OpenCV] Failed loading via OPENCV_DIR " + dll + " -> " + t);
                    }
                }
            }
        }

        throw new UnsatisfiedLinkError("[OpenCV] Could not load native library. "
            + "Place the proper opencv_java*.dll/.so/.dylib in ./opencv/bin or set OPENCV_DIR.");
    }

    private static String libFileName(String base) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return base + ".dll";
        if (os.contains("mac")) return "lib" + base + ".dylib";
        return "lib" + base + ".so"; // Linux
    }
}