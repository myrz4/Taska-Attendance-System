/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    region: "asia-southeast1",
    uid: "billing-reprice-admin",
    webApiKey: "",
    period: new Date().toISOString().slice(0, 7),
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
      continue;
    }
    if (token === "--period" && argv[index + 1]) {
      out.period = argv[index + 1];
      index += 1;
      continue;
    }
    if (token === "--parentId" && argv[index + 1]) {
      out.parentIds.push(argv[index + 1]);
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
  if (args.webApiKey) return String(args.webApiKey).trim();
  const fromEnv = String(process.env.FIREBASE_WEB_API_KEY || "").trim();
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
  const parsed = JSON.parse(body);
  if (!response.ok) {
    const message = parsed && parsed.error && parsed.error.message ? parsed.error.message : body;
    throw new Error(`signInWithCustomToken failed: ${response.status} ${message}`);
  }
  if (!parsed.idToken) {
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
  const parsed = JSON.parse(body);
  if (!response.ok) {
    throw new Error(`${functionName} HTTP ${response.status}: ${body}`);
  }
  return parsed && parsed.result && typeof parsed.result === "object"
    ? parsed.result
    : parsed;
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
    throw new Error("Unable to resolve Firebase Web API key.");
  }

  if (admin.apps.length === 0) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId,
    });
  }

  const customToken = await admin.auth().createCustomToken(args.uid, {
    role: "admin",
    smoke: "billing-reprice",
  });
  const idToken = await signInWithCustomToken(webApiKey, customToken);
  const payload = {
    period: String(args.period || "").trim(),
  };
  if (Array.isArray(args.parentIds) && args.parentIds.length > 0) {
    payload.parentIds = args.parentIds;
  }

  const result = await callCallable(projectId, args.region, "billingAdminGenerateInvoicesForPeriod", idToken, payload);
  console.log("Billing repricing result\n");
  console.log(`projectId: ${projectId}`);
  console.log(`region: ${args.region}`);
  console.log(`period: ${payload.period}`);
  console.log(`scopedParents: ${payload.parentIds ? payload.parentIds.length : 0}`);
  console.log(JSON.stringify(result, null, 2));

  if (!result || result.ok !== true) {
    throw new Error("billingAdminGenerateInvoicesForPeriod returned a non-ok result");
  }
}

main().catch((err) => {
  console.error("reprice-billing-period failed:", err && err.message ? err.message : err);
  process.exit(1);
});