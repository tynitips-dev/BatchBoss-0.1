import {randomBytes, createHash} from 'node:crypto';
import {getFirestore, FieldValue, type Firestore, type Transaction} from 'firebase-admin/firestore';
import {getAuth} from 'firebase-admin/auth';
import {onCall, HttpsError} from 'firebase-functions/v2/https';

const CODE=/^BB-[A-F0-9]{12}$/;
const SUBJECT=/^sub-[a-f0-9]{32}$/;
export function referralTestRuntime() {
 return process.env.FUNCTIONS_EMULATOR==='true' &&
  /^demo-[a-z0-9-]+$/.test(process.env.GCLOUD_PROJECT||'') &&
  process.env.BATCHBOSS_PARTNER_TEST_ENABLED==='true';
}
const now=()=>FieldValue.serverTimestamp();
export async function registerBakerTest(request:any) {
 if(!referralTestRuntime())throw new HttpsError('failed-precondition','Referral signup is available only in the isolated emulator pilot.');
 const uid=request.auth?.uid, email=request.auth?.token?.email;
 if(!uid||typeof email!=='string'||request.auth?.token?.firebase?.sign_in_provider==='anonymous')
  throw new HttpsError('unauthenticated','Sign in with your bakery email.');
 const data=request.data||{};
 if(Object.keys(data).some(k=>!['firstName','surname','bakeryName','referralCode'].includes(k))||
  !['firstName','surname','bakeryName'].every(k=>typeof data[k]==='string'&&data[k].trim().length>0&&data[k].length<=120)||
  !CODE.test(data.referralCode||''))throw new HttpsError('invalid-argument','Enter your name, bakery and referral code.');
 const db=getFirestore(),profileRef=db.doc('users/'+uid),mappingRef=db.doc('bakerPartnerReferrals/'+uid);
 const user=await getAuth().getUser(uid);
 const created=Date.parse(user.metadata.creationTime);
 const bakeryRef=db.collection('bakeries').doc();
 const subject='sub-'+randomBytes(16).toString('hex');
 const event={version:1,eventId:'evt-'+randomBytes(16).toString('hex'),source:'baker',kind:'registration',subject,referralCode:data.referralCode,mode:'test'};
 await db.runTransaction(async t=>{
  const [profile,mapping]=await Promise.all([t.get(profileRef),t.get(mappingRef)]);
  if(mapping.exists){
   if(mapping.get('referralCode')!==data.referralCode||!profile.exists)throw new HttpsError('already-exists','Signup referral cannot be changed.');
   return;
  }
  if(profile.exists||!Number.isFinite(created)||created>Date.now()||Date.now()-created>10*60*1000)
   throw new HttpsError('failed-precondition','Only a new bakery signup can capture a referral.');
  t.create(bakeryRef,{name:data.bakeryName.trim(),ownerUid:uid,currency:'ZAR',createdAt:now(),updatedAt:now()});
  t.create(profileRef,{uid,email,bakeryId:bakeryRef.id,bakeryName:data.bakeryName.trim(),firstName:data.firstName.trim(),surname:data.surname.trim(),role:'owner',subscriptionPlan:'free',subscriptionStatus:'free',createdAt:now()});
  t.create(bakeryRef.collection('members').doc(uid),{uid,email,role:'owner',joinedAt:now()});
  t.create(mappingRef,{subject,referralCode:data.referralCode,mode:'test',createdAt:now()});
  t.create(db.doc('partnerTestOutbox/'+event.eventId),{event,status:'pending',createdAt:now()});
 });
 return {submitted:true,testMode:true};
}
export const registerReferredBakerTest=onCall({region:'europe-west1'},registerBakerTest);

export async function testAttributionForCheckout(db:Firestore,uid:string,domain:string) {
 if(!referralTestRuntime())return null;
 if(domain!=='test')throw new HttpsError('failed-precondition','Referral pilot cannot use live checkout.');
 const snap=await db.doc('bakerPartnerReferrals/'+uid).get();
 if(!snap.exists)return null;
 const value=snap.data()!;
 if(value.mode!=='test'||!CODE.test(value.referralCode)||!SUBJECT.test(value.subject))throw new HttpsError('failed-precondition','Referral requires reconciliation.');
 return {subject:value.subject,referralCode:value.referralCode};
}
export function recordBakerTestPayment(t:Transaction,db:Firestore,intent:any,reference:string) {
 if(!referralTestRuntime()||!intent?.partnerReferral)return;
 if(intent.domain!=='test'||intent.provider!=='paystack'||intent.currency!=='ZAR'||
  !['monthly','annual'].includes(intent.interval)||!Number.isSafeInteger(intent.amount)||intent.amount<=0||
  !CODE.test(intent.partnerReferral.referralCode)||!SUBJECT.test(intent.partnerReferral.subject))
  throw new HttpsError('failed-precondition','Invalid test referral payment.');
 const event={version:1,eventId:'evt-'+createHash('sha256').update('baker:'+reference).digest('hex').slice(0,32),
  source:'baker',kind:'payment',subject:intent.partnerReferral.subject,referralCode:intent.partnerReferral.referralCode,
  reference,planId:intent.interval==='annual'?'bakerAnnual':'bakerMonthly',amountCents:intent.amount,currency:'ZAR',mode:'test'};
 t.create(db.doc('partnerTestOutbox/'+event.eventId),{event,status:'pending',createdAt:now()});
}
