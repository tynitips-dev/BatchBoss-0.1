import {checkoutAmount,CheckoutPlan} from './paddle-checkout-core';
export const launchPrices={monthly_launch:{USD:749,ZAR:9900,GBP:549,CAD:1049,AUD:1099,NZD:1249},annual_launch:{USD:7499,ZAR:99000,GBP:5499,CAD:10499,AUD:10999,NZD:12499}} as const;
export type LaunchOffer=keyof typeof launchPrices;
export type SalePlan=CheckoutPlan|LaunchOffer;
export function isLaunchOffer(p:unknown):p is LaunchOffer{return typeof p==='string'&&Object.hasOwn(launchPrices,p);}
export function baseLaunchPlan(p:LaunchOffer):'monthly'|'annual'{return p==='monthly_launch'?'monthly':'annual';}
export function launchAmount(p:LaunchOffer,currency:string){return (launchPrices[p] as Record<string,number>)[currency];}
export function launchConfig(raw:string,offer:LaunchOffer,country:string,currency:string,now=Date.now()){
 if(country==='ZA')return null;
 const parsed=JSON.parse(raw),row=parsed?.[country]?.[offer],amount=launchAmount(offer,currency),regular=checkoutAmount(baseLaunchPlan(offer),currency);
 if(!row||typeof row.discountId!=='string'||!/^dsc_[a-z0-9]{26}$/.test(row.discountId)||typeof row.endsAt!=='string'||!Number.isFinite(Date.parse(row.endsAt))||Date.parse(row.endsAt)<=now||!Number.isInteger(row.periods)||row.periods<1||row.periods>12||offer==='annual_launch'&&row.periods!==1||amount===undefined||regular===undefined||regular<=amount)return null;
 return {discountId:row.discountId as string,endsAt:row.endsAt as string,periods:row.periods as number,amount,regular,currency,difference:regular-amount};
}
export function launchDiscountMatches(remote:any,expected:NonNullable<ReturnType<typeof launchConfig>>,priceId:string,now=Date.now()):boolean{
 return !!remote&&remote.id===expected.discountId&&remote.status==='active'&&remote.mode==='standard'&&remote.type==='flat'&&remote.currency_code===expected.currency&&remote.amount===String(expected.difference)&&remote.enabled_for_checkout===false&&Array.isArray(remote.restrict_to)&&remote.restrict_to.length===1&&remote.restrict_to[0]===priceId&&typeof remote.expires_at==='string'&&Date.parse(remote.expires_at)===Date.parse(expected.endsAt)&&Date.parse(remote.expires_at)>now&&(remote.usage_limit==null||Number.isInteger(remote.times_used)&&remote.times_used<remote.usage_limit)&&(expected.periods===1?remote.recur===false&&remote.maximum_recurring_intervals==null:remote.recur===true&&remote.maximum_recurring_intervals===expected.periods);
}
