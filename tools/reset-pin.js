/*
  Local admin tool: reset/set the derived PIN (Firebase Auth email/password) for a parent/teacher.
  IMPORTANT: Run this only on your admin machine. Do NOT bundle serviceAccountKey.json in any app.

  Usage examples:
    node tools/reset-pin.js --serviceAccount ./serviceAccountKey.json --email p_0112345678@taskazurah.local --pin 1234
    node tools/reset-pin.js --serviceAccount ./serviceAccountKey.json --kind parent --phone 0112345678 --pin 1234
    node tools/reset-pin.js --serviceAccount ./serviceAccountKey.json --kind teacher --phone +601112577356 --pin 4321

  Notes:
    - This resets the Firebase Auth PASSWORD for the derived email.
    - For teachers, you may also need to set custom claim role=teacher (see tools/set-role.js).
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
  console.error(`\nUsage:\n  node tools/reset-pin.js --serviceAccount <path> (--email <derivedEmail> | --kind <parent|teacher> --phone <phone>) --pin <4-6 digits>\n`);
  process.exit(1);
}

function digitsOnly(input) {
  return String(input || '').replace(/[^0-9]/g, '');
}

// Canonicalize to local digits (0xxxxxxxxx) because Firestore stores phones like 011...
function phoneLocalDigitsFromAny(phone) {
  const digits = digitsOnly(phone);
  if (!digits) return '';
  if (digits.startsWith('60') && digits.length > 2) return '0' + digits.slice(2);
  if (digits.startsWith('0')) return digits;
  if (digits.startsWith('1')) return '0' + digits;
  return digits;
}

function derivedEmail(kind, phone) {
  const local = phoneLocalDigitsFromAny(phone);
  if (!local) return '';
  const prefix = kind === 'teacher' ? 't_' : 'p_';
  return `${prefix}${local}@taskazurah.local`;
}

async function main() {
  const args = parseArgs(process.argv);
  const serviceAccountPath = args.serviceAccount;
  const emailArg = args.email;
  const kind = args.kind;
  const phone = args.phone;
  const pin = args.pin;

  if (!serviceAccountPath) usageAndExit('Missing --serviceAccount');
  if (!pin || !/^[0-9]{4,6}$/.test(pin)) usageAndExit('Missing or invalid --pin (4-6 digits)');

  let email = emailArg;
  if (!email) {
    if (!kind || !['parent', 'teacher'].includes(kind)) usageAndExit('Missing or invalid --kind (parent|teacher)');
    if (!phone) usageAndExit('Missing --phone');
    email = derivedEmail(kind, phone);
    if (!email) usageAndExit('Unable to derive email from phone');
  }

  const resolved = path.resolve(serviceAccountPath);
  if (!fs.existsSync(resolved)) usageAndExit(`Service account file not found: ${resolved}`);

  const admin = require('firebase-admin');
  if (admin.apps.length === 0) {
    admin.initializeApp({
      credential: admin.credential.cert(require(resolved)),
    });
  }

  const auth = admin.auth();

  let user;
  try {
    user = await auth.getUserByEmail(email);
  } catch (e) {
    // If user doesn't exist, create it.
    if (e && (e.code === 'auth/user-not-found' || String(e.message || '').includes('user-not-found'))) {
      user = await auth.createUser({
        email,
        password: pin,
        emailVerified: true,
      });
      console.log('✅ Created user for derived email');
      console.log(`   email: ${email}`);
      console.log(`   uid  : ${user.uid}`);
      return;
    }
    throw e;
  }

  await auth.updateUser(user.uid, { password: pin });

  console.log('✅ PIN reset successful');
  console.log(`   email: ${email}`);
  console.log(`   uid  : ${user.uid}`);
  console.log('\nNOTE: If the user is currently signed in, they should sign out + sign in again.');
}

main().catch((err) => {
  console.error('❌ Failed to reset PIN:', err && err.message ? err.message : err);
  process.exit(1);
});
