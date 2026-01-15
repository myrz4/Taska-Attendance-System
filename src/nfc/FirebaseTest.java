package nfc;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.util.List;

public class FirebaseTest {
    public static void main(String[] args) {
        try {
            // Initialize Firestore via your FirestoreService class
            FirestoreService.db();
            Firestore db = FirestoreService.db();
            System.out.println("✅ Firestore initialized successfully!");
            
            // Try reading documents from your 'children' collection
            ApiFuture<QuerySnapshot> future = db.collection("children").get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();

            System.out.println("Found " + docs.size() + " documents in 'children':");
            for (QueryDocumentSnapshot doc : docs) {
                System.out.println(" - " + doc.getId() + " => " + doc.getData());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}