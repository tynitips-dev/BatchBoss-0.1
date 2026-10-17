import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";

const resendApiKey = defineSecret("RESEND_API_KEY");
const db = getFirestore();

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char] || char);
}

export const sendBatchBossProPaymentEmail = onDocumentUpdated(
  {
    document: "subscriptionPayments/{reference}",
    region: "europe-west1",
    secrets: [resendApiKey],
  },
  async event => {
    const before = event.data?.before.data();
    const payment = event.data?.after.data();
    if (!payment || before?.status === "success" ||
        payment.status !== "success" || payment.provider !== "paystack" ||
        payment.domain !== "live") return;

    const uid = String(payment.uid || "");
    const reference = event.params.reference;
    if (!uid || !/^[a-zA-Z0-9_.-]{1,100}$/.test(reference)) return;

    const profile = await db.doc(`users/${uid}`).get();
    if (profile.data()?.subscriptionSource !== "paystack" ||
        profile.data()?.subscriptionPlan !== "pro") return;

    const account = await getAuth().getUser(uid);
    const email = String(account.email || "").trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return;

    const delivered = db.collection("emailDeliveries").doc(`paystack_${reference}`);
    try {
      await delivered.create({
        type: "pro_payment",
        uid,
        reference,
        status: "sending",
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (error: unknown) {
      if (error && typeof error === "object" && "code" in error && error.code === 6) return;
      throw error;
    }

    const interval = payment.interval === "annual" ? "Annual" : "Monthly";
    const amountInCents = Number(payment.amount);
    const amount = Number.isFinite(amountInCents) && amountInCents > 0
      ? (amountInCents / 100).toFixed(2) : null;
    const firstName = escapeHtml(String(profile.data()?.firstName || account.displayName || "Baker").trim().split(/\s+/)[0].slice(0, 80));
    const paidDate = typeof payment.paidAt === "string" && !Number.isNaN(Date.parse(payment.paidAt))
      ? new Date(payment.paidAt).toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric", timeZone: "Africa/Johannesburg" })
      : "See your Paystack payment record";
    const html = `<!doctype html><html><body style="background:#fff4fa;font-family:Arial,sans-serif;color:#14213c;margin:0">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" style="max-width:540px;background:white;border-radius:16px;overflow:hidden">
        <tr><td style="background:#14213c;color:white;padding:26px;text-align:center;font-size:28px;font-weight:bold">Batch<span style="color:#f26aa3">Boss</span></td></tr>
        <tr><td style="padding:32px">
          <h1 style="font-size:24px">You're officially a BatchBoss Pro! 💗</h1>
          <p>Hi ${firstName},</p><p>Your payment has been confirmed and your Pro subscription is active. Thank you for choosing BatchBoss!</p>
          <p><strong>Plan:</strong> Pro ${interval}<br>
          ${amount ? `<strong>Amount paid:</strong> R${amount}<br>` : ""}
          <strong>Payment date:</strong> ${escapeHtml(paidDate)}<br>
          <strong>Payment reference:</strong> ${escapeHtml(reference)}</p>
          <p style="margin:30px 0"><a href="https://app.batchboss.co.za" style="background:#e94c93;color:white;padding:14px 20px;text-decoration:none;border-radius:8px;font-weight:bold">Open BatchBoss Pro</a></p>
          <p>Your subscription details and management options are available in your account.</p>
          <p>Happy baking!<br>The BatchBoss Team</p>
        </td></tr>
        <tr><td style="background:#fce8f2;padding:20px;text-align:center;font-size:12px">This is a payment confirmation, not a tax invoice.<br><a href="https://batchboss.co.za">batchboss.co.za</a></td></tr>
      </table>
    </td></tr></table></body></html>`;

    try {
      const result = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${resendApiKey.value()}`,
          "Content-Type": "application/json",
          "Idempotency-Key": `batchboss-pro-${reference}`,
        },
        body: JSON.stringify({
          from: "BatchBoss <welcome@batchboss.co.za>",
          to: [email],
          subject: "Your BatchBoss Pro subscription is active! 💗",
          html,
        }),
      });
      if (!result.ok) {
        console.error("Pro email sending failed", { reference, status: result.status });
        await delivered.set({ status: "failed", httpStatus: result.status, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        return;
      }
      const response = await result.json() as { id?: string };
      await delivered.set({
        status: "sent",
        providerMessageId: String(response.id || ""),
        sentAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    } catch (error) {
      console.error("Pro email request failed", { reference, error });
      await delivered.set({ status: "failed", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
  }
);
