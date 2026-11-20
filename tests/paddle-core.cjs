const {test}=require('node:test');
const assert=require('node:assert/strict');
const {grantsPaddleAccess,shouldApplyPaddleEvent,mappedPaddlePrice,parseVerifiedPaddleEvent}=require('../functions/lib/paddle');
const now=Date.parse('2026-11-20T09:00:00Z'),future=now+86400000;
test('only active and trialing with a future expiry grant access',()=>{
 for(const status of ['active','trialing'])assert.equal(grantsPaddleAccess(status,future,now),true);
 for(const status of ['canceled','paused','past_due','unknown'])assert.equal(grantsPaddleAccess(status,future,now),false);
 assert.equal(grantsPaddleAccess('active',now,now),false);
 assert.equal(grantsPaddleAccess('trialing',null,now),false);
});
test('stale, duplicate and invalid event times never replace newer state',()=>{
 assert.equal(shouldApplyPaddleEvent(now,now-1),false);
 assert.equal(shouldApplyPaddleEvent(now,now),false);
 assert.equal(shouldApplyPaddleEvent(now,now+1),true);
 assert.equal(shouldApplyPaddleEvent(undefined,now),true);
 assert.equal(shouldApplyPaddleEvent(undefined,NaN),false);
});
test('only the configured product, price, interval and quantity qualify',()=>{
 const pri='pri_'+'a'.repeat(26),pro='pro_'+'b'.repeat(26);
 const config={[pri]:{productId:pro,interval:'month'}};
 const item={quantity:1,price:{id:pri,product_id:pro,billing_cycle:{interval:'month',frequency:1}}};
 assert.deepEqual(mappedPaddlePrice({items:[item]},config),config[pri]);
 assert.equal(mappedPaddlePrice({items:[{...item,quantity:2}]},config),undefined);
 assert.equal(mappedPaddlePrice({items:[item,item]},config),undefined);
 assert.equal(mappedPaddlePrice({items:[{...item,price:{...item.price,product_id:'pro_'+'c'.repeat(26)}}]},config),undefined);
 assert.equal(mappedPaddlePrice({items:[item]},{}),undefined);
});
test('verified envelope parser rejects malformed input; signature checking belongs to webhook endpoint',()=>{
 const event={event_id:'evt_'+'a'.repeat(26),event_type:'subscription.updated',occurred_at:'2026-11-20T09:00:00Z',data:{id:'sub_'+'b'.repeat(26)}};
 assert.deepEqual(parseVerifiedPaddleEvent(JSON.stringify(event)),event);
 assert.throws(()=>parseVerifiedPaddleEvent(JSON.stringify({...event,occurred_at:'bad'})));
 assert.throws(()=>parseVerifiedPaddleEvent('{}'));
});


const {paymentRegion,remotePriceMatches,completedMatches,standardPlans,isFreeCheckoutProfile}=require('../functions/lib/paddle-checkout-core');
test('country routes are derived from verified bakery metadata, never currency alone',()=>{
 assert.equal(paymentRegion({}).provider,'paystack');
 for(const [country,currency] of Object.entries({US:'USD',GB:'GBP',CA:'CAD',AU:'AUD',NZ:'NZD'}))assert.deepEqual(paymentRegion({countryCode:country,currency}),{country,currency,provider:'paddle'});
 assert.equal(paymentRegion({countryCode:'GB',currency:'USD'}),null);
 assert.equal(paymentRegion({countryCode:'IN',currency:'INR'}),null);
});
test('server standard pricing matches the published six-country catalog',()=>{
 const catalog=require('../web/src/regional-plan-prices.json');
 for(const [k,name] of Object.entries({weekly:'Weekly Pro',monthly:'Monthly Pro',annual:'Annual Pro',finder:'Baker Finder Monthly',full_suite:'Full Suite Monthly'}))assert.deepEqual(standardPlans[k].prices,catalog.find(x=>x.name===name).prices);
});
test('remote price must match country override, currency, product, interval and amount',()=>{
 const e={priceId:'pri_1',productId:'pro_1',interval:'month',country:'GB',currency:'GBP',amount:849};
 const p={id:e.priceId,product_id:e.productId,status:'active',billing_cycle:{interval:'month',frequency:1},unit_price:{amount:'1099',currency_code:'USD'},unit_price_overrides:[{country_codes:['GB'],unit_price:{amount:'849',currency_code:'GBP'}}]};
 assert.equal(remotePriceMatches(p,e),true);
 assert.equal(remotePriceMatches({...p,unit_price_overrides:[]},e),false);
 assert.equal(remotePriceMatches(p,{...e,amount:850}),false);
 assert.equal(remotePriceMatches({...p,status:'archived'},e),false);
});
test('completed webhook must match the exact server-created checkout',()=>{
 const expected={transactionId:'txn_one',customerId:'ctm_one',currency:'GBP',priceId:'pri_one',productId:'pro_one',interval:'month'};
 const tx={id:'txn_one',status:'completed',origin:'api',customer_id:'ctm_one',currency_code:'GBP',collection_mode:'automatic',items:[{quantity:1,price:{id:'pri_one',product_id:'pro_one',billing_cycle:{interval:'month',frequency:1}}}]};
 assert.equal(completedMatches(tx,expected),true);
 for(const change of [{id:'txn_other'},{customer_id:'ctm_other'},{currency_code:'USD'},{status:'paid'},{origin:'web'},{items:[]}])assert.equal(completedMatches({...tx,...change},expected),false);
 assert.equal(completedMatches(tx,undefined),false);
 assert.equal(isFreeCheckoutProfile({subscriptionPlan:'free',subscriptionStatus:'free'}),true);
 for(const source of ['paystack','paddle','payfast','admin_promotion'])assert.equal(isFreeCheckoutProfile({subscriptionPlan:'free',subscriptionStatus:'free',subscriptionSource:source}),false);
});

test('event ordering preserves provider microsecond precision',()=>{const {providerEventClock}=require('../functions/lib/paddle');const a=providerEventClock('2026-11-20T09:00:00.123001Z'),b=providerEventClock('2026-11-20T09:00:00.123002Z');assert.equal(b-a,1);assert.equal(shouldApplyPaddleEvent(b,a),false);assert.equal(providerEventClock('2026-11-20T11:00:00.123002+02:00'),b);});

test('explicit plan mapping separates monthly Pro, Finder and Full Suite; invalid intervals are rejected',()=>{const {configuredCheckoutPlan,planRights}=require('../functions/lib/paddle-checkout-core');assert.equal(configuredCheckoutPlan({interval:'month'}),'monthly');assert.equal(configuredCheckoutPlan({interval:'month',plan:'finder'}),'finder');assert.equal(configuredCheckoutPlan({interval:'year',plan:'finder'}),null);assert.deepEqual(planRights('finder'),{pro:false,finder:true,suite:false});assert.deepEqual(planRights('full_suite'),{pro:true,finder:true,suite:true});});

test('one-off Marketplace fee follows the regional catalog and requires a nonrecurring price',()=>{const {marketplaceFees,oneOffPriceMatches,marketplaceCompleted}=require('../functions/lib/paddle-marketplace-core');const catalog=require('../web/src/regional-plan-prices.json');assert.deepEqual(marketplaceFees,catalog.find(x=>x.name==='Marketplace Listing').prices);const ex={priceId:'pri_x',productId:'pro_x',country:'GB',currency:'GBP',amount:119};const remote={id:ex.priceId,product_id:ex.productId,status:'active',billing_cycle:null,unit_price:{amount:'149',currency_code:'USD'},unit_price_overrides:[{country_codes:['GB'],unit_price:{amount:'119',currency_code:'GBP'}}]};assert.equal(oneOffPriceMatches(remote,ex),true);assert.equal(oneOffPriceMatches({...remote,billing_cycle:{interval:'month',frequency:1}},ex),false);assert.equal(oneOffPriceMatches({...remote,unit_price_overrides:[]},ex),false);assert.equal(marketplaceCompleted({},ex),false);});

test('launch discounts match catalog prices and explicit renewal terms',()=>{const {launchPrices,launchConfig,launchDiscountMatches}=require('../functions/lib/paddle-launch-core');const catalog=require('../web/src/regional-plan-prices.json');assert.deepEqual(launchPrices.monthly_launch,catalog.find(x=>x.name==='Monthly Pro launch special').prices);assert.deepEqual(launchPrices.annual_launch,catalog.find(x=>x.name==='Annual Pro launch special').prices);const now=Date.parse('2026-11-20'),endsAt='2026-12-20T00:00:00Z',discountId='dsc_'+'a'.repeat(26);const raw=JSON.stringify({GB:{monthly_launch:{discountId,endsAt,periods:2},annual_launch:{discountId,endsAt,periods:2}}});const x=launchConfig(raw,'monthly_launch','GB','GBP',now);assert.equal(x.difference,300);assert.equal(x.regular,849);assert.equal(launchConfig(raw,'annual_launch','GB','GBP',now),null);assert.equal(launchConfig(raw,'monthly_launch','GB','GBP',Date.parse(endsAt)),null);const d={id:discountId,status:'active',mode:'standard',type:'flat',currency_code:'GBP',amount:'300',enabled_for_checkout:false,restrict_to:['pri_month'],expires_at:endsAt,recur:true,maximum_recurring_intervals:2};assert.equal(launchDiscountMatches(d,x,'pri_month',now),true);for(const change of [{amount:'299'},{maximum_recurring_intervals:null},{enabled_for_checkout:true},{restrict_to:['pri_other']},{currency_code:'USD'},{expires_at:'2027-01-01'}])assert.equal(launchDiscountMatches({...d,...change},x,'pri_month',now),false);});
