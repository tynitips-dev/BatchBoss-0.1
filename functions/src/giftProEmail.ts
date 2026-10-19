import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

const resendApiKey = defineSecret("RESEND_API_KEY");
const db = getFirestore();
const escapeHtml = (value:string) => value.replace(/[&<>"']/g, character => (
  { "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[character] || character
));

export const sendBatchBossGiftProEmail = onDocumentCreated({
  document:"proGiftEmailEvents/{eventId}",
  region:"europe-west1",
  secrets:[resendApiKey],
}, async event => {
  const gift = event.data?.data();
  if(!gift || gift.type!=="gift_pro" || gift.durationDays!==7) return;
  const uid=String(gift.uid||"");
  if(!uid) return;
  const profile=await db.doc(`users/${uid}`).get();
  const current=profile.data();
  if(current?.subscriptionSource!=="admin_promotion" || current?.subscriptionStatus!=="active") return;
  const expiry=gift.expiresAt?.toDate?.();
  if(!(expiry instanceof Date) || !Number.isFinite(expiry.getTime()) || expiry.getTime()<=Date.now())return;
  const account=await getAuth().getUser(uid);
  const email=String(account.email||"").trim();
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))return;
  const id=event.params.eventId;
  const delivery=db.collection("emailDeliveries").doc(`gift_${id}`);
  try{
    await delivery.create({uid,type:"gift_pro",status:"sending",createdAt:FieldValue.serverTimestamp()});
  }catch(error:unknown){
    if(error && typeof error==="object" && "code" in error && error.code===6)return;
    throw error;
  }
  const firstName=escapeHtml(String(current.firstName||account.displayName||"Baker").trim().split(/\s+/)[0].slice(0,80));
  const expires=escapeHtml(expiry.toLocaleDateString("en-ZA",{day:"numeric",month:"long",year:"numeric",timeZone:"Africa/Johannesburg"}));
  const html=`<!doctype html><html><body style="margin:0;background:#fff4fa;font-family:Arial,sans-serif;color:#14213c">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 12px">
  <table role="presentation" width="100%" style="max-width:540px;background:white;border-radius:16px;overflow:hidden">
  <tr><td style="background:#14213c;color:white;padding:26px;text-align:center;font-size:28px;font-weight:bold">Batch<span style="color:#f26aa3">Boss</span></td></tr>
  <tr><td style="padding:32px"><h1 style="font-size:24px">7 days of BatchBoss Pro, on us!</h1>
  <p>Hi ${firstName},</p><p>Welcome to BatchBoss! We've gifted you <strong>7 days of Pro access</strong> to explore more ways to cost your bakes and run your bakery.</p>
  <p><strong>Your Pro access is active now.</strong><br>It expires on ${expires}.</p>
  <p>You won't need to enter card details. This gift does not start a paid subscription and won't automatically renew.</p>
  <p style="margin:30px 0"><a href="https://app.batchboss.co.za" style="background:#e94c93;color:white;padding:14px 20px;text-decoration:none;border-radius:8px;font-weight:bold">Explore BatchBoss Pro</a></p>
  <p>Bake Smarter. Price Better.<br>The BatchBoss Team</p></td></tr>
  <tr><td style="background:#fce8f2;padding:20px;text-align:center;font-size:12px">A complimentary account notification from BatchBoss.<br><a href="https://batchboss.co.za">batchboss.co.za</a></td></tr>
  </table></td></tr></table></body></html>`;
  try {
    const result=await fetch("https://api.resend.com/emails",{
      method:"POST",
      headers:{"Authorization":`Bearer ${resendApiKey.value()}`,"Content-Type":"application/json","Idempotency-Key":`batchboss-gift-${id}`},
      body:JSON.stringify({from:"BatchBoss <welcome@batchboss.co.za>",to:[email],subject:"A gift from BatchBoss: 7 days of Pro on us!",html}),
    });
    if(!result.ok){
      await delivery.set({status:"failed",httpStatus:result.status,updatedAt:FieldValue.serverTimestamp()},{merge:true});
      console.error("Gift Pro email failed",{status:result.status});
      return;
    }
    const response=await result.json() as {id?:string};
    await delivery.set({status:"sent",providerMessageId:String(response.id||""),sentAt:FieldValue.serverTimestamp()},{merge:true});
  }catch{
    await delivery.set({status:"failed",updatedAt:FieldValue.serverTimestamp()},{merge:true});
    console.error("Gift Pro email request failed");
  }
});
