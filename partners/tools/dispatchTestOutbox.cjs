"use strict";
// LOCAL TEST TOOL ONLY. There is no scheduler, deployment hook or hosted dispatcher.
const { createRequire } = require("node:module");
const sdk = createRequire(require("node:path").join(__dirname, "../functions/package.json"));
const { initializeApp, deleteApp } = sdk("firebase-admin/app");
const { getFirestore } = sdk("firebase-admin/firestore");
const { getAuth } = sdk("firebase-admin/auth");
const { validateEvent, digest } = require("../functions/referralContract");

function settings(env) {
 const source=env.BATCHBOSS_TEST_SOURCE, sourceProject=env.BATCHBOSS_TEST_SOURCE_PROJECT, partnerProject=env.BATCHBOSS_TEST_PARTNER_PROJECT;
 if(env.BATCHBOSS_PARTNER_TEST_ENABLED!=="true"||!["baker","supplier"].includes(source)||
  !/^demo-[a-z0-9-]+$/.test(sourceProject||"")||!/^demo-[a-z0-9-]+$/.test(partnerProject||"")||sourceProject===partnerProject||
  !/^127\.0\.0\.1:[0-9]{2,5}$/.test(env.FIRESTORE_EMULATOR_HOST||"")||
  !/^127\.0\.0\.1:[0-9]{2,5}$/.test(env.FIREBASE_AUTH_EMULATOR_HOST||""))
  throw new Error("Local referral delivery requires distinct demo projects and loopback emulators.");
 const receiver=new URL(env.BATCHBOSS_TEST_RECEIVER||"");
 if(receiver.protocol!=="http:"||receiver.hostname!=="127.0.0.1"||!receiver.port||receiver.username||receiver.password||
    receiver.search||receiver.hash||receiver.pathname!=="/"+partnerProject+"/us-central1/ingestPartnerTestEvent")
  throw new Error("Only the exact local test receiver is allowed.");
 return {source,sourceProject,partnerProject,receiver:receiver.href};
}
async function drainTestOutbox(db, source, send) {
 if(!/^127\.0\.0\.1:[0-9]{2,5}$/.test(process.env.FIRESTORE_EMULATOR_HOST||"")||!["baker","supplier"].includes(source)||!/^demo-/.test(db.projectId))throw new Error("Invalid test source");
 const result={delivered:0,pending:0};
 // Registrations go first; payment-before-registration remains pending for a later pass.
 for(const kind of ["registration","payment"]) {
  const page=await db.collection("partnerTestOutbox").where("status","==","pending").where("event.kind","==",kind).limit(100).get();
  for(const item of page.docs) {
   try {
    const event=validateEvent(item.get("event"));
    if(event.source!==source||event.eventId!==item.id)throw new Error("Source mismatch");
    const reply=await send(event);
    if(reply?.accepted!==true||reply?.payable!==false)throw new Error("Receiver did not acknowledge a test event");
    await db.runTransaction(async t=>{
     const current=await t.get(item.ref);
     if(digest(current.get("event"))!==digest(event))throw new Error("Outbox event changed");
     if(current.get("status")==="pending")t.update(item.ref,{status:"delivered",deliveredAtMillis:Date.now()});
    });
    result.delivered++;
   } catch {
    // Do not persist tokens, remote error bodies, emails or internal account identifiers.
    result.pending++;
   }
  }
 }
 return result;
}
async function run(env=process.env) {
 const config=settings(env);
 const sourceApp=initializeApp({projectId:config.sourceProject},"referral-source-"+Date.now());
 const targetApp=initializeApp({projectId:config.partnerProject},"referral-target-"+Date.now());
 try {
  const auth=getAuth(targetApp), uid="referral-bridge-"+config.source;
  try { await auth.createUser({uid,email:uid+"@example.test",emailVerified:true}); }
  catch(error) { if(error.code!=="auth/uid-already-exists"&&error.code!=="auth/email-already-exists")throw error; }
  await auth.updateUser(uid,{emailVerified:true});
  await auth.setCustomUserClaims(uid,{batchbossPartnerAdmin:true,batchbossPartnerBridgeSource:config.source});
  const customToken=await auth.createCustomToken(uid);
  const signIn=await fetch("http://"+env.FIREBASE_AUTH_EMULATOR_HOST+"/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=emulator-only",{
   method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer owner"},body:JSON.stringify({token:customToken,returnSecureToken:true,targetProjectId:config.partnerProject}),redirect:"error"
  });
  if(!signIn.ok)throw new Error("Local test authentication failed");
  const {idToken}=await signIn.json();
  if(typeof idToken!=="string")throw new Error("Missing local test identity");
  return await drainTestOutbox(getFirestore(sourceApp),config.source,async event=>{
   const response=await fetch(config.receiver,{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+idToken},
    body:JSON.stringify({data:event}),redirect:"error",signal:AbortSignal.timeout(10000)});
   if(!response.ok)throw new Error("Test receiver unavailable");
   return (await response.json()).result;
  });
 } finally { await Promise.all([deleteApp(sourceApp),deleteApp(targetApp)]); }
}
module.exports={settings,drainTestOutbox,run};
if(require.main===module)run().then(result=>console.log(JSON.stringify(result))).catch(()=>{console.error("Local test delivery failed; events remain pending.");process.exitCode=1;});
