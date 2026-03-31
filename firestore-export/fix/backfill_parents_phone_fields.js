const admin = require('firebase-admin');

// One-time migration helper:
// Backfill / normalize parents.phone, parents.phoneTail, parents.phoneE164
//
// Usage:
//   node fix/backfill_parents_phone_fields.js            (dry-run)
//   node fix/backfill_parents_phone_fields.js --commit  (write updates)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function digitsOnly(input) {
  return String(input || '').replace(/[^0-9]/g, '');
}

function phoneLocalDigitsFromAny(phone) {
  const digits = digitsOnly(phone);
  if (!digits) return '';
  if (digits.startsWith('60') && digits.length > 2) return '0' + digits.slice(2);
  if (digits.startsWith('0')) return digits;
  if (digits.startsWith('1')) return '0' + digits;
  return digits;
}

function myTail(phoneAny) {
  let d = digitsOnly(phoneAny);
  if (!d) return '';
  if (d.startsWith('60') && d.length > 2) d = d.slice(2);
  if (d.startsWith('0') && d.length > 1) d = d.slice(1);
  return d;
}

function toE164My(phoneAny) {
  const local = phoneLocalDigitsFromAny(phoneAny);
  if (!local || !local.startsWith('0') || local.length < 9) return '';
  return '+60' + local.slice(1);
}

async function main() {
  const commit = process.argv.includes('--commit');

  const snap = await db.collection('parents').get();
  const docs = snap.docs;

  let scanned = 0;
  let skipped = 0;
  let planned = 0;
  let written = 0;

  const previews = [];

  let batch = db.batch();
  let batchOps = 0;

  async function flushBatch() {
    if (!commit) return;
    if (batchOps === 0) return;
    await batch.commit();
    written += batchOps;
    batch = db.batch();
    batchOps = 0;
  }

  for (const d of docs) {
    scanned++;
    const data = d.data() || {};

    const rawPhone = (data.phone ?? data.phoneE164 ?? '').toString();
    const local = phoneLocalDigitsFromAny(rawPhone);
    const tail = myTail(local);
    const e164 = toE164My(local);

    if (!local || !tail || !e164) {
      skipped++;
      continue;
    }

    const next = {};

    const currentPhone = (data.phone ?? '').toString().trim().replace(/\s+/g, '');
    if (currentPhone !== local) next.phone = local;

    const currentTail = (data.phoneTail ?? '').toString().trim();
    if (currentTail !== tail) next.phoneTail = tail;

    const currentE164 = (data.phoneE164 ?? '').toString().trim();
    if (currentE164 !== e164) next.phoneE164 = e164;

    const keys = Object.keys(next);
    if (keys.length === 0) continue;

    planned++;
    if (previews.length < 30) {
      previews.push({ id: d.id, phone: next.phone ?? currentPhone, phoneTail: next.phoneTail ?? currentTail, phoneE164: next.phoneE164 ?? currentE164 });
    }

    if (commit) {
      batch.set(d.ref, next, { merge: true });
      batchOps++;
      if (batchOps >= 450) {
        await flushBatch();
      }
    }
  }

  await flushBatch();

  console.log(`\nScanned parents docs: ${scanned}`);
  console.log(`Skipped (missing/invalid phone): ${skipped}`);
  console.log(`Planned updates: ${planned}${commit ? ' (committed)' : ' (dry-run)'}`);
  if (commit) console.log(`Writes applied: ${written}`);

  if (previews.length) {
    console.log('\nPreview (first 30):');
    console.table(previews);
  }

  if (!commit) {
    console.log('\nRun with --commit to write changes.');
  }
}

main().catch((err) => {
  console.error('❌ backfill_parents_phone_fields failed:', err && err.message ? err.message : err);
  process.exit(1);
});
