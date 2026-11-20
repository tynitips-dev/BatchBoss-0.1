import {useEffect,useState} from 'react'
import {createRoot} from 'react-dom/client'
import {initializePaddle} from '@paddle/paddle-js'
import {getFunctions,httpsCallable} from 'firebase/functions'
import {auth,app} from '../firebase'
import {launchCountries,regionPresets,formatRegionalMoney,type LaunchCountry} from '../workspace-utils'
import catalog from '../regional-plan-prices.json'
import './pricing.css'

type Plan={name:string;interval:string;prices:Partial<Record<string,number>>}
type Resume={transactionId:string;customerId:string;country:LaunchCountry;token:string;environment:'sandbox'|'live';listingId:string|null;launchOffer:boolean}
const plans=catalog as ReadonlyArray<Plan>
// Remove Paddle's automatic checkout query before SDK initialization. Only the
// backend may authorize reopening it, and the original account must be signed in.
const url=new URL(location.href)
const transaction=url.searchParams.get('_ptxn')||''
if(url.searchParams.has('_ptxn')){url.searchParams.delete('_ptxn');history.replaceState(null,'',url.href)}
const functions=getFunctions(app,'europe-west1')
function Pricing(){
 const [country,setCountry]=useState<LaunchCountry>('ZA')
 const [notice,setNotice]=useState('')
 const [pending,setPending]=useState(false)
 const [signedIn,setSignedIn]=useState(false)
 useEffect(()=>{let active=true;auth.authStateReady().then(()=>{if(active)setSignedIn(!!auth.currentUser)}).catch(()=>undefined);return()=>{active=false}},[])
 async function resume(){
  if(pending||!signedIn||!/^txn_[a-z0-9]{26}$/.test(transaction))return
  setPending(true);setNotice('')
  try{
   const {data}=await httpsCallable<{transactionId:string},Resume>(functions,'resumePaddleCheckout')({transactionId:transaction})
   if(data.transactionId!==transaction||!/^ctm_[a-z0-9]{26}$/.test(data.customerId)||!data.token.startsWith(data.environment==='live'?'live_':'test_')||!Object.hasOwn(regionPresets,data.country))throw Error('Checkout configuration needs review.')
   const client=await initializePaddle({token:data.token,...(data.environment==='sandbox'?{environment:'sandbox' as const}:{})})
   if(!client)throw Error('Checkout could not load.')
   const success=new URL(data.listingId?'/?payment=paddle_marketplace':'/?payment=paddle',location.origin)
   if(data.listingId)success.searchParams.set('listing',data.listingId)
   client.Checkout.open({transactionId:data.transactionId,customer:{id:data.customerId,address:{countryCode:data.country}},settings:{displayMode:'overlay',variant:'one-page',allowLogout:false,...(data.launchOffer?{allowDiscountRemoval:false,showAddDiscounts:false}:{}),successUrl:success.href}})
  }catch(e){setNotice(e instanceof Error?e.message:'Checkout could not be resumed. Contact support before paying again.')}
  finally{setPending(false)}
 }
 const {currency,locale}=regionPresets[country]
 return <main><a href="/">Back to Baker Portal</a><h1>Choose your BatchBoss plan</h1>
 <p>Free tools, Pro subscriptions and optional bakery services. Select a country to compare prices.</p>
 {transaction&&<section className="notice"><h2>Resume a payment</h2>{signedIn?<button disabled={pending||!/^txn_[a-z0-9]{26}$/.test(transaction)} onClick={resume}>{pending?'Checking payment…':'Open verified checkout'}</button>:<p>Sign in to the original Baker Portal account, then resume your pending purchase from Subscriptions or Marketplace.</p>}</section>}
 <label>Country <select value={country} onChange={e=>setCountry(e.target.value as LaunchCountry)}>{launchCountries.map(c=><option key={c.code} value={c.code}>{c.name} ({regionPresets[c.code].currency})</option>)}</select></label>
 <p className="notice">Compare regional prices here, then sign in to subscribe. Your bakery country determines the payment provider. Final taxes, renewal and trial terms appear at checkout.</p>
 {notice&&<p role="alert">{notice}</p>}
 <div className="plans"><article><h2>BatchBoss Free</h2><p className="price">{formatRegionalMoney(0,currency,locale)}</p><p>Start with free tools.</p><a href="/?signup=1">Open BatchBoss</a></article>{plans.map(plan=>{
  const amount=plan.prices[currency];if(amount===undefined)return null
  return <article key={plan.name}><h2>{plan.name}</h2><p className="price">{formatRegionalMoney(amount/100,currency,locale)} <small>/ {plan.interval} · {currency}</small></p>
   {plan.name.includes('launch special')&&<small>Limited launch offer. Availability and recurring terms are confirmed before checkout.</small>}
   <p><a href={plan.name==='Marketplace Listing'?'/?tab=marketplace':'/?payment=subscription'}>Go to Baker Portal</a></p>
  </article>
 })}</div></main>
}
createRoot(document.getElementById('root')!).render(<Pricing/>)
