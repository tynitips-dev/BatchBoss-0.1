import {useState,type FormEvent} from 'react'
import {getFunctions,httpsCallable} from 'firebase/functions'
import {app} from './firebase'

export default function RedeemProCode(){
 const [code,setCode]=useState(()=>new URLSearchParams(location.search).get('redeem')||'')
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('')
 async function redeem(event:FormEvent){
  event.preventDefault();if(busy)return;setBusy(true);setError('');setMessage('')
  try{
   const result=await httpsCallable<{code:string},{redeemed:boolean;alreadyRedeemed:boolean;expiresAt:string}>(getFunctions(app,'europe-west1'),'redeemProCode')({code:code.trim().toUpperCase()})
   setMessage(result.data.alreadyRedeemed?'You have already used this code. It has not been applied again.':`Your Pro access is active until ${new Date(result.data.expiresAt).toLocaleDateString('en-ZA')}. No payment or automatic renewal was started.`)
   setCode('');const url=new URL(location.href);url.searchParams.delete('redeem');history.replaceState(null,'',url)
  }catch(error){setError(error instanceof Error?error.message:'The code could not be redeemed. Please try again.')}
  finally{setBusy(false)}
 }
 return <section className="content-card"><h3>Have a Pro code?</h3><p>Redeem a BatchBoss code from a campaign or partner. Each code can be used once per account. Your source is recorded with the redemption.</p><form className="modal-form" onSubmit={redeem}><label>Pro code<input name="proCode" value={code} onChange={event=>setCode(event.target.value)} maxLength={28} autoComplete="off" placeholder="BBP-…" required/></label><button className="primary-button" disabled={busy}>{busy?'Redeeming…':'Redeem Pro code'}</button></form>{message&&<p className="success-note" role="status">{message}</p>}{error&&<p className="error-message" role="alert">{error}</p>}</section>
}
