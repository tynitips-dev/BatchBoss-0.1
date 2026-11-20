const {test}=require('node:test'),assert=require('node:assert/strict');const {legacyPayfastConflict}=require('../functions/lib/legacy-billing-core');
test('legacy PayFast renewals preserve the same account but cannot override Paddle, Paystack, or another token',()=>{
 assert.equal(legacyPayfastConflict({subscriptionSource:'payfast',payfastToken:'old'},'old',false),false);
 assert.equal(legacyPayfastConflict({},'old',false),false);
 for(const p of [{subscriptionSource:'paddle'},{paddleSubscriptionId:'sub_1'},{subscriptionSource:'paystack'},{paystackSubscriptionCode:'SUB_1'},{subscriptionSource:'payfast',payfastToken:'other'},{subscriptionSource:'admin_promotion',subscriptionExpiresAt:{toMillis:()=>Date.now()+60000}}])assert.equal(legacyPayfastConflict(p,'old',false),true);
 assert.equal(legacyPayfastConflict({},'old',true),true);
});
