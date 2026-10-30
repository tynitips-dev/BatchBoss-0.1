"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const { memoryDb,loadFunctions } = require("./testSupport");
const { ingestTestEvent } = require("./referralTestIngest");
const { validateEvent } = require("./referralContract");
const code="BB-ABCDEF123456", subject="sub-"+"a".repeat(32);
const event=(kind,n=1,source="supplier")=>({
 version:1,eventId:"evt-"+n.toString(16).padStart(32,"0"),source,kind,subject,referralCode:code,mode:"test",
 ...(kind==="payment"?{reference:"payment-123456",planId:source==="supplier"?"supplierGrowth":"bakerMonthly",amountCents:39900,currency:"ZAR"}:{})
});
function setup(){const db=memoryDb();db.seed("partnerCodes/"+code,{partnerUid:"agent"});db.seed("partnerProfiles/agent",{status:"approved"});return db;}
test("both segments bind registration to first test payment; never payable",async()=>{
 for(const source of ["supplier","baker"]){
  const db=setup();await ingestTestEvent(db,event("registration",1,source));
  await Promise.all([ingestTestEvent(db,event("payment",2,source)),ingestTestEvent(db,event("payment",2,source))]);
  await ingestTestEvent(db,{...event("payment",3,source),reference:"renewal-123456"});
  const sales=db.entries().filter(([k])=>k.startsWith("partnerTestFirstSales/"));
  assert.equal(sales.length,1);assert.equal(sales[0][1].payable,false);
  assert.equal(sales[0][1].commissionCents,source==="supplier"?7980:9975);
  assert.equal(db.entries().some(([k])=>k.startsWith("partnerCommissions/")),false);
 }
});
test("out-of-order delivery retries; rejects changed replays, suspended codes and unknown fields",async()=>{
 const db=setup();await assert.rejects(ingestTestEvent(db,event("payment",2)));
 await ingestTestEvent(db,event("registration"));
 await assert.rejects(ingestTestEvent(db,{...event("registration"),source:"baker"}));
 db.seed("partnerProfiles/agent",{status:"suspended"});
 await assert.rejects(ingestTestEvent(db,event("payment",2)));
 for(const bad of [{supplierId:"private"},{mode:"live"},{planId:"toString"},{amountCents:0},{currency:"USD"}])
  assert.throws(()=>validateEvent({...event("payment",2),...bad}));
});
test("first-touch ownership and payment reference cannot move to another subject",async()=>{
 const db=setup(), other="BB-123456ABCDEF";
 db.seed("partnerCodes/"+other,{partnerUid:"other"});db.seed("partnerProfiles/other",{status:"approved"});
 await ingestTestEvent(db,event("registration"));
 await assert.rejects(ingestTestEvent(db,{...event("registration",4),referralCode:other}));
 await ingestTestEvent(db,event("payment",2));
 const second={...event("registration",5),subject:"sub-"+"b".repeat(32)};
 await ingestTestEvent(db,second);
 await assert.rejects(ingestTestEvent(db,{...event("payment",6),subject:second.subject}));
});
test("supplier actual signup and verified checkout flow reaches test attribution",{
 skip:!process.env.SUPPLIER_SOURCE
},async()=>{
 const db=setup();
 const supplierPath=process.env.SUPPLIER_SOURCE;
 const signup=loadFunctions(path.join(supplierPath,"partnerReferral.js"),db,{FUNCTIONS_EMULATOR:"true",GCLOUD_PROJECT:"demo-batchboss-partners",BATCHBOSS_PARTNER_TEST_ENABLED:"true"});
 const request={auth:{uid:"supplier-private-id",token:{email:"supplier@example.test",email_verified:true}},
 data:{businessName:"Test business",contactName:"Test owner",referralCode:code}};
 await signup.registerReferredSupplierTest(request);
 await signup.registerReferredSupplierTest(request);
 db.seed("supplierApplications/supplier-private-id",{status:"approved"});
 let providerCalls=0;
 const fakePaystack=async(url,options)=>{
  providerCalls++;
  if(url.endsWith("/initialize")){
   const body=JSON.parse(options.body);
   assert.equal(body.metadata.includes("supplier-private-id"),false);
   assert.equal(body.metadata.includes(code),false);
   return {ok:true,json:async()=>({status:true,data:{authorization_url:"https://checkout.paystack.com/test",reference:body.reference}})};
  }
  const ref=url.split("/").at(-1);
  return {ok:true,json:async()=>({status:true,data:{reference:ref,status:"success",amount:39900,currency:"ZAR",domain:"test",customer:{email:"supplier@example.test"}}})};
 };
 const checkout=loadFunctions(path.join(supplierPath,"supplierCheckout.js"),db,{PAYSTACK_SECRET_KEY:"sk_test_fake",FUNCTIONS_EMULATOR:"true",GCLOUD_PROJECT:"demo-batchboss-suppliers",BATCHBOSS_PARTNER_TEST_ENABLED:"true"},fakePaystack);
 const started=await checkout.startSupplierTestCheckout({...request,data:{planId:"growth"}});
 const verification={...request,data:{reference:started.reference}};
 await checkout.verifySupplierTestCheckout(verification);await checkout.verifySupplierTestCheckout(verification);
 const outbox=db.entries().filter(([k])=>k.startsWith("partnerTestOutbox/")).map(([,v])=>v.event);
 assert.equal(outbox.length,2);
 for(const e of outbox){assert.equal(JSON.stringify(e).includes("supplier-private-id"),false); await ingestTestEvent(db,e);}
 assert.equal(db.entries().filter(([k])=>k.startsWith("partnerTestFirstSales/")).length,1);
 assert.equal(db.entries().some(([k])=>k.startsWith("supplierEntitlements/")),false);
 assert.equal(providerCalls,2);
});

test("central test bridge is absent in production and requires a verified admin in emulator",async()=>{
 const db=setup(),file=path.join(__dirname,"index.js");
 assert.equal(loadFunctions(file,db,{}).ingestPartnerTestEvent,undefined);
 const handler=loadFunctions(file,db,{FUNCTIONS_EMULATOR:"true",GCLOUD_PROJECT:"demo-batchboss-partners",BATCHBOSS_PARTNER_TEST_ENABLED:"true"}).ingestPartnerTestEvent;
 await assert.rejects(handler({data:event("registration")}),{code:"unauthenticated"});
 await assert.rejects(handler({auth:{uid:"agent",token:{email_verified:true}},data:event("registration")}),{code:"permission-denied"});
 await assert.rejects(handler({auth:{uid:"admin",token:{email_verified:false,batchbossPartnerAdmin:true}},data:event("registration")}),{code:"unauthenticated"});
 await assert.rejects(handler({auth:{uid:"bridge",token:{email_verified:true,batchbossPartnerAdmin:true,batchbossPartnerBridgeSource:"supplier"}},data:event("registration",88,"baker")}),{code:"permission-denied"});
 const result=await handler({auth:{uid:"admin",token:{email_verified:true,batchbossPartnerAdmin:true}},data:event("registration")});
 assert.equal(result.payable,false);
});
