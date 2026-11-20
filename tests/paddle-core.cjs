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
