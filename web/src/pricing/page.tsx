import {useEffect,useState} from 'react'
import {createRoot} from 'react-dom/client'
import {initializePaddle,type Paddle} from '@paddle/paddle-js'
import {auth} from '../firebase'
import {launchCountries,regionPresets,formatRegionalMoney,type LaunchCountry} from '../workspace-utils'
import catalog from '../regional-plan-prices.json'
import './pricing.css'

type Plan={name:string;interval:string;prices:Partial<Record<string,number>>}
const plans=catalog as ReadonlyArray<Plan>
const env=import.meta.env
// Do not initialize live Paddle until server-side verification, account linking and renewal/cancellation handling are implemented.
const sandbox=env.VITE_PADDLE_SANDBOX_ENABLED==='true' && typeof env.VITE_PADDLE_CLIENT_TOKEN==='string' && env.VITE_PADDLE_CLIENT_TOKEN.startsWith('test_')
const ids:Record<string,string|undefined>={
 'Weekly Pro':env.VITE_PADDLE_PRO_WEEK,
 'Monthly Pro':env.VITE_PADDLE_PRO_MONTH,
 'Annual Pro':env.VITE_PADDLE_PRO_YEAR,
 'Monthly Pro launch special':env.VITE_PADDLE_LAUNCH_MONTH,
 'Annual Pro launch special':env.VITE_PADDLE_LAUNCH_YEAR,
}
const settings={displayMode:'overlay',variant:'one-page',successUrl:new URL('/welcome/',location.origin).href} as const
type Quote={id:string;total:string}
function Pricing(){
 const [country,setCountry]=useState<LaunchCountry>('ZA')
 const [paddle,setPaddle]=useState<Paddle>()
 const [preview,setPreview]=useState<{country:LaunchCountry;quotes:Record<string,Quote>}>()
 const [notice,setNotice]=useState('')
 const [email,setEmail]=useState('')
 useEffect(()=>{let active=true
  auth.authStateReady().then(()=>{if(active)setEmail(auth.currentUser?.email||'')}).catch(()=>undefined)
  if(sandbox){initializePaddle({environment:'sandbox',token:env.VITE_PADDLE_CLIENT_TOKEN,checkout:{settings}}).then(client=>{if(active&&client)setPaddle(client);else if(active)setNotice('Sandbox could not load.')}).catch(()=>{if(active)setNotice('Sandbox could not load.')})}
  return()=>{active=false}
 },[])
 useEffect(()=>{if(!paddle)return;let active=true;setPreview(undefined)
  async function load(client:Paddle){
   const quotes:Record<string,Quote>={}
   await Promise.all(plans.map(async plan=>{
    const id=ids[plan.name]
    if(!id||!/^pri_[a-z0-9]{26}$/.test(id)||Object.values(ids).filter(x=>x===id).length!==1)return
    try{
     const result=await client.PricePreview({items:[{priceId:id,quantity:1}],address:{countryCode:country}})
     const item=result.data.details.lineItems.find(x=>x.price.id===id&&x.quantity===1)
     if(item?.price.billingCycle?.interval===plan.interval&&item.price.billingCycle.frequency===1&&item.formattedTotals.total)
      quotes[plan.name]={id,total:item.formattedTotals.total}
    }catch{/* One unavailable price must not hide the rest of the catalog. */}
   }))
   if(active)setPreview({country,quotes})
  }
  void load(paddle)
  return()=>{active=false}
 },[paddle,country])
 const {currency,locale}=regionPresets[country]
 function checkout(plan:Plan){
  const q=preview?.country===country?preview.quotes[plan.name]:undefined
  if(!sandbox||!paddle||!q)return
  try{paddle.Checkout.open({settings,items:[{priceId:q.id,quantity:1}],...(email.trim()?{customer:{email:email.trim(),address:{countryCode:country}}}:{})})}catch{setNotice('Test checkout could not open.')}
 }
 return <main><a href="/">Back to Baker Portal</a><h1>Choose your BatchBoss plan</h1>
 <p>Free tools, Pro subscriptions and optional bakery services. Select a country to compare prices.</p>
 <label>Country <select value={country} onChange={e=>setCountry(e.target.value as LaunchCountry)}>{launchCountries.map(c=><option key={c.code} value={c.code}>{c.name} ({regionPresets[c.code].currency})</option>)}</select></label>
 <p className="notice">{sandbox?'Paddle sandbox testing only. Use test payment details. No real money or Pro access is transferred.':'Catalog preview. Live Paddle checkout is not enabled. Existing Paystack subscriptions are managed in the Baker Portal.'}</p>
 {notice&&<p role="alert">{notice}</p>}
 {sandbox&&<label>Test email (optional) <input type="email" value={email} onChange={e=>setEmail(e.target.value)}/></label>}
 <div className="plans"><article><h2>BatchBoss Free</h2><p className="price">{formatRegionalMoney(0,currency,locale)}</p><p>Start with free tools.</p><a href="/">Open BatchBoss</a></article>{plans.map(plan=>{
  const amount=plan.prices[currency];if(amount===undefined)return null
  const quote=preview?.country===country?preview.quotes[plan.name]:undefined
  const isLaunch=plan.name.includes('launch special')
  return <article key={plan.name}><h2>{plan.name}</h2><p className="price">{formatRegionalMoney(amount/100,currency,locale)} <small>/ {plan.interval} · {currency}</small></p>
   {isLaunch&&<small>{country==='ZA'?'Promotional terms apply.':'Proposed launch pricing; terms and availability pending.'}</small>}
   {sandbox&&ids[plan.name]?<><small>{quote?'Paddle test quote: '+quote.total+' (taxes may affect totals)':preview?'Test quote unavailable for this country':'Checking test quote…'}</small><button disabled={!quote} onClick={()=>checkout(plan)}>Open test checkout</button></>:<small>Checkout availability to be confirmed.</small>}
  </article>
 })}</div></main>
}
createRoot(document.getElementById('root')!).render(<Pricing/>)
