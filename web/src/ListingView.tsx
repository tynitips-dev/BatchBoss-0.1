import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { getFunctions, httpsCallable } from 'firebase/functions'
import { app } from './firebase'

type Kind = 'directoryListing' | 'marketplace'
type Draft = { id: string; title: string; area: string; description: string; priceCents: number | null }
const functions = getFunctions(app, 'europe-west1')
const listDrafts = httpsCallable<{ kind: Kind }, Draft[]>(functions, 'listListingDrafts')
const saveDraft = httpsCallable<Draft & { kind: Kind }, { id: string }>(functions, 'saveListingDraft')
const empty = { title: '', area: '', description: '', price: '' }

function DraftEditor({ kind }: { kind: Kind }) {
  const marketplace = kind === 'marketplace'
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [form, setForm] = useState(empty)
  const [id, setId] = useState<string>(() => crypto.randomUUID())
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  function edit(draft: Draft) {
    setId(draft.id)
    setForm({ title: draft.title, area: draft.area, description: draft.description, price: draft.priceCents === null ? '' : (draft.priceCents / 100).toFixed(2) })
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    const draft = { id, title: form.title.trim(), area: form.area.trim(), description: form.description.trim(), priceCents: marketplace ? Math.round(Number(form.price) * 100) : null }
    try {
      const result = await saveDraft({ ...draft, kind })
      const saved = { ...draft, id: result.data.id }
      setDrafts(previous => [...previous.filter(item => item.id !== saved.id), saved])
      edit(saved)
      setNotice('Private draft saved.')
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
      <button type="button" className="outline-button" disabled={loading || saving} onClick={() => {
        setId(crypto.randomUUID())
        setForm(empty)
        setNotice('')
      }}>New item draft</button>
      {drafts.map(draft => <button key={draft.id} type="button" className="outline-button" disabled={saving} onClick={() => edit(draft)}>{draft.title}</button>)}
    </div>}
    <form onSubmit={submit}>
      <fieldset disabled={loading || saving}>
        <label>{marketplace ? 'Item name' : 'Bakery name'}<input required maxLength={100} value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} /></label>
        <label>Area<input required maxLength={100} value={form.area} onChange={event => setForm({ ...form, area: event.target.value })} /></label>
        <label>Description<textarea required maxLength={1000} value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></label>
        {marketplace && <label>Item price (R)<input type="number" required min="0.01" max="1000000" step="0.01" value={form.price} onChange={event => setForm({ ...form, price: event.target.value })} /></label>}
        <button className="primary-button" type="submit">{saving ? 'Saving…' : 'Save private draft'}</button>
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
    <p>{isDirectory ? 'Directory Listing is included with Full Suite.' : 'Full Suite includes 3 new Marketplace listings per month. Extra listings cost R20 each per month.'} Public listing submissions open when the service launches.</p>
    <div className="card-actions">
      <a className="outline-button" href={publicPreview + (isDirectory ? 'baker-finder.html' : 'marketplace.html')}>
        {isDirectory ? 'Preview Find a Baker' : 'Preview Marketplace'}
      </a>
      <button className="primary-button" type="button" onClick={onViewPlans}>View listing plans</button>
    </div>
    <DraftEditor key={tab} kind={tab} />
  </section>
}

export function UpcomingListingPlans() {
  const plans = [
    { title: 'Weekly BatchBoss Pro', price: listingPrices.weeklyPro, detail: 'Seven days of BatchBoss Pro access.' },
    { title: 'Directory Listing', price: listingPrices.directory, detail: 'A bakery profile in Find a Baker.' },
    { title: 'Marketplace Listing', price: listingPrices.marketplace, detail: 'Monthly billing for each item listing.' },
    { title: 'BatchBoss Full Suite', price: listingPrices.fullSuite, detail: 'BatchBoss Pro, a Directory Listing and 3 new Marketplace listings per month. Extra listings cost R20 each per month.' },
  ]

  return <section className="content-card">
    <h2>Upcoming plans and listing prices</h2>
    <p>Preview pricing. Checkout and listing submissions are coming soon.</p>
    <div className="plans">
      {plans.map(plan => <article key={plan.title}>
        <h3>{plan.title}</h3>
        <strong>{plan.price}</strong>
        <p>{plan.detail}</p>
      </article>)}
    </div>
  </section>
}
