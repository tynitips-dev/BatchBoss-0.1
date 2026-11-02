const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createRequire}=require('node:module');
const path=require('node:path');
const sdk=createRequire(path.join(__dirname,'../functions/package.json'));
const host=process.env.FIRESTORE_EMULATOR_HOST||'';
const project=process.env.GCLOUD_PROJECT||'';
if(!/^(127\.0\.0\.1|localhost):\d+$/.test(host)||!/^demo-/.test(project))throw Error('This test requires isolated loopback Firestore and a demo project.');
sdk('firebase-admin/app').initializeApp({projectId:project});
const {getFirestore,Timestamp}=sdk('firebase-admin/firestore');
const db=getFirestore();
const {createProCodeHandler,redeemProCodeHandler,normaliseProCode,validateProOffer}=require('../functions/lib/proCodes');
const admin={uid:'code-admin',token:{admin:true}};
const member=uid=>({uid,token:{email:uid+'@example.test'}});
const now=Date.now();
async function profile(uid,extra={}){await db.doc('users/'+uid).set({bakeryId:'bakery-'+uid,subscriptionPlan:'free',subscriptionStatus:'free',...extra});}
async function offer(source='Android testers',maxRedemptions=10,durationDays=30){return (await createProCodeHandler({auth:admin,data:{source,maxRedemptions,durationDays,expiresAtMillis:now+86400000}},db));}
function redeem(uid,code,time=now){return redeemProCodeHandler({auth:member(uid),data:{code}},db,time);}

test('only admins create bounded offers; anonymous and malformed redemption are denied',async()=>{
 await assert.rejects(()=>createProCodeHandler({auth:member('ordinary'),data:{}},db),{code:'permission-denied'});
 await assert.rejects(()=>redeemProCodeHandler({data:{code:'x'}},db),{code:'unauthenticated'});
 assert.throws(()=>normaliseProCode('not-a-code'));
 for(const change of [{durationDays:0},{maxRedemptions:0},{expiresAtMillis:now-1},{source:''}])assert.throws(()=>validateProOffer({source:'Test',durationDays:30,maxRedemptions:10,expiresAtMillis:now+86400000,...change},now));
});
test('redemption records source and creates expiring Pro without a payment; retry does not extend',async()=>{
 await profile('test-baker');const created=await offer();
 const result=await redeem('test-baker',' '+created.code.toLowerCase()+' ');
 assert.equal(result.alreadyRedeemed,false);
 const first=(await db.doc('users/test-baker').get()).data();
 assert.equal(first.subscriptionPlan,'promo');assert.equal(first.subscriptionSource,'admin_promotion');
 assert.equal(first.subscriptionExpiresAt.toMillis(),now+30*86400000);
 const retry=await redeem('test-baker',created.code,now+10000);assert.equal(retry.alreadyRedeemed,true);
 assert.equal((await db.doc('proRedeemCodes/'+created.id).get()).get('redemptions'),1);
 const receipts=await db.collection('proCodeRedemptions').where('uid','==','test-baker').get();
 assert.equal(receipts.size,1);assert.equal(receipts.docs[0].get('source'),'Android testers');
 assert.equal((await db.collection('paymentReceipts').get()).size,0);
});
test('concurrent retries and competing users cannot exceed one remaining use',async()=>{
 for(const uid of ['race-a','race-b','race-c'])await profile(uid);
 const created=await offer('Partner A',1,7);
 const results=await Promise.allSettled(['race-a','race-a','race-b','race-c'].map(uid=>redeem(uid,created.code)));
 const successful=results.filter(r=>r.status==='fulfilled');assert.ok(successful.length>=1);
 const granted=(await Promise.all(['race-a','race-b','race-c'].map(uid=>db.doc('users/'+uid).get()))).filter(s=>s.get('subscriptionPlan')==='promo');
 assert.equal(granted.length,1);assert.equal((await db.doc('proRedeemCodes/'+created.id).get()).get('redemptions'),1);
});
test('expired or disabled codes, incomplete accounts and active paid users cannot consume a use',async()=>{
 const created=await offer('Private campaign');await profile('expiry');await profile('paid',{subscriptionSource:'paystack',subscriptionStatus:'active',subscriptionExpiresAt:Timestamp.fromMillis(now+60*86400000)});
 await assert.rejects(()=>redeem('paid',created.code),{code:'failed-precondition'});
 await assert.rejects(()=>redeem('missing-profile',created.code),{code:'failed-precondition'});
 await assert.rejects(()=>redeem('expiry',created.code,now+2*86400000),{code:'failed-precondition'});
 await db.doc('proRedeemCodes/'+created.id).update({active:false});
 await assert.rejects(()=>redeem('expiry',created.code),{code:'failed-precondition'});
 assert.equal((await db.doc('proRedeemCodes/'+created.id).get()).get('redemptions'),0);
 assert.equal((await db.doc('users/paid').get()).get('subscriptionSource'),'paystack');
});
test('an existing Pro gift is extended, never shortened; replay after expiry grants nothing new',async()=>{
 await profile('gift',{subscriptionSource:'admin_promotion',subscriptionStatus:'active',subscriptionExpiresAt:Timestamp.fromMillis(now+10*86400000)});
 const created=await offer('Welcome',10,7);await redeem('gift',created.code);
 assert.equal((await db.doc('users/gift').get()).get('subscriptionExpiresAt').toMillis(),now+17*86400000);
 const result=await redeem('gift',created.code,now+40*86400000);assert.equal(result.alreadyRedeemed,true);
 assert.equal((await db.doc('users/gift').get()).get('subscriptionExpiresAt').toMillis(),now+17*86400000);
});
test('code definitions and redemption records are not publicly readable',async()=>{
 const code=await offer('Private source');
 for(const collection of ['proRedeemCodes','proCodeRedemptions']){
  const response=await fetch(`http://${host}/v1/projects/${project}/databases/(default)/documents/${collection}/${code.id}`);
  assert.equal(response.status,403);
 }
});
