"use strict";
const {test,after}=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),http=require("node:http");
const {createRequire}=require("node:module");
const bakerRoot=path.resolve(process.env.BATCHBOSS_BAKER_SOURCE||path.join(__dirname,".."));
const partnerRoot=path.resolve(process.env.BATCHBOSS_PARTNER_SOURCE||"");
const supplierRoot=process.env.BATCHBOSS_SUPPLIER_SOURCE&&path.resolve(process.env.BATCHBOSS_SUPPLIER_SOURCE);
const sdk=createRequire(path.join(bakerRoot,"functions/package.json"));
const {initializeApp,deleteApp}=sdk("firebase-admin/app");
const {getFirestore,FieldValue}=sdk("firebase-admin/firestore");
const {getAuth}=sdk("firebase-admin/auth");
if(!/^127\.0\.0\.1:[0-9]+$/.test(process.env.FIRESTORE_EMULATOR_HOST||"")||
 !/^127\.0\.0\.1:[0-9]+$/.test(process.env.FIREBASE_AUTH_EMULATOR_HOST||""))
 throw new Error("Tests require loopback Firestore and Auth emulators.");
const sourceProject="demo-batchboss-baker",partnerProject="demo-batchboss-partners",supplierProject="demo-batchboss-suppliers";
process.env.FUNCTIONS_EMULATOR="true";process.env.GCLOUD_PROJECT=sourceProject;
process.env.BATCHBOSS_PARTNER_TEST_ENABLED="true";
process.env.PAYSTACK_SECRET_KEY="sk_test_fixture";
process.env.PAYSTACK_ENABLED="true";
process.env.PAYSTACK_MONTHLY_PLAN_CODE="PLN_testMonthly";
process.env.PAYSTACK_ANNUAL_PLAN_CODE="PLN_testAnnual";
const bakerApp=initializeApp({projectId:sourceProject});
const partnerApp=initializeApp({projectId:partnerProject},"partner-tests");
const supplierApp=initializeApp({projectId:supplierProject},"supplier-tests");
const bakerDb=getFirestore(bakerApp),partnerDb=getFirestore(partnerApp),supplierDb=getFirestore(supplierApp);
const {ingestTestEvent}=require(path.join(partnerRoot,"partners/functions/referralTestIngest"));
const {validateEvent}=require(path.join(partnerRoot,"partners/functions/referralContract"));
const {settings,drainTestOutbox,run}=require(path.join(partnerRoot,"partners/tools/dispatchTestOutbox.cjs"));
const nativeFetch=global.fetch;
let amountOverride=null;
let lastBakerReference="";
global.fetch=async(url,options={})=>{
 const parsed=new URL(url);
 if(parsed.hostname==="api.paystack.co") {
  const body=options.body?JSON.parse(options.body):{};
  let data;
  if(parsed.pathname.startsWith("/plan/"))data={amount:14900,currency:"ZAR",interval:"monthly",domain:"test"};
  else if(parsed.pathname==="/customer")data={customer_code:"CUS_fixture"};
  else if(parsed.pathname==="/transaction/initialize"){
   assert.equal(JSON.stringify(body.metadata).includes("baker-private"),false);
   assert.equal(JSON.stringify(body.metadata).includes("BB-ABCDEF123456"),false);
   lastBakerReference=body.reference;
   data={authorization_url:"https://checkout.paystack.com/fixture",reference:body.reference};
  } else if(parsed.pathname.startsWith("/transaction/verify/"))data={
   reference:parsed.pathname.split("/").at(-1),status:"success",amount:amountOverride??14900,currency:"ZAR",
   customer:{customer_code:"CUS_fixture"},plan:{plan_code:"PLN_testMonthly"},domain:"test",paid_at:"2026-10-29T12:00:00Z",id:123
  }; else throw new Error("Unexpected provider call");
  return {ok:true,status:200,json:async()=>({status:true,data})};
 }
 if(parsed.hostname!=="127.0.0.1")throw new Error("External network forbidden in referral tests");
 return nativeFetch(url,options);
};
function load(file,db,auth=getAuth(bakerApp),env=process.env,provider=global.fetch) {
 const req=createRequire(file),module={exports:{}};
 // SDK transaction callbacks require a Promise from the SDK's realm, even when handler code runs in a VM.
 const transactionDb=new Proxy(db,{get(target,key){
  if(key==="runTransaction")return callback=>target.runTransaction(async tx=>await callback(tx));
  const value=Reflect.get(target,key,target);return typeof value==="function"?value.bind(target):value;
 }});
 class HttpsError extends Error {constructor(code,message){super(message);this.code=code;}}
 const localRequire=name=>{
  if(name==="firebase-admin/firestore")return {getFirestore:()=>transactionDb,FieldValue};
  if(name==="firebase-admin/app")return {initializeApp:()=>{},getApps:()=>[{}]};
  if(name==="firebase-admin/auth")return {getAuth:()=>auth};
  if(name==="firebase-functions/v2/https")return {HttpsError,onCall:(...args)=>args.at(-1),onRequest:(...args)=>args.at(-1)};
  if(name==="firebase-functions/v2/scheduler")return {onSchedule:(...args)=>args.at(-1)};
  if(name==="firebase-functions/params")return {defineSecret:n=>({value:()=>env[n]}),defineString:(n,o)=>({value:()=>env[n]??o?.default??""})};
  if(name.startsWith("./"))return load(req.resolve(name),db,auth,env,provider);
  return req(name);
 };
 vm.runInNewContext(fs.readFileSync(file,"utf8"),{module,exports:module.exports,require:localRequire,process:{env},
  fetch:provider,URL,Buffer,AbortSignal,console,Date},{filename:file});
 return module.exports;
}
async function rules(project,file){
 const response=await nativeFetch("http://"+process.env.FIRESTORE_EMULATOR_HOST+"/emulator/v1/projects/"+project+"/databases/(default):securityRules",{
 method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({rules:{files:[{name:"firestore.rules",content:fs.readFileSync(file,"utf8")}]}})});
 assert.equal(response.ok,true,await response.text());
}
async function idToken(app,uid,claims={}) {
 const auth=getAuth(app);
 try{await auth.createUser({uid,email:uid+"@example.test",emailVerified:true});}catch(e){if(e.code!=="auth/uid-already-exists")throw e;}
 await auth.setCustomUserClaims(uid,claims);
 const customToken=await auth.createCustomToken(uid);
 const response=await nativeFetch("http://"+process.env.FIREBASE_AUTH_EMULATOR_HOST+"/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=emulator-only",{
 method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer owner"},body:JSON.stringify({token:customToken,returnSecureToken:true,targetProjectId:app.options.projectId})});
 const result=await response.json();assert.equal(response.ok,true,result.error?.message);return result.idToken;
}
async function clientRead(project,document,token) {
 return nativeFetch("http://"+process.env.FIRESTORE_EMULATOR_HOST+"/v1/projects/"+project+"/databases/(default)/documents/"+document,{
 headers:{Authorization:"Bearer "+token}});
}
let server;
after(async()=>{global.fetch=nativeFetch;if(server)await new Promise(resolve=>server.close(resolve));await Promise.all([bakerApp,partnerApp,supplierApp].map(deleteApp));});

test("actual baker handlers and isolated project delivery: signup -> checkout -> verified receipt -> first-sale test credit",async()=>{
 await rules(sourceProject,path.join(bakerRoot,"web/firestore.rules"));
 await rules(partnerProject,path.join(partnerRoot,"partners/firestore.rules"));
 if(supplierRoot)await rules(supplierProject,path.join(supplierRoot,"firestore.rules"));
 const code="BB-ABCDEF123456";
 await partnerDb.doc("partnerCodes/"+code).set({partnerUid:"test-agent"});
 await partnerDb.doc("partnerProfiles/test-agent").set({status:"approved"});
 const token=await idToken(bakerApp,"baker-private");
 const decoded=await getAuth(bakerApp).verifyIdToken(token);
 const request={auth:{uid:decoded.uid,token:decoded},data:{firstName:"Test",surname:"Baker",bakeryName:"Fixture bakery",referralCode:code}};
 const signup=load(path.join(bakerRoot,"functions/lib/partnerReferral.js"),bakerDb);
 await signup.registerBakerTest(request);
 await signup.registerBakerTest(request);
 await assert.rejects(signup.registerBakerTest({...request,data:{...request.data,referralCode:"BB-123456ABCDEF"}}));
 assert.equal((await bakerDb.collection("partnerTestOutbox").get()).size,1);
 assert.equal((await clientRead(sourceProject,"bakerPartnerReferrals/baker-private",token)).status,403);
 const pay=load(path.join(bakerRoot,"functions/lib/paystack.js"),bakerDb);
 await pay.createPaystackCheckout({...request,data:{plan:"monthly"}});
 assert.match(lastBakerReference,/^BB-/);
 amountOverride=1;
 await assert.rejects(pay.verifyPaystackPayment({...request,data:{reference:lastBakerReference}}));
 assert.equal((await bakerDb.collection("partnerTestOutbox").get()).size,1);
 amountOverride=null;
 const verify={...request,data:{reference:lastBakerReference}};
 await Promise.all([pay.verifyPaystackPayment(verify),pay.verifyPaystackPayment(verify)]);
 assert.equal((await bakerDb.doc("users/baker-private").get()).get("subscriptionStatus"),"free");
 assert.equal((await bakerDb.collection("partnerTestOutbox").get()).size,2);
 const forbidden=["baker-private",decoded.email];
 for(const doc of (await bakerDb.collection("partnerTestOutbox").get()).docs){
  validateEvent(doc.get("event"));
  for(const value of forbidden)assert.equal(JSON.stringify(doc.get("event")).includes(value),false);
 }
 // The local HTTP fixture authenticates real Auth-emulator ID tokens before invoking the actual callable handler.
 const central=load(path.join(partnerRoot,"partners/functions/index.js"),partnerDb,getAuth(partnerApp),{...process.env,GCLOUD_PROJECT:partnerProject});
 let dropAck=true;
 server=http.createServer(async(req,res)=>{
  try{
   const token=String(req.headers.authorization||"").replace(/^Bearer /,"");
   let decoded;
   try{decoded=await getAuth(partnerApp).verifyIdToken(token);}catch{res.writeHead(401);res.end();return;}
   let raw="";for await(const chunk of req){raw+=chunk;if(raw.length>32768)throw new Error("Too large");}
   const data=JSON.parse(raw).data;
   const result=await central.ingestPartnerTestEvent({auth:{uid:decoded.uid,token:decoded},data});
   if(data.kind==="payment"&&dropAck){dropAck=false;res.writeHead(503);res.end();return;}
   res.writeHead(200,{"Content-Type":"application/json"});res.end(JSON.stringify({result}));
  }catch(error){res.writeHead(error.code==="permission-denied"?403:400);res.end();}
 });
 await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
 const receiver="http://127.0.0.1:"+server.address().port+"/"+partnerProject+"/us-central1/ingestPartnerTestEvent";
 const env={...process.env,BATCHBOSS_TEST_SOURCE:"baker",BATCHBOSS_TEST_SOURCE_PROJECT:sourceProject,
 BATCHBOSS_TEST_PARTNER_PROJECT:partnerProject,BATCHBOSS_TEST_RECEIVER:receiver};
 assert.equal((await nativeFetch(receiver,{method:"POST",body:"{}"})).status,401);
 const first=await run(env);assert.equal(first.delivered,1);assert.equal(first.pending,1);
 assert.equal((await partnerDb.collection("partnerTestFirstSales").get()).size,1);
 const retry=await run(env);assert.equal(retry.delivered,1);assert.equal(retry.pending,0);
 assert.equal((await partnerDb.collection("partnerTestFirstSales").get()).size,1);
 assert.equal((await partnerDb.collection("partnerCommissions").get()).size,0);
 const sourceToken=await idToken(partnerApp,"source-baker",{batchbossPartnerAdmin:true,batchbossPartnerBridgeSource:"baker"});
 const bakerEvent=(await bakerDb.collection("partnerTestOutbox").get()).docs[0].get("event");
 const spoofed=await nativeFetch(receiver,{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+sourceToken},
 body:JSON.stringify({data:{...bakerEvent,source:"supplier"}})});
 assert.equal(spoofed.status,403);
 assert.equal((await clientRead(partnerProject,"partnerTestEvents/"+bakerEvent.eventId,sourceToken)).status,403);
 for(const bad of [{BATCHBOSS_TEST_SOURCE_PROJECT:"batch-boss-android"},{BATCHBOSS_TEST_RECEIVER:"https://partners.batchboss.co.za/"},{FIRESTORE_EMULATOR_HOST:"firestore.googleapis.com:443"},{BATCHBOSS_TEST_PARTNER_PROJECT:sourceProject}])
  assert.throws(()=>settings({...env,...bad}));
 const noLive=load(path.join(bakerRoot,"functions/lib/paystack.js"),bakerDb,getAuth(bakerApp),{...process.env,PAYSTACK_SECRET_KEY:"sk_live_forbidden"});
 await assert.rejects(noLive.createPaystackCheckout({...request,data:{plan:"monthly"}}),/Live checkout is disabled/);
 assert.equal(signup.referralTestRuntime(),true);
 const off=load(path.join(bakerRoot,"functions/lib/partnerReferral.js"),bakerDb,getAuth(bakerApp),{...process.env,GCLOUD_PROJECT:"batch-boss-android"});
 assert.equal(off.referralTestRuntime(),false);
 await assert.rejects(off.registerBakerTest(request));
});

test("supplier actual handlers deliver to the same isolated partner authority without exposing account IDs",{
 skip:!supplierRoot
},async()=>{
 const token=await idToken(supplierApp,"supplier-private");
 const decoded=await getAuth(supplierApp).verifyIdToken(token);
 const signup=load(path.join(supplierRoot,"functions/partnerReferral.js"),supplierDb,getAuth(supplierApp),
  {...process.env,GCLOUD_PROJECT:supplierProject});
 const request={auth:{uid:decoded.uid,token:decoded},data:{businessName:"Supplier fixture",contactName:"Fixture owner",referralCode:"BB-ABCDEF123456"}};
 await signup.registerReferredSupplierTest(request);
 await supplierDb.doc("supplierApplications/supplier-private").update({status:"approved"});
 const supplierProvider=async(url,options)=>{
  const body=options.body?JSON.parse(options.body):{};
  const data=url.endsWith("/initialize")?{authorization_url:"https://checkout.paystack.com/fixture",reference:body.reference}:
   {reference:url.split("/").at(-1),status:"success",amount:39900,currency:"ZAR",domain:"test",customer:{email:decoded.email}};
  if(body.metadata)assert.equal(body.metadata.includes("supplier-private"),false);
  return {ok:true,json:async()=>({status:true,data})};
 };
 const checkout=load(path.join(supplierRoot,"functions/supplierCheckout.js"),supplierDb,getAuth(supplierApp),
  {...process.env,GCLOUD_PROJECT:supplierProject},supplierProvider);
 const start=await checkout.startSupplierTestCheckout({...request,data:{planId:"growth"}});
 await checkout.verifySupplierTestCheckout({...request,data:{reference:start.reference}});
 await checkout.verifySupplierTestCheckout({...request,data:{reference:start.reference}});
 for(const item of (await supplierDb.collection("partnerTestOutbox").get()).docs){
  validateEvent(item.get("event"));
  assert.equal(JSON.stringify(item.get("event")).includes("supplier-private"),false);
 }
 assert.equal((await clientRead(supplierProject,"supplierPartnerReferrals/supplier-private",token)).status,403);
 const env={...process.env,BATCHBOSS_TEST_SOURCE:"supplier",BATCHBOSS_TEST_SOURCE_PROJECT:supplierProject,
 BATCHBOSS_TEST_PARTNER_PROJECT:partnerProject,BATCHBOSS_TEST_RECEIVER:"http://127.0.0.1:"+server.address().port+"/"+partnerProject+"/us-central1/ingestPartnerTestEvent"};
 const result=await run(env);assert.equal(result.delivered,2);assert.equal(result.pending,0);
 assert.equal((await partnerDb.collection("partnerTestFirstSales").get()).size,2);
 assert.equal((await partnerDb.collection("partnerCommissions").get()).size,0);
 assert.equal((await supplierDb.collection("supplierEntitlements").get()).size,0);
 for(const doc of (await partnerDb.collection("partnerTestFirstSales").get()).docs)assert.equal(doc.get("payable"),false);
});
