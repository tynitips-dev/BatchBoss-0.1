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
