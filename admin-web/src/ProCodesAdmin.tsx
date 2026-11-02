import {useEffect,useState,type FormEvent} from 'react'
import {httpsCallable} from 'firebase/functions'
import {functions} from './firebase'
type Code={id:string;source:string;durationDays:number;maxRedemptions:number;redemptions:number;active:boolean;expiresAt:string}
export default function ProCodesAdmin(){
 const [codes,setCodes]=useState<Code[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState(''),[created,setCreated]=useState('')
 async function refresh(){const result=await httpsCallable<void,{codes:Code[]}>(functions,'listProRedeemCodes')();setCodes(result.data.codes)}
 useEffect(()=>{void refresh().catch(error=>setError(error instanceof Error?error.message:'Could not load Pro codes.'))},[])
 async function create(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(busy)return;const form=event.currentTarget;const data=new FormData(form);setBusy(true);setError('');setCreated('')
  try{
   const result=await httpsCallable<{source:string;durationDays:number;maxRedemptions:number;expiresAtMillis:number},{code:string}>(functions,'createProRedeemCode')({source:String(data.get('source')||''),durationDays:Number(data.get('durationDays')),maxRedemptions:Number(data.get('maxRedemptions')),expiresAtMillis:new Date(String(data.get('expiresAt'))).getTime()})
   setCreated(result.data.code);await refresh()
  }catch(error){setError(error instanceof Error?error.message:'Could not create a code.')}
  finally{setBusy(false)}
 }
 async function disable(id:string){if(busy)return;setBusy(true);setError('');try{await httpsCallable<{id:string},{disabled:boolean}>(functions,'disableProRedeemCode')({id});await refresh()}catch(error){setError(error instanceof Error?error.message:'Could not disable code.')}finally{setBusy(false)}}
 return <section className="panel"><h2>Pro redemption codes</h2><p>Create one code per partner or campaign to record its source and grant a fixed Pro period. Codes do not create payments, renewals or partner commission.</p><form onSubmit={create}><label>Campaign / partner source<input name="source" required maxLength={120} placeholder="e.g. November Android testers"/></label><label>Pro days<input name="durationDays" type="number" min="1" max="365" defaultValue={30} required/></label><label>Maximum redemptions<input name="maxRedemptions" type="number" min="1" max="100000" defaultValue={10} required/></label><label>Redeem before (local time)<input name="expiresAt" type="datetime-local" required/></label><button disabled={busy}>{busy?'Working…':'Create Pro code'}</button></form>{created&&<div role="status"><p>Copy this code now; the full code is only shown once.</p><input aria-label="New Pro code" value={created} readOnly/><p>Shareable link</p><input aria-label="Pro code link" readOnly value={`https://app.batchboss.co.za/?signup=1&redeem=${created}`}/></div>}{error&&<p role="alert">{error}</p>}<h3>Latest codes</h3>{codes.map(code=><article key={code.id}><strong>{code.source}</strong><p>{code.durationDays} Pro days · {code.redemptions}/{code.maxRedemptions} redeemed · expires {new Date(code.expiresAt).toLocaleString('en-ZA')} · {code.active?'Enabled':'Disabled'}</p><button disabled={busy||!code.active} onClick={()=>disable(code.id)}>Disable future redemptions</button></article>)}</section>
}
