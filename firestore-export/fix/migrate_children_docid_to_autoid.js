const admin = require('firebase-admin');

// One-time migration helper:
// Move from legacy children/{nfcUid} (docId = NFC UID) to children/{childId} (docId = auto-id)
// while storing NFC UID in a replaceable field: children.nfc_uid
//
// What it does:
// - For each legacy child doc where doc.id looks like an NFC UID:
//   - Create (or reuse) a new children doc with an auto-id and nfc_uid = legacy doc.id
//   - Mark the legacy doc with migratedToChildId so apps can follow redirects
// - Updates parents docs to replace legacy NFC IDs in childIds/childRefs/childId/childRef
// - Updates attendance docs to replace legacy NFC IDs in childId/childRef (doc id is left as-is)
//
// Usage:
//   node fix/migrate_children_docid_to_autoid.js            (dry-run)
//   node fix/migrate_children_docid_to_autoid.js --commit  (write changes)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function looksLikeNfcUid(id) {
  if (!id) return false;
  const s = String(id).trim();
  // NFC UIDs in this project have historically been hex-ish strings.
  // Firestore auto IDs contain many non-hex letters; this heuristic prevents accidental migration.
  return /^[0-9a-fA-F]{4,32}$/.test(s);
}

function childIdFromRef(ref) {
  if (!ref) return '';
  // Accept Firestore DocumentReference
  if (typeof ref === 'object' && typeof ref.path === 'string') {
    const m = ref.path.match(/\/children\/(.+)$/i);
    return m ? m[1] : '';
  }
  // Accept string paths
  const s = String(ref).trim();
  const m = s.match(/\/?children\/?([^/]+)$/i);
  return m ? m[1] : '';
}

function replaceChildIdInRef(ref, newChildId) {
  if (!ref) return ref;
  if (typeof ref === 'object' && typeof ref.path === 'string') {
    return db.collection('children').doc(newChildId);
  }
  const s = String(ref);
  if (s.includes('/children/')) {
    return s.replace(/\/children\/[^/]+$/i, `/children/${newChildId}`);
  }
  if (/^children\//i.test(s)) {
    return s.replace(/^children\/[^/]+$/i, `children/${newChildId}`);
  }
  // Unknown format; best effort: replace trailing id
  return `/children/${newChildId}`;
}

async function commitBatches(writes, commit) {
  const CHUNK = 450;
  let committed = 0;

  for (let i = 0; i < writes.length; i += CHUNK) {
    const chunk = writes.slice(i, i + CHUNK);
    if (!chunk.length) continue;

    if (commit) {
      const batch = db.batch();
      for (const w of chunk) {
        if (w.type === 'set') batch.set(w.ref, w.data, w.options);
        else if (w.type === 'update') batch.update(w.ref, w.data);
      }
      await batch.commit();
    }

    committed += chunk.length;
  }

  return committed;
}

async function main() {
  const commit = process.argv.includes('--commit');

  const childrenSnap = await db.collection('children').get();
  const childrenDocs = childrenSnap.docs.map((d) => ({ id: d.id, ref: d.ref, data: d.data() }));

  // Map legacy NFC UID (old doc id) -> target childId (new doc id)
  const legacyToNew = new Map();

  const writes = [];
  let candidates = 0;
  let reused = 0;
  let created = 0;
  let alreadyRedirect = 0;
  let skipped = 0;

  for (const c of childrenDocs) {
    const legacyId = c.id;
    const data = c.data || {};

    if (!looksLikeNfcUid(legacyId)) {
      skipped++;
      continue;
    }

    if (data.migratedToChildId) {
      alreadyRedirect++;
      continue;
    }

    const nfcUid = (data.nfc_uid ?? '').toString().trim();
    if (nfcUid && nfcUid !== legacyId) {
      // This looks like a modern doc already: auto-id with a separate nfc_uid.
      skipped++;
      continue;
    }

    candidates++;

    // If a modern child doc already exists with nfc_uid == legacyId, reuse it.
    const existingSnap = await db
      .collection('children')
      .where('nfc_uid', '==', legacyId)
      .get();

    let targetChildRef = null;
    for (const d of existingSnap.docs) {
      const dData = d.data() || {};
      if (d.id === legacyId) continue; // same legacy doc
      if (dData.migratedToChildId) continue; // redirect doc
      targetChildRef = d.ref;
      break;
    }

    if (targetChildRef) {
      reused++;
    } else {
      targetChildRef = db.collection('children').doc();
      const newData = {
        ...data,
        nfc_uid: legacyId,
        migratedFromNfcUid: legacyId,
        migratedAt: admin.firestore.FieldValue.serverTimestamp(),
      };
      // Do not carry over redirect pointer if present (should not be)
      delete newData.migratedToChildId;

      writes.push({ type: 'set', ref: targetChildRef, data: newData, options: { merge: true } });
      created++;
    }

    legacyToNew.set(legacyId, targetChildRef.id);

    // Mark legacy doc as redirect
    writes.push({
      type: 'update',
      ref: c.ref,
      data: {
        migratedToChildId: targetChildRef.id,
        migratedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
    });
  }

  // Update parents docs
  const parentsSnap = await db.collection('parents').get();
  let parentsTouched = 0;

  for (const p of parentsSnap.docs) {
    const data = p.data() || {};
    let changed = false;
    const patch = {};

    // Arrays: childIds
    if (Array.isArray(data.childIds)) {
      const next = data.childIds.map((x) => {
        const key = (x ?? '').toString();
        const mapped = legacyToNew.get(key);
        if (mapped) {
          changed = true;
          return mapped;
        }
        return x;
      });
      if (changed) patch.childIds = next;
    }

    // Arrays: childRefs
    if (Array.isArray(data.childRefs)) {
      const nextRefs = data.childRefs.map((r) => {
        const oldChildId = childIdFromRef(r);
        const mapped = legacyToNew.get(oldChildId);
        if (mapped) {
          changed = true;
          return replaceChildIdInRef(r, mapped);
        }
        return r;
      });
      if (changed) patch.childRefs = nextRefs;
    }

    // Legacy scalar: childId
    if (data.childId != null) {
      const key = String(data.childId).trim();
      const mapped = legacyToNew.get(key);
      if (mapped) {
        changed = true;
        patch.childId = mapped;
      }
    }

    // Legacy scalar: childRef
    if (data.childRef != null) {
      const oldChildId = childIdFromRef(data.childRef);
      const mapped = legacyToNew.get(oldChildId);
      if (mapped) {
        changed = true;
        patch.childRef = replaceChildIdInRef(data.childRef, mapped);
      }
    }

    if (changed) {
      parentsTouched++;
      writes.push({ type: 'update', ref: p.ref, data: patch });
    }
  }

  // Update attendance docs
  const attendanceSnap = await db.collection('attendance').get();
  let attendanceTouched = 0;

  for (const a of attendanceSnap.docs) {
    const data = a.data() || {};
    let changed = false;
    const patch = {};

    if (data.childId != null) {
      const key = String(data.childId).trim();
      const mapped = legacyToNew.get(key);
      if (mapped) {
        changed = true;
        patch.childId = mapped;
        patch.childRef = db.collection('children').doc(mapped);
        // Preserve NFC UID as an attribute so scans can still be traced.
        patch.nfc_uid = key;
      }
    }

    if (!changed && data.childRef != null) {
      const oldChildId = childIdFromRef(data.childRef);
      const mapped = legacyToNew.get(oldChildId);
      if (mapped) {
        changed = true;
        patch.childId = mapped;
        patch.childRef = db.collection('children').doc(mapped);
        patch.nfc_uid = oldChildId;
      }
    }

    if (changed) {
      attendanceTouched++;
      writes.push({ type: 'update', ref: a.ref, data: patch });
    }
  }

  console.log(`\nFound children docs: ${childrenDocs.length}`);
  console.log(`Legacy child candidates: ${candidates}`);
  console.log(`- New children created: ${created}${commit ? ' (committed)' : ' (dry-run)'}`);
  console.log(`- Existing children reused: ${reused}`);
  console.log(`- Already redirect docs skipped: ${alreadyRedirect}`);
  console.log(`- Skipped (non-legacy / safe-skip): ${skipped}`);
  console.log(`Parents docs touched: ${parentsTouched}`);
  console.log(`Attendance docs touched: ${attendanceTouched}`);

  const preview = Array.from(legacyToNew.entries()).slice(0, 20);
  if (preview.length) {
    console.log('\nPreview (first 20 legacy -> new):');
    for (const [from, to] of preview) {
      console.log(`- ${from} -> ${to}`);
    }
    if (legacyToNew.size > preview.length) {
      console.log(`... (${legacyToNew.size - preview.length} more)`);
    }
  }

  const committed = await commitBatches(writes, commit);
  console.log(`\nPlanned writes: ${writes.length}`);
  console.log(`Applied writes: ${committed}${commit ? '' : ' (dry-run only)'}`);
  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
