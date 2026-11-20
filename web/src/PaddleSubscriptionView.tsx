import {useEffect,useState} from 'react'
import {initializePaddle} from '@paddle/paddle-js'
import {getFunctions,httpsCallable} from 'firebase/functions'
import {app,auth} from './firebase'
import {sendEmailVerification} from 'firebase/auth'
import type {UserProfile} from './types'
import {useBakeryRegion} from './BakeryRegion'
import type {LaunchCountry} from './workspace-utils'
import RedeemProCode from './RedeemProCode'
import RegionalPlanPrices from './RegionalPlanPrices'

type Plan='weekly'|'monthly'|'annual'|'finder'|'full_suite'|'monthly_launch'|'annual_launch'
type Mode='sandbox'|'live'
type Options={enabled:boolean;plans:{plan:Plan;amount:number;currency:string;regularAmount?:number;periods?:number;endsAt?:string}[];environment?:Mode}
type Checkout={transactionId:string;customerId:string;country:LaunchCountry;token:string;environment:Mode}
const functions=getFunctions(app,'europe-west1')
const labels={weekly:'Weekly Pro',monthly:'Monthly Pro',annual:'Annual Pro',finder:'Baker Finder',full_suite:'Full Suite',monthly_launch:'Monthly Pro launch special',annual_launch:'Annual Pro launch special'}
const periods={weekly:'week',monthly:'month',annual:'year',finder:'month',full_suite:'month',monthly_launch:'month',annual_launch:'year'}

export default function PaddleSubscriptionView({profile,isPro}:{profile:UserProfile;isPro:boolean}){
 const region=useBakeryRegion()
 const [emailVerified,setEmailVerified]=useState(auth.currentUser?.emailVerified===true),[verificationNote,setVerificationNote]=useState('')
 const [options,setOptions]=useState<Options|null>(null),[busy,setBusy]=useState(''),[error,setError]=useState('')
 const linked=profile.subscriptionSource==='paddle'||!!profile.paddleSubscriptionId
 const otherBilling=!!profile.subscriptionSource&&!linked
 const paymentReturned=new URLSearchParams(location.search).get('payment')==='paddle'
 useEffect(()=>{let active=true
  httpsCallable<void,Options>(functions,'getPaddleCheckoutOptions')().then(r=>{if(active)setOptions(r.data)}).catch(()=>{if(active)setOptions({enabled:false,plans:[]})})
  return()=>{active=false}
 },[profile.uid])
 const [acceptedLaunch,setAcceptedLaunch]=useState<Partial<Record<Plan,boolean>>>({})
 async function subscribe(plan:Plan){
  if(busy||!options?.enabled||linked||otherBilling||isPro||!emailVerified)return
  setBusy(plan);setError('')
  try{
   const {data}=await httpsCallable<{plan:Plan;launchTermsAccepted:boolean},Checkout>(functions,'createPaddleCheckout')({plan,launchTermsAccepted:acceptedLaunch[plan]===true})
   if(data.country!==region.countryCode||data.environment!==options.environment||!/^txn_[a-z0-9]{26}$/.test(data.transactionId)||!/^ctm_[a-z0-9]{26}$/.test(data.customerId)||!data.token.startsWith(data.environment==='sandbox'?'test_':'live_'))throw Error('Billing configuration could not be verified.')
   const client=await initializePaddle({token:data.token,...(data.environment==='sandbox'?{environment:'sandbox' as const}:{})})
   if(!client)throw Error('Checkout could not load. Try again with the same plan.')
   client.Checkout.open({transactionId:data.transactionId,customer:{id:data.customerId,address:{countryCode:data.country}},settings:{displayMode:'overlay',variant:'one-page',successUrl:new URL('/?payment=paddle',location.origin).href}})
  }catch(e){setError(e instanceof Error?e.message:'Checkout could not open. Please contact support.')}
  finally{setBusy('')}
 }
 async function manage(){
  setBusy('manage');setError('')
  try{
   const {data}=await httpsCallable<void,{url:string}>(functions,'createPaddlePortalSession')()
   const url=new URL(data.url)
   if(url.protocol!=='https:'||!['customer-portal.paddle.com','sandbox-customer-portal.paddle.com'].includes(url.hostname))throw Error('Unexpected billing portal address.')
   location.assign(url.href)
  }catch(e){setError(e instanceof Error?e.message:'Account management is temporarily unavailable.')}
  finally{setBusy('')}
 }
 return <section className="subscription-view"><RedeemProCode/><RegionalPlanPrices/><a href="/pricing/">Compare BatchBoss plans and regional prices</a>
 <div className="subscription-intro"><span className="eyebrow">Your plan</span><h2>{isPro?'BatchBoss Pro is active':linked&&profile.paddlePlan==='finder'?'Your Baker Finder subscription':'Choose your BatchBoss plan'}</h2><p>Recipe costing, products, suppliers, invoices, quotes and receipts in one bakery workspace.</p></div>
 {error&&<p role="alert" className="error-message">{error}</p>}
 {paymentReturned&&<p role="status" className="success-note">{isPro?'Your subscription is active.':'Your payment is being verified. Access updates after the secure payment confirmation. Please do not start another purchase.'}</p>}
 {isPro&&<p className="success-note">Your Pro tools are unlocked.</p>}
 {linked?<div className="billing-actions"><button disabled={!!busy} className="outline-button" onClick={manage}>Manage subscription, payment method or invoices</button><small>Scheduled cancellation keeps access until the current billing period ends.</small></div>:!isPro&&!otherBilling&&<>
 <h3>Subscriptions ({region.currency})</h3>
 {!emailVerified&&<div className="billing-note"><p>Verify your email address before opening a paid subscription.</p><button className="outline-button" onClick={async()=>{try{if(!auth.currentUser)throw Error('Sign in first.');await sendEmailVerification(auth.currentUser);setVerificationNote('Check your email for the verification link.')}catch{setVerificationNote('Could not send verification email. Try again later.')}}}>Send verification email</button><button className="outline-button" onClick={async()=>{try{await auth.currentUser?.reload();setEmailVerified(auth.currentUser?.emailVerified===true);setVerificationNote(auth.currentUser?.emailVerified?'Email verified.':'Email is not verified yet.')}catch{setVerificationNote('Refresh failed. Please sign in again.')}}}>I have verified my email</button>{verificationNote&&<p role="status">{verificationNote}</p>}</div>}
 {!options?<p>Checking regional payment availability…</p>:!options.enabled?<p className="warning-note">Online subscription checkout is not available in your region yet.</p>:<>
 {options.environment==='sandbox'?<p className="warning-note">Sandbox only. Use test payment details; this does not activate live access.</p>:<p className="warning-note">Private checkout pilot. Confirm payment and renewal terms before continuing.</p>}
 <div className="plans">{options.plans.filter(p=>p.currency===region.currency).map(p=><article key={p.plan}><h3>{labels[p.plan]}</h3>{p.plan==='finder'&&<p>Directory listing only, no Pro access.</p>}{p.plan==='full_suite'&&<p>Pro, Baker Finder and three included Marketplace listings per month.</p>}<strong>{region.format(p.amount/100)}</strong><small> / {periods[p.plan]}. Applicable taxes, trial terms and final total are shown at checkout.</small>{p.regularAmount!==undefined&&<><p>Launch price for {p.periods} paid {p.periods===1?periods[p.plan]:periods[p.plan]+'s'}, then {region.format(p.regularAmount/100)}/{periods[p.plan]} automatically unless canceled. Trial, tax and final total are shown at checkout.</p><label><input type="checkbox" checked={acceptedLaunch[p.plan]===true} onChange={e=>setAcceptedLaunch(old=>({...old,[p.plan]:e.target.checked}))}/> I accept these renewal terms.</label></>}<button className="primary-button" disabled={!emailVerified||!!busy||paymentReturned||p.regularAmount!==undefined&&!acceptedLaunch[p.plan]} onClick={()=>subscribe(p.plan)}>{busy===p.plan?'Opening checkout…':'Subscribe'}</button></article>)}</div>
 </>}</>}
 {otherBilling&&!isPro&&<p className="warning-note">Please contact support to resolve your existing billing before starting a new subscription.</p>}
 <p className="billing-note">Status: <strong>{linked?profile.paddleSubscriptionStatus||profile.subscriptionStatus||'free':profile.subscriptionStatus||'free'}</strong>. Payments are processed on a secure checkout. BatchBoss does not store card details.</p><a href="mailto:info@batchboss.co.za">Need billing help? info@batchboss.co.za</a>
 </section>
}
