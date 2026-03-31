const admin = require('firebase-admin');

// One-time migration helper:
// Move from legacy teachers/{username} (docId = username) to teachers/{teacherId} (docId = auto-id)
// while keeping username as a unique field: teachers.username
//
// What it does:
// - Groups all teacher docs by normalized username (from `data.username` or docId fallback)
// - Chooses/creates a canonical auto-id doc per username
// - Merges fields (keeps fcmToken/image/password_hash/etc) into the canonical doc
// - Marks legacy docs with migratedToTeacherId (and optionally deletes them)
// - Removes deprecated fields: base_salary/experience/join_date/status
//
// Usage:
//   node fix/migrate_teachers_docid_to_autoid.js                 (dry-run)
//   node fix/migrate_teachers_docid_to_autoid.js --commit        (write changes)
//   node fix/migrate_teachers_docid_to_autoid.js --commit --deleteLegacy

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function normUsername(u) {
  return (u || '').toString().trim().toLowerCase();
}

function isLegacyId(id) {
  if (!id) return false;
  return /^t\d+$/i.test(id) || /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
}

function profileScore(data) {
  if (!data) return 0;
  const keys = ['name', 'username', 'phone', 'email', 'class', 'image', 'password_hash', 'fcmToken', 'tips_total'];
  return keys.reduce((acc, k) => acc + (data[k] != null ? 1 : 0), 0);
}

function mergePreferNonEmpty(base, extra) {
  const out = { ...(base || {}) };
  for (const [k, v] of Object.entries(extra || {})) {
    if (v == null) continue;
    if (typeof v === 'string' && v.trim() === '') continue;
    // Prefer already-present non-empty values.
    if (out[k] == null || (typeof out[k] === 'string' && out[k].trim() === '')) {
      out[k] = v;
    }
  }
  return out;
}

async function main() {
  const commit = process.argv.includes('--commit');
  const deleteLegacy = process.argv.includes('--deleteLegacy');

  const snap = await db.collection('teachers').get();
  const docs = snap.docs.map((d) => ({ id: d.id, ref: d.ref, data: d.data() || {} }));

  // Group docs by username
  const groups = new Map();
  for (const d of docs) {
    const username = normUsername(d.data.username || (!isLegacyId(d.id) ? d.id : ''));
    if (!username) continue;
    if (!groups.has(username)) groups.set(username, []);
    groups.get(username).push(d);
  }

  const planned = [];
  const legacyDeletes = [];

  for (const [username, list] of groups.entries()) {
    // Decide if an auto-id canonical doc already exists for this username
    const existingAuto = list
      .filter((d) => normUsername(d.data.username) === username)
      .find((d) => d.id !== username && !isLegacyId(d.id));

    let canonicalRef;
    let canonicalId;

    if (existingAuto) {
      canonicalRef = existingAuto.ref;
      canonicalId = existingAuto.id;
    } else {
      canonicalRef = db.collection('teachers').doc();
      canonicalId = canonicalRef.id;
    }

    // Pick best profile doc for overrides
    let best = null;
    for (const d of list) {
      const score = profileScore(d.data);
      if (!best || score > best.score) best = { d, score };
    }

    // Merge: start with best profile, then fill missing from others (e.g. fcmToken)
    let merged = { ...(best ? best.d.data : {}) };
    for (const d of list) {
      merged = mergePreferNonEmpty(merged, d.data);
    }

    // Canonical fields
    merged.username = username;

    // Remove deprecated fields
    delete merged.base_salary;
    delete merged.experience;
    delete merged.join_date;
    delete merged.status;

    // Write canonical doc
    planned.push({ type: 'set', to: canonicalId, from: list.map((x) => x.id), username });
    if (commit) {
      await canonicalRef.set(merged, { merge: true });
    }

    // Mark legacy docs (including username-docId) as migrated
    for (const d of list) {
      if (d.id === canonicalId) continue;
      planned.push({ type: 'update', id: d.id, migratedTo: canonicalId });
      if (commit) {
        await d.ref.set(
          {
            migratedToTeacherId: canonicalId,
            migratedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      }

      // Optionally delete only old-style IDs (username-docId or tXX/uuid)
      if (deleteLegacy && (d.id === username || isLegacyId(d.id))) {
        legacyDeletes.push(d.id);
        if (commit) {
          await d.ref.delete();
        }
      }
    }
  }

  console.log(`\nFound ${docs.length} teacher docs`);
  console.log(`Usernames detected: ${groups.size}`);
  console.log(`Planned ops: ${planned.length}${commit ? ' (committed)' : ' (dry-run)'}`);

  console.log('\nPreview (first 50):');
  for (const op of planned.slice(0, 50)) {
    if (op.type === 'set') {
      console.log(`- upsert teachers/${op.to}  (username=${op.username})  <= merged from [${op.from.join(', ')}]`);
    } else {
      console.log(`- mark teachers/${op.id} -> migratedToTeacherId=${op.migratedTo}`);
    }
  }
  if (planned.length > 50) console.log(`... (${planned.length - 50} more)`);

  if (deleteLegacy) {
    console.log(`\nLegacy deletes: ${legacyDeletes.length}${commit ? ' (committed)' : ' (dry-run)'}`);
    for (const id of legacyDeletes.slice(0, 50)) {
      console.log(`- delete teachers/${id}`);
    }
    if (legacyDeletes.length > 50) console.log(`... (${legacyDeletes.length - 50} more)`);
  }

  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
