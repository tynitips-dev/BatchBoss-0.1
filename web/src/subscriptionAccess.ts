export function subscriptionAccess(status:string|undefined,plan:string|undefined,source:string|undefined,expiresAtMillis:number|null|undefined,nowMillis=Date.now()):boolean {
  if(!['active','trialing'].includes(status||''))return false
  return expiresAtMillis!=null ? Number.isFinite(expiresAtMillis)&&expiresAtMillis>nowMillis : plan!=='promo'&&source!=='paystack'
}
