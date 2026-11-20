// Shared server-side routing and trusted checkout validation. Prices are minor units.
export const standardPlans={
  "weekly": {
    "interval": "week",
    "prices": {
      "USD": 299,
      "ZAR": 3999,
      "GBP": 229,
      "CAD": 449,
      "AUD": 449,
      "NZD": 499
    }
  },
  "monthly": {
    "interval": "month",
    "prices": {
      "USD": 1099,
      "ZAR": 14900,
      "GBP": 849,
      "CAD": 1599,
      "AUD": 1699,
      "NZD": 1899
    }
  },
  "full_suite": {"interval":"month","prices":{"USD":1699,"ZAR":22000,"GBP":1299,"CAD":2499,"AUD":2599,"NZD":2899}},
  "finder": {"interval":"month","prices":{"USD":399,"ZAR":4900,"GBP":299,"CAD":549,"AUD":599,"NZD":649}},
  "annual": {
    "interval": "year",
    "prices": {
      "USD": 8999,
      "ZAR": 119900,
      "GBP": 6999,
      "CAD": 12999,
      "AUD": 13999,
      "NZD": 14999
    }
  }
} as const;
const currencies:Record<string,string>={ZA:'ZAR',US:'USD',GB:'GBP',CA:'CAD',AU:'AUD',NZ:'NZD'};
export type CheckoutPlan=keyof typeof standardPlans;
export function paymentRegion(bakery:Record<string,unknown>|undefined) {
 if(!bakery)return null;
 const country=bakery.countryCode===undefined?'ZA':bakery.countryCode;
 const currency=bakery.currency===undefined?'ZAR':bakery.currency;
 if(typeof country!=='string'||!Object.hasOwn(currencies,country)||currencies[country]!==currency)return null;
 return {country,currency:currencies[country],provider:country==='ZA'?'paystack':'paddle'};
}
export function checkoutAmount(plan:CheckoutPlan,currency:string):number|undefined {
 return (standardPlans[plan]?.prices as Record<string,number>|undefined)?.[currency];
}
export function isCheckoutPlan(plan:unknown):plan is CheckoutPlan {
 return typeof plan==='string'&&Object.hasOwn(standardPlans,plan);
}
export function remotePriceMatches(remote:any,expected:{priceId:string;productId:string;interval:string;country:string;currency:string;amount:number}):boolean {
 if(!remote||remote.id!==expected.priceId||remote.status!=='active'||remote.product_id!==expected.productId||remote.billing_cycle?.interval!==expected.interval||remote.billing_cycle?.frequency!==1)return false;
 const matches=Array.isArray(remote.unit_price_overrides)?remote.unit_price_overrides.filter((v:any)=>Array.isArray(v.country_codes)&&v.country_codes.includes(expected.country)):[];
 if(matches.length>1)return false;
 const unit=matches.length?matches[0].unit_price:remote.unit_price;
 return unit?.currency_code===expected.currency&&unit.amount===String(expected.amount);
}
export function isFreeCheckoutProfile(p:any):boolean {
 return !!p&&p.subscriptionPlan==='free'&&p.subscriptionStatus==='free'&&!p.subscriptionSource&&!p.paystackSubscriptionCode&&!p.paddleSubscriptionId&&!p.payfastToken;
}
export function completedMatches(data:any,expected:any):boolean {
 const item=Array.isArray(data?.items)&&data.items.length===1?data.items[0]:null;
 return !!expected&&data?.id===expected.transactionId&&data.status==='completed'&&data.origin==='api'&&data.customer_id===expected.customerId&&data.currency_code===expected.currency&&data.collection_mode==='automatic'&&item?.quantity===1&&item.price?.id===expected.priceId&&item.price?.product_id===expected.productId&&item.price?.billing_cycle?.interval===expected.interval&&item.price?.billing_cycle?.frequency===1;
}

/** Existing Pro maps without an explicit plan remain compatible. Additional offers must be explicit. */
export function configuredCheckoutPlan(price:{interval:string;plan?:unknown;offer?:string}):CheckoutPlan|null {
 if(price.offer)return null;
 if(price.plan!==undefined)return isCheckoutPlan(price.plan)&&standardPlans[price.plan].interval===price.interval?price.plan:null;
 return price.interval==='week'?'weekly':price.interval==='month'?'monthly':price.interval==='year'?'annual':null;
}
export function planRights(plan:CheckoutPlan|null){return {pro:plan==='weekly'||plan==='monthly'||plan==='annual'||plan==='full_suite',finder:plan==='finder'||plan==='full_suite',suite:plan==='full_suite'};}
