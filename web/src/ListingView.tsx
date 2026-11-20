import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getFunctions, httpsCallable } from 'firebase/functions'
import { app } from './firebase'
import {initializePaddle} from '@paddle/paddle-js'
import type {LaunchCountry} from './workspace-utils'
import { useBakeryRegion } from './BakeryRegion'

type Kind = 'directoryListing' | 'marketplace'
type Draft = { id: string; title: string; area: string; description: string; priceCents: number | null; logo: string; photos: string[]; status?: 'draft'|'pending'|'paid'|'published'|'sold';countryCode?:string|null;currency?:string|null;regionMismatch?:boolean }
const functions = getFunctions(app, 'europe-west1')
type MarketOptions={enabled:boolean;amount?:number;currency?:string;environment?:'sandbox'|'live'}
type MarketCheckout={transactionId:string;customerId:string;country:LaunchCountry;token:string;environment:'sandbox'|'live'}
type ListingRegion={countryCode:string;currency:string;locale:string;publicationEnabled:boolean;directoryPublicationEnabled?:boolean;marketplacePublicationEnabled?:boolean;paddleOptions?:MarketOptions}
const getListingRegion=httpsCallable<void,ListingRegion>(functions,'getListingRegion')
const listDrafts = httpsCallable<{ kind: Kind }, Draft[]>(functions, 'listListingDrafts')
const saveDraft = httpsCallable<Draft & { kind: Kind }, { id: string }>(functions, 'saveListingDraft')
const publishListing = httpsCallable<{ kind: Kind; id: string }, { id: string }>(functions, 'publishBakerListing')
const createMarketplaceCheckout = httpsCallable<{id:string},{checkoutUrl:string}>(functions,'createMarketplaceCheckout')
const verifyMarketplacePayment = httpsCallable<{reference:string},{status:string;testMode?:boolean}>(functions,'verifyMarketplacePayment')
const markSold = httpsCallable<{id:string},{id:string;status:string}>(functions,'markMarketplaceSold')
const empty = { title: '', area: '', description: '', price: '', logo: '', photos: [] as string[] }

async function resizeListingImage(file: File) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 8 * 1024 * 1024) {
    throw new Error('Choose a JPG, PNG or WebP image up to 8 MB.')
  }
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.src = url
    await image.decode()
    const canvas = document.createElement('canvas')
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Image processing is unavailable.')
    for (const size of [640, 480, 320, 240]) {
      const scale = Math.min(1, size / Math.max(image.width, image.height))
      canvas.width = Math.max(1, Math.round(image.width * scale))
      canvas.height = Math.max(1, Math.round(image.height * scale))
      context.fillStyle = '#fff'
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.drawImage(image, 0, 0, canvas.width, canvas.height)
      const data = canvas.toDataURL('image/jpeg', 0.65)
      if (data.length <= 65000) return data
    }
    throw new Error('Choose a smaller image.')
  } finally {
    URL.revokeObjectURL(url)
  }
}

function DraftEditor({ kind,region }: { kind: Kind;region:ListingRegion }) {
  const marketplace = kind === 'marketplace'
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [form, setForm] = useState(empty)
  const [id, setId] = useState<string>(() => crypto.randomUUID())
  const [loading, setLoading] = useState(true)
  const [processingPhoto, setProcessingPhoto] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const selected=drafts.find(draft => draft.id === id)
  const selectedStatus=selected?.status
  const local=region.countryCode==='ZA',paddleAvailable=region.paddleOptions?.enabled===true&&region.paddleOptions.currency===region.currency&&Number.isSafeInteger(region.paddleOptions.amount)
  const feeLabel=local?'R20':paddleAvailable?new Intl.NumberFormat(region.locale,{style:'currency',currency:region.currency}).format(region.paddleOptions!.amount!/100):''

  function edit(draft: Draft) {
    setId(draft.id)
    setForm({ title: draft.title, area: draft.area, description: draft.description, logo: draft.logo ?? '', photos: draft.photos ?? [], price: draft.priceCents === null ? '' : (draft.priceCents / 100).toFixed(2) })
    setNotice('')
  }

  useEffect(() => {
    let current = true
    listDrafts({ kind }).then(({ data }) => {
      if (!current) return
      setDrafts(data)
      if (kind === 'marketplace') {
        const params = new URLSearchParams(location.search)
        const reference = params.get('reference') || params.get('trxref')
        if(params.get('payment')==='paddle_marketplace'){
          const paid=data.find(d=>d.id===params.get('listing'));
          if(paid){edit(paid);setNotice(paid.status==='published'?'Payment verified. Your listing is published.':'Your payment is being verified. Refresh the listing without starting another purchase.')}
        }
        if (params.get('payment') === 'marketplace' && reference) {
          void verifyMarketplacePayment({reference}).then(async ({data:payment}) => {
            if (!current) return
            if(payment.status==='success'&&!payment.testMode){
              const fresh=await listDrafts({kind})
              if(!current)return
              setDrafts(fresh.data)
              setNotice('Payment verified. Your listing is published.')
              const url=new URL(location.href)
              for(const key of ['payment','reference','trxref'])url.searchParams.delete(key)
              history.replaceState(null,'',url.href)
            }else setNotice('Payment is not confirmed for live publication.')
          }).catch(() => { if(current) setError('Payment could not be verified. Do not pay again; contact support or retry verification.') })
        }
      }
      if (kind === 'directoryListing' && data[0]) edit(data[0])
      setLoading(false)
    }).catch(() => {
      if (current) setError('Could not load your drafts. Refresh to try again.')
    })
    return () => { current = false }
  }, [kind])

  async function chooseImage(file: File, logo: boolean) {
    setProcessingPhoto(true)
    setError('')
    try {
      const image = await resizeListingImage(file)
      setForm(previous => logo ? { ...previous, logo: image } : { ...previous, photos: [...previous.photos, image].slice(0, 3) })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not process the image.')
    } finally {
      setProcessingPhoto(false)
    }
  }

  async function openListingCheckout(listingId:string){
    if(local){
      const {data}=await createMarketplaceCheckout({id:listingId})
      const url=new URL(data.checkoutUrl)
      if(url.origin!=='https://checkout.paystack.com')throw Error('Unexpected payment address.')
      location.assign(url.href);return
    }
    if(!paddleAvailable)throw Error('International Marketplace payment is not enabled.')
    const {data}=await httpsCallable<{id:string},MarketCheckout>(functions,'createPaddleMarketplaceCheckout')({id:listingId})
    if(data.country!==region.countryCode||data.environment!==region.paddleOptions?.environment||!/^txn_[a-z0-9]{26}$/.test(data.transactionId)||!/^ctm_[a-z0-9]{26}$/.test(data.customerId)||!data.token.startsWith(data.environment==='sandbox'?'test_':'live_'))throw Error('Billing configuration could not be verified.')
    if(data.environment==='live')setDrafts(previous=>previous.map(d=>d.id===listingId?{...d,status:'pending'}:d))
    const client=await initializePaddle({token:data.token,...(data.environment==='sandbox'?{environment:'sandbox' as const}:{})})
    if(!client)throw Error('Checkout could not load. Resume the saved listing rather than creating another.')
    const url=new URL('/?tab=marketplace&payment=paddle_marketplace',location.origin);url.searchParams.set('listing',listingId)
    client.Checkout.open({transactionId:data.transactionId,customer:{id:data.customerId,address:{countryCode:data.country}},settings:{displayMode:'overlay',variant:'one-page',allowLogout:false,successUrl:url.href}})
    setNotice(data.environment==='sandbox'?'Sandbox checkout does not publish a live listing.':'Complete secure checkout, then refresh to confirm publication. Do not pay again.')
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (processingPhoto||selected?.regionMismatch||selectedStatus==='pending'||selectedStatus==='sold') return
    setSaving(true)
    setError('')
    setNotice('')
    const draft = { id, logo: form.logo, photos: form.photos, title: form.title.trim(), area: form.area.trim(), description: form.description.trim(), priceCents: marketplace ? Math.round(Number(form.price) * 100) : null }
    try {
      const result = await saveDraft({ ...draft, kind })
      const saved = { ...draft, id: result.data.id, status: selectedStatus ?? 'draft' as const,currency:region.currency,countryCode:region.countryCode }
      setDrafts(previous => [...previous.filter(item => item.id !== saved.id), saved])
      edit(saved)
      if ((event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'publish') {
        setNotice('Draft saved. Checking publication eligibility…')
        await publishListing({ kind, id: saved.id })
        setDrafts((await listDrafts({kind})).data)
        setNotice('Listing published.')
      } else if (marketplace && (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'pay') {
        await openListingCheckout(saved.id)
      } else {
        setNotice('Private draft saved.')
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not save the draft.')
    } finally {
      setSaving(false)
    }
  }

  return <div>
    <h3>{marketplace ? 'Marketplace drafts' : 'Your bakery profile draft'}</h3>
    <p>Drafts are private to your signed-in account.</p>
    {marketplace && <div className="card-actions">
      <button type="button" className="outline-button" disabled={loading || saving || processingPhoto} onClick={() => {
        setId(crypto.randomUUID())
        setForm(empty)
        setNotice('')
      }}>New item draft</button>
      {drafts.map(draft => <button key={draft.id} type="button" className="outline-button" disabled={saving || processingPhoto} onClick={() => edit(draft)}>{draft.title} ({draft.status ?? 'draft'})</button>)}
    </div>}
    <form onSubmit={submit}>
      {selected?.regionMismatch&&<p role="alert">This listing has an existing country or currency. Its price cannot be relabelled.</p>}
      <fieldset disabled={loading || saving || processingPhoto || selectedStatus==='sold'||selectedStatus==='pending'||!!selected?.regionMismatch}>
        <label>{marketplace ? 'Item name' : 'Bakery name'}<input required maxLength={100} value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} /></label>
        <label>Area<input required maxLength={100} value={form.area} onChange={event => setForm({ ...form, area: event.target.value })} /></label>
        <label>Description<textarea required maxLength={1000} value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></label>
        {marketplace && <label>Item price ({region.currency})<input type="number" required min="0.01" max="1000000" step="0.01" value={form.price} onChange={event => setForm({ ...form, price: event.target.value })} /></label>}
        {!marketplace && <div>
          <label>Bakery logo<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (file) void chooseImage(file, true)
          }} /></label>
          {form.logo && <><img src={form.logo} alt="Bakery logo preview" width={100} /><button type="button" onClick={() => setForm({ ...form, logo: '' })}>Remove logo</button></>}
        </div>}
        <label>{marketplace ? 'Product photos' : 'Photos of previous cakes'} (up to 3)
          <input type="file" accept="image/jpeg,image/png,image/webp" disabled={form.photos.length >= 3} onChange={event => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (file) void chooseImage(file, false)
          }} />
        </label>
        <div className="card-actions">{form.photos.map((photo, index) => <div key={index}>
          <img src={photo} alt={`Photo ${index + 1} preview`} width={120} />
          <button type="button" onClick={() => setForm({ ...form, photos: form.photos.filter((_, i) => i !== index) })}>Remove photo {index + 1}</button>
        </div>)}</div>
        {processingPhoto && <p role="status">Preparing image…</p>}
        <button className="primary-button" type="submit">{saving ? 'Saving…' : 'Save private draft'}</button>
        {(marketplace?region.marketplacePublicationEnabled:region.directoryPublicationEnabled)&&<button className="outline-button" type="submit" value="publish">Save and publish</button>}
        {marketplace && (local?region.publicationEnabled:paddleAvailable) && !['published','paid','sold','pending'].includes(selectedStatus ?? '') && <button className="primary-button" type="submit" value="pay">{region.paddleOptions?.environment==='sandbox'?'Test listing checkout':`Pay ${feeLabel} and publish`}</button>}
      </fieldset>
    </form>
    {marketplace&&(local?region.publicationEnabled:paddleAvailable)&&selectedStatus==='pending'&&!selected?.regionMismatch&&<button type="button" className="primary-button" disabled={saving} onClick={async()=>{setSaving(true);setError('');try{await openListingCheckout(id)}catch(reason){setError(reason instanceof Error?reason.message:'Could not resume checkout. Do not pay twice.')}finally{setSaving(false)}}}>Resume existing {feeLabel} checkout</button>}
    {marketplace&&<button type="button" className="outline-button" disabled={saving||loading} onClick={async()=>{setSaving(true);setError('');try{const fresh=(await listDrafts({kind})).data;setDrafts(fresh);const current=fresh.find(d=>d.id===id);if(current)edit(current);setNotice(current?.status==='published'?'Listing published.':'Listing status refreshed.')}catch{setError('Could not refresh listing status.')}finally{setSaving(false)}}}>Refresh listing status</button>}
    {marketplace && selectedStatus==='published' && <button type="button" className="outline-button" disabled={saving} onClick={async()=>{if(!window.confirm('Mark this item as sold? It will be removed from the public Marketplace.'))return;setSaving(true);setError('');try{await markSold({id});setDrafts((await listDrafts({kind})).data);setNotice('Item marked as sold.')}catch(reason){setError(reason instanceof Error?reason.message:'Could not mark item sold.')}finally{setSaving(false)}}}>Mark as sold</button>}
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
  </div>
}

const publicPreview = 'https://www.batchboss.co.za/batchboss-preview/'
const listingPrices = {
  directory: 'R49 per month',
  marketplace: 'R20 once-off per listing',
}

export function ListingView({ tab, onViewPlans }: {
  tab: 'directoryListing' | 'marketplace'
  onViewPlans: () => void
}) {
  const isDirectory = tab === 'directoryListing'
  const [region,setRegion]=useState<ListingRegion|null>(null),[regionError,setRegionError]=useState('')
  useEffect(()=>{let active=true;getListingRegion().then(async r=>{if(!active)return;setRegion(r.data);if(r.data.countryCode!=='ZA'){try{const {data}=await httpsCallable<void,MarketOptions>(functions,'getPaddleMarketplaceOptions')();if(active)setRegion({...r.data,paddleOptions:data})}catch{/* publication and private drafts remain available */}}}).catch(()=>{if(active)setRegionError('Could not verify bakery region. Refresh before editing listings.')});return()=>{active=false}},[])
  if(!region)return <section className="content-card"><p role="status">{regionError||'Loading listing region…'}</p></section>

  return <section className="content-card">
    <h2>{isDirectory ? 'Directory Listing' : 'Marketplace'}</h2>
    <p>{isDirectory
      ? 'Show customers what you bake, where you’re based and how they can enquire.'
      : 'List pre-owned baking equipment and supplies for other bakers to discover.'}</p>
    <p>Listing country: {region.countryCode} · Item currency: {region.currency}</p>
    {region.countryCode==='ZA'?<><p><strong>{isDirectory?listingPrices.directory:listingPrices.marketplace}</strong></p><p>{isDirectory?'Directory Listing is included with Full Suite.':'Full Suite includes 3 new Marketplace listings per month. Extra listings cost R20 each, once-off.'}</p></>:<p role="status">{isDirectory?'Baker Finder publication requires an active international Finder or Full Suite plan.':'International Full Suite includes three new Marketplace listings per calendar month (UTC). '+(region.paddleOptions?.enabled&&region.paddleOptions.currency===region.currency&&Number.isSafeInteger(region.paddleOptions.amount)?'Individual listings cost '+new Intl.NumberFormat(region.locale,{style:'currency',currency:region.currency}).format(region.paddleOptions.amount!/100)+' once-off.':'Individual paid listings are not available yet.')} You can save private drafts in your accounting currency.</p>}
    <div className="card-actions">
      <a className="outline-button" href={publicPreview + (isDirectory ? 'baker-finder.html' : 'marketplace.html')}>
        {isDirectory ? 'Preview Find a Baker' : 'Preview Marketplace'}
      </a>
      <button className="primary-button" type="button" onClick={onViewPlans}>View listing plans</button>
    </div>
    <DraftEditor key={tab+':'+region.countryCode} kind={tab} region={region} />
  </section>
}

type CheckoutPlan = 'directory_listing'

export function UpcomingListingPlans(){
  const region=useBakeryRegion()
  return region.countryCode==='ZA'?<DomesticListingPlans/>:<InternationalMarketplacePrice/>
}
function InternationalMarketplacePrice(){
  const region=useBakeryRegion()
  const [options,setOptions]=useState<MarketOptions|null>(null)
  useEffect(()=>{let active=true;httpsCallable<void,MarketOptions>(functions,'getPaddleMarketplaceOptions')().then(({data})=>{if(active)setOptions(data)}).catch(()=>{if(active)setOptions({enabled:false})});return()=>{active=false}},[region.countryCode])
  return <section className="content-card"><h2>Marketplace</h2><div className="plans"><article><h3>Marketplace Listing</h3><strong>{options?.enabled&&options.currency===region.currency&&Number.isSafeInteger(options.amount)?region.format(options.amount!/100):options?'Currently unavailable':'Checking price…'}</strong><p>One payment per item listing. No recurring fee.</p><a className="outline-button" href="/?tab=marketplace">Open Marketplace</a></article></div></section>
}
function DomesticListingPlans() {
  const [directoryManage,setDirectoryManage]=useState(false)
  const [available, setAvailable] = useState<Partial<Record<CheckoutPlan, boolean>>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let current = true
    httpsCallable<void, { enabled: boolean; testMode?: boolean; directoryManagementAvailable?:boolean; listingPlans?: Partial<Record<CheckoutPlan, boolean>> }>(functions, 'getPaystackPlans')()
      .then(({ data }) => { if(current && !data.testMode){setDirectoryManage(data.directoryManagementAvailable===true);if(data.enabled)setAvailable(data.listingPlans??{})} })
      .catch(() => { if (current) setError('Could not check checkout availability.') })
    return () => { current = false }
  }, [])

  async function checkout(plan: CheckoutPlan) {
    setBusy(true)
    setError('')
    try {
      const { data } = await httpsCallable<{ plan: CheckoutPlan }, { checkoutUrl: string }>(functions, 'createPaystackCheckout')({ plan })
      const url = new URL(data.checkoutUrl)
      if (url.origin !== 'https://checkout.paystack.com') throw new Error('Unexpected payment address.')
      location.assign(url.href)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not open checkout.')
      setBusy(false)
    }
  }

  async function manageDirectory(){
    setBusy(true);setError('')
    try{const {data}=await httpsCallable<void,{url:string}>(functions,'managePaystackDirectory')();const url=new URL(data.url);if(url.origin!=='https://paystack.com')throw Error('Unexpected billing management address.');location.assign(url.href)}catch(e){setError(e instanceof Error?e.message:'Could not open Directory billing.');setBusy(false)}
  }
  const plans = [
    { code: 'directory_listing', title: 'Directory Listing', price: listingPrices.directory, detail: 'A bakery profile in Find a Baker.' },
    { title: 'Marketplace Listing', price: listingPrices.marketplace, detail: 'One payment per item listing. No recurring fee.' },
  ] as const

  return <section className="content-card">
    <h2>Directory & Marketplace</h2>
    {directoryManage&&<button className="outline-button" disabled={busy} onClick={manageDirectory}>Manage Directory subscription</button>}
    <p>Paid plans renew at the interval shown. Manage renewal through BatchBoss.</p>
    {error && <p role="alert">{error}</p>}
    <div className="plans">
      {plans.map(plan => <article key={plan.title}>
        <h3>{plan.title}</h3>
        <strong>{plan.price}</strong>
        <p>{plan.detail}</p>
        {'code' in plan && <button className="primary-button" type="button" disabled={busy || !available[plan.code]} onClick={() => checkout(plan.code)}>
          {available[plan.code] ? 'Subscribe now' : 'Checkout unavailable'}
        </button>}{!('code' in plan)&&<a className="outline-button" href="/?tab=marketplace">Open Marketplace</a>}
      </article>)}
    </div>
  </section>
}
