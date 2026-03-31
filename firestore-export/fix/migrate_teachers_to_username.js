const admin = require('firebase-admin');

// One-time migration helper:
// - Option A: canonical teacher document id is the username (lowercase)
// - Merges legacy docs like teachers/t01 -> teachers/sofea
// - Keeps token-only docs (teachers/sofea with fcmToken) and merges profile fields into them
//
// Usage:
//   node fix/migrate_teachers_to_username.js            (dry-run)
//   node fix/migrate_teachers_to_username.js --commit  (write merges)
//   node fix/migrate_teachers_to_username.js --commit --deleteLegacy

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function normUsername(u) {
  return (u || '').toString().trim().toLowerCase();
}

function isLegacyId(id) {
  return /^t\d+$/i.test(id) || /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
}

function profileScore(data) {
  if (!data) return 0;
  const keys = [
    'name',
    'username',
    'phone',
    'email',
    'class',
    'experience',
    'join_date',
    'image',
    'password_hash',
    'base_salary',
    'tips_total',
  ];
  return keys.reduce((acc, k) => acc + (data[k] != null ? 1 : 0), 0);
}

async function main() {
  const commit = process.argv.includes('--commit');
  const deleteLegacy = process.argv.includes('--deleteLegacy');

  const snap = await db.collection('teachers').get();
  const docs = snap.docs.map((d) => ({ id: d.id, data: d.data() }));

  // Choose the best profile doc per username.
  const bestByUsername = new Map();

  for (const { id, data } of docs) {
    const username = normUsername(data.username || (!isLegacyId(id) ? id : ''));
    if (!username) continue;

    const score = profileScore(data);
    if (score === 0) continue;

    const existing = bestByUsername.get(username);
    if (!existing || existing.score < score) {
      bestByUsername.set(username, { id, data, score });
    }
  }

  const ops = [];
  const legacyToDelete = [];

  for (const [username, best] of bestByUsername.entries()) {
    const canonicalId = username;
    const canonicalRef = db.collection('teachers').doc(canonicalId);

    const canonicalSnap = await canonicalRef.get();
    const canonicalData = canonicalSnap.exists ? canonicalSnap.data() : null;

    const merged = {
      ...(best.data || {}),
      ...(canonicalData || {}),
      username: canonicalId,
    };

    const willWrite = !canonicalSnap.exists || JSON.stringify(merged) !== JSON.stringify({ ...(canonicalData || {}), username: canonicalId });

    if (willWrite) {
      ops.push({ type: 'set', id: canonicalId, from: best.id, mergeKeys: Object.keys(merged) });
      if (commit) {
        await canonicalRef.set(merged, { merge: true });
      }
    }

  }

  // Delete legacy docs (tXX/UUID) once canonical docs exist.
  // Important: After merges, canonical docs may become the "best" profile doc;
  // deletion must not rely on `best.id`.
  if (deleteLegacy) {
    for (const { id, data } of docs) {
      if (!isLegacyId(id)) continue;
      const username = normUsername((data && data.username) || '');
      if (!username) continue;

      const canonicalId = username;
      if (canonicalId === id) continue;

      const canonicalSnap = await db.collection('teachers').doc(canonicalId).get();
      if (!canonicalSnap.exists) continue;

      legacyToDelete.push(id);
      if (commit) {
        await db.collection('teachers').doc(id).delete();
      }
    }
  }

  console.log(`\nFound ${docs.length} teacher docs`);
  console.log(`Canonical usernames detected: ${bestByUsername.size}`);
  console.log(`Planned writes: ${ops.length}${commit ? ' (committed)' : ' (dry-run)'}`);

  if (ops.length) {
    console.log('\nWrites preview:');
    for (const op of ops.slice(0, 50)) {
      console.log(`- teachers/${op.id}  <= merge from ${op.from}`);
    }
    if (ops.length > 50) console.log(`... (${ops.length - 50} more)`);
  }

  if (deleteLegacy) {
    console.log(`\nLegacy deletes: ${legacyToDelete.length}${commit ? ' (committed)' : ' (dry-run)'}`);
    for (const id of legacyToDelete.slice(0, 50)) {
      console.log(`- delete teachers/${id}`);
    }
    if (legacyToDelete.length > 50) console.log(`... (${legacyToDelete.length - 50} more)`);
  }

  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
