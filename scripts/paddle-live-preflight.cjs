#!/usr/bin/env node
// Read-only production preflight. No Paddle records or Firebase state are changed.
// Run with Node 22 from the repository root, after functions compilation.
const fs=require('node:fs'),path=require('node:path'),{timingSafeEqual}=require('node:crypto');
const {remotePriceMatches,configuredCheckoutPlan,standardPlans,checkoutAmount}=require('../functions/lib/paddle-checkout-core');
const {oneOffPriceMatches,marketplaceFees}=require('../functions/lib/paddle-marketplace-core');
const {launchConfig,launchDiscountMatches}=require('../functions/lib/paddle-launch-core');
const countries={GB:'GBP',US:'USD',CA:'CAD',AU:'AUD',NZ:'NZD'};
const requiredEvents=['subscription.created','subscription.updated','subscription.canceled','subscription.activated','subscription.trialing','subscription.paused','subscription.resumed','subscription.past_due','customer.created','customer.updated','transaction.completed'];
const origin='https://api.paddle.com',defaultURL='https://app.batchboss.co.za/pricing/';
let failed=0;
function result(name,ok){console.log((ok?'PASS ':'FAIL ')+name);if(!ok)failed++}
function sameSecret(a,b){if(typeof a!=='string'||typeof b!=='string'||!a||!b)return false;const aa=Buffer.from(a),bb=Buffer.from(b);return aa.length===bb.length&&timingSafeEqual(aa,bb)}
async function run(){
 const configPath=process.argv[2]||'functions/.env.batch-boss-android';
 // Node's dotenv parser handles quoted JSON values; shell secrets override non-secret settings.
 const config={...require('node:util').parseEnv(fs.readFileSync(path.resolve(configPath),'utf8')),...process.env};
 const key=config.PADDLE_API_KEY,secret=config.PADDLE_WEBHOOK_SECRET;
 if(!key||!secret)throw Error('Provide the API key and notification signing secret in the local process environment.');
 async function api(url){
  const u=new URL(url,origin);
  if(u.origin!==origin)throw Error('Unexpected API host');
  const response=await fetch(u,{method:'GET',headers:{Authorization:'Bearer '+key,'Paddle-Version':'1'},signal:AbortSignal.timeout(20000)});
  if(!response.ok)throw Error(u.pathname+' HTTP '+response.status+'; check key scope and live account.');
  return response.json();
 }
 async function list(endpoint){
  const rows=[];let next=endpoint;
  for(let i=0;i<100&&next;i++){
   const r=await api(next);if(!Array.isArray(r.data))throw Error('Invalid list response');
   rows.push(...r.data);next=r.meta?.pagination?.has_more?r.meta.pagination.next:null;
  }
  if(next)throw Error('Pagination limit reached');return rows;
 }
 result('Live environment',config.PADDLE_ENVIRONMENT==='live');
 result('Live client token prefix',typeof config.PADDLE_CLIENT_TOKEN==='string'&&config.PADDLE_CLIENT_TOKEN.startsWith('live_'));
 result('Public access remains disabled for initial pilot',config.PADDLE_PUBLIC_CHECKOUT_ENABLED!=='true'&&config.BATCHBOSS_GLOBAL_SIGNUP_ENABLED!=='true');
 const domains=await list('/checkout-domains?status=approved');
 result('Approved Baker Portal domain',domains.some(d=>d.domain==='app.batchboss.co.za'&&d.status==='approved'));
 const verification=await list('/verifications?verification_type=onboarding');
 result('Completed onboarding verification',verification.some(v=>v.verification_type==='onboarding'&&v.status==='completed'));
 const checkout=await api('/settings/checkout-url');
 result('Exact default payment link',checkout.data?.default_url===defaultURL);
 const dest=process.env.PADDLE_EXPECTED_WEBHOOK_URL;
 if(!dest)result('Explicit deployed webhook URL provided',false);
 else{
  const parsed=new URL(dest);
  if(parsed.protocol!=='https:')throw Error('Webhook must use HTTPS');
  const notifications=await list('/notification-settings?active=true');
  const found=notifications.filter(n=>n.destination===dest&&n.active&&n.type==='url');
  result('One live notification destination',found.length===1&&found[0].api_version===1&&['platform','all'].includes(found[0].traffic_source));
  result('Required subscribed events',found.length===1&&requiredEvents.every(e=>found[0].subscribed_events.some(x=>x.name===e)));
  result('Notification signing secret matches',found.length===1&&sameSecret(found[0].endpoint_secret_key,secret));
 }
 const map=JSON.parse(config.PADDLE_PRO_PRICE_MAP||'{}');
 const allPrices=new Map();
 for(const plan of Object.keys(standardPlans)){
  const matches=Object.entries(map).filter(([,v])=>configuredCheckoutPlan(v)===plan);
  if(matches.length!==1){result(plan+' unique price ID',false);continue}
  const [priceId,mapped]=matches[0];
  if(!/^pri_[a-z0-9]{26}$/.test(priceId)||!/^pro_[a-z0-9]{26}$/.test(mapped.productId)){result(plan+' valid IDs',false);continue}
  const price=(await api('/prices/'+priceId)).data;
  const product=(await api('/products/'+mapped.productId)).data;
  result(plan+' active product',product?.id===mapped.productId&&product.status==='active');
  for(const [country,currency] of Object.entries(countries))result(plan+' '+currency,remotePriceMatches(price,{...mapped,priceId,country,currency,amount:checkoutAmount(plan,currency)}));
  // The original seven-day trial requirement is checked, not silently applied.
  result(plan+' seven-day trial',price?.trial_period?.interval==='day'&&price.trial_period.frequency===7&&(!price.trial_period.unit_price||price.trial_period.unit_price.amount==='0'));
  allPrices.set(plan,priceId);
 }
 const marketID=config.PADDLE_MARKETPLACE_PRICE_ID,marketProduct=config.PADDLE_MARKETPLACE_PRODUCT_ID;
 if(/^pri_[a-z0-9]{26}$/.test(marketID||'')&&/^pro_[a-z0-9]{26}$/.test(marketProduct||'')){
  const price=(await api('/prices/'+marketID)).data;
  const product=(await api('/products/'+marketProduct)).data;
  result('Marketplace active product',product?.id===marketProduct&&product.status==='active'&&!map[marketID]);
  for(const [country,currency] of Object.entries(countries))result('marketplace '+currency,oneOffPriceMatches(price,{priceId:marketID,productId:marketProduct,country,currency,amount:marketplaceFees[currency]}));
 }else result('Marketplace one-off IDs',false);
 if(config.PADDLE_LAUNCH_ENABLED==='true'){
  for(const [country,currency] of Object.entries(countries))for(const offer of ['monthly_launch','annual_launch']){
   const expected=launchConfig(config.PADDLE_LAUNCH_DISCOUNT_MAP||'{}',offer,country,currency);
   const priceId=allPrices.get(offer==='monthly_launch'?'monthly':'annual');
   if(!expected||!priceId){result(offer+' '+currency+' configured terms',false);continue}
   const remote=(await api('/discounts/'+expected.discountId)).data;
   result(offer+' '+currency,launchDiscountMatches(remote,expected,priceId));
  }
 }else console.log('PENDING launch offer configuration; still disabled.');
 console.log(failed?'STOP: '+failed+' check(s) failed. Do not open live checkout.':'PASS: read-only settings checks. Real webhook delivery, Firebase state and live checkout must still be verified.');
 process.exitCode=failed?1:0;
}
run().catch(error=>{console.error('STOP: '+(error instanceof Error?error.message:'Preflight failed'));process.exitCode=1});
