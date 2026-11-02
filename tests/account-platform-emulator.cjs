const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createRequire}=require('node:module');
const path=require('node:path');
const sdk=createRequire(path.join(__dirname,'../functions/package.json'));
const host=process.env.FIRESTORE_EMULATOR_HOST||'',authHost=process.env.FIREBASE_AUTH_EMULATOR_HOST||'',project=process.env.GCLOUD_PROJECT||'';
if(!/^(127\.0\.0\.1|localhost):\d+$/.test(host)||!/^(127\.0\.0\.1|localhost):\d+$/.test(authHost)||!/^demo-/.test(project))throw Error('Platform tests require local Auth/Firestore emulators and a demo project.');
sdk('firebase-admin/app').initializeApp({projectId:project});
const db=sdk('firebase-admin/firestore').getFirestore();
const {accountPlatform}=require('../functions/lib/accountPlatform');
const prefix=`projects/${project}/databases/(default)/documents/`;
const string=stringValue=>({stringValue});
async function account(){
 const response=await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:`platform-${crypto.randomUUID()}@example.test`,password:'Synthetic-test-1234',returnSecureToken:true})});
 assert.equal(response.status,200);const body=await response.json();return {uid:body.localId,token:body.idToken};
}
async function commit(user,fields,options={}){
 const id=options.uid||user.uid;
 const transforms=options.timestamp===false?[]:[{fieldPath:'lastLoginPlatformAt',setToServerValue:'REQUEST_TIME'}];
 return fetch(`http://${host}/v1/projects/${project}/databases/(default)/documents:commit`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${user.token}`},body:JSON.stringify({writes:[{update:{name:prefix+'users/'+id,fields},...(options.create?{}:{updateMask:{fieldPaths:Object.keys(fields)}}),...(transforms.length?{updateTransforms:transforms}:{})}]})});
}
test('only explicit known platforms are reported; legacy and malformed values are Unknown',()=>{
 for(const value of [undefined,null,'',{},'Android','ios',123])assert.equal(accountPlatform(value),'unknown');
 assert.equal(accountPlatform('android'),'android');assert.equal(accountPlatform('web'),'web');
});
test('new signup records an explicit source but it cannot later be rewritten',async()=>{
 const user=await account(),bakeryId='source-'+user.uid;
 await db.doc('bakeries/'+bakeryId).set({ownerUid:user.uid});
 const response=await commit(user,{uid:string(user.uid),bakeryId:string(bakeryId),role:string('owner'),subscriptionPlan:string('free'),subscriptionStatus:string('free'),registrationPlatform:string('android'),lastLoginPlatform:string('web')},{create:true});
 assert.equal(response.status,200,await response.text());
 const login=await commit(user,{lastLoginPlatform:string('android')});assert.equal(login.status,200,await login.text());
 const snapshot=await db.doc('users/'+user.uid).get();
 assert.equal(snapshot.get('registrationPlatform'),'android');assert.equal(snapshot.get('lastLoginPlatform'),'android');assert.ok(snapshot.get('lastLoginPlatformAt').toMillis()>0);
 const rewrite=await commit(user,{registrationPlatform:string('web')});assert.equal(rewrite.status,403);
});
test('older accounts gain last-platform metadata without inventing registration history',async()=>{
 const user=await account();await db.doc('users/'+user.uid).set({uid:user.uid,bakeryId:'legacy',role:'owner',subscriptionPlan:'free',subscriptionStatus:'free'});
 const response=await commit(user,{lastLoginPlatform:string('web')});assert.equal(response.status,200,await response.text());
 const value=(await db.doc('users/'+user.uid).get()).data();assert.equal(value.registrationPlatform,undefined);assert.equal(value.lastLoginPlatform,'web');
 const invalid=await commit(user,{lastLoginPlatform:string('ios')});assert.equal(invalid.status,403);
 const fakeTime=await commit(user,{lastLoginPlatformAt:{timestampValue:'2020-01-01T00:00:00Z'}},{timestamp:false});assert.equal(fakeTime.status,403);
 const pro=await commit(user,{lastLoginPlatform:string('android'),subscriptionPlan:string('pro')});assert.equal(pro.status,403);
 const other=await account();await db.doc('users/'+other.uid).set({uid:other.uid,bakeryId:'other',role:'owner',subscriptionPlan:'free',subscriptionStatus:'free'});
 const cross=await commit(user,{lastLoginPlatform:string('android')},{uid:other.uid});assert.equal(cross.status,403);
});
