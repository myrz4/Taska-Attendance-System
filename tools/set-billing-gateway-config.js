/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    provider: "billplz",
    mode: "redirect",
    enabled: true,
    isSandbox: true,
    collectionId: "",
    returnUrl: "",
    callbackUrl: "",
    clearCallbackUrl: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    const next = argv[index + 1];
    if (token === "--serviceAccount" && next) {
      out.serviceAccount = next;
      index += 1;
      continue;
    }
    if (token === "--project" && next) {
      out.projectId = next;
      index += 1;
      continue;
    }
    if (token === "--provider" && next) {
      out.provider = next;
      index += 1;
      continue;
    }
    if (token === "--mode" && next) {
      out.mode = next;
      index += 1;
      continue;
    }
    if (token === "--collectionId" && next) {
      out.collectionId = next;
      index += 1;
      continue;
    }
    if (token === "--returnUrl" && next) {
      out.returnUrl = next;
      index += 1;
      continue;
    }
    if (token === "--callbackUrl" && next) {
      out.callbackUrl = next;
      index += 1;
      continue;
    }
    if (token === "--sandbox") {
      out.isSandbox = true;
      continue;
    }
    if (token === "--production") {
      out.isSandbox = false;
      continue;
    }
    if (token === "--disable") {
      out.enabled = false;
      continue;
    }
    if (token === "--enable") {
      out.enabled = true;
      continue;
    }
    if (token === "--clear-callback-url") {
      out.clearCallbackUrl = true;
      continue;
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

  const provider = String(args.provider || "billplz").trim().toLowerCase();
  const requiresRedirectConfig = provider !== "dummy";

  if (requiresRedirectConfig && !args.collectionId) {
    throw new Error("Missing required --collectionId value.");
  }

  if (requiresRedirectConfig && !args.returnUrl) {
    throw new Error("Missing required --returnUrl value.");
  }

  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
  const projectId = String(args.projectId || serviceAccount.project_id || loadDefaultProjectId(rootDir)).trim();
  if (!projectId) {
    throw new Error("Unable to resolve Firebase project ID.");
  }

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId,
  });

  const payload = {
    provider,
    mode: String(args.mode || (provider === "dummy" ? "dummy" : "redirect")).trim().toLowerCase(),
    enabled: Boolean(args.enabled),
    isSandbox: Boolean(args.isSandbox),
    collectionId: requiresRedirectConfig ? String(args.collectionId).trim() : "",
    returnUrl: requiresRedirectConfig ? String(args.returnUrl).trim() : "",
  };

  if (!requiresRedirectConfig || args.clearCallbackUrl) {
    payload.callbackUrl = "";
  } else if (args.callbackUrl) {
    payload.callbackUrl = String(args.callbackUrl).trim();
  }

  await admin.firestore().collection("billingConfig").doc("paymentGateway").set(payload, { merge: true });

  console.log("Updated billingConfig/paymentGateway with:");
  console.log(JSON.stringify(payload, null, 2));
}

main().catch((err) => {
  console.error("set-billing-gateway-config failed:", err && err.message ? err.message : err);
  process.exit(1);
});