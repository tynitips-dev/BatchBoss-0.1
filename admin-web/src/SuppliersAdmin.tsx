import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { getApp, getApps, initializeApp } from 'firebase/app'
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, type User } from 'firebase/auth'
import { getFunctions, httpsCallable } from 'firebase/functions'
import { getBlob, getStorage, ref } from 'firebase/storage'

type SupplierStatus = 'pending' | 'approved' | 'rejected' | 'suspended'
type CampaignReviewItem = { supplierId:string; campaignId:string; title:string; description:string; placement:string; startDate:string; endDate:string }
type SupplierApplication = { uid: string; businessName?: string; contactName?: string; email?: string; status: SupplierStatus }
const projectId = import.meta.env.VITE_SUPPLIER_FIREBASE_PROJECT_ID
const ready = projectId === 'batchboss-suppliers' && Boolean(import.meta.env.VITE_SUPPLIER_FIREBASE_API_KEY && import.meta.env.VITE_SUPPLIER_FIREBASE_APP_ID)
const config = {
  apiKey: import.meta.env.VITE_SUPPLIER_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_SUPPLIER_FIREBASE_AUTH_DOMAIN,
  projectId,
  appId: import.meta.env.VITE_SUPPLIER_FIREBASE_APP_ID,
  messagingSenderId: import.meta.env.VITE_SUPPLIER_FIREBASE_MESSAGING_SENDER_ID,
}
function supplierServices() {
  if (!ready) throw new Error('Supplier Firebase connection has not been configured.')
  const app = getApps().some(item => item.name === 'supplier-admin') ? getApp('supplier-admin') : initializeApp(config, 'supplier-admin')
  return { auth: getAuth(app), functions: getFunctions(app), storage: getStorage(app) }
}
function errorText(error: unknown) {
  const code = (error as {code?:string})?.code || ''
  if (code.includes('permission-denied')) return 'Access denied. Your supplier identity requires the server-issued batchbossSupplierAdmin claim.'
  if (code.includes('not-found') || code.includes('unavailable')) return 'Supplier approval functions are not deployed or reachable in the supplier Firebase project.'
  return error instanceof Error ? error.message : 'Supplier service unavailable.'
}

function CampaignArtwork({supplierId,campaignId}:{supplierId:string;campaignId:string}) {
  const [url,setUrl]=useState<string|null>(null)
  const [status,setStatus]=useState('Loading artwork…')
  useEffect(()=>{
    let active=true
    let objectUrl:string|null=null
    const path=`supplier-campaigns/${supplierId}/${campaignId}/artwork`
    getBlob(ref(supplierServices().storage,path)).then(blob=>{
      objectUrl=URL.createObjectURL(blob)
      if(active){setUrl(objectUrl);setStatus('')}else URL.revokeObjectURL(objectUrl)
    }).catch(e=>{
      const code=(e as {code?:string}).code
      if(active)setStatus(code==='storage/object-not-found'?'No artwork attached':code==='storage/unauthorized'?'Artwork access denied':`Cannot load artwork (${code||'network error'})`)
    })
    return ()=>{active=false;if(objectUrl)URL.revokeObjectURL(objectUrl)}
  },[supplierId,campaignId])
  return url?<img className="supplier-review-image" src={url} alt="Supplier advertisement artwork"/>:<div className="supplier-review-image supplier-review-image-empty">{status}</div>
}

function SupplierCampaignReviews() {
  const [items,setItems]=useState<CampaignReviewItem[]>([])
  const [loading,setLoading]=useState(true)
  const [busy,setBusy]=useState('')
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [reason,setReason]=useState('')
  const [rejecting,setRejecting]=useState('')
  const refresh=useCallback(async()=>{
    setLoading(true);setError('')
    try {
      const fn=httpsCallable<undefined,{campaigns:CampaignReviewItem[]}>(supplierServices().functions,'listCampaignReviewQueue')
      setItems((await fn()).data.campaigns)
    }catch(e){setError(errorText(e))}
    finally{setLoading(false)}
  },[])
  useEffect(()=>{void refresh()},[refresh])
  async function review(item:CampaignReviewItem,decision:'approved'|'rejected'){
    const key=item.supplierId+':'+item.campaignId
    if(!window.confirm(`${decision==='approved'?'Approve':'Reject'} creative for "${item.title}"? Approval will NOT activate advertising.`))return
    setBusy(key);setError('');setNotice('')
    try{
      const call=httpsCallable(supplierServices().functions,'reviewSupplierCampaign')
      await call({supplierId:item.supplierId,campaignId:item.campaignId,decision,reason:decision==='rejected'?reason.trim():undefined})
      setRejecting('');setReason('')
      await refresh()
      setNotice(`Campaign ${decision}. Advertising remains unpublished.`)
    }catch(e){setError(errorText(e))}
    finally{setBusy('')}
  }
  return <div className="supplier-campaign-review">
    <div className="supplier-admin-tools"><h3>Pending advertising campaigns</h3><button className="secondary" onClick={()=>void refresh()} disabled={loading||!!busy}>Refresh campaigns</button></div>
    <p>Review uploaded artwork and campaign details. Moderator approval requires a valid plan and does not publish an advert or charge anyone.</p>
    {loading?<p>Loading campaign reviews…</p>:items.length===0?<p>No campaigns awaiting review.</p>:items.map(item=>{
      const key=item.supplierId+':'+item.campaignId
      return <article key={key} className="supplier-review-item">
        <CampaignArtwork supplierId={item.supplierId} campaignId={item.campaignId}/>
        <div className="supplier-review-copy">
          <h3>{item.title}</h3>
          <p>{item.description||'No description provided.'}</p>
          <small><strong>Placement:</strong> {item.placement} | <strong>Dates:</strong> {item.startDate} to {item.endDate}</small>
          <small>Supplier ID: {item.supplierId}</small>
          <div className="supplier-admin-tools">
            <button className="secondary" disabled={!!busy} onClick={()=>void review(item,'approved')}>Approve creative</button>
            <button className="secondary" disabled={!!busy} onClick={()=>{setRejecting(key);setReason('')}}>Reject</button>
          </div>
          {rejecting===key&&<div className="supplier-rejection-form"><label>Rejection reason (optional)<textarea maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
            <div className="supplier-admin-tools"><button className="secondary" disabled={!!busy} onClick={()=>void review(item,'rejected')}>Confirm rejection</button>
              <button className="secondary" onClick={()=>setRejecting('')}>Cancel</button></div></div>}
        </div>
      </article>
    })}
    {error&&<div className="error" role="alert">{error}</div>}
    {notice&&<p className="notice" role="status">{notice}</p>}
  </div>
}

/** Isolated supplier login: the customer-admin Firebase identity is NOT a supplier-admin grant. */
export default function SuppliersAdmin({initialTab='applications'}:{initialTab?:'applications'|'campaigns'}) {
  const [user,setUser] = useState<User|null>(null)
  const [authorized,setAuthorized] = useState(false)
  const [checking,setChecking] = useState(ready)
  const [status,setStatus] = useState<SupplierStatus>('pending')
  const [tab,setTab] = useState<'applications'|'campaigns'>(initialTab)
  const [applications,setApplications] = useState<SupplierApplication[]>([])
  const [loading,setLoading] = useState(false)
  const [busy,setBusy] = useState('')
  const [error,setError] = useState('')
  const [notice,setNotice] = useState('')

  useEffect(() => {
    if (!ready) return
    const {auth} = supplierServices()
    return onAuthStateChanged(auth, async current => {
      setUser(current); setAuthorized(false); setApplications([]); setError('')
      if (current) {
        try {const token=await current.getIdTokenResult(true);setAuthorized(token.claims.batchbossSupplierAdmin===true)}
        catch(e){setError(errorText(e))}
      }
      setChecking(false)
    })
  }, [])

  const refresh = useCallback(async () => {
    if (!authorized) return
    setLoading(true);setError('')
    try {
      const {functions}=supplierServices()
      const call=httpsCallable<{status:SupplierStatus;limit:number},{applications:SupplierApplication[]}>(functions,'listSupplierApplications')
      const result=await call({status,limit:50})
      setApplications(result.data.applications)
    } catch(e) {setError(errorText(e))}
    finally {setLoading(false)}
  },[authorized,status])
  useEffect(()=>{void refresh()},[refresh])

  async function login(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();setError('');setChecking(true)
    const form=new FormData(event.currentTarget)
    try{await signInWithEmailAndPassword(supplierServices().auth,String(form.get('email')).trim(),String(form.get('password')))}
    catch(e){setError(errorText(e));setChecking(false)}
  }
  async function review(item:SupplierApplication,decision:'approved'|'rejected') {
    const reason=decision==='rejected'?window.prompt('Optional reason for rejecting this supplier:', ''):undefined
    if(reason===null)return
    if(!window.confirm(`${decision==='approved'?'Approve':'Reject'} ${item.businessName||item.email||'supplier'}? This updates their supplier account, not billing.`))return
    setBusy(item.uid);setError('');setNotice('')
    try{
      const {functions}=supplierServices()
      const payload:{supplierId:string;decision:'approved'|'rejected';reason?:string}={supplierId:item.uid,decision}
      if(decision==='rejected')payload.reason=reason||''
      await httpsCallable<typeof payload,{status:string}>(functions,'reviewSupplierApplication')(payload)
      setNotice(`Supplier application ${decision}.`);await refresh()
    } catch(e){setError(errorText(e))}
    finally{setBusy('')}
  }
  return <section className="panel supplier-admin-section">
    <h2>Supplier administration</h2>
    <p>Separate supplier accounts, moderation and subscriptions. Customer accounts and bakery records remain unchanged.</p>
    {!ready ? <div className="error">Supplier Firebase connection is not configured for this build. Set the VITE_SUPPLIER_FIREBASE_* web configuration for the existing batchboss-suppliers project.</div> :
      checking ? <p>Checking supplier administrator access…</p> :
      !user ? <form className="supplier-admin-login" onSubmit={login}>
        <p>Sign in with a supplier-project account granted the server-side <code>batchbossSupplierAdmin</code> claim.</p>
        <label>Supplier admin email<input name="email" type="email" required autoComplete="email" /></label>
        <label>Password<input name="password" type="password" required autoComplete="current-password" /></label>
        <button className="secondary" type="submit">Connect supplier administration</button>
      </form> :
      !authorized ? <div className="error">This account is signed in but does not have supplier administrator permissions. An existing customer administrator is not automatically authorized in the separate supplier project. <button className="secondary" onClick={()=>signOut(supplierServices().auth)}>Switch supplier account</button></div> :
      <>
        <div className="supplier-admin-tabs"><button type="button" className={tab==='applications'?'active':''} onClick={()=>setTab('applications')}>Supplier applications</button><button type="button" className={tab==='campaigns'?'active':''} onClick={()=>setTab('campaigns')}>Campaign reviews</button></div>
        {tab==='campaigns'?<SupplierCampaignReviews/>:<>
        <p>Connected to supplier administration as {user.email}. Approval does not activate a paid listing.</p>
        <div className="supplier-admin-tools">
          <label>Status <select value={status} onChange={event=>setStatus(event.target.value as SupplierStatus)}><option value="pending">Pending</option><option value="approved">Approved</option><option value="rejected">Rejected</option><option value="suspended">Suspended</option></select></label>
          <button className="secondary" type="button" disabled={loading} onClick={()=>void refresh()}>{loading?'Loading…':'Refresh suppliers'}</button>
          <button className="secondary" type="button" onClick={()=>signOut(supplierServices().auth)}>Disconnect</button>
        </div>
        {loading ? <p>Loading applications…</p> : applications.length===0 ? <p>No applications found with this status.</p> : applications.map(item=><article key={item.uid} className="supplier-admin-item">
          <div><strong>{item.businessName||'Unnamed supplier'}</strong><p>{item.contactName||'No contact'} · {item.email||'No email'}</p><small>Status: {item.status}</small></div>
          {item.status==='pending'&&<div className="supplier-admin-tools">
            <button className="secondary" disabled={!!busy} onClick={()=>void review(item,'approved')}>Approve</button>
            <button className="secondary" disabled={!!busy} onClick={()=>void review(item,'rejected')}>Reject</button>
          </div>}
        </article>)}
        </>}
      </>}
    {error&&<div className="error" role="alert">{error}</div>}
    {notice&&<p className="notice" role="status">{notice}</p>}
    <p><small>Requires deployed trusted supplier callable functions and a server-issued supplier admin claim. Firestore status must never be changed directly from this browser. Public listing additionally requires an approved profile and verified paid entitlement.</small></p>
  </section>
}
