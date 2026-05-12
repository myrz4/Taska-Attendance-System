/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

const feeEngine = require("../teacher_app_taskazurah/functions/fee-engine");

const LEGACY_BILLING_CODES = [
  "monthly_fulltime_3m_2y",
  "monthly_fulltime_2y_4y",
  "transit_halfday_month",
  "transit_2h_month",
  "transit_schoolholiday_month",
  "overtime_after_530",
  "overtime_8pm_12am",
  "transport_tadika_month",
  "registration_fulltime_oneoff",
  "registration_transit_oneoff",
  "annual_fee_yearly",
  "comms_book_oneoff",
  "insurance_oneoff_age2plus",
];

const TASKA_ZURAH_CATALOG_ID = "taska_zurah_2026";
const TASKA_ZURAH_CATALOG_VERSION = "taska_zurah_2026";

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    dryRun: false,
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
    if (token === "--dryRun") {
      out.dryRun = true;
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

function catalogTable(raw) {
  const src = raw && typeof raw === "object" ? raw : {};
  if (src.table && typeof src.table === "object") {
    return src.table;
  }
  return src;
}

function isLegacyCatalogDoc(raw) {
  const table = catalogTable(raw);
  return LEGACY_BILLING_CODES.some((code) => Object.prototype.hasOwnProperty.call(table, code));
}

function canonicalCatalogPayload(updatedBy = "catalog-repair-script") {
  const catalog = feeEngine.buildDefaultCatalog();
  return {
    version: TASKA_ZURAH_CATALOG_VERSION,
    active: true,
    table: catalog.table,
    policy: catalog.policy,
    defaultTransitMonthlyCode: "",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedBy: String(updatedBy || "catalog-repair-script"),
  };
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
  const catalogSnap = await db.collection("billingCatalog").get();
  const deletedCatalogIds = [];
  const deactivatedCatalogIds = [];

  if (args.dryRun) {
    for (const doc of catalogSnap.docs) {
      if (doc.id !== TASKA_ZURAH_CATALOG_ID && isLegacyCatalogDoc(doc.data() || {})) {
        deletedCatalogIds.push(doc.id);
      } else if (doc.id !== TASKA_ZURAH_CATALOG_ID && doc.get("active") === true) {
        deactivatedCatalogIds.push(doc.id);
      }
    }
  } else {
    const batch = db.batch();
    for (const doc of catalogSnap.docs) {
      if (doc.id !== TASKA_ZURAH_CATALOG_ID && isLegacyCatalogDoc(doc.data() || {})) {
        batch.delete(doc.ref);
        deletedCatalogIds.push(doc.id);
        continue;
      }
      if (doc.id !== TASKA_ZURAH_CATALOG_ID && doc.get("active") === true) {
        batch.set(doc.ref, { active: false }, { merge: true });
        deactivatedCatalogIds.push(doc.id);
      }
    }

    batch.set(
      db.collection("billingCatalog").doc(TASKA_ZURAH_CATALOG_ID),
      canonicalCatalogPayload(),
      { merge: true }
    );
    batch.set(
      db.collection("billingConfig").doc("current"),
      {
        activeCatalogId: TASKA_ZURAH_CATALOG_ID,
        defaultTransitMonthlyCode: "",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedBy: "catalog-repair-script",
      },
      { merge: true }
    );
    await batch.commit();
  }

  console.log("Taska Zurah billing catalog repair\n");
  console.log(`projectId: ${projectId}`);
  console.log(`serviceAccount: ${serviceAccountPath}`);
  console.log(`dryRun: ${String(args.dryRun)}`);
  console.log(`canonicalCatalogId: ${TASKA_ZURAH_CATALOG_ID}`);
  console.log(`deletedLegacyCatalogs: ${deletedCatalogIds.length > 0 ? deletedCatalogIds.join(", ") : "none"}`);
  console.log(`deactivatedCatalogs: ${deactivatedCatalogIds.length > 0 ? deactivatedCatalogIds.join(", ") : "none"}`);
  if (!args.dryRun) {
    console.log("status: canonical Taska Zurah catalog is now active");
  }
}

main().catch((err) => {
  console.error("repair-billing-catalog-taska-zurah failed:", err && err.message ? err.message : err);
  process.exit(1);
});