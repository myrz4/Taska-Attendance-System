const admin = require("firebase-admin");
const fs = require("fs");

// Initialize Firebase Admin
admin.initializeApp({
  credential: admin.credential.cert(require("./serviceAccountKey.json"))
});

const db = admin.firestore();

async function exportCollection(collectionName) {
  const snapshot = await db.collection(collectionName).get();
  const data = [];

  snapshot.forEach(doc => {
    data.push({
      id: doc.id,
      ...doc.data()
    });
  });

  fs.writeFileSync(
    `${collectionName}.json`,
    JSON.stringify(data, null, 2)
  );

  console.log(`✅ ${collectionName} exported (${data.length} docs)`);
}

(async () => {
  await exportCollection("admins");
  await exportCollection("teachers");
  await exportCollection("parents");
  await exportCollection("children");
})();