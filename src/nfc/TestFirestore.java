package nfc;

import com.google.cloud.firestore.*;
import java.util.concurrent.ExecutionException;

public class TestFirestore {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreService.db();

        System.out.println("DEBUG >> Scanning Firestore structure...");

        // List all root collections
        for (CollectionReference col : db.listCollections()) {
            System.out.println("ROOT COLLECTION: " + col.getId());

            // For each document inside the root collection
            for (DocumentReference docRef : col.listDocuments()) {
                System.out.println("  DOC: " + docRef.getId());

                // For each subcollection inside that document
                for (CollectionReference subCol : docRef.listCollections()) {
                    System.out.println("    SUBCOLLECTION: " + subCol.getId());
                }
            }
        }

        System.out.println("✅ Scan complete.");
    }
}