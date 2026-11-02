import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

const db = getFirestore();
const resendKey = defineSecret("RESEND_API_KEY");
const sender = "BatchBoss <welcome@batchboss.co.za>";

type Mail = { subject: string; heading: string; message: string; lines?: string[] };
function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c] || c);
}
function day(value: unknown): string | null {
  const v = value as { toDate?: () => Date } | undefined;
  const date = typeof v?.toDate === "function" ? v.toDate() : typeof value === "string" ? new Date(value) : null;
  if (!date || !Number.isFinite(date.getTime())) return null;
  return date.toLocaleDateString("en-ZA", { timeZone: "Africa/Johannesburg", day: "numeric", month: "long", year: "numeric" });
}
function html(mail: Mail, firstName: string): string {
  const lines = (mail.lines || []).map(s => `<p style="font-size:14px">${escapeHtml(s)}</p>`).join("");
  return `<!doctype html><html><body style="background:#fff5fa;font-family:Arial,sans-serif;color:#14213c;margin:0">
   <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 12px">
   <table role="presentation" width="100%" style="max-width:540px;background:#fff;border-radius:16px;overflow:hidden">
   <tr><td style="background:#14213c;color:white;text-align:center;padding:25px;font-size:28px;font-weight:bold">Batch<span style="color:#f26aa3">Boss</span></td></tr>
   <tr><td style="padding:32px"><h1 style="font-size:24px">${escapeHtml(mail.heading)}</h1>
   <p>Hi ${escapeHtml(firstName)},</p><p>${escapeHtml(mail.message)}</p>${lines}
   <p style="padding:16px 0"><a href="https://app.batchboss.co.za" style="display:inline-block;padding:14px 20px;color:white;background:#e94c93;border-radius:8px;text-decoration:none;font-weight:bold">Manage BatchBoss</a></p>
   <p>Happy baking!<br>The BatchBoss Team</p></td></tr>
   <tr><td style="background:#fce8f2;padding:20px;text-align:center;font-size:12px">This is an account notification from BatchBoss.<br><a href="https://batchboss.co.za">batchboss.co.za</a></td></tr></table></td></tr></table></body></html>`;
}
async function sendOnce(id: string, uid: string, kind: string, mail: Mail) {
  const claim = db.collection("emailDeliveries").doc(id);
  const account = await getAuth().getUser(uid);
  const email = String(account.email || "");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return;
  const user = (await db.doc(`users/${uid}`).get()).data() || {};
  if (user.subscriptionSource !== "paystack" || user.subscriptionPlan !== "pro") return;
  try {
    await claim.create({ uid, kind, status: "sending", createdAt: FieldValue.serverTimestamp() });
  } catch (error: unknown) {
    if (error && typeof error === "object" && "code" in error && error.code === 6) return;
    throw error;
  }
  const firstName = String(user.firstName || account.displayName || "Baker").trim().split(/\s+/)[0].slice(0, 80);
  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { Authorization: `Bearer ${resendKey.value()}`, "Content-Type": "application/json", "Idempotency-Key": `batchboss-${id}` },
      body: JSON.stringify({ from: sender, to: [email], subject: mail.subject, html: html(mail, firstName) }),
    });
    if (!response.ok) {
      await claim.set({ status: "failed", httpStatus: response.status, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      console.error("Resend notification declined", kind, response.status);
      return;
    }
    const result = await response.json() as { id?: string };
    await claim.set({ status: "sent", providerMessageId: result.id || "", sentAt: FieldValue.serverTimestamp() }, { merge: true });
  } catch (error) {
    await claim.set({ status: "failed", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    console.error("Resend notification failed", kind, error);
  }
}

export const sendBatchBossFailedPaymentEmail = onDocumentCreated({
  document: "paymentEmailEvents/{eventId}", region: "europe-west1", secrets: [resendKey],
}, async event => {
  const data = event.data?.data();
  if (!data || data.type !== "failed_payment" || data.domain !== "live" || typeof data.uid !== "string") return;
  const amount = Number(data.amount);
  await sendOnce(`failed_${event.params.eventId}`, data.uid, "failed_payment", {
    subject: "Action needed: Your BatchBoss payment was unsuccessful",
    heading: "We couldn't process your payment",
    message: "Paystack reported that your subscription payment was unsuccessful. Please check your payment method to avoid an interruption to your Pro access.",
    lines: [`Plan: ${data.interval === "annual" ? "Annual" : "Monthly"}`, ...(Number.isFinite(amount) && amount > 0 ? [`Amount: R${(amount / 100).toFixed(2)}`] : [])],
  });
});

export const sendBatchBossCancellationEmail = onDocumentUpdated({
  document: "paystackSubscriptions/{code}", region: "europe-west1", secrets: [resendKey],
}, async event => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after || after.domain !== "live" || typeof after.uid !== "string") return;
  const ended = ["cancelled", "completed", "complete", "non-renewing", "disabled"];
  const oldStatus = String(before?.status || "").toLowerCase();
  const status = String(after.status || "").toLowerCase();
  if (!ended.includes(status) || ended.includes(oldStatus)) return;
  const profile = (await db.doc(`users/${after.uid}`).get()).data() || {};
  const ending = day(profile.subscriptionExpiresAt);
  await sendOnce(`cancel_${event.params.code}`, after.uid, "cancellation", {
    subject: "Your BatchBoss Pro subscription change",
    heading: "Your Pro subscription has been cancelled",
    message: "Paystack has confirmed that your subscription is no longer set to renew.",
    lines: [ending ? `Your recorded Pro access end date: ${ending}` : "Check your account for your remaining Pro access period.", "Your free BatchBoss account remains available."],
  });
});

export const sendBatchBossRenewalReminders = onSchedule({
  region: "europe-west1", schedule: "every day 08:00", timeZone: "Africa/Johannesburg", secrets: [resendKey],
}, async () => {
  const users = await db.collection("users").where("subscriptionSource", "==", "paystack").get();
  const today = new Date();
  for (const snapshot of users.docs) {
    const user = snapshot.data();
    if (user.subscriptionPlan !== "pro" || user.subscriptionStatus !== "active" ||
        user.subscriptionRenews !== true || user.paystackSubscriptionStatus !== "active") continue;
    const expiry = user.subscriptionExpiresAt?.toDate?.() as Date | undefined;
    if (!expiry || !Number.isFinite(expiry.getTime())) continue;
    const days = Math.ceil((expiry.getTime() - today.getTime()) / 86400000);
    if (days < 2 || days > 3) continue;
    const date = day(user.subscriptionExpiresAt);
    if (!date) continue;
    const amount = user.subscriptionBillingInterval === "annual" ? "R1,199.00" : "R149.00";
    // These are regular plan prices only. Do not guess a promotional renewal amount.
    await sendOnce(`renewal_${snapshot.id}_${expiry.toISOString().slice(0, 10)}`, snapshot.id, "renewal", {
      subject: "Your BatchBoss Pro renewal is approaching",
      heading: "A friendly renewal reminder",
      message: "Your BatchBoss subscription is approaching its next billing date. Check your subscription settings to confirm the amount and whether automatic renewal is enabled.",
      lines: [`Recorded end of current billing period: ${date}`, "Your actual renewal amount may differ if you are on a promotion.", "You can manage your subscription from your BatchBoss account."],
    });
  }
});
