require("dotenv").config();

const crypto = require("crypto");
const express = require("express");
const cors = require("cors");
const { google } = require("googleapis");

const app = express();
app.use(cors());
app.use(express.json({ limit: "1mb" }));

const port = Number(process.env.PORT || 8080);
const packageName = process.env.ANDROID_PACKAGE_NAME || "";
const nonceTtlMs = Number(process.env.NONCE_TTL_MS || 180000);

const nonceStore = new Map();

function issueNonce() {
  const nonce = crypto.randomBytes(24).toString("base64url");
  nonceStore.set(nonce, Date.now());
  return nonce;
}

function consumeNonceOnce(nonce) {
  const issuedAt = nonceStore.get(nonce);
  if (!issuedAt) return false;
  nonceStore.delete(nonce);
  return Date.now() - issuedAt <= nonceTtlMs;
}

function purgeOldNonces() {
  const now = Date.now();
  for (const [nonce, issuedAt] of nonceStore.entries()) {
    if (now - issuedAt > nonceTtlMs) nonceStore.delete(nonce);
  }
}
setInterval(purgeOldNonces, 60000).unref();

app.get("/healthz", (_req, res) => {
  res.json({ ok: true });
});

app.get("/integrity/nonce", (_req, res) => {
  res.json({ nonce: issueNonce() });
});

app.post("/integrity/verify", async (req, res) => {
  try {
    const integrityToken = req.body?.integrityToken;
    const nonce = req.body?.nonce;
    const requestPackage = req.body?.packageName;

    if (!integrityToken || !nonce || !requestPackage) {
      return res.status(400).json({ error: "Missing required fields" });
    }

    if (!packageName) {
      return res.status(500).json({ error: "ANDROID_PACKAGE_NAME is not configured" });
    }

    if (requestPackage !== packageName) {
      return res.status(400).json({ error: "Package name mismatch" });
    }

    if (!consumeNonceOnce(nonce)) {
      return res.status(400).json({ error: "Invalid or expired nonce" });
    }

    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/playintegrity"]
    });
    const client = await auth.getClient();

    const playintegrity = google.playintegrity({
      version: "v1",
      auth: client
    });

    const decodeResponse = await playintegrity.v1.decodeIntegrityToken({
      packageName,
      requestBody: { integrityToken }
    });

    const payload = decodeResponse.data.tokenPayloadExternal || {};
    const requestDetails = payload.requestDetails || {};
    const deviceVerdicts = payload.deviceIntegrity?.deviceRecognitionVerdict || [];
    const appVerdict = payload.appIntegrity?.appRecognitionVerdict || "";

    const nonceMatches = requestDetails.nonce === nonce;
    const packageMatches = requestDetails.requestPackageName === packageName;
    const meetsDeviceIntegrity = deviceVerdicts.includes("MEETS_DEVICE_INTEGRITY");
    const playRecognized = appVerdict === "PLAY_RECOGNIZED";

    const allowed = nonceMatches && packageMatches && playRecognized && meetsDeviceIntegrity;

    return res.json({
      allowed,
      meetsDeviceIntegrity,
      playRecognized,
      verdicts: deviceVerdicts
    });
  } catch (error) {
    const message = error?.response?.data?.error?.message || error.message || "Integrity verification failed";
    return res.status(500).json({ error: message });
  }
});

app.listen(port, () => {
  console.log(`Integrity backend listening on port ${port}`);
});
