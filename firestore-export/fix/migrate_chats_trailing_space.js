const admin = require('firebase-admin');

// Chat maintenance / migration:
// 1) Migrates legacy collection `chats ` (note trailing space) -> `chats`.
// 2) Normalizes `/chats` doc IDs to stable format based on teacherId + parentId
//    extracted from teacherRef + parentRef.
//
// Stable doc id format:
//   teacher_<teacherId>_parent_<parentId>
// where teacherId and parentId are Firestore document IDs.
//
// Usage:
//   node fix/migrate_chats_trailing_space.js               (dry-run)
//   node fix/migrate_chats_trailing_space.js --commit      (copy)
//   node fix/migrate_chats_trailing_space.js --commit --deleteLegacy  (copy + delete legacy)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

const LEGACY = 'chats ';
const CANON = 'chats';

function norm(s) {
  return (s || '').toString().trim().toLowerCase();
}

function extractIdFromRef(ref) {
  if (!ref) return '';
  const s = ref.toString().trim();
  // Accept "/parents/abc" or "parents/abc" etc.
  const parts = s.replace(/^\//, '').split('/').filter(Boolean);
  if (parts.length >= 2) return parts[1];
  return '';
}

function stableChatId(teacherId, parentId) {
  return `teacher_${norm(teacherId)}_parent_${norm(parentId)}`;
}

function hasFlag(name) {
  return process.argv.includes(name);
}

async function copyMessagesSubcollection(legacyChatRef, canonChatRef, commit) {
  const msgsSnap = await legacyChatRef.collection('messages').get();
  if (msgsSnap.empty) return { copied: 0 };

  let copied = 0;
  const docs = msgsSnap.docs;

  // Batch writes (<= 500 ops)
  const CHUNK = 400;
  for (let i = 0; i < docs.length; i += CHUNK) {
    const batch = db.batch();
    const slice = docs.slice(i, i + CHUNK);
    for (const d of slice) {
      const canonMsgRef = canonChatRef.collection('messages').doc(d.id);
      batch.set(canonMsgRef, d.data(), { merge: true });
    }
    if (commit) {
      await batch.commit();
    }
    copied += slice.length;
  }

  return { copied };
}

async function upsertChatDoc(canonRef, sourceData, commit) {
  if (!commit) return;
  await canonRef.set(sourceData || {}, { merge: true });
}

async function migrateCollection(legacyCollectionName, commit, deleteLegacy) {
  const legacySnap = await db.collection(legacyCollectionName).get();
  console.log(`Legacy chats found in "${legacyCollectionName}": ${legacySnap.size}`);

  if (!legacySnap.size) {
    return { migratedChats: 0, migratedMsgs: 0 };
  }

  let plannedChats = 0;
  let plannedMsgs = 0;

  for (const chatDoc of legacySnap.docs) {
    plannedChats++;
    const legacyRef = chatDoc.ref;
    const canonRef = db.collection(CANON).doc(chatDoc.id);
    const msgSnap = await legacyRef.collection('messages').get();
    plannedMsgs += msgSnap.size;
    const data = chatDoc.data() || {};
    const teacherRef = data.teacherRef || '';
    const parentRef = data.parentRef || '';
    console.log(`- ${legacyCollectionName}/${chatDoc.id} -> ${CANON}/${chatDoc.id}  (messages=${msgSnap.size})  refs=[${teacherRef}, ${parentRef}]`);
  }

  console.log(`\nPlanned from "${legacyCollectionName}": chats=${plannedChats}, messages=${plannedMsgs}`);

  if (!commit) {
    return { migratedChats: 0, migratedMsgs: 0 };
  }

  let migratedChats = 0;
  let migratedMsgs = 0;

  for (const chatDoc of legacySnap.docs) {
    const legacyRef = chatDoc.ref;
    const canonRef = db.collection(CANON).doc(chatDoc.id);

    await upsertChatDoc(canonRef, chatDoc.data(), true);

    const { copied } = await copyMessagesSubcollection(legacyRef, canonRef, true);
    migratedMsgs += copied;

    if (deleteLegacy) {
      // delete messages first
      const msgsSnap = await legacyRef.collection('messages').get();
      const docs = msgsSnap.docs;
      const CHUNK = 400;
      for (let i = 0; i < docs.length; i += CHUNK) {
        const batch = db.batch();
        for (const d of docs.slice(i, i + CHUNK)) {
          batch.delete(d.ref);
        }
        await batch.commit();
      }
      await legacyRef.delete();
    }

    migratedChats++;
    if (migratedChats % 10 === 0) {
      console.log(`Migrated ${migratedChats}/${plannedChats} docs from "${legacyCollectionName}"...`);
    }
  }

  console.log(`Done migrating "${legacyCollectionName}" -> "${CANON}": chats=${migratedChats}, messages=${migratedMsgs}.`);
  return { migratedChats, migratedMsgs };
}

async function normalizeCanonDocIds(commit, deleteLegacy) {
  const snap = await db.collection(CANON).get();
  console.log(`\nScanning "${CANON}" for docId normalization: ${snap.size} chat docs`);

  let planned = 0;
  let normalized = 0;
  let copiedMsgs = 0;

  for (const d of snap.docs) {
    const data = d.data() || {};
    const teacherId = data.teacherId || extractIdFromRef(data.teacherRef) || data.teacherUsername || '';
    const parentId = data.parentId || extractIdFromRef(data.parentRef) || '';

    if (!teacherId || !parentId) {
      continue;
    }

    const desiredId = stableChatId(teacherId, parentId);
    if (desiredId === d.id) {
      // Ensure metadata has ids/usernames for newer clients.
      if (commit) {
        await d.ref.set(
          {
            teacherId: teacherId,
            teacherUsername: norm(teacherId),
            parentId: parentId,
            parentUsername: data.parentUsername || data.parentName ? norm(data.parentUsername || data.parentName) : undefined,
            teacherRef: data.teacherRef || `/teachers/${teacherId}`,
            parentRef: data.parentRef || `/parents/${parentId}`,
          },
          { merge: true },
        );
      }
      continue;
    }

    planned++;
    console.log(`- normalize ${CANON}/${d.id} -> ${CANON}/${desiredId}`);

    if (!commit) continue;

    const targetRef = db.collection(CANON).doc(desiredId);
    await targetRef.set(
      {
        ...data,
        teacherId: teacherId,
        teacherUsername: norm(teacherId),
        parentId: parentId,
        parentRef: data.parentRef || `/parents/${parentId}`,
        teacherRef: data.teacherRef || `/teachers/${teacherId}`,
      },
      { merge: true },
    );

    const { copied } = await copyMessagesSubcollection(d.ref, targetRef, true);
    copiedMsgs += copied;

    if (deleteLegacy) {
      // delete messages then doc
      const msgsSnap = await d.ref.collection('messages').get();
      const docs = msgsSnap.docs;
      const CHUNK = 400;
      for (let i = 0; i < docs.length; i += CHUNK) {
        const batch = db.batch();
        for (const md of docs.slice(i, i + CHUNK)) {
          batch.delete(md.ref);
        }
        await batch.commit();
      }
      await d.ref.delete();
    }

    normalized++;
  }

  if (!commit) {
    console.log(`Planned normalizations: ${planned} (dry-run only)`);
    return;
  }
  console.log(`Normalized chat docs: ${normalized}, messages copied: ${copiedMsgs}`);
}

async function main() {
  const commit = hasFlag('--commit');
  const deleteLegacy = hasFlag('--deleteLegacy');

  // 1) Migrate legacy trailing-space collection into canon.
  const res = await migrateCollection(LEGACY, commit, deleteLegacy);

  if (!commit && res.migratedChats === 0) {
    console.log('\nDry-run mode: re-run with --commit to apply changes.');
  }

  // 2) Normalize doc ids inside canon.
  await normalizeCanonDocIds(commit, deleteLegacy);

  console.log('\nAll done.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
