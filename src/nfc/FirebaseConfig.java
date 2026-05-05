package nfc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

public final class FirebaseConfig {
    private FirebaseConfig() {
    }

    public static String readPropertyFromJarFiles(String fileName, String key) {
        try {
            File f = resolveJarFilesFile(fileName);
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

    private static File resolveJarFilesFile(String fileName) {
        File[] candidates = new File[] {
            new File("jar_files", fileName),
            new File("app/jar_files", fileName),
            resolveFromCodeSource(fileName)
        };

        for (File candidate : candidates) {
            if (candidate != null && candidate.exists()) {
                return candidate;
            }
        }

        return candidates[0];
    }

    private static File resolveFromCodeSource(String fileName) {
        try {
            URL location = FirebaseConfig.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;

            File codeSource = new File(location.toURI());
            File baseDir = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
            if (baseDir == null) return null;

            File sameDir = new File(baseDir, "jar_files/" + fileName);
            if (sameDir.exists()) {
                return sameDir;
            }

            File parentDir = baseDir.getParentFile();
            if (parentDir != null) {
                return new File(parentDir, "jar_files/" + fileName);
            }
        } catch (SecurityException | URISyntaxException ignored) {
            return null;
        }

        return null;
    }
}
