/*
  Local admin tool: set Firebase Auth custom claims for a user.
  IMPORTANT: Run this only on your admin machine. Do NOT bundle serviceAccountKey.json in JavaFX.

  Usage examples:
    node tools/set-role.js --serviceAccount ./serviceAccountKey.json --email admin@example.com --role admin
    node tools/set-role.js --serviceAccount ./serviceAccountKey.json --email teacher@example.com --role teacher
    node tools/set-role.js --serviceAccount ./serviceAccountKey.json --uid <UID> --role teacher

  Notes:
    - The Firestore rules in ../firestore.rules expect request.auth.token.role to be 'admin' or 'teacher'.
    - After setting claims, the user must sign out/in to refresh their ID token.
*/

const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i++) {
    const part = argv[i];
    if (!part.startsWith('--')) continue;
    const key = part.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      args[key] = next;
      i++;
    } else {
      args[key] = true;
    }
  }
  return args;
}

function usageAndExit(message) {
  if (message) console.error(`\nERROR: ${message}`);
  console.error(`\nUsage:\n  node tools/set-role.js --serviceAccount <path> (--email <email> | --uid <uid> | --phone <phone>) --role <admin|teacher>\n`);
  process.exit(1);
}

function resolveServiceAccountPath(maybePath) {
  const candidates = [];

  if (maybePath) {
    candidates.push(path.resolve(maybePath));
  }

  // Common locations in this repo (prefer firestore-export to keep secrets out of app root).
  candidates.push(path.resolve(process.cwd(), 'firestore-export', 'serviceAccountKey.json'));
  candidates.push(path.resolve(process.cwd(), 'serviceAccountKey.json'));

  for (const p of candidates) {
    if (p && fs.existsSync(p)) return p;
  }

  return { notFound: true, candidates };
}

function digitsOnly(input) {
  return String(input || '').replace(/[^0-9]/g, '');
}

function normalizePhoneToE164MY(input) {
  const raw = String(input || '').trim();
  if (!raw) return '';
  if (raw.startsWith('+')) {
    const digits = digitsOnly(raw);
    return digits.length >= 10 ? `+${digits}` : '';
  }

  const digits = digitsOnly(raw);
  if (!digits) return '';
  if (digits.startsWith('60')) return digits.length >= 11 ? `+${digits}` : '';
  if (digits.startsWith('0')) {
    const rest = digits.slice(1);
    return rest.length >= 9 ? `+60${rest}` : '';
  }
  // If user typed 11... without 0, assume Malaysia mobile and prefix +60.
  if (digits.startsWith('1')) return digits.length >= 9 ? `+60${digits}` : '';
  return '';
}

async function main() {
  const args = parseArgs(process.argv);
  const serviceAccountPath = args.serviceAccount;
  const role = args.role;
  const email = args.email;
  const uidArg = args.uid;
  const phoneArg = args.phone;

  const resolvedServiceAccount = resolveServiceAccountPath(serviceAccountPath);
  if (resolvedServiceAccount && resolvedServiceAccount.notFound) {
    usageAndExit(
      `Service account file not found. Tried:\n  - ${resolvedServiceAccount.candidates.join('\n  - ')}`,
    );
  }
  if (!role || !['admin', 'teacher'].includes(role)) usageAndExit('Missing or invalid --role (admin|teacher)');
  if (!email && !uidArg && !phoneArg) usageAndExit('Provide either --email, --uid, or --phone');

  const admin = require('firebase-admin');
  if (admin.apps.length === 0) {
    admin.initializeApp({
      credential: admin.credential.cert(require(resolvedServiceAccount)),
    });
  }

  const auth = admin.auth();

  let user;
  if (email) user = await auth.getUserByEmail(email);
  else if (uidArg) user = await auth.getUser(uidArg);
  else {
    const phoneE164 = normalizePhoneToE164MY(phoneArg);
    if (!phoneE164) usageAndExit(`Invalid --phone format: ${phoneArg}`);
    user = await auth.getUserByPhoneNumber(phoneE164);
  }

  const uid = user.uid;
  const existingClaims = user.customClaims || {};

  const newClaims = {
    ...existingClaims,
    role,
  };

  await auth.setCustomUserClaims(uid, newClaims);

  // Re-fetch so we can show the final claims.
  const updated = await auth.getUser(uid);

  console.log('✅ Custom claims updated');
  console.log(`   uid : ${uid}`);
  console.log(`   email : ${user.email || '(no email)'} `);
  console.log(`   phone : ${user.phoneNumber || '(no phone)'} `);
  console.log(`   role : ${role}`);
  console.log(`   claims : ${JSON.stringify(updated.customClaims || {})}`);
  console.log('\nNOTE: The user must sign out + sign in again to refresh their token.');
}

main().catch((err) => {
  console.error('❌ Failed to set role:', err && err.message ? err.message : err);
  process.exit(1);
});
