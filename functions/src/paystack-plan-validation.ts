export type PlanExpectation = {code:string; amount:number; interval:'monthly'|'annually'; domain:'live'|'test'};
export function matchesConfiguredPaystackPlan(remote:unknown, expected:PlanExpectation):boolean {
 if(!remote || typeof remote!=='object')return false;
 const plan=remote as Record<string,unknown>;
 return plan.plan_code===expected.code && typeof plan.amount==='number' && plan.amount===expected.amount &&
  plan.interval===expected.interval && plan.currency==='ZAR' && plan.domain===expected.domain;
}
