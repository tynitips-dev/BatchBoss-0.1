const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createRequire}=require('node:module');
const path=require('node:path');
const {randomUUID,createHash}=require('node:crypto');
const sdk=createRequire(path.join(__dirname,'../functions/package.json'));
const host=process.env.FIRESTORE_EMULATOR_HOST||'',project=process.env.GCLOUD_PROJECT||'';
if(!/^(127\.0\.0\.1|localhost):\d+$/.test(host)||!/^demo-/.test(project))throw Error('Regional listing tests require an isolated Firestore emulator.');
sdk('firebase-admin/app').initializeApp({projectId:project});
const {getFirestore,Timestamp}=sdk('firebase-admin/firestore');
const db=getFirestore();
const api=require('../functions/lib/listingDrafts');
const member=uid=>({uid,token:{email:uid+'@example.test'}});
const rows='listingCatalogs/marketplace/entries/';
const draftRef=(uid,id)=>db.doc('listingWorkspaces/'+uid+'/drafts/'+id);
const invoke=(fn,uid,data)=>fn.run({auth:uid?member(uid):undefined,data});
async function seed(uid,region){await db.doc('users/'+uid).set({bakeryId:'bakery-'+uid});await db.doc('bakeries/bakery-'+uid).set(region);}
async function giveSuite(uid){await db.doc('listingEntitlements/'+uid).set({fullSuiteExpiresAt:Timestamp.fromMillis(Date.now()+86400000)});}
const item=id=>({kind:'marketplace',id,title:'Mixer',area:'Local city',description:'Example item',priceCents:4599,photos:[]});
test('foreign listing drafts use the bakery currency and cannot publish on existing domestic plans',async()=>{
 const uid='listing-us-'+randomUUID(),id=randomUUID();
 await seed(uid,{countryCode:'US',currency:'USD'});
 assert.deepEqual(await invoke(api.getListingRegion,uid,{}),{countryCode:'US',currency:'USD',locale:'en-US',publicationEnabled:false,directoryPublicationEnabled:false,marketplacePublicationEnabled:false});
 await invoke(api.saveListingDraft,uid,{...item(id),countryCode:'ZA',currency:'ZAR'});
 const saved=(await draftRef(uid,id).get()).data();
 assert.equal(saved.countryCode,'US');assert.equal(saved.currency,'USD');assert.equal(saved.priceCents,4599);
 await giveSuite(uid);
 await assert.rejects(()=>invoke(api.publishBakerListing,uid,{kind:'marketplace',id}),{code:'failed-precondition'});
 assert.equal((await db.doc(rows+createHash('sha256').update(JSON.stringify([uid,id])).digest('hex')).get()).exists,false);
 await db.doc('bakeries/bakery-'+uid).set({countryCode:'ZA',currency:'ZAR'});
 await assert.rejects(()=>invoke(api.saveListingDraft,uid,item(id)),{code:'failed-precondition'});
 assert.equal((await draftRef(uid,id).get()).get('currency'),'USD');
});
test('legacy SA and new ZAR listings retain their price, and public feed has no private data',async()=>{
 const uid='listing-za-'+randomUUID(),id=randomUUID();
 await seed(uid,{});await giveSuite(uid);
 await invoke(api.saveListingDraft,uid,item(id));
 await invoke(api.publishBakerListing,uid,{kind:'marketplace',id});
 const foreign='listing-foreign-'+randomUUID();
 await seed(foreign,{countryCode:'GB',currency:'GBP'});await db.doc('listingEntitlements/'+foreign).set({paddleSubscriptionId:'sub_fixture',paddleCountryCode:'GB',paddleCurrency:'GBP',paddleSuiteExpiresAt:Timestamp.fromMillis(Date.now()+86400000)});
 const foreignId=createHash('sha256').update(foreign).digest('hex');
 await db.doc(rows+foreignId).set({ownerUid:foreign,kind:'marketplace',countryCode:'GB',currency:'GBP',title:'UK test oven',area:'London',description:'Test',priceCents:12345,status:'published',internalNotes:'private'});
 const local=await invoke(api.listPublishedBakerListings,null,{kind:'marketplace',countryCode:'ZA'});
 const uk=await invoke(api.listPublishedBakerListings,null,{kind:'marketplace',countryCode:'GB'});
 assert.ok(local.listings.some(x=>x.title==='Mixer'&&x.priceCents===4599&&x.currency==='ZAR'));
 assert.ok(local.listings.every(x=>x.countryCode==='ZA'));
 assert.ok(uk.listings.some(x=>x.title==='UK test oven'&&x.priceCents===12345&&x.currency==='GBP'));
 assert.ok(uk.listings.every(x=>x.countryCode==='GB'&&!('ownerUid' in x)&&!('internalNotes' in x)));
 await assert.rejects(()=>invoke(api.listPublishedBakerListings,null,{kind:'marketplace',countryCode:'ZZ'}),{code:'invalid-argument'});
 await invoke(api.markMarketplaceSold,uid,{id});
 assert.equal((await db.doc(rows+createHash('sha256').update(JSON.stringify([uid,id])).digest('hex')).get()).exists,false);
});
test('pending checkout prevents price edits and duplicate publication attempts',async()=>{
 const uid='listing-pending-'+randomUUID(),id=randomUUID();await seed(uid,{countryCode:'ZA',currency:'ZAR'});await giveSuite(uid);
 await invoke(api.saveListingDraft,uid,item(id));
 await db.doc('listingWorkspaces/'+uid+'/marketplaceCheckouts/'+id).set({status:'pending'});
 await assert.rejects(()=>invoke(api.saveListingDraft,uid,{...item(id),priceCents:999}),{code:'failed-precondition'});
 await assert.rejects(()=>invoke(api.publishBakerListing,uid,{kind:'marketplace',id}),{code:'failed-precondition'});
 assert.equal((await draftRef(uid,id).get()).get('priceCents'),4599);
});

test('region-bound Paddle grants enforce Finder-only rights and three monthly Full Suite listings',async()=>{
 const uid='listing-paddle-'+randomUUID();await seed(uid,{countryCode:'GB',currency:'GBP'});const access=db.doc('listingEntitlements/'+uid),future=Timestamp.fromMillis(Date.now()+86400000),past=Timestamp.fromMillis(0);
 await access.set({paddleSubscriptionId:'sub_fixture',paddleCountryCode:'GB',paddleCurrency:'GBP',paddleFinderExpiresAt:future,paddleSuiteExpiresAt:past});
 let options=await invoke(api.getListingRegion,uid,{});assert.equal(options.directoryPublicationEnabled,true);assert.equal(options.marketplacePublicationEnabled,false);
 await invoke(api.saveListingDraft,uid,{kind:'directoryListing',title:'Test UK bakery',area:'London',description:'Test',photos:[]});await invoke(api.publishBakerListing,uid,{kind:'directoryListing'});
 const first=randomUUID();await invoke(api.saveListingDraft,uid,item(first));await assert.rejects(()=>invoke(api.publishBakerListing,uid,{kind:'marketplace',id:first}),{code:'failed-precondition'});
 await access.update({paddleSuiteExpiresAt:future});
 for(let i=0;i<4;i++){const id=i===0?first:randomUUID();await invoke(api.saveListingDraft,uid,item(id));if(i<3)await invoke(api.publishBakerListing,uid,{kind:'marketplace',id});else await assert.rejects(()=>invoke(api.publishBakerListing,uid,{kind:'marketplace',id}),{code:'resource-exhausted'});}
 await access.update({paddleSuiteExpiresAt:past,paddleFinderExpiresAt:past});options=await invoke(api.getListingRegion,uid,{});assert.equal(options.publicationEnabled,false);
});
