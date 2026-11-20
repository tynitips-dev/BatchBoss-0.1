// Historical PayFast payments may still arrive after checkout retirement.
// Record them for reconciliation, but do not overwrite another provider or grant.
export function legacyPayfastConflict(profile:Record<string,any>,token:string,paddlePending:boolean):boolean {
 if(paddlePending||profile.paddleSubscriptionId||profile.subscriptionSource==='paddle')return true;
 if(profile.paystackSubscriptionCode||profile.subscriptionSource==='paystack')return true;
 if(profile.subscriptionSource==='payfast'&&profile.payfastToken&&profile.payfastToken!==token)return true;
 if(profile.subscriptionSource==='admin_promotion'&&profile.subscriptionExpiresAt?.toMillis?.()>Date.now())return true;
 return !!profile.subscriptionSource&&!['payfast','admin_promotion'].includes(profile.subscriptionSource);
}
