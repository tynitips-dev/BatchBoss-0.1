import { useEffect, useState } from 'react'
import { httpsCallable, type Functions } from 'firebase/functions'

type Account = { uid:string; businessName:string; businessType?:string; businessTypeOther?:string; contactName:string; email:string; status:string; selectedPlanId:string|null; paidPlanId:string|null; paidStatus:string; activePlanId:string|null; accessSource:string; accessExpiresAt:string|null; manualVersion:number; manualActive:boolean; profileStatus:string; profileVersion:string|null }
const plans = ['directory','growth','featured']
export default function SupplierAccounts({functions}:{functions:Functions}) {
  const [items,setItems]=useState<Account[]>([])
  const [cursor,setCursor]=useState<string|null>(null)
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [search,setSearch]=useState('')
  const [editing,setEditing]=useState<string|null>(null)
  const [plan,setPlan]=useState('directory')
  const [granting,setGranting]=useState<string|null>(null)
  const [days,setDays]=useState(30)
  const [reason,setReason]=useState('')
  const [confirmation,setConfirmation]=useState('')
  const [deleting,setDeleting]=useState<string|null>(null)
  const [typedName,setTypedName]=useState('')
  async function load(after?:string) {
    setBusy(true);setError('')
    try {
      const result=await httpsCallable<{cursor?:string},{suppliers:Account[];nextCursor:string|null}>(functions,'listSupplierAccounts')(after?{cursor:after}:{})
      setItems(old=>after?[...old,...result.data.suppliers]:result.data.suppliers)
      setCursor(result.data.nextCursor)
    } catch(e) {setError(e instanceof Error?e.message:'Unable to load suppliers.')}
    finally {setBusy(false)}
  }
  useEffect(()=>{void load()},[functions])
  async function save(item:Account) {
    setBusy(true);setError('');setNotice('')
    try {
      await httpsCallable(functions,'setSupplierSelectedPlan')({supplierId:item.uid,planId:plan,expectedPlanId:item.selectedPlanId})
      setItems(old=>old.map(row=>row.uid===item.uid?{...row,selectedPlanId:plan}:row))
      setEditing(null);setNotice('Chosen package updated. Billing and paid access are unchanged.')
    } catch(e) {setError(e instanceof Error?e.message:'Unable to change package. Refresh and try again.')}
    finally {setBusy(false)}
  }
  async function access(item:Account,action:'grant'|'revoke') {
    const key=item.uid+':'+action
    if(confirmation!==key){setConfirmation(key);return}
    setBusy(true);setError('');setNotice('')
    try {
      await httpsCallable(functions,'setSupplierAdminAccess')({supplierId:item.uid,action,planId:plan,days,reason,expectedVersion:item.manualVersion||0})
      setGranting(null);setConfirmation('');await load();setNotice('Administrator access updated and audited. No Paystack charge was created.')
    }catch(e){setError(e instanceof Error?e.message:'Unable to update access.')}
    finally{setBusy(false)}
  }
  async function publishProfile(item:Account){
    if(confirmation!==item.uid+':profile'){setConfirmation(item.uid+':profile');return}
    setBusy(true);setError('')
    try{await httpsCallable(functions,'reviewSupplierProfile')({supplierId:item.uid,decision:'publish'});await load();setConfirmation('');setNotice('Business profile published.')}
    catch(e){setError(e instanceof Error?e.message:'Unable to publish profile.')}
    finally{setBusy(false)}
  }
  async function profileAction(item:Account){
    setBusy(true);setError('');setNotice('')
    const restoring=item.profileStatus==='deleted'
    try{
      await httpsCallable(functions,restoring?'restoreSupplierProfile':'deleteSupplierProfile')({supplierId:item.uid,expectedProfileVersion:item.profileVersion,confirmationName:typedName})
      setDeleting(null);setTypedName('');await load()
      setNotice(restoring?'Profile restored as a private draft. Review and publication are required again.':'Profile deleted from public display. Listing and adverts are withdrawn. Login and billing records are retained; you can restore the profile here.')
    }catch(e){setError(e instanceof Error?e.message:'Unable to change profile status.')}
    finally{setBusy(false)}
  }
  const filtered=items.filter(item=>`${item.businessName} ${item.contactName} ${item.email} ${item.status}`.toLowerCase().includes(search.toLowerCase()))
  return <section aria-label="Supplier list">
    <h3>Suppliers and packages</h3>
    <p>Manage chosen packages and active package access. Administrator grants are time-limited, audited and never charge Paystack. Verified paid subscriptions remain separate.</p>
    <div className="supplier-admin-tools"><label>Search loaded suppliers<input value={search} onChange={e=>setSearch(e.target.value)}/></label><button className="secondary" disabled={busy} onClick={()=>{setEditing(null);void load()}}>Refresh list</button></div>
    {filtered.map(item=><article className="supplier-admin-item" key={item.uid}>
      <div><strong>{item.businessName||'Unnamed supplier'}</strong><p>{item.contactName||'No contact'} · {item.email||'No email'}</p><p>Business type: {item.businessType||'Not provided'}{item.businessType==='Other'&&item.businessTypeOther?` — ${item.businessTypeOther}`:''}</p><p>Status: {item.status}</p><p>Chosen package: <strong>{item.selectedPlanId||'Not selected'}</strong></p><p>Verified paid package: {item.paidPlanId||'None'} · {item.paidStatus}</p><p>Active package: <strong>{item.activePlanId||'None'}</strong> · {item.accessSource||'None'}{item.accessExpiresAt?` · until ${new Date(item.accessExpiresAt).toLocaleDateString('en-ZA')}`:''}</p><p>Directory profile: {item.profileStatus||'missing'}</p></div>
      {editing===item.uid?<div className="supplier-admin-tools"><label>Chosen package<select value={plan} onChange={e=>{setPlan(e.target.value);setConfirmation('')}}>{plans.map(value=><option key={value} value={value}>{value}</option>)}</select></label><button className="secondary" disabled={busy} onClick={()=>void save(item)}>Save package</button><button className="secondary" disabled={busy} onClick={()=>setEditing(null)}>Cancel</button></div>:<button className="secondary" disabled={busy} onClick={()=>{setEditing(item.uid);setGranting(null);setConfirmation('');setPlan(item.selectedPlanId||'directory')}}>Change package</button>}
      <div className="supplier-admin-tools"><button className="secondary" disabled={busy||item.status!=='approved'} onClick={()=>{setGranting(item.uid);setEditing(null);setConfirmation('');setPlan(item.activePlanId||item.selectedPlanId||'growth');setDays(30);setReason('')}}>Manage active package</button>{!['published','deleted','missing'].includes(item.profileStatus)&&<button className="secondary" disabled={busy||!item.activePlanId} onClick={()=>void publishProfile(item)}>{confirmation===item.uid+':profile'?'Confirm public directory listing':'Publish directory profile'}</button>}</div>
      {granting===item.uid&&<div className="supplier-admin-tools" style={{flexWrap:'wrap'}}><label>Active package<select value={plan} onChange={e=>{setPlan(e.target.value);setConfirmation('')}}>{plans.map(value=><option key={value} value={value}>{value}</option>)}</select></label><label>Access duration (days)<input type="number" min={1} max={365} value={days} onChange={e=>{setDays(Number(e.target.value));setConfirmation('')}}/></label><label>Reason for access change<input maxLength={500} value={reason} onChange={e=>{setReason(e.target.value);setConfirmation('')}}/></label><button className="secondary" disabled={busy||reason.trim().length<5||!Number.isInteger(days)||days<1||days>365} onClick={()=>void access(item,'grant')}>{confirmation===item.uid+':grant'?'Confirm no-charge package grant':'Grant package without charge'}</button>{item.manualActive&&<button className="secondary" disabled={busy||reason.trim().length<5} onClick={()=>void access(item,'revoke')}>{confirmation===item.uid+':revoke'?'Confirm revocation':'Revoke admin grant'}</button>}<button className="secondary" disabled={busy} onClick={()=>setGranting(null)}>Cancel access change</button></div>}
      {item.profileVersion&&<button className="secondary" disabled={busy} onClick={()=>{setDeleting(item.uid);setTypedName('');setEditing(null);setGranting(null)}}>{item.profileStatus==='deleted'?'Restore supplier profile':'Delete supplier profile'}</button>}
      {deleting===item.uid&&<div role="group" aria-label="Confirm supplier profile change" style={{width:'100%'}}><p>{item.profileStatus==='deleted'?'Restore this profile as a private draft? Its adverts will still need approval again.':'Delete this supplier profile from public display? Its directory listing and adverts will stop showing. The profile can be restored; the login, campaigns and billing records are retained.'}</p><label>Type {item.businessName||'Unnamed supplier'} to confirm<input value={typedName} onChange={e=>setTypedName(e.target.value)} autoComplete="off"/></label><button className="secondary" disabled={busy||typedName!==(item.businessName||'Unnamed supplier')} onClick={()=>void profileAction(item)}>{item.profileStatus==='deleted'?'Confirm restore profile':'Confirm delete profile'}</button><button className="secondary" disabled={busy} onClick={()=>{setDeleting(null);setTypedName('')}}>Cancel profile change</button></div>}
    </article>)}
    {!busy&&!filtered.length&&<p>No suppliers found{search?' in the loaded records':''}.</p>}
    {cursor&&<button className="secondary" disabled={busy} onClick={()=>void load(cursor)}>Load more suppliers</button>}
    {busy&&<p role="status">Loading or saving suppliers…</p>}
    {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  </section>
}
