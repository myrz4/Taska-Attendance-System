const admin = require('firebase-admin');

// Backfill helper:
// Populate children docs with parent info based on existing parent->child links.
//
// Writes (per child):
// - parentIds: string[]
// - parentNames: string[]
// - parentPhones: string[]
// - parentName: string (joined with \n for convenience)
// - parentContact: string (joined with \n for convenience)
//
// Usage:
//   node fix/backfill_children_parent_cache.js            (dry-run)
//   node fix/backfill_children_parent_cache.js --commit  (write docs)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function safeStr(v) {
  return v == null ? '' : String(v).trim();
}

function extractChildIdFromRef(ref) {
  if (!ref) return '';
  if (typeof ref === 'object' && typeof ref.path === 'string') {
    const m = ref.path.match(/\/children\/(.+)$/i);
    return m ? m[1] : '';
  }
  const s = String(ref).trim();
  const m = s.match(/\/?children\/?([^/]+)$/i);
  return m ? m[1] : '';
}

async function resolveChildDocId(childIdMaybe) {
  const id = safeStr(childIdMaybe);
  if (!id) return null;

  const direct = await db.collection('children').doc(id).get();
  if (direct.exists) return direct.id;

  // Fallback: if parent points to legacy NFC UID, find modern child by nfc_uid.
  const qs = await db
    .collection('children')
    .where('nfc_uid', '==', id)
    .get();

  for (const d of qs.docs) {
    const data = d.data() || {};
    if (data.migratedToChildId) continue; // redirect doc
    return d.id;
  }

  return null;
}

async function main() {
  const commit = process.argv.includes('--commit');

  const parentsSnap = await db.collection('parents').get();

  // childDocId -> parent summary list
  const childToParents = new Map();

  for (const p of parentsSnap.docs) {
    const data = p.data() || {};
    const parentId = p.id;
    const parentName = safeStr(data.parentName);
    const parentPhone = safeStr(data.phone);

    let rawChildIds = [];

    if (Array.isArray(data.childIds) && data.childIds.length) {
      rawChildIds = data.childIds.map((x) => safeStr(x)).filter(Boolean);
    } else if (data.childId) {
      rawChildIds = [safeStr(data.childId)].filter(Boolean);
    } else if (Array.isArray(data.childRefs) && data.childRefs.length) {
      rawChildIds = data.childRefs
        .map((r) => extractChildIdFromRef(r))
        .map((x) => safeStr(x))
        .filter(Boolean);
    } else if (data.childRef) {
      const id = extractChildIdFromRef(data.childRef);
      if (id) rawChildIds = [id];
    }

    for (const rawChildId of rawChildIds) {
      // Resolve to actual child docId (handles legacy pointers)
      // eslint-disable-next-line no-await-in-loop
      const childDocId = await resolveChildDocId(rawChildId);
      if (!childDocId) continue;

      const list = childToParents.get(childDocId) || [];
      list.push({ parentId, parentName, parentPhone });
      childToParents.set(childDocId, list);
    }
  }

  const writes = [];
  const previews = [];

  for (const [childDocId, parents] of childToParents.entries()) {
    // de-dup by parentId
    const seen = new Set();
    const uniq = [];
    for (const p of parents) {
      if (!p.parentId) continue;
      if (seen.has(p.parentId)) continue;
      seen.add(p.parentId);
      uniq.push(p);
    }

    // Stable ordering for readability
    uniq.sort((a, b) => (a.parentName || a.parentId).localeCompare(b.parentName || b.parentId));

    const parentIds = uniq.map((p) => p.parentId);
    const parentNames = uniq.map((p) => p.parentName).filter(Boolean);
    const parentPhones = uniq.map((p) => p.parentPhone).filter(Boolean);

    const parentNameJoined = parentNames.length ? parentNames.join('\n') : '';
    const parentPhoneJoined = parentPhones.length ? parentPhones.join('\n') : '';

    const patch = {
      parentIds,
      parentNames,
      parentPhones,
      parentName: parentNameJoined,
      parentContact: parentPhoneJoined,
      parentCacheUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      parentCacheSource: 'parents.childIds/childRefs',
    };

    previews.push({ childDocId, parentIds, parentNames, parentPhones });

    writes.push({ ref: db.collection('children').doc(childDocId), data: patch });
  }

  console.log(`\nParents scanned: ${parentsSnap.size}`);
  console.log(`Children to update (unique): ${writes.length}`);

  if (previews.length) {
    console.log('\nPreview (first 20):');
    for (const p of previews.slice(0, 20)) {
      console.log(`- children/${p.childDocId}: parents=${p.parentIds.join(', ') || '-'}`);
    }
    if (previews.length > 20) console.log(`... (${previews.length - 20} more)`);
  }

  if (!commit) {
    console.log('\nDry-run only. Use --commit to write changes.');
    return;
  }

  // Batch commits (<= 500 ops)
  const CHUNK = 450;
  let committed = 0;
  for (let i = 0; i < writes.length; i += CHUNK) {
    const chunk = writes.slice(i, i + CHUNK);
    const batch = db.batch();
    for (const w of chunk) {
      batch.set(w.ref, w.data, { merge: true });
    }
    // eslint-disable-next-line no-await-in-loop
    await batch.commit();
    committed += chunk.length;
  }

  console.log(`\nCommitted child updates: ${committed}`);
  console.log('Done.');
}

main().catch((e) => {
  console.error('Backfill failed:', e);
  process.exit(1);
});
