package nfc;

import java.io.FileInputStream;
import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

public class FirebaseConnection {

    private static Firestore db;

    public static Firestore getDB() {
        if (db == null) {
            try {
                FileInputStream serviceAccount =
                        new FileInputStream("target/dependency/serviceAccountKey.json");

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId("taskazurah")
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    System.out.println("🔥 Firebase initialized successfully.");
                }

                db = FirestoreOptions.getDefaultInstance().getService();
                System.out.println("🔥 Firestore connected successfully.");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return db;
    }
}