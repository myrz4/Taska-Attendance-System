const admin = require('firebase-admin');

// One-time migration helper:
// Normalize children.birthDate to ISO string yyyy-MM-dd.
//
// Handles legacy formats like:
//   "Fri Feb 07 2003 00:00:00 GMT+0800 (Malaysia Time)"
//
// Usage:
//   node fix/migrate_children_birthdate_to_iso.js            (dry-run)
//   node fix/migrate_children_birthdate_to_iso.js --commit  (write updates)

admin.initializeApp({
  credential: admin.credential.cert(require('../serviceAccountKey.json')),
});

const db = admin.firestore();

function pad2(n) {
  return String(n).padStart(2, '0');
}

function toIsoDateStringWithOffset(dateObj, offsetMinutes) {
  // Convert JS Date -> yyyy-MM-dd in a fixed offset timezone (Malaysia is +08:00, no DST).
  const shifted = new Date(dateObj.getTime() + offsetMinutes * 60 * 1000);
  const y = shifted.getUTCFullYear();
  const m = pad2(shifted.getUTCMonth() + 1);
  const d = pad2(shifted.getUTCDate());
  return `${y}-${m}-${d}`;
}

const MONTHS = {
  jan: '01',
  feb: '02',
  mar: '03',
  apr: '04',
  may: '05',
  jun: '06',
  jul: '07',
  aug: '08',
  sep: '09',
  oct: '10',
  nov: '11',
  dec: '12',
};

function parseLegacyBirthDateString(raw) {
  const trimmed = raw.trim();
  if (!trimmed) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return { iso: trimmed, legacy: null };
  }

  // Preferred: parse the date portion directly so we never get an off-by-one due to timezone.
  // Example: Fri Feb 07 2003 00:00:00 GMT+0800 (Malaysia Time)
  const m = trimmed.match(/^(?:\w{3})\s+(\w{3})\s+(\d{2})\s+(\d{4})\s+/);
  if (m) {
    const mon = MONTHS[m[1].toLowerCase()];
    if (mon) {
      const day = m[2];
      const year = m[3];
      return { iso: `${year}-${mon}-${day}`, legacy: trimmed };
    }
  }

  // Matches: Fri Feb 07 2003 00:00:00 GMT+0800 (Malaysia Time)
  // Make it RFC2822-ish for Date.parse by removing the parenthesized label.
  const noParen = trimmed.replace(/\s*\([^)]*\)\s*$/, '');

  // Insert space before timezone if needed: "GMT+0800" -> "+0800" or "GMT +0800"
  // JS Date parsing is inconsistent; try a couple of shapes.
  const candidates = [
    noParen.replace('GMT', ''),
    noParen.replace('GMT', 'GMT '),
    noParen,
  ];

  for (const c of candidates) {
    const ms = Date.parse(c);
    if (!Number.isNaN(ms)) {
      const dateObj = new Date(ms);
      return { iso: toIsoDateStringWithOffset(dateObj, 480), legacy: trimmed };
    }
  }

  return null;
}

async function main() {
  const commit = process.argv.includes('--commit');

  const snap = await db.collection('children').get();
  const docs = snap.docs.map((d) => ({ id: d.id, ref: d.ref, data: d.data() }));

  let planned = 0;
  const previews = [];

  for (const { id, ref, data } of docs) {
    const birth = data ? data.birthDate : undefined;

    if (birth == null) continue;

    // Keep empty strings as-is.
    if (typeof birth === 'string' && birth.trim() === '') continue;

    // Already normalized ISO.
    if (typeof birth === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(birth.trim())) continue;

    let parsed = null;

    if (typeof birth === 'string') {
      parsed = parseLegacyBirthDateString(birth);
    } else if (birth && typeof birth.toDate === 'function') {
      // Firestore Timestamp
      parsed = { iso: toIsoDateStringWithOffset(birth.toDate(), 480), legacy: null };
    } else if (birth instanceof Date) {
      parsed = { iso: toIsoDateStringWithOffset(birth, 480), legacy: null };
    }

    if (!parsed || !parsed.iso) continue;

    planned++;
    previews.push({ id, from: String(birth), to: parsed.iso });

    if (commit) {
      const update = {
        birthDate: parsed.iso,
      };

      // Preserve the previous non-ISO string once for rollback/debugging.
      if (typeof birth === 'string' && parsed.legacy && data.birthDate_legacy == null) {
        update.birthDate_legacy = parsed.legacy;
      }

      await ref.set(update, { merge: true });
    }
  }

  console.log(`\nFound ${docs.length} children docs`);
  console.log(`Planned birthDate normalizations: ${planned}${commit ? ' (committed)' : ' (dry-run)'}`);

  if (previews.length) {
    console.log('\nPreview (first 50):');
    for (const p of previews.slice(0, 50)) {
      console.log(`- children/${p.id}: ${p.from} -> ${p.to}`);
    }
    if (previews.length > 50) console.log(`... (${previews.length - 50} more)`);
  }

  console.log('\nDone.');
}

main().catch((e) => {
  console.error('Migration failed:', e);
  process.exit(1);
});
