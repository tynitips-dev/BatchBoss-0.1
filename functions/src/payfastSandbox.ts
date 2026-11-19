import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";

// These functions can never select the live gateway or write production entitlements.
const merchantId=defineSecret("PAYFAST_SANDBOX_MERCHANT_ID");
const merchantKey=defineSecret("PAYFAST_SANDBOX_MERCHANT_KEY");
const passphrase=defineSecret("PAYFAST_SANDBOX_PASSPHRASE");
const secrets=[merchantId,merchantKey,passphrase];
const origin="https://admin.batchboss.co.za";
const host="https://sandbox.payfast.co.za";
const plans={weekly:{amount:"39.99",frequency:"2"},monthly:{amount:"149.99",frequency:"3"},annual:{amount:"1199.00",frequency:"6"}} as const;
type Plan=keyof typeof plans;
const encode=(v:string)=>encodeURIComponent(v).replace(/%20/g,"+").replace(/[!'()*~]/g,c=>"%"+c.charCodeAt(0).toString(16).toUpperCase());
const hash=(raw:string)=>createHash("md5").update(raw+"&passphrase="+encode(passphrase.value().trim())).digest("hex");
const canonical=(entries:[string,string][])=>entries.map(([k,v])=>k+"="+encode(v)).join("&");
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const db=()=>getFirestore();
function billingDate(plan:Plan){
  const d=new Date(Date.now()+2*3600000);
  if(plan==="weekly")d.setUTCDate(d.getUTCDate()+7);
  else {const day=d.getUTCDate();d.setUTCDate(1);d.setUTCMonth(d.getUTCMonth()+(plan==="annual"?12:1));d.setUTCDate(Math.min(day,new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate()));}
  return d.toISOString().slice(0,10);
}
export const createPayfastSandboxCheckout=onCall({region:"europe-west1",invoker:"public",cors:[origin],secrets},async req=>{
  if(req.auth?.token.admin!==true)throw new HttpsError("permission-denied","Administrator access required.");
  if(req.data?.confirmTest!==true)throw new HttpsError("invalid-argument","Confirm sandbox testing.");
  const plan=req.data?.plan as Plan;
  if(!Object.prototype.hasOwnProperty.call(plans,plan))throw new HttpsError("invalid-argument","Unknown plan.");
  const p=plans[plan];
  const id=randomUUID(), next=billingDate(plan);
  const fields:Record<string,string>={merchant_id:merchantId.value().trim(),merchant_key:merchantKey.value().trim(),
    return_url:origin+"/?payfastSandbox=returned",cancel_url:origin+"/?payfastSandbox=cancelled",
    notify_url:"https://europe-west1-batch-boss-android.cloudfunctions.net/payfastSandboxNotify",
    m_payment_id:id,amount:p.amount,item_name:"BatchBoss Sandbox "+plan,
    payment_method:"cc",subscription_type:"1",billing_date:next,recurring_amount:p.amount,frequency:p.frequency,cycles:"0"};
  fields.signature=hash(canonical(Object.entries(fields).filter(([,v])=>v!=="")));
  await db().doc("payfastSandboxSessions/"+id).create({uid:req.auth.uid,plan,amount:p.amount,frequency:p.frequency,billingDate:next,status:"pending",environment:"sandbox",createdAt:FieldValue.serverTimestamp()});
  return {environment:"sandbox",reference:id,checkoutUrl:host+"/eng/process",fields};
});
export const payfastSandboxNotify=onRequest({region:"europe-west1",invoker:"public",secrets},async(req,res)=>{
  try{
    if(req.method!=="POST"){res.status(405).send("Method not allowed");return;}
    const entries=[...new URLSearchParams(req.rawBody.toString("utf8")).entries()];
    if(entries.length>80||entries.some(([k])=>!/^[a-zA-Z0-9_]+$/.test(k))||new Set(entries.map(([k])=>k)).size!==entries.length){res.status(400).send("Invalid fields");return;}
    const f=Object.fromEntries(entries);
    const raw=canonical(entries.filter(([k])=>k!=="signature"));
    if(!/^[a-f0-9]{32}$/i.test(f.signature||"")||!timingSafeEqual(Buffer.from(f.signature.toLowerCase()),Buffer.from(hash(raw)))||f.merchant_id!==merchantId.value().trim()){res.status(400).send("Invalid signature or merchant");return;}
    const ref=f.m_payment_id, tx=f.pf_payment_id, status=f.payment_status;
    if(!uuid.test(ref||"")||!/^[a-zA-Z0-9_-]{1,100}$/.test(tx||"")||!["COMPLETE","CANCELLED","FAILED"].includes(status)){res.status(400).send("Invalid reference or status");return;}
    const session=db().doc("payfastSandboxSessions/"+ref);
    const snapshot=await session.get();
    const expected=snapshot.data();
    if(!expected||expected.environment!=="sandbox"){res.status(404).send("Unknown sandbox session");return;}
    if(status==="COMPLETE"&&(!/^\d+(\.\d{1,2})?$/.test(f.amount_gross||"")||Math.round(Number(f.amount_gross)*100)!==Math.round(Number(expected.amount)*100)||!uuid.test(f.token||""))){res.status(400).send("Amount or token mismatch");return;}
    const validation=await fetch(host+"/eng/query/validate",{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},body:raw,signal:AbortSignal.timeout(15000),redirect:"error"});
    if(!validation.ok){res.status(503).send("Validation unavailable");return;}
    if((await validation.text()).trim()!=="VALID"){res.status(400).send("Invalid sandbox notification");return;}
    const event=db().doc("payfastSandboxEvents/"+tx+"_"+status);
    await db().runTransaction(async t=>{
      const [e,s]=await Promise.all([t.get(event),t.get(session)]);
      if(e.exists)return;
      if(s.data()?.environment!=="sandbox")throw Error("Unknown session");
      t.create(event,{reference:ref,transactionId:tx,status,environment:"sandbox",recordedAt:FieldValue.serverTimestamp()});
      t.update(session,{status,transactionId:tx,signatureVerified:true,serverValidated:true,subscriptionTokenReceived:uuid.test(f.token||""),updatedAt:FieldValue.serverTimestamp()});
    });
    // No live account updates, commission, email, or charge. Trusted source IP checks
    // remain a required production launch gate; sandbox records are diagnostic only.
    res.status(200).send("OK");
  }catch{res.status(500).send("Sandbox notification failed");}
});
export const getPayfastSandboxResult=onCall({region:"europe-west1",invoker:"public",cors:[origin]},async req=>{
  if(req.auth?.token.admin!==true)throw new HttpsError("permission-denied","Administrator access required.");
  const id=String(req.data?.reference||"");
  if(!uuid.test(id))throw new HttpsError("invalid-argument","Invalid sandbox reference.");
  const p=(await db().doc("payfastSandboxSessions/"+id).get()).data();
  if(!p||p.uid!==req.auth.uid)throw new HttpsError("not-found","Sandbox session not found.");
  return {reference:id,environment:"sandbox",plan:p.plan,amount:p.amount,frequency:p.frequency,billingDate:p.billingDate,status:p.status,
    signatureVerified:p.signatureVerified===true,serverValidated:p.serverValidated===true,subscriptionTokenReceived:p.subscriptionTokenReceived===true};
});
