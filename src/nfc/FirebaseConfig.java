package nfc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public final class FirebaseConfig {
    private FirebaseConfig() {
    }

    public static String readPropertyFromJarFiles(String fileName, String key) {
        try {
            File f = new File("jar_files/" + fileName);
            if (!f.exists()) return null;

            Properties props = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
            }
            return props.getProperty(key);
        } catch (java.io.IOException | SecurityException ignored) {
            return null;
        }
    }
}
