import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

const resendApiKey = defineSecret("RESEND_API_KEY");
const db = getFirestore();

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character] || character);
}

export const sendBatchBossWelcomeEmail = onDocumentCreated(
  {
    document: "users/{uid}",
    region: "europe-west1",
    secrets: [resendApiKey],
  },
  async event => {
    const snapshot = event.data;
    if (!snapshot) return;
    const user = snapshot.data();
    const email = String(user.email || "").trim();
    const firstName = String(user.firstName || "").trim().slice(0, 80);
    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      console.warn("Welcome email skipped: missing or invalid email", event.params.uid);
      return;
    }

    const record = db.collection("emailDeliveries").doc(`welcome_${event.params.uid}`);
    // A one-time claim prevents duplicate messages from repeated trigger delivery.
    // Failed requests are recorded for administrator review, not silently retried.
    try {
      await record.create({
        type: "welcome",
        uid: event.params.uid,
        status: "sending",
        createdAt: FieldValue.serverTimestamp(),
      });
    } catch (error: unknown) {
      if (error && typeof error === "object" && "code" in error && error.code === 6) return;
      throw error;
    }

    const name = escapeHtml(firstName || "Baker");
    const html = `<!doctype html><html><body style="margin:0;background:#fff5fa;font-family:Arial,sans-serif;color:#172343">
      <table role="presentation" cellpadding="0" cellspacing="0" width="100%"><tr><td align="center" style="padding:32px 12px">
        <table role="presentation" width="100%" style="max-width:540px;background:#ffffff;border-radius:16px;overflow:hidden">
          <tr><td style="background:#14213c;color:white;text-align:center;padding:28px;font-size:28px;font-weight:700">Batch<span style="color:#f26aa3">Boss</span></td></tr>
          <tr><td style="padding:32px">
            <h1 style="font-size:25px;color:#14213c">Welcome to BatchBoss, ${name}! 🧁</h1>
            <p>Your baking business deserves more than guesswork.</p>
            <p>Get started with your free account: add ingredients and their prices, organise recipes and explore your baking costs.</p>
            <p style="padding:16px 0"><a href="https://app.batchboss.co.za" style="background:#e94c93;color:#ffffff;padding:13px 22px;border-radius:8px;text-decoration:none;font-weight:bold">Open BatchBoss</a></p>
            <p>Bake Smarter. Price Better.<br>The BatchBoss Team</p>
          </td></tr><tr><td style="padding:22px;background:#fce8f2;color:#536073;text-align:center;font-size:12px">
            You received this account email because you registered for BatchBoss.<br>
            <a href="https://batchboss.co.za">batchboss.co.za</a>
          </td></tr>
        </table>
      </td></tr></table></body></html>`;

    try {
      const response = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${resendApiKey.value()}`,
          "Content-Type": "application/json",
          "Idempotency-Key": `batchboss-welcome-${event.params.uid}`,
        },
        body: JSON.stringify({
          from: "BatchBoss <welcome@batchboss.co.za>",
          to: [email],
          subject: "Welcome to BatchBoss! Let's get baking 🧁",
          html,
        }),
      });
      if (!response.ok) {
        console.error("Resend welcome failed", response.status, event.params.uid);
        await record.set({ status: "failed", httpStatus: response.status, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        return;
      }
      const result = await response.json() as { id?: string };
      await record.set({
        status: "sent",
        providerMessageId: String(result.id || ""),
        sentAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    } catch (error) {
      console.error("Resend welcome request failed", event.params.uid, error);
      await record.set({ status: "failed", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
  }
);
