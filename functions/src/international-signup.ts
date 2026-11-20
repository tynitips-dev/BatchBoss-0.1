// Regional onboarding is server-owned; the existing ZA registration path is unchanged.
import {getAuth} from 'firebase-admin/auth';
import {getFirestore,FieldValue} from 'firebase-admin/firestore';
import {defineString} from 'firebase-functions/params';
import {onCall,HttpsError} from 'firebase-functions/v2/https';
const publicSignup=defineString('BATCHBOSS_GLOBAL_SIGNUP_ENABLED',{default:'false'});
const publicCheckout=defineString('PADDLE_PUBLIC_CHECKOUT_ENABLED',{default:'false'});
const environment=defineString('PADDLE_ENVIRONMENT',{default:'sandbox'});
const pilot=defineString('PADDLE_TEST_UID',{default:''});
const enabled=defineString('PADDLE_CHECKOUT_ENABLED',{default:'false'});
const webhook=defineString('PADDLE_WEBHOOK_ENABLED',{default:'false'});
const provision=defineString('PADDLE_PROVISION_ENABLED',{default:'false'});
const options={region:'europe-west1',invoker:'public' as const,cors:['https://app.batchboss.co.za']};
export const regionalCountries={GB:'GBP',US:'USD',CA:'CAD',AU:'AUD',NZ:'NZD'} as const;
export function internationalFields(country:unknown){
 if(typeof country!=='string'||!Object.hasOwn(regionalCountries,country))return null;
 const code=country as keyof typeof regionalCountries;
 return {countryCode:code,currency:regionalCountries[code],locale:'en-'+code,measurementSystem:code==='US'?'us':'metric',temperatureUnit:code==='US'?'F':'C',regionalVersion:1};
}
function publiclyReady(){return [publicSignup,publicCheckout,enabled,webhook,provision].every(p=>p.value()==='true')&&environment.value()==='live';}
function textField(v:unknown){if(typeof v!=='string'||!v.trim()||v.trim().length>100)throw new HttpsError('invalid-argument','Enter a bakery name, first name and surname (up to 100 characters each).');return v.trim();}
async function prepare(uid:string,data:any){
 const fields=internationalFields(data?.country);
 if(!fields)throw new HttpsError('invalid-argument','Choose a supported international country.');
 const firstName=textField(data?.firstName),surname=textField(data?.surname),bakeryName=textField(data?.bakeryName);
 const user=await getAuth().getUser(uid);
 if(!user.email||user.disabled)throw new HttpsError('failed-precondition','A valid email account is required.');
 const db=getFirestore(),profile=db.doc('users/'+uid),bakery=db.doc('bakeries/global-'+uid),member=bakery.collection('members').doc(uid);
 return db.runTransaction(async tx=>{
  const [u,b,mem]=await Promise.all([tx.get(profile),tx.get(bakery),tx.get(member)]);
  if(u.exists){
   if(u.data()?.bakeryId===bakery.id&&b.data()?.ownerUid===uid&&b.data()?.countryCode===fields.countryCode&&b.data()?.currency===fields.currency&&mem.exists)return {bakeryId:bakery.id,created:false};
   throw new HttpsError('failed-precondition','This account already has a bakery. Its country and currency cannot be changed.');
  }
  if(b.exists||mem.exists)throw new HttpsError('failed-precondition','An incomplete workspace requires support review.');
  tx.create(bakery,{name:bakeryName,ownerUid:uid,...fields,createdAt:FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp()});
  tx.create(profile,{uid,bakeryId:bakery.id,bakeryName,firstName,surname,email:user.email,role:'owner',subscriptionPlan:'free',subscriptionStatus:'free',registrationPlatform:'web',lastLoginPlatform:'web',lastLoginPlatformAt:FieldValue.serverTimestamp(),createdAt:FieldValue.serverTimestamp()});
  tx.create(member,{uid,email:user.email,role:'owner',joinedAt:FieldValue.serverTimestamp()});
  return {bakeryId:bakery.id,created:true};
 });
}
export const getInternationalRegistrationOptions=onCall(options,()=>({countries:publiclyReady()?Object.keys(regionalCountries):[]}));
export const createInternationalWorkspace=onCall(options,async request=>{
 if(!request.auth||request.auth.token.firebase?.sign_in_provider==='anonymous')throw new HttpsError('unauthenticated','Sign in first.');
 if(!publiclyReady())throw new HttpsError('failed-precondition','International registration is not available yet.');
 return prepare(request.auth.uid,request.data);
});
export const preparePaddlePilotWorkspace=onCall(options,async request=>{
 if(request.auth?.token.admin!==true)throw new HttpsError('permission-denied','Administrator access is required.');
 const uid=request.data?.uid;
 if(typeof uid!=='string'||!uid||uid.includes('/')||uid!==pilot.value())throw new HttpsError('failed-precondition','Use only the configured new test account.');
 return prepare(uid,request.data);
});
