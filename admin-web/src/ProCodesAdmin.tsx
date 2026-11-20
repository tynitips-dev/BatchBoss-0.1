import {useEffect,useState,type FormEvent} from 'react'
import {httpsCallable} from 'firebase/functions'
import {functions} from './firebase'
type Code={id:string;source:string;durationDays:number;maxRedemptions:number;redemptions:number;active:boolean;expiresAt:string}
const status=(c:Code)=>!c.active?'Disabled':Date.parse(c.expiresAt)<=Date.now()?'Expired':c.redemptions>=c.maxRedemptions?'Fully redeemed':'Active'
const pageSize=10
export default function ProCodesAdmin(){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState('All'),[page,setPage]=useState(0)
 const [codes,setCodes]=useState<Code[]>([]),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[created,setCreated]=useState<{code:string;id:string}|null>(null),[notice,setNotice]=useState('')
 async function refresh(){setLoading(true);try{const result=await httpsCallable<void,{codes:Code[]}>(functions,'listProRedeemCodes')();setCodes(result.data.codes)}finally{setLoading(false)}}
 useEffect(()=>{void refresh().catch(error=>setError(error instanceof Error?error.message:'Could not load Pro codes.'))},[])
 async function create(event:FormEvent<HTMLFormElement>){
  event.preventDefault();if(busy)return;const form=event.currentTarget;const data=new FormData(form);setBusy(true);setError('');setNotice('')
  try{
   const result=await httpsCallable<{source:string;durationDays:number;maxRedemptions:number;expiresAtMillis:number;customCode?:string},{code:string;id:string}>(functions,'createProRedeemCode')({source:String(data.get('source')||''),customCode:String(data.get('customCode')||'').trim().toUpperCase(),durationDays:Number(data.get('durationDays')),maxRedemptions:Number(data.get('maxRedemptions')),expiresAtMillis:new Date(String(data.get('expiresAt'))).getTime()})
   setCreated(result.data);form.reset();setSearch('');setFilter('All');setPage(0);await refresh()
  }catch(error){setError(error instanceof Error?error.message:'Could not create or refresh a code.')}
  finally{setBusy(false)}
 }
 async function disable(id:string){if(busy||!window.confirm('Disable future redemptions for this code? Existing Pro access is unaffected.'))return;setBusy(true);setError('');try{await httpsCallable<{id:string},{disabled:boolean}>(functions,'disableProRedeemCode')({id});await refresh()}catch(error){setError(error instanceof Error?error.message:'Could not disable code.')}finally{setBusy(false)}}
 async function copy(value:string){try{await navigator.clipboard.writeText(value);setNotice('Copied to clipboard.')}catch{setNotice('Could not copy automatically. Select and copy the text manually.')}}
 const link=created?'https://app.batchboss.co.za/?signup=1&redeem='+encodeURIComponent(created.code):''
 const filtered=codes.filter(c=>(filter==='All'||status(c)===filter)&&(c.source+' '+(created?.id===c.id?created.code:'')).toLowerCase().includes(search.trim().toLowerCase()))
 const pages=Math.max(1,Math.ceil(filtered.length/pageSize)),current=Math.min(page,pages-1)
 return <div className="codes-workspace">
 <section className="panel codes-create"><span className="eyebrow">Campaigns &amp; partners</span><h2>Create Pro code</h2><p>Grant a fixed period of Pro access and track redemptions by campaign. No payment or recurring subscription is created.</p>
 <form className="codes-form" onSubmit={create}>
 <label>Custom code <small>Optional</small><input name="customCode" minLength={5} maxLength={24} pattern="[A-Za-z0-9]{5,24}" autoComplete="off" placeholder="GLOBAL7" style={{textTransform:'uppercase'}}/></label>
 <label>Campaign / partner source<input name="source" required maxLength={120} placeholder="Global launch"/></label>
 <label>Pro days<input name="durationDays" type="number" min="1" max="365" defaultValue={7} required/></label>
 <label>Maximum redemptions<input name="maxRedemptions" type="number" min="1" max="100000" defaultValue={10} required/></label>
 <label>Redeem before (local time)<input name="expiresAt" type="datetime-local" required/></label>
 <div className="codes-submit"><button className="primary" disabled={busy}>{busy?'Working…':'+ Create Pro code'}</button></div>
 </form><small>Use 5–24 letters or numbers, or leave the custom code blank for a secure random code.</small>
 {created&&<div className="codes-created"><strong>New code created</strong><p>Copy and save the full code now. It cannot be retrieved later.</p><label>Redemption code<div className="codes-copy"><input aria-label="New Pro code" value={created.code} readOnly/><button type="button" className="secondary" onClick={()=>copy(created.code)}>Copy code</button></div></label><label>Shareable link<div className="codes-copy"><input aria-label="Pro code link" readOnly value={link}/><button type="button" className="secondary" onClick={()=>copy(link)}>Copy link</button></div></label><p role="status">{notice}</p></div>}
 {error&&<p className="error" role="alert">{error}</p>}</section>
 <section className="codes-summary">{[['Active',codes.filter(c=>status(c)==='Active').length],['Total redemptions',codes.reduce((n,c)=>n+c.redemptions,0)],['Expired / disabled / fully redeemed',codes.filter(c=>status(c)!=='Active').length]].map(([label,value])=><article className="panel" key={label}><span>{label}</span><strong>{value}</strong></article>)}</section>
 <section className="panel codes-list"><div className="panel-title"><div><h2>Pro redemption codes</h2><p>Manage your latest campaigns and partners.</p></div><button className="secondary" disabled={loading||busy} onClick={()=>{setError('');void refresh().catch(e=>setError(e instanceof Error?e.message:'Refresh failed.'))}}>Refresh</button></div>
 <div className="codes-filters"><label>Search<input value={search} onChange={e=>{setSearch(e.target.value);setPage(0)}} placeholder="Search campaign / partner"/></label><label>Status<select value={filter} onChange={e=>{setFilter(e.target.value);setPage(0)}}>{['All','Active','Expired','Disabled','Fully redeemed'].map(s=><option key={s}>{s}</option>)}</select></label></div>
 <div className="table-wrap"><table><thead><tr>{['Code','Campaign / partner','Pro days','Redemptions','Redeem before','Status','Actions'].map(h=><th key={h} scope="col">{h}</th>)}</tr></thead><tbody>{filtered.slice(current*pageSize,(current+1)*pageSize).map(c=>{const s=status(c),visible=created?.id===c.id?created.code:null;return <tr key={c.id}><td><strong>{visible||'Protected'}</strong><small>Ref: {c.id.slice(0,8)}</small></td><td>{c.source}</td><td>{c.durationDays}</td><td><strong>{c.redemptions} / {c.maxRedemptions}</strong><progress max={c.maxRedemptions} value={Math.min(c.redemptions,c.maxRedemptions)}/></td><td>{new Date(c.expiresAt).toLocaleString('en-ZA')}</td><td><span className={'code-status '+s.toLowerCase().replaceAll(' ','-')}>{s}</span></td><td><div className="actions">{visible&&<button onClick={()=>copy(visible)}>Copy</button>}<button className="danger" disabled={busy||s!=='Active'} onClick={()=>disable(c.id)}>Disable</button></div></td></tr>})}</tbody></table></div>
 {loading?<p role="status">Loading codes…</p>:!filtered.length?<p>No matching codes.</p>:null}
 <div className="codes-pagination"><small>{filtered.length?current*pageSize+1:0}–{Math.min((current+1)*pageSize,filtered.length)} of {filtered.length} loaded codes</small><div className="actions"><button disabled={!current} onClick={()=>setPage(current-1)}>Previous</button><button disabled={current+1>=pages} onClick={()=>setPage(current+1)}>Next</button></div></div>
 <small>Showing up to 100 latest records. Older full codes are stored as hashes, so they cannot be displayed or copied.</small></section></div>
}
