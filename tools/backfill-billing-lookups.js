/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    parentIds: [],
  };

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--serviceAccount" && argv[index + 1]) {
      out.serviceAccount = argv[index + 1];
      index += 1;
      continue;
    }
    if (token === "--project" && argv[index + 1]) {
      out.projectId = argv[index + 1];
      index += 1;
      continue;
    }
    if (token === "--parentId" && argv[index + 1]) {
      out.parentIds.push(String(argv[index + 1]).trim());
      index += 1;
    }
  }

  return out;
}

function repoRoot() {
  return path.resolve(__dirname, "..");
}

function findFirstExisting(paths) {
  for (const candidate of paths) {
    if (candidate && fs.existsSync(candidate)) return candidate;
  }
  return "";
}

function loadJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function loadDefaultProjectId(rootDir) {
  const firebasercPath = path.join(rootDir, ".firebaserc");
  if (!fs.existsSync(firebasercPath)) return "";
  const raw = loadJson(firebasercPath);
  return String(raw && raw.projects && raw.projects.default ? raw.projects.default : "").trim();
}

function uniqueSortedStrings(values) {
  return Array.from(new Set(
    (Array.isArray(values) ? values : [])
      .map((value) => String(value || "").trim())
      .filter(Boolean),
  )).sort();
}

function invoiceChildIds(invoice) {
  const ids = [];
  if (invoice && Array.isArray(invoice.childIds)) ids.push(...invoice.childIds);
  if (invoice && invoice.childId) ids.push(invoice.childId);
  return uniqueSortedStrings(ids);
}

function childCoverageKey(period, childIds) {
  const normalizedPeriod = String(period || "").trim();
  const normalizedChildIds = uniqueSortedStrings(childIds);
  if (!normalizedPeriod || !normalizedChildIds.length) return "";
  return `${normalizedPeriod}::${normalizedChildIds.join("|")}`;
}

function invoiceChildCoverageKey(invoice) {
  const existing = String(invoice && invoice.childCoverageKey ? invoice.childCoverageKey : "").trim();
  if (existing) return existing;
  return childCoverageKey(invoice && invoice.period, invoiceChildIds(invoice));
}

function invoiceCoverageLookupRef(db, coverageKey) {
  const normalizedCoverageKey = String(coverageKey || "").trim();
  return normalizedCoverageKey ? db.collection("billingInvoiceCoverage").doc(normalizedCoverageKey) : null;
}

function childPeriodLookupRef(db, period, childId) {
  const normalizedPeriod = String(period || "").trim();
  const normalizedChildId = String(childId || "").trim();
  if (!normalizedPeriod || !normalizedChildId) return null;
  return db.collection("billingChildPeriodLookup").doc(`${normalizedPeriod}::${normalizedChildId}`);
}

function paymentSessionLookupRef(db, kind, value) {
  const normalizedKind = String(kind || "").trim();
  const normalizedValue = String(value || "").trim();
  if (!normalizedKind || !normalizedValue) return null;
  const hash = crypto.createHash("sha1").update(normalizedValue).digest("hex");
  return db.collection("billingSessionLookup").doc(`${normalizedKind}:${hash}`);
}

function makeBatchWriter(db, limit = 400) {
  let batch = db.batch();
  let opCount = 0;
  let commitCount = 0;

  return {
    async set(ref, data, options) {
      if (!ref) return;
      if (opCount >= limit) {
        await this.flush();
      }
      batch.set(ref, data, options);
      opCount += 1;
    },
    async flush() {
      if (opCount <= 0) return;
      await batch.commit();
      batch = db.batch();
      opCount = 0;
      commitCount += 1;
    },
    commitCount() {
      return commitCount;
    },
  };
}

async function loadParents(db, parentIds) {
  const normalizedParentIds = uniqueSortedStrings(parentIds);
  if (!normalizedParentIds.length) {
    const snap = await db.collection("parents").get();
    return snap.docs;
  }

  const docs = await Promise.all(normalizedParentIds.map((parentId) => db.collection("parents").doc(parentId).get()));
  return docs.filter((doc) => doc.exists);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const rootDir = repoRoot();
  const serviceAccountPath = findFirstExisting([
    args.serviceAccount ? path.resolve(args.serviceAccount) : "",
    path.join(rootDir, "teacher_app_taskazurah", "service-account-taskazurah.json"),
    path.join(rootDir, "firestore-export", "serviceAccountKey.json"),
  ]);
  if (!serviceAccountPath) {
    throw new Error("No service account JSON found. Pass --serviceAccount <path>.");
  }

  const serviceAccount = loadJson(serviceAccountPath);
  const projectId = String(args.projectId || serviceAccount.project_id || loadDefaultProjectId(rootDir)).trim();
  if (!projectId) {
    throw new Error("Unable to resolve Firebase project ID.");
  }

  if (admin.apps.length === 0) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId,
    });
  }

  const db = admin.firestore();
  const writer = makeBatchWriter(db);
  const summary = {
    projectId,
    scannedParents: 0,
    scannedInvoices: 0,
    scannedSessions: 0,
    coverageLookupsTouched: 0,
    childPeriodLookupsTouched: 0,
    sessionLookupsTouched: 0,
  };

  const parentDocs = await loadParents(db, args.parentIds);
  for (const parentDoc of parentDocs) {
    summary.scannedParents += 1;

    const invoiceSnap = await parentDoc.ref.collection("invoices").get();
    for (const invoiceDoc of invoiceSnap.docs) {
      summary.scannedInvoices += 1;

      const invoiceData = invoiceDoc.data() || {};
      const period = String(invoiceData.period || "").trim();
      const childIds = invoiceChildIds(invoiceData);
      const coverageKey = invoiceChildCoverageKey(invoiceData);

      if (coverageKey) {
        await writer.set(invoiceCoverageLookupRef(db, coverageKey), {
          period,
          childIds,
          childCoverageKey: coverageKey,
          invoicePaths: admin.firestore.FieldValue.arrayUnion(invoiceDoc.ref.path),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
        summary.coverageLookupsTouched += 1;
      }

      for (const childId of childIds) {
        await writer.set(childPeriodLookupRef(db, period, childId), {
          period,
          childId,
          invoicePaths: admin.firestore.FieldValue.arrayUnion(invoiceDoc.ref.path),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
        summary.childPeriodLookupsTouched += 1;
      }

      const sessionSnap = await invoiceDoc.ref.collection("sessions").get();
      for (const sessionDoc of sessionSnap.docs) {
        summary.scannedSessions += 1;
        const sessionData = sessionDoc.data() || {};
        const providerSessionId = String(sessionData.providerSessionId || "").trim();
        const providerReference = String(sessionData.providerReference || "").trim();
        const basePayload = {
          sessionPath: sessionDoc.ref.path,
          invoicePath: invoiceDoc.ref.path,
          provider: String(sessionData.provider || "").trim(),
          status: String(sessionData.status || "").trim(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        };

        if (providerSessionId) {
          await writer.set(paymentSessionLookupRef(db, "providerSessionId", providerSessionId), {
            ...basePayload,
            kind: "providerSessionId",
            value: providerSessionId,
          }, { merge: true });
          summary.sessionLookupsTouched += 1;
        }

        if (providerReference) {
          await writer.set(paymentSessionLookupRef(db, "providerReference", providerReference), {
            ...basePayload,
            kind: "providerReference",
            value: providerReference,
          }, { merge: true });
          summary.sessionLookupsTouched += 1;
        }
      }
    }
  }

  await writer.flush();

  console.log("Billing lookup backfill complete\n");
  console.log(JSON.stringify({
    ...summary,
    batchCommits: writer.commitCount(),
    scopedParents: uniqueSortedStrings(args.parentIds).length,
  }, null, 2));
}

main().catch((err) => {
  console.error("backfill-billing-lookups failed:", err && err.message ? err.message : err);
  process.exit(1);
});