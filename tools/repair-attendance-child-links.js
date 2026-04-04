const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const options = {
    apply: false,
    date: new Date().toISOString().slice(0, 10),
    serviceAccount: "",
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--apply") {
      options.apply = true;
      continue;
    }
    if (arg === "--dry-run") {
      options.apply = false;
      continue;
    }
    if (arg.startsWith("--date=")) {
      options.date = arg.slice("--date=".length).trim();
      continue;
    }
    if (arg === "--date" && index + 1 < argv.length) {
      options.date = String(argv[index + 1] || "").trim();
      index += 1;
      continue;
    }
    if (arg.startsWith("--service-account=")) {
      options.serviceAccount = arg.slice("--service-account=".length).trim();
      continue;
    }
    if (arg === "--service-account" && index + 1 < argv.length) {
      options.serviceAccount = String(argv[index + 1] || "").trim();
      index += 1;
    }
  }

  if (!/^\d{4}-\d{2}-\d{2}$/.test(options.date)) {
    throw new Error(`Invalid --date value: ${options.date}`);
  }

  return options;
}

function loadServiceAccount(explicitPath) {
  const candidatePaths = [
    explicitPath,
    process.env.GOOGLE_APPLICATION_CREDENTIALS || "",
    path.join(process.cwd(), "firestore-export", "serviceAccountKey.json"),
  ].filter(Boolean);

  for (const candidatePath of candidatePaths) {
    const resolvedPath = path.resolve(candidatePath);
    if (!fs.existsSync(resolvedPath)) {
      continue;
    }
    return {
      resolvedPath,
      json: JSON.parse(fs.readFileSync(resolvedPath, "utf8")),
    };
  }

  throw new Error(
    "No service account key found. Use --service-account <path> or set GOOGLE_APPLICATION_CREDENTIALS.",
  );
}

function normalizeChildRefPath(rawValue) {
  if (!rawValue) {
    return "";
  }
  if (typeof rawValue.path === "string") {
    return rawValue.path.trim();
  }

  let value = String(rawValue).trim();
  if (!value) {
    return "";
  }

  value = value.replace(/^\/+/, "");
  const documentsMarker = "/documents/";
  const markerIndex = value.indexOf(documentsMarker);
  if (markerIndex >= 0) {
    value = value.slice(markerIndex + documentsMarker.length);
  }
  if (value.startsWith("documents/")) {
    value = value.slice("documents/".length);
  }

  const childIndex = value.indexOf("children/");
  if (childIndex < 0) {
    return "";
  }

  return value.slice(childIndex).trim();
}

function childDocIdFromRef(rawValue) {
  const normalizedPath = normalizeChildRefPath(rawValue);
  if (!normalizedPath.startsWith("children/")) {
    return "";
  }

  return normalizedPath.slice("children/".length).split("/")[0].trim();
}

async function followChildMigration(db, childSnap) {
  let currentSnap = childSnap;

  for (let hop = 0; hop < 4; hop += 1) {
    if (!currentSnap || !currentSnap.exists) {
      return null;
    }

    const migratedToChildId = String(currentSnap.get("migratedToChildId") || "").trim();
    if (!migratedToChildId || migratedToChildId === currentSnap.id) {
      return currentSnap;
    }

    const nextSnap = await db.collection("children").doc(migratedToChildId).get();
    if (!nextSnap.exists) {
      return currentSnap;
    }
    currentSnap = nextSnap;
  }

  return currentSnap;
}

async function collectAttendanceDocs(db, dateKey) {
  const merged = new Map();
  const prefix = `${dateKey}_`;
  const documentId = admin.firestore.FieldPath.documentId();

  const snapshots = await Promise.all([
    db.collection("attendance")
      .orderBy(documentId)
      .startAt(prefix)
      .endAt(`${prefix}\uf8ff`)
      .get(),
    db.collection("attendance").where("dateKey", "==", dateKey).get(),
    db.collection("attendance").where("date", "==", dateKey).get(),
  ]);

  for (const snapshot of snapshots) {
    for (const doc of snapshot.docs) {
      merged.set(doc.id, doc);
    }
  }

  return [...merged.values()];
}

async function resolveCanonicalChild(db, data) {
  const currentChildId = String(data.childId || "").trim();
  if (currentChildId) {
    const childSnap = await db.collection("children").doc(currentChildId).get();
    const canonicalSnap = await followChildMigration(db, childSnap);
    if (canonicalSnap && canonicalSnap.exists) {
      return {
        childId: canonicalSnap.id,
        childRef: canonicalSnap.ref,
        nfcUid: String(canonicalSnap.get("nfc_uid") || data.nfc_uid || currentChildId || "").trim(),
      };
    }
  }

  const childRefId = childDocIdFromRef(data.childRef || data.child_ref);
  if (childRefId) {
    const childSnap = await db.collection("children").doc(childRefId).get();
    const canonicalSnap = await followChildMigration(db, childSnap);
    if (canonicalSnap && canonicalSnap.exists) {
      return {
        childId: canonicalSnap.id,
        childRef: canonicalSnap.ref,
        nfcUid: String(canonicalSnap.get("nfc_uid") || data.nfc_uid || currentChildId || "").trim(),
      };
    }
  }

  const nfcCandidates = [
    String(data.nfc_uid || "").trim(),
    currentChildId,
  ].filter(Boolean);

  for (const nfcUid of nfcCandidates) {
    const matches = await db.collection("children").where("nfc_uid", "==", nfcUid).limit(2).get();
    if (!matches.empty) {
      const childSnap = await followChildMigration(db, matches.docs[0]);
      if (!childSnap || !childSnap.exists) {
        continue;
      }
      return {
        childId: childSnap.id,
        childRef: childSnap.ref,
        nfcUid,
      };
    }
  }

  return null;
}

function buildPatch({ doc, data, resolvedChild, dateKey }) {
  const patch = {
    attendanceId: doc.id,
    childId: resolvedChild.childId,
    childRef: resolvedChild.childRef,
    dateKey,
  };

  if (resolvedChild.nfcUid) {
    patch.nfc_uid = resolvedChild.nfcUid;
  }

  return patch;
}

function patchNeeded(data, resolvedChild, dateKey, docId) {
  const currentChildId = String(data.childId || "").trim();
  const currentChildRefId = childDocIdFromRef(data.childRef || data.child_ref);
  const currentNfcUid = String(data.nfc_uid || "").trim();
  const currentDateKey = String(data.dateKey || "").trim();
  const expectedNfcUid = String(resolvedChild.nfcUid || "").trim();

  return currentChildId !== resolvedChild.childId
    || currentChildRefId !== resolvedChild.childId
    || (expectedNfcUid && currentNfcUid !== expectedNfcUid)
    || currentDateKey !== dateKey
    || String(data.attendanceId || "").trim() !== docId;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const serviceAccount = loadServiceAccount(options.serviceAccount);

  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount.json),
    });
  }

  const db = admin.firestore();
  const docs = await collectAttendanceDocs(db, options.date);

  console.log(`Using service account: ${serviceAccount.resolvedPath}`);
  console.log(`Scanning ${docs.length} attendance documents for ${options.date}`);
  console.log(options.apply ? "Mode: apply" : "Mode: dry-run");

  let updated = 0;
  let alreadyCanonical = 0;
  let unresolved = 0;

  for (const doc of docs) {
    const data = doc.data() || {};
    const resolvedChild = await resolveCanonicalChild(db, data);
    if (!resolvedChild) {
      unresolved += 1;
      console.log(`[unresolved] ${doc.id} childId=${String(data.childId || "").trim()} childRef=${normalizeChildRefPath(data.childRef || data.child_ref) || "-"}`);
      continue;
    }

    if (!patchNeeded(data, resolvedChild, options.date, doc.id)) {
      alreadyCanonical += 1;
      continue;
    }

    const patch = buildPatch({
      doc,
      data,
      resolvedChild,
      dateKey: options.date,
    });

    console.log(`[repair] ${doc.id} -> childId=${patch.childId} nfc_uid=${patch.nfc_uid || "-"}`);
    if (options.apply) {
      await doc.ref.set(patch, { merge: true });
    }
    updated += 1;
  }

  console.log(`Summary: updated=${updated}, alreadyCanonical=${alreadyCanonical}, unresolved=${unresolved}`);
  if (!options.apply) {
    console.log("Dry-run only. Re-run with --apply to write the repairs.");
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});