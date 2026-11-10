import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getFunctions, httpsCallable } from 'firebase/functions'
import { app } from './firebase'

type Kind = 'directoryListing' | 'marketplace'
type Draft = { id: string; title: string; area: string; description: string; priceCents: number | null; logo: string; photos: string[] }
const functions = getFunctions(app, 'europe-west1')
const listDrafts = httpsCallable<{ kind: Kind }, Draft[]>(functions, 'listListingDrafts')
const saveDraft = httpsCallable<Draft & { kind: Kind }, { id: string }>(functions, 'saveListingDraft')
const publishListing = httpsCallable<{ kind: Kind; id: string }, { id: string }>(functions, 'publishBakerListing')
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

function DraftEditor({ kind }: { kind: Kind }) {
  const marketplace = kind === 'marketplace'
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [form, setForm] = useState(empty)
  const [id, setId] = useState<string>(() => crypto.randomUUID())
  const [loading, setLoading] = useState(true)
  const [processingPhoto, setProcessingPhoto] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (processingPhoto) return
    setSaving(true)
    setError('')
    setNotice('')
    const draft = { id, logo: form.logo, photos: form.photos, title: form.title.trim(), area: form.area.trim(), description: form.description.trim(), priceCents: marketplace ? Math.round(Number(form.price) * 100) : null }
    try {
      const result = await saveDraft({ ...draft, kind })
      const saved = { ...draft, id: result.data.id }
      setDrafts(previous => [...previous.filter(item => item.id !== saved.id), saved])
      edit(saved)
      if ((event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'publish') {
        setNotice('Draft saved. Checking publication eligibility…')
        await publishListing({ kind, id: saved.id })
        setNotice('Listing published.')
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
      {drafts.map(draft => <button key={draft.id} type="button" className="outline-button" disabled={saving || processingPhoto} onClick={() => edit(draft)}>{draft.title}</button>)}
    </div>}
    <form onSubmit={submit}>
      <fieldset disabled={loading || saving || processingPhoto}>
        <label>{marketplace ? 'Item name' : 'Bakery name'}<input required maxLength={100} value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} /></label>
        <label>Area<input required maxLength={100} value={form.area} onChange={event => setForm({ ...form, area: event.target.value })} /></label>
        <label>Description<textarea required maxLength={1000} value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></label>
        {marketplace && <label>Item price (R)<input type="number" required min="0.01" max="1000000" step="0.01" value={form.price} onChange={event => setForm({ ...form, price: event.target.value })} /></label>}
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
        <button className="outline-button" type="submit" value="publish">Save and publish</button>
      </fieldset>
    </form>
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
  </div>
}

const publicPreview = 'https://www.batchboss.co.za/batchboss-preview/'
const listingPrices = {
  directory: 'R49 per month',
  marketplace: 'R20 per listing per month',
  weeklyPro: 'R39.99 per week',
  fullSuite: 'R220 per month',
}

export function ListingView({ tab, onViewPlans }: {
  tab: 'directoryListing' | 'marketplace'
  onViewPlans: () => void
}) {
  const isDirectory = tab === 'directoryListing'

  return <section className="content-card">
    <h2>{isDirectory ? 'Directory Listing' : 'Marketplace'}</h2>
    <p>{isDirectory
      ? 'Show customers what you bake, where you’re based and how they can enquire.'
      : 'List pre-owned baking equipment and supplies for other bakers to discover.'}</p>
    <p><strong>{isDirectory ? listingPrices.directory : listingPrices.marketplace}</strong></p>
    <p>{isDirectory ? 'Directory Listing is included with Full Suite.' : 'Full Suite includes 3 new Marketplace listings per month. Extra listings cost R20 each per month.'}</p>
    <div className="card-actions">
      <a className="outline-button" href={publicPreview + (isDirectory ? 'baker-finder.html' : 'marketplace.html')}>
        {isDirectory ? 'Preview Find a Baker' : 'Preview Marketplace'}
      </a>
      <button className="primary-button" type="button" onClick={onViewPlans}>View listing plans</button>
    </div>
    <DraftEditor key={tab} kind={tab} />
  </section>
}

type CheckoutPlan = 'weekly' | 'directory_listing' | 'full_suite'

export function UpcomingListingPlans() {
  const [available, setAvailable] = useState<Partial<Record<CheckoutPlan, boolean>>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let current = true
    httpsCallable<void, { enabled: boolean; testMode?: boolean; listingPlans?: Partial<Record<CheckoutPlan, boolean>> }>(functions, 'getPaystackPlans')()
      .then(({ data }) => { if (current && data.enabled && !data.testMode) setAvailable(data.listingPlans ?? {}) })
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

  const plans = [
    { code: 'weekly', title: 'Weekly BatchBoss Pro', price: listingPrices.weeklyPro, detail: 'Seven days of BatchBoss Pro access.' },
    { code: 'directory_listing', title: 'Directory Listing', price: listingPrices.directory, detail: 'A bakery profile in Find a Baker.' },
    { title: 'Marketplace Listing', price: listingPrices.marketplace, detail: 'Monthly billing for each item listing.' },
    { code: 'full_suite', title: 'BatchBoss Full Suite', price: listingPrices.fullSuite, detail: 'BatchBoss Pro, a Directory Listing and 3 new Marketplace listings per month. Extra listings cost R20 each per month.' },
  ] as const

  return <section className="content-card">
    <h2>Upcoming plans and listing prices</h2>
    <p>Paid plans renew at the interval shown. Manage renewal through BatchBoss.</p>
    {error && <p role="alert">{error}</p>}
    <div className="plans">
      {plans.map(plan => <article key={plan.title}>
        <h3>{plan.title}</h3>
        <strong>{plan.price}</strong>
        <p>{plan.detail}</p>
        {'code' in plan && <button className="primary-button" type="button" disabled={busy || !available[plan.code]} onClick={() => checkout(plan.code)}>
          {available[plan.code] ? 'Continue to Paystack' : 'Checkout coming soon'}
        </button>}
      </article>)}
    </div>
  </section>
}
