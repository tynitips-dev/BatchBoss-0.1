import {createHash, randomBytes} from 'node:crypto';
import {getFirestore, FieldValue, Timestamp} from 'firebase-admin/firestore';
import {HttpsError, onCall} from 'firebase-functions/v2/https';

const hash=(s:string)=>createHash('sha256').update(s).digest('hex');
const options={region:'europe-west1',invoker:'public' as const};
function signedIn(request:any){if(!request.auth || request.auth.token?.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in to your bakery account.');return request.auth;}
function admin(request:any){const user=signedIn(request);if(user.token.admin!==true)throw new HttpsError('permission-denied','Administrator access is required.');return user;}
const now=()=>FieldValue.serverTimestamp();
export function normaliseProCode(value:unknown){
 const code=typeof value==='string'?value.trim().toUpperCase():'';
 if(!/^BBP-[A-F0-9]{24}$/.test(code))throw new HttpsError('invalid-argument','Please check the Pro code.');
 return code;
}
export function validateProOffer(data:any,time=Date.now()){
 const source=typeof data?.source==='string'?data.source.trim():'';
 const {durationDays,maxRedemptions,expiresAtMillis}=data||{};
 if(!source || source.length>120 || !Number.isInteger(durationDays) || durationDays<1 || durationDays>365 ||
  !Number.isInteger(maxRedemptions) || maxRedemptions<1 || maxRedemptions>100000 ||
  !Number.isSafeInteger(expiresAtMillis) || expiresAtMillis<=time || expiresAtMillis>time+366*86400000)
  throw new HttpsError('invalid-argument','Enter a source, 1–365 Pro days, 1–100000 uses and a future expiry within one year.');
 return {source,durationDays,maxRedemptions,expiresAtMillis};
}
export async function createProCodeHandler(request:any,db=getFirestore()){
 const actor=admin(request),offer=validateProOffer(request.data);
 const code='BBP-'+randomBytes(12).toString('hex').toUpperCase(),id=hash(code);
 const batch=db.batch();
 batch.create(db.doc('proRedeemCodes/'+id),{source:offer.source,durationDays:offer.durationDays,maxRedemptions:offer.maxRedemptions,
  expiresAt:Timestamp.fromMillis(offer.expiresAtMillis),redemptions:0,active:true,createdBy:actor.uid,createdAt:now()});
 batch.create(db.collection('adminAuditLogs').doc(),{action:'PRO_CODE_CREATED',codeId:id,source:offer.source,durationDays:offer.durationDays,
  maxRedemptions:offer.maxRedemptions,actorUid:actor.uid,createdAt:now()});
 await batch.commit();
 return {code,id,...offer};
}
export async function redeemProCodeHandler(request:any,db=getFirestore(),time=Date.now()){
 const user=signedIn(request),code=normaliseProCode(request.data?.code),id=hash(code);
 const codeRef=db.doc('proRedeemCodes/'+id),profileRef=db.doc('users/'+user.uid),receiptRef=db.doc('proCodeRedemptions/'+id+'_'+hash(user.uid));
 return db.runTransaction(async transaction=>{
  const [offerSnap,profileSnap,receipt]=await Promise.all([transaction.get(codeRef),transaction.get(profileRef),transaction.get(receiptRef)]);
  if(receipt.exists)return {redeemed:true,alreadyRedeemed:true,expiresAt:receipt.get('expiresAt').toDate().toISOString()};
  const offer=offerSnap.data(),profile=profileSnap.data();
  if(!profile || !profile.bakeryId)throw new HttpsError('failed-precondition','Finish setting up your bakery account first.');
  if(!offer || offer.active!==true || !offer.expiresAt?.toMillis || offer.expiresAt.toMillis()<=time ||
   !Number.isInteger(offer.redemptions)||offer.redemptions<0||offer.redemptions>=offer.maxRedemptions)
   throw new HttpsError('failed-precondition','This code is unavailable, expired or fully redeemed.');
  validateProOffer({source:offer.source,durationDays:offer.durationDays,maxRedemptions:offer.maxRedemptions,expiresAtMillis:offer.expiresAt.toMillis()},time);
  const previousExpiry=profile.subscriptionExpiresAt?.toMillis?.()||0;
  if(profile.subscriptionSource==='paystack' && (previousExpiry>time || ['active','trialing'].includes(profile.subscriptionStatus)))
   throw new HttpsError('failed-precondition','Your paid subscription is protected. Contact support to use this code.');
  const start=profile.subscriptionSource==='admin_promotion' && profile.subscriptionStatus==='active'?Math.max(time,previousExpiry):time;
  const expiresAt=Timestamp.fromMillis(start+offer.durationDays*86400000);
  transaction.update(profileRef,{subscriptionPlan:'promo',subscriptionStatus:'active',subscriptionSource:'admin_promotion',
   subscriptionStartedAt:now(),subscriptionExpiresAt:expiresAt,subscriptionGrantedBy:offer.createdBy,updatedAt:now()});
  transaction.update(codeRef,{redemptions:offer.redemptions+1});
  transaction.create(receiptRef,{uid:user.uid,bakeryId:profile.bakeryId,codeId:id,source:offer.source,durationDays:offer.durationDays,expiresAt,createdAt:now()});
  transaction.create(db.collection('adminAuditLogs').doc(),{action:'PRO_CODE_REDEEMED',codeId:id,source:offer.source,targetUid:user.uid,durationDays:offer.durationDays,expiresAt,createdAt:now()});
  return {redeemed:true,alreadyRedeemed:false,expiresAt:expiresAt.toDate().toISOString()};
 });
}
export const createProRedeemCode=onCall(options,request=>createProCodeHandler(request));
export const redeemProCode=onCall(options,request=>redeemProCodeHandler(request));
export const listProRedeemCodes=onCall(options,async request=>{
 admin(request);const rows=await getFirestore().collection('proRedeemCodes').orderBy('createdAt','desc').limit(100).get();
 return {codes:rows.docs.map(doc=>{const value=doc.data();return {id:doc.id,source:value.source,durationDays:value.durationDays,maxRedemptions:value.maxRedemptions,redemptions:value.redemptions,active:value.active,expiresAt:value.expiresAt.toDate().toISOString()};})};
});
export const disableProRedeemCode=onCall(options,async request=>{
 const actor=admin(request),id=request.data?.id;
 if(typeof id!=='string'||!/^[a-f0-9]{64}$/.test(id))throw new HttpsError('invalid-argument','Select a code.');
 const db=getFirestore();await db.runTransaction(async transaction=>{
  const ref=db.doc('proRedeemCodes/'+id),snap=await transaction.get(ref);
  if(!snap.exists)throw new HttpsError('not-found','Code not found.');
  transaction.update(ref,{active:false,disabledAt:now(),disabledBy:actor.uid});
  transaction.create(db.collection('adminAuditLogs').doc(),{action:'PRO_CODE_DISABLED',codeId:id,actorUid:actor.uid,createdAt:now()});
 });return {disabled:true};
});
