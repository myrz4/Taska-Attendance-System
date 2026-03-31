const admin = require('firebase-admin');

// One-time migration helper:
// Create relationship documents so 1 parent can link to many children,
// and 1 child can link to many parents (mother/father/guardian).
//
// Source (legacy): parents/{parentId} has fields like childId / childRef / childName.
// Target (new): family_links/{parentId}__{childId}
//
// Usage:
//   node fix/migrate_parent_child_links.js            (dry-run)
//   node fix/migrate_parent_child_links.js --commit  (write docs)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function normalizePhone(raw) {
  if (raw == null) return '';
  return String(raw).trim().replace(/\s+/g, '');
}

function childIdFromChildRef(childRef) {
  if (!childRef) return '';
  const s = String(childRef).trim();
  // Accept "/children/<id>" or "children/<id>"
  const m = s.match(/\/?children\/?([^/]+)$/i);
  return m ? m[1] : '';
}

function makeLinkId(parentId, childId) {
  // Deterministic + idempotent.
  // Use a separator that is unlikely to appear in ids.
  return `${parentId}__${childId}`;
}

async function main() {
  const commit = process.argv.includes('--commit');

  const snap = await db.collection('parents').get();
  const parents = snap.docs.map((d) => ({ id: d.id, ref: d.ref, data: d.data() }));

  let planned = 0;
  let skipped = 0;
  let alreadyExists = 0;

  const previews = [];

  for (const p of parents) {
    const parentId = p.id;
    const data = p.data || {};

    const parentPhone = normalizePhone(data.phone);
    const parentName = (data.parentName ?? '').toString().trim();

    const legacyChildId = (data.childId ?? '').toString().trim();
    const legacyChildRef = (data.childRef ?? '').toString().trim();
    const childId = legacyChildId || childIdFromChildRef(legacyChildRef);

    if (!childId) {
      skipped++;
      continue;
    }

    const linkId = makeLinkId(parentId, childId);
    const linkRef = db.collection('family_links').doc(linkId);

    const linkSnap = await linkRef.get();
    if (linkSnap.exists) {
      alreadyExists++;
      continue;
    }

    planned++;

    const childName = (data.childName ?? '').toString().trim();
    const teacher = (data.teacher ?? '').toString().trim();

    const payload = {
      parentId,
      parentRef: `/parents/${parentId}`,
      parentPhone: parentPhone || null,
      parentName: parentName || null,
      childId,
      childRef: `/children/${childId}`,
      childName: childName || null,
      teacher: teacher || null,

      // We cannot safely infer mother/father from legacy docs.
      // Default to guardian; admin can edit later.
      role: 'guardian',

      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      migratedFrom: 'parents.childId/childRef',
    };

    previews.push({
      parentId,
      parentPhone,
      childId,
      linkId,
      role: payload.role,
    });

    if (commit) {
      await linkRef.set(payload, { merge: true });
    }
  }

  console.log(`\nFound ${parents.length} parents docs`);
  console.log(
    `Planned new family_links: ${planned}${commit ? ' (committed)' : ' (dry-run)'}`,
  );
  console.log(`Skipped (no childId/childRef): ${skipped}`);
  console.log(`Already existed: ${alreadyExists}`);

  if (previews.length) {
    console.log('\nPreview (first 50):');
    for (const p of previews.slice(0, 50)) {
      console.log(
        `- family_links/${p.linkId}: parent=${p.parentId} phone=${p.parentPhone || '-'} child=${p.childId} role=${p.role}`,
      );
    }
    if (previews.length > 50) console.log(`... (${previews.length - 50} more)`);
  }

  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
