/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    region: "asia-southeast1",
    uid: "billing-smoke-admin",
    webApiKey: "",
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
    if (token === "--region" && argv[index + 1]) {
      out.region = argv[index + 1];
      index += 1;
      continue;
    }
    if (token === "--uid" && argv[index + 1]) {
      out.uid = argv[index + 1];
      index += 1;
      continue;
    }
    if (token === "--webApiKey" && argv[index + 1]) {
      out.webApiKey = argv[index + 1];
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

function loadPropertyFile(filePath) {
  const out = {};
  if (!fs.existsSync(filePath)) return out;
  const raw = fs.readFileSync(filePath, "utf8");
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const idx = trimmed.indexOf("=");
    if (idx <= 0) continue;
    out[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
  }
  return out;
}

function resolveWebApiKey(args, rootDir) {
  const fromEnv = String(process.env.FIREBASE_WEB_API_KEY || "").trim();
  if (args.webApiKey) return String(args.webApiKey).trim();
  if (fromEnv) return fromEnv;

  const props = loadPropertyFile(path.join(rootDir, "jar_files", "firebase.properties"));
  return String(props.webApiKey || "").trim();
}

async function signInWithCustomToken(projectWebApiKey, customToken) {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${encodeURIComponent(projectWebApiKey)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
    },
  );

  const body = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch (error) {
    parsed = null;
  }

  if (!response.ok) {
    const message = parsed && parsed.error && parsed.error.message ? parsed.error.message : body;
    throw new Error(`signInWithCustomToken failed: ${response.status} ${message}`);
  }

  if (!parsed || !parsed.idToken) {
    throw new Error("signInWithCustomToken returned no idToken");
  }

  return parsed.idToken;
}

async function callCallable(projectId, region, functionName, idToken, data) {
  const url = `https://${region}-${projectId}.cloudfunctions.net/${functionName}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
    },
    body: JSON.stringify({ data: data || {} }),
  });

  const body = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch (error) {
    parsed = null;
  }

  if (!response.ok) {
    throw new Error(`${functionName} HTTP ${response.status}: ${body}`);
  }

  const result = parsed && parsed.result && typeof parsed.result === "object"
    ? parsed.result
    : parsed;

  if (!result || typeof result !== "object") {
    throw new Error(`${functionName} returned an invalid payload`);
  }

  return result;
}

function effectiveGatewaySummary(data) {
  const provider = String(data.provider || data.activeProvider || "").trim().toLowerCase();
  const allowRealProvider = data.allowRealProvider === true;
  const effectiveProvider = provider && (provider === "dummy" || allowRealProvider) ? provider : "dummy";
  const effectiveMode = effectiveProvider === "dummy"
    ? "dummy"
    : String(data.mode || "redirect").trim().toLowerCase();
  return {
    configuredProvider: provider || "<empty>",
    allowRealProvider,
    effectiveProvider,
    effectiveMode,
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

  const webApiKey = resolveWebApiKey(args, rootDir);
  if (!webApiKey) {
    throw new Error("Unable to resolve Firebase Web API key. Set FIREBASE_WEB_API_KEY or pass --webApiKey.");
  }

  if (admin.apps.length === 0) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId,
    });
  }

  const customToken = await admin.auth().createCustomToken(args.uid, {
    role: "admin",
    smoke: "billing-postdeploy",
  });
  const idToken = await signInWithCustomToken(webApiKey, customToken);

  const health = await callCallable(projectId, args.region, "billingGetHealth", idToken, {});
  const catalogs = await callCallable(projectId, args.region, "billingAdminListCatalogs", idToken, {});
  const audit = await callCallable(projectId, args.region, "billingAdminListAudit", idToken, { limit: 5 });

  const db = admin.firestore();
  const currentSnap = await db.collection("billingConfig").doc("current").get();
  const currentData = currentSnap.exists ? (currentSnap.data() || {}) : {};
  const gatewaySnap = await db.collection("billingConfig").doc("paymentGateway").get();
  const gatewayData = gatewaySnap.exists ? (gatewaySnap.data() || {}) : {};
  const gateway = effectiveGatewaySummary(gatewayData);

  const catalogRows = Array.isArray(catalogs.catalogs) ? catalogs.catalogs : [];
  const activeCatalogs = catalogRows.filter((row) => row && row.active);
  const activeCatalogId = String(currentData.activeCatalogId || "").trim();
  const activeCatalogMatchesPointer = !activeCatalogId || activeCatalogs.some((row) => String(row.id || "").trim() === activeCatalogId);

  const failures = [];
  if (!health.ok) failures.push(`billingGetHealth returned ok=${String(health.ok)}`);
  if (!health.health || health.health.isValid !== true) failures.push("billingGetHealth reported invalid active catalog");
  if (!catalogs.ok) failures.push(`billingAdminListCatalogs returned ok=${String(catalogs.ok)}`);
  if (catalogRows.length === 0) failures.push("billingAdminListCatalogs returned no catalogs");
  if (activeCatalogs.length === 0) failures.push("no active billing catalog is marked active");
  if (!activeCatalogMatchesPointer) failures.push(`billingConfig/current activeCatalogId does not match any active catalog: ${activeCatalogId}`);
  if (!audit.ok) failures.push(`billingAdminListAudit returned ok=${String(audit.ok)}`);

  console.log("Billing post-deploy smoke\n");
  console.log(`projectId: ${projectId}`);
  console.log(`region: ${args.region}`);
  console.log(`serviceAccount: ${serviceAccountPath}`);
  console.log(`smokeUid: ${args.uid}`);
  console.log(`healthOk: ${String(health.ok)}`);
  console.log(`healthValid: ${String(health.health && health.health.isValid === true)}`);
  console.log(`catalogCount: ${String(catalogRows.length)}`);
  console.log(`activeCatalogCount: ${String(activeCatalogs.length)}`);
  console.log(`activeCatalogPointer: ${activeCatalogId || "<empty>"}`);
  console.log(`activeCatalogPointerValid: ${String(activeCatalogMatchesPointer)}`);
  console.log(`auditEntries: ${String(Array.isArray(audit.entries) ? audit.entries.length : 0)}`);
  console.log(`paymentProvider: ${gateway.effectiveProvider}`);
  console.log(`paymentMode: ${gateway.effectiveMode}`);
  console.log(`allowRealProvider: ${String(gateway.allowRealProvider)}`);
  console.log("");

  if (health.health) {
    console.log(`resolvedDefaultTransitCode: ${String(health.health.resolvedDefaultTransitCode || "") || "<empty>"}`);
    console.log(`missingRequiredCodes: ${Array.isArray(health.health.missingRequiredCodes) && health.health.missingRequiredCodes.length > 0 ? health.health.missingRequiredCodes.join(", ") : "none"}`);
    console.log("");
  }

  if (failures.length > 0) {
    console.error("Smoke FAILED:");
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(1);
  }

  console.log("Smoke PASSED");
}

main().catch((err) => {
  console.error("smoke-billing-postdeploy failed:", err && err.message ? err.message : err);
  process.exit(1);
});