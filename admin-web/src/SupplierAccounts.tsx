import { useEffect, useState } from 'react'
import { httpsCallable, type Functions } from 'firebase/functions'

type Account = { uid:string; businessName:string; contactName:string; email:string; status:string; selectedPlanId:string|null; paidPlanId:string|null; paidStatus:string }
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
  const filtered=items.filter(item=>`${item.businessName} ${item.contactName} ${item.email} ${item.status}`.toLowerCase().includes(search.toLowerCase()))
  return <section aria-label="Supplier list">
    <h3>Suppliers and packages</h3>
    <p>Change the package a supplier has chosen. Verified paid access is shown separately; changing the choice does not charge the supplier or grant paid access.</p>
    <div className="supplier-admin-tools"><label>Search loaded suppliers<input value={search} onChange={e=>setSearch(e.target.value)}/></label><button className="secondary" disabled={busy} onClick={()=>{setEditing(null);void load()}}>Refresh list</button></div>
    {filtered.map(item=><article className="supplier-admin-item" key={item.uid}>
      <div><strong>{item.businessName||'Unnamed supplier'}</strong><p>{item.contactName||'No contact'} · {item.email||'No email'}</p><p>Status: {item.status}</p><p>Chosen package: <strong>{item.selectedPlanId||'Not selected'}</strong></p><p>Verified paid package: {item.paidPlanId||'None'} · {item.paidStatus}</p></div>
      {editing===item.uid?<div className="supplier-admin-tools"><label>Chosen package<select value={plan} onChange={e=>setPlan(e.target.value)}>{plans.map(value=><option key={value} value={value}>{value}</option>)}</select></label><button className="secondary" disabled={busy} onClick={()=>void save(item)}>Save package</button><button className="secondary" disabled={busy} onClick={()=>setEditing(null)}>Cancel</button></div>:<button className="secondary" disabled={busy} onClick={()=>{setEditing(item.uid);setPlan(item.selectedPlanId||'directory')}}>Change package</button>}
    </article>)}
    {!busy&&!filtered.length&&<p>No suppliers found{search?' in the loaded records':''}.</p>}
    {cursor&&<button className="secondary" disabled={busy} onClick={()=>void load(cursor)}>Load more suppliers</button>}
    {busy&&<p role="status">Loading or saving suppliers…</p>}
    {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  </section>
}
