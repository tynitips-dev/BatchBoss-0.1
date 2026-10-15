const test=require('node:test');const assert=require('node:assert/strict');
const {matchesConfiguredPaystackPlan:matches}=require('../functions/lib/paystack-plan-validation');
const expected={code:'PLN_example',amount:9900,interval:'monthly',domain:'test'};
const plan={plan_code:'PLN_example',amount:9900,interval:'monthly',currency:'ZAR',domain:'test'};
test('valid configured plan',()=>assert.equal(matches(plan,expected),true));
for(const [field,value] of [['amount',14900],['interval','annually'],['currency','USD'],['domain','live'],['plan_code','PLN_wrong'],['amount','9900']]){
 test('reject mismatched '+field,()=>assert.equal(matches({...plan,[field]:value},expected),false));
}
test('reject incomplete plan',()=>assert.equal(matches({},expected),false));
