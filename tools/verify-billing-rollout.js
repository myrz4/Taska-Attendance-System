/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function parseArgs(argv) {
  const out = {
    serviceAccount: "",
    projectId: "",
    region: "asia-southeast1",
    fixCallbackUrl: false,
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
    if (token === "--fix-callback-url") {
      out.fixCallbackUrl = true;
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

function expectedCallbackUrl(projectId, region) {
  return `https://${region}-${projectId}.cloudfunctions.net/billingBillplzCallback`;
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

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId,
  });

  const db = admin.firestore();
  const docRef = db.collection("billingConfig").doc("paymentGateway");
  const snap = await docRef.get();
  const data = snap.exists ? (snap.data() || {}) : {};
  const callbackUrl = String(data.callbackUrl || "").trim();
  const provider = String(data.provider || data.activeProvider || "").trim().toLowerCase();
  const allowRealProvider = data.allowRealProvider === true;
  const effectiveProvider = provider && (provider === "dummy" || allowRealProvider) ? provider : "dummy";
  const effectiveMode = effectiveProvider === "dummy"
    ? "dummy"
    : String(data.mode || "redirect").trim().toLowerCase();
  const expectedUrl = expectedCallbackUrl(projectId, args.region);

  const findings = [
    { label: "projectId", value: projectId },
    { label: "region", value: args.region },
    { label: "serviceAccount", value: serviceAccountPath },
    { label: "configuredProvider", value: provider || "<empty>" },
    { label: "effectiveProvider", value: effectiveProvider },
    { label: "effectiveMode", value: effectiveMode },
    { label: "allowRealProvider", value: String(allowRealProvider) },
    { label: "enabled", value: String(data.enabled !== false) },
    { label: "collectionId", value: String(data.collectionId || data.billplzCollectionId || "<empty>") },
    { label: "returnUrl", value: String(data.returnUrl || "<empty>") },
    { label: "callbackUrl", value: callbackUrl || "<empty>" },
    { label: "expectedCallbackUrl", value: expectedUrl },
  ];

  console.log("Billing rollout verification\n");
  for (const item of findings) {
    console.log(`${item.label}: ${item.value}`);
  }
  console.log("");

  const warnings = [];
  if (!provider) {
    warnings.push("paymentGateway provider is empty");
  } else if (provider !== "dummy" && !allowRealProvider) {
    warnings.push("real provider is configured but runtime lock keeps billing on dummy mode");
  } else if (provider === "billplz") {
    if (data.enabled === false) warnings.push("paymentGateway is disabled");
    if (!String(data.collectionId || data.billplzCollectionId || "").trim()) warnings.push("collectionId is empty");
    if (!String(data.returnUrl || "").trim()) warnings.push("returnUrl is empty");
    if (callbackUrl && callbackUrl !== expectedUrl) warnings.push("callbackUrl does not match the deployed billingBillplzCallback URL pattern");
  }

  if (args.fixCallbackUrl) {
    await docRef.set({ callbackUrl: expectedUrl }, { merge: true });
    console.log(`Updated callbackUrl to ${expectedUrl}`);
  } else if (provider === "billplz" && !callbackUrl) {
    console.log("callbackUrl is blank. This is allowed because checkout creation will auto-fill the deployed function URL.");
  } else if (effectiveProvider === "dummy") {
    console.log("Demo payment mode is active using the internal dummy provider. Real payment credentials and callback rollout are not required.");
  }

  if (warnings.length > 0) {
    console.log("\nWarnings:");
    for (const warning of warnings) {
      console.log(`- ${warning}`);
    }
  } else {
    console.log("No rollout config warnings detected.");
  }
}

main().catch((err) => {
  console.error("verify-billing-rollout failed:", err && err.message ? err.message : err);
  process.exit(1);
});