import {useState} from 'react'
import {getFunctions,httpsCallable} from 'firebase/functions'
import {app} from './firebase'
import RedeemProCode from './RedeemProCode'
import type {UserProfile} from './types'
const functions=getFunctions(app,'europe-west1')
type Status={status:string;nextPaymentDate:string|null;accessUntil:string|null;renews:boolean}
export default function LegacyPayfastView({profile,isPro}:{profile:UserProfile;isPro:boolean}){
 const [status,setStatus]=useState<Status|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[confirm,setConfirm]=useState(false)
 async function refresh(){
  setBusy(true);setMessage('')
  try{setStatus((await httpsCallable<void,Status>(functions,'getPayfastSubscriptionStatus')()).data)}
  catch(e){setMessage(e instanceof Error?e.message:'Could not verify subscription. Contact support.')}
  finally{setBusy(false)}
 }
 async function cancel(){
  if(!confirm||busy)return
  setBusy(true);setMessage('')
  try{const {data}=await httpsCallable<{confirm:boolean},{cancelled:boolean;accessUntil:string|null}>(functions,'cancelPayfastSubscription')({confirm:true})
   if(data.cancelled){setStatus(null);setConfirm(false);setMessage('Recurring PayFast payments stopped. Your already-paid access continues until its expiry date.')}
  }catch(e){setMessage(e instanceof Error?e.message:'Cancellation could not be confirmed. Contact support.')}
  finally{setBusy(false)}
 }
 return <section className="subscription-view"><RedeemProCode/><h2>{isPro?'BatchBoss Pro is active':'Your subscription'}</h2><p>New PayFast subscriptions are no longer offered. Your existing payment record remains available. Manage your existing subscription before switching payment providers.</p><p>Status: {profile.subscriptionStatus||'unknown'}</p><button className="outline-button" disabled={busy} onClick={refresh}>Check existing subscription</button>{status&&<p>Provider status: {status.status}{status.accessUntil?' · Paid access until '+new Date(status.accessUntil).toLocaleDateString():''}</p>}<label><input type="checkbox" checked={confirm} onChange={e=>setConfirm(e.target.checked)}/> I want to stop future recurring payments.</label><button className="outline-button" disabled={busy||!confirm} onClick={cancel}>Cancel recurring payments</button>{message&&<p role="status">{message}</p>}<a href="mailto:info@batchboss.co.za">Contact billing support</a></section>
}
