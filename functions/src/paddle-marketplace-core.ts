// Marketplace charges are separate from recurring subscriptions and listed item values.
export const marketplaceFees:Record<string,number>={ZAR:2000,USD:149,GBP:119,CAD:219,AUD:249,NZD:249};
export const validListingId=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
export function oneOffPriceMatches(remote:any,expected:{priceId:string;productId:string;country:string;currency:string;amount:number}):boolean {
 if(!remote||remote.id!==expected.priceId||remote.product_id!==expected.productId||remote.status!=='active'||remote.billing_cycle!==null||remote.trial_period!=null)return false;
 const matches=Array.isArray(remote.unit_price_overrides)?remote.unit_price_overrides.filter((v:any)=>Array.isArray(v.country_codes)&&v.country_codes.includes(expected.country)):[];
 if(matches.length>1)return false;
 const unit=matches.length?matches[0].unit_price:remote.unit_price;
 return unit?.currency_code===expected.currency&&unit.amount===String(expected.amount);
}
export function marketplaceCompleted(data:any,expected:any):boolean{
 const item=Array.isArray(data?.items)&&data.items.length===1?data.items[0]:null;
 return !!expected&&data?.id===expected.transactionId&&data.status==='completed'&&data.origin==='api'&&data.customer_id===expected.customerId&&data.currency_code===expected.currency&&data.collection_mode==='automatic'&&data.subscription_id==null&&item?.quantity===1&&item.price?.id===expected.priceId&&item.price?.product_id===expected.productId&&item.price?.billing_cycle===null;
}
