const {test}=require('node:test');const assert=require('node:assert/strict');
const {listingCountry,listingRegion,listingBelongsToCountry,sameListingRegion,marketplaceBillingAvailable}=require('../functions/lib/listingRegion.js');
test('legacy listings remain ZAR regardless of viewer country',()=>{
 assert.deepEqual(listingRegion({}),{countryCode:'ZA',currency:'ZAR',locale:'en-ZA'});
 assert.equal(listingBelongsToCountry({},'US'),false);
 assert.equal(listingBelongsToCountry({},'ZA'),true);
 assert.equal(listingRegion({countryCode:'US',currency:'ZAR'}),null);
 assert.equal(listingRegion({currency:null}),null);
 assert.equal(listingRegion({countryCode:'GB'}),null);
 assert.equal(listingCountry('__proto__'),null);
});
test('seller region is a snapshot, not a currency conversion',()=>{
 for(const [countryCode,currency] of [['US','USD'],['GB','GBP'],['CA','CAD'],['AU','AUD'],['NZ','NZD']]){
  const row={countryCode,currency,priceCents:499};
  assert.equal(listingRegion(row).currency,currency);
  assert.equal(listingBelongsToCountry(row,countryCode),true);
  assert.equal(listingBelongsToCountry(row,'ZA'),false);
  assert.equal(sameListingRegion(row,{}),false);
  assert.equal(marketplaceBillingAvailable(listingRegion(row)),false);
  assert.equal(row.priceCents,499);
 }
 assert.equal(sameListingRegion({}, {countryCode:'ZA',currency:'ZAR'}),true);
 assert.equal(marketplaceBillingAvailable(listingRegion({})),true);
 assert.equal(marketplaceBillingAvailable(null),false);
});

test('international permissions require matching verified region and never use domestic expiry',()=>{const {listingPermissions}=require('../functions/lib/listingRegion');const now=1000,future={toMillis:()=>2000},past={toMillis:()=>0};const gb=listingRegion({countryCode:'GB',currency:'GBP'}),za=listingRegion({});assert.deepEqual(listingPermissions(gb,{fullSuiteExpiresAt:future},now),{finder:false,suite:false});const a={paddleSubscriptionId:'sub_fixture',paddleCountryCode:'GB',paddleCurrency:'GBP',paddleFinderExpiresAt:future,paddleSuiteExpiresAt:past};assert.deepEqual(listingPermissions(gb,a,now),{finder:true,suite:false});assert.deepEqual(listingPermissions(gb,{...a,paddleSuiteExpiresAt:future},now),{finder:true,suite:true});assert.deepEqual(listingPermissions(gb,{...a,paddleCountryCode:'US'},now),{finder:false,suite:false});assert.deepEqual(listingPermissions(za,a,now),{finder:false,suite:false});});
