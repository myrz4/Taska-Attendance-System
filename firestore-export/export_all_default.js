const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

admin.initializeApp({
  credential: admin.credential.cert(require("./serviceAccountKey.json"))
});

const db = admin.firestore(); // ✅ (default) database

async function exportAllCollections() {
  const collections = await db.listCollections();

  if (!fs.existsSync("exports")) {
    fs.mkdirSync("exports");
  }

  for (const collection of collections) {
    const snapshot = await collection.get();
    const data = [];

    snapshot.forEach(doc => {
      data.push({
        id: doc.id,
        ...doc.data()
      });
    });

    const filePath = path.join("exports", `${collection.id}.json`);
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2));

    console.log(`✅ Exported: ${collection.id}`);
  }

  console.log("\n🎉 ALL collections from (default) exported successfully.");
}

exportAllCollections();