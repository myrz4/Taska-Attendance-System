/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

const BILLING_REQUIRED_CODES = [
  "monthly_fee_baby_to_2",
  "monthly_fee_age_2_to_3",
  "monthly_fee_age_4",
  "registration_fee",
  "insurance_takaful",
  "yearly_maintenance_fee",
  "overtime_weekday_half_hour",
  "overtime_saturday_half_hour",
  "transit_1hour",
  "transit_1day",
  "transit_1week",
];

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
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

function loadDefaultProjectId(rootDir) {
  const firebasercPath = path.join(rootDir, ".firebaserc");
  if (!fs.existsSync(firebasercPath)) return "";
  const raw = JSON.parse(fs.readFileSync(firebasercPath, "utf8"));
  return String(raw && raw.projects && raw.projects.default ? raw.projects.default : "").trim();
}

function sanitizeTransitCode(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (!value) return "";
  return /^transit_[a-z0-9_]+$/.test(value) ? value : "";
}

function moneySen(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) return 0;
  return Math.round(numberValue);
}

function normalizeCatalogDoc(raw) {
  const src = raw && typeof raw === "object" ? raw : {};
  const tableRaw = (src.table && typeof src.table === "object")
    ? src.table
    : Object.fromEntries(
      Object.entries(src).filter(([key, value]) => {
        if (["version", "active", "updatedAt", "updatedBy"].includes(key)) return false;
        return value && typeof value === "object"
          && Object.prototype.hasOwnProperty.call(value, "staff")
          && Object.prototype.hasOwnProperty.call(value, "nonstaff");
      }),
    );

  const table = {};
  for (const [code, value] of Object.entries(tableRaw)) {
    if (!value || typeof value !== "object") continue;
    table[code] = {
      staff: moneySen(value.staff),
      nonstaff: moneySen(value.nonstaff),
    };
  }

  return {
    version: String(src.version || ""),
    active: Boolean(src.active),
    defaultTransitMonthlyCode: sanitizeTransitCode(src.defaultTransitMonthlyCode),
    table,
  };
}

function pickDefaultTransitCode(table, configuredCode) {
  const rows = table && table.table ? table.table : (table || {});
  if (configuredCode && rows[configuredCode]) return configuredCode;
  return "";
}

function billingCatalogHealthSnapshot(table) {
  const rows = table && table.table ? table.table : (table || {});
  const configuredDefaultTransitCode = sanitizeTransitCode(table && table.defaultTransitMonthlyCode);
  const resolvedDefaultTransitCode = pickDefaultTransitCode(table, configuredDefaultTransitCode);
  const missingRequiredCodes = BILLING_REQUIRED_CODES.filter((code) => !rows[code]);
  const transitMonthlyCodes = Object.keys(rows)
    .filter((code) => String(code).startsWith("transit_") && String(code).endsWith("_month"))
    .sort();

  return {
    version: String((table && table.version) || ""),
    rowCount: Object.keys(rows).length,
    missingRequiredCodes,
    configuredDefaultTransitCode,
    resolvedDefaultTransitCode,
    defaultTransitConfiguredValid: !configuredDefaultTransitCode || Boolean(rows[configuredDefaultTransitCode]),
    defaultTransitResolvedValid: !resolvedDefaultTransitCode || Boolean(rows[resolvedDefaultTransitCode]),
    transitMonthlyCodes,
    isValid: missingRequiredCodes.length === 0
      && (!configuredDefaultTransitCode || Boolean(rows[configuredDefaultTransitCode])),
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

  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
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
  const pointerSnap = await db.collection("billingConfig").doc("current").get();
  const pointerData = pointerSnap.exists ? (pointerSnap.data() || {}) : {};
  const activeCatalogId = String(pointerData.activeCatalogId || "").trim();
  const pointerTransitCode = sanitizeTransitCode(pointerData.defaultTransitMonthlyCode);

  const activeCatalogSnap = await db.collection("billingCatalog").where("active", "==", true).get();
  const activeDocs = activeCatalogSnap.docs || [];
  const failures = [];

  if (!activeCatalogId) failures.push("billingConfig/current activeCatalogId is empty");
  if (activeDocs.length === 0) failures.push("no active billing catalog documents found");
  if (activeDocs.length > 1) failures.push(`multiple active billing catalogs found (${activeDocs.length})`);

  let selectedDoc = null;
  if (activeCatalogId) {
    const pointed = await db.collection("billingCatalog").doc(activeCatalogId).get();
    if (!pointed.exists) {
      failures.push(`billingConfig/current points to missing catalog ${activeCatalogId}`);
    } else {
      selectedDoc = pointed;
      if (pointed.get("active") !== true) {
        failures.push(`billingConfig/current points to catalog ${activeCatalogId} that is not marked active`);
      }
    }
  }

  if (!selectedDoc && activeDocs.length > 0) {
    selectedDoc = activeDocs[0];
  }

  let health = null;
  if (selectedDoc) {
    const normalized = normalizeCatalogDoc(selectedDoc.data() || {});
    normalized.defaultTransitMonthlyCode = pointerTransitCode || normalized.defaultTransitMonthlyCode || "";
    health = billingCatalogHealthSnapshot(normalized);
    if (!health.isValid) {
      failures.push("active billing catalog failed health validation");
    }
  } else {
    failures.push("no billing catalog available to validate");
  }

  console.log("Billing pre-deploy check\n");
  console.log(`projectId: ${projectId}`);
  console.log(`serviceAccount: ${serviceAccountPath}`);
  console.log(`activeCatalogId: ${activeCatalogId || "<empty>"}`);
  console.log(`activeCatalogCount: ${String(activeDocs.length)}`);
  console.log(`pointerDefaultTransitCode: ${pointerTransitCode || "<empty>"}`);
  if (selectedDoc) {
    console.log(`selectedCatalogId: ${selectedDoc.id}`);
  }
  if (health) {
    console.log(`catalogVersion: ${health.version || "<empty>"}`);
    console.log(`rowCount: ${String(health.rowCount)}`);
    console.log(`resolvedDefaultTransitCode: ${health.resolvedDefaultTransitCode || "<empty>"}`);
    console.log(`missingRequiredCodes: ${health.missingRequiredCodes.length > 0 ? health.missingRequiredCodes.join(", ") : "none"}`);
    console.log(`transitMonthlyCodes: ${health.transitMonthlyCodes.length > 0 ? health.transitMonthlyCodes.join(", ") : "none"}`);
  }
  console.log("");

  if (failures.length > 0) {
    console.error("Pre-deploy check FAILED:");
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(1);
  }

  console.log("Pre-deploy check PASSED");
}

main().catch((err) => {
  console.error("check-billing-predeploy failed:", err && err.message ? err.message : err);
  process.exit(1);
});