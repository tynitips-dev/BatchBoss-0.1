"use strict";
const { validateEvent } = require("./referralContract");
const { ingestTestEvent } = require("./referralTestIngest");

// Proposed project IDs must be confirmed before provisioning.
const PROJECT = "batchboss-partner-stage-261106";
const CALLERS = {
  baker: "referral-sender@batchboss-baker-stage-261106.iam.gserviceaccount.com",
  supplier: "referral-sender@batchboss-supply-stage-261106.iam.gserviceaccount.com"
};

function stagingEnabled(env) {
  return env.GCLOUD_PROJECT === PROJECT &&
    (!env.GOOGLE_CLOUD_PROJECT || env.GOOGLE_CLOUD_PROJECT === PROJECT) &&
    env.BATCHBOSS_HOSTED_REFERRAL_TEST === "true" &&
    env.FUNCTIONS_EMULATOR !== "true" &&
    !env.FIRESTORE_EMULATOR_HOST && !env.FIREBASE_AUTH_EMULATOR_HOST;
}

function stagingEndpoints(onRequest, db, env = process.env, logger = console) {
  if (!stagingEnabled(env)) return {};
  const endpoint = source => onRequest({
    // Cloud Run IAM authenticates the caller before the handler runs.
    invoker: [CALLERS[source]],
    serviceAccount: "referral-receiver@" + PROJECT + ".iam.gserviceaccount.com",
    region: "us-central1",
    cors: false,
    maxInstances: 2
  }, async (req, res) => {
    if (!stagingEnabled(env)) return res.sendStatus(503);
    if (req.method !== "POST") return res.set("Allow", "POST").sendStatus(405);
    if (!req.is("application/json")) return res.sendStatus(415);
    if (!Buffer.isBuffer(req.rawBody) || req.rawBody.length > 4096) {
      return res.sendStatus(413);
    }
    if (req.body?.source !== source) return res.sendStatus(403);
    let event;
    try {
      event = validateEvent(req.body);
    } catch {
      return res.sendStatus(400);
    }
    try {
      return res.status(200).json(await ingestTestEvent(db(), event));
    } catch {
      // Never expose attribution or provider details in errors or logs.
      logger.warn("Staging referral ingestion did not commit");
      return res.sendStatus(503);
    }
  });
  return {
    stageBakerReferralEvent: endpoint("baker"),
    stageSupplierReferralEvent: endpoint("supplier")
  };
}

module.exports = { PROJECT, stagingEnabled, stagingEndpoints };
