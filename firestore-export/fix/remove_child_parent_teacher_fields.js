const admin = require('firebase-admin');

// One-time cleanup helper:
// Remove legacy “designated teacher” fields from children + parents.
//
// Usage:
//   node fix/remove_child_parent_teacher_fields.js            (dry-run)
//   node fix/remove_child_parent_teacher_fields.js --commit  (apply updates)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

const CHILD_FIELDS = ['teacher_username', 'teacherName', 'teacherRef'];
const PARENT_FIELDS = ['teacher', 'teacherName', 'teacherRef'];

function pickDeletions(data, fields) {
  const deletions = {};
  for (const f of fields) {
    if (Object.prototype.hasOwnProperty.call(data, f)) {
      deletions[f] = admin.firestore.FieldValue.delete();
    }
  }
  return deletions;
}

async function cleanCollection({ collection, fields, commit }) {
  const snap = await db.collection(collection).get();
  const docs = snap.docs;

  let touched = 0;
  let updated = 0;

  const preview = [];

  for (const d of docs) {
    const data = d.data() || {};
    const deletions = pickDeletions(data, fields);

    const keys = Object.keys(deletions);
    if (keys.length === 0) continue;

    touched++;
    preview.push({ id: d.id, fields: keys });

    if (commit) {
      await d.ref.set(deletions, { merge: true });
      updated++;
    }
  }

  return {
    total: docs.length,
    touched,
    updated: commit ? updated : 0,
    preview,
  };
}

async function main() {
  const commit = process.argv.includes('--commit');

  const children = await cleanCollection({
    collection: 'children',
    fields: CHILD_FIELDS,
    commit,
  });

  const parents = await cleanCollection({
    collection: 'parents',
    fields: PARENT_FIELDS,
    commit,
  });

  console.log(`\nChildren docs: ${children.total}`);
  console.log(
    `Will remove legacy teacher fields from: ${children.touched}` +
      (commit ? ` (updated: ${children.updated})` : ' (dry-run)'),
  );

  console.log(`\nParents docs: ${parents.total}`);
  console.log(
    `Will remove legacy teacher fields from: ${parents.touched}` +
      (commit ? ` (updated: ${parents.updated})` : ' (dry-run)'),
  );

  const combinedPreview = [...children.preview.map((p) => ({ c: 'children', ...p })), ...parents.preview.map((p) => ({ c: 'parents', ...p }))];

  if (combinedPreview.length) {
    console.log('\nPreview (first 50):');
    for (const p of combinedPreview.slice(0, 50)) {
      console.log(`- ${p.c}/${p.id}: delete [${p.fields.join(', ')}]`);
    }
    if (combinedPreview.length > 50) {
      console.log(`... (${combinedPreview.length - 50} more)`);
    }
  }

  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Cleanup failed:', e);
  process.exit(1);
});
