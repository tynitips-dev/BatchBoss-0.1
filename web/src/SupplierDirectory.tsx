import { useEffect, useRef, useState } from 'react'
import './supplier-directory.css'

export type Supplier = { id: string; businessName: string; category: string; city: string; province: string; description: string; delivery: string }
type Page = { suppliers: Supplier[]; nextCursor: string | null }
const categories = ['Ingredients', 'Packaging', 'Equipment', 'Decorating']
const publicId = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
function endpointUrl(value: unknown) {
  try { const url = new URL(String(value)); return url.protocol === 'https:' && !url.username && !url.password ? url.href : '' } catch { return '' }
}
function readPage(value: unknown): Page {
  if (!value || typeof value !== 'object') throw Error('Invalid response')
  const page = value as Page
  if (!Array.isArray(page.suppliers) || !(page.nextCursor === null || typeof page.nextCursor === 'string' && publicId.test(page.nextCursor))) throw Error('Invalid page')
  const suppliers = page.suppliers.filter(p => p && typeof p.id === 'string' && publicId.test(p.id) && categories.includes(p.category) && ['businessName', 'city', 'province', 'description', 'delivery'].every(key => typeof p[key as keyof Supplier] === 'string'))
  return { suppliers, nextCursor: page.nextCursor }
}
export default function SupplierDirectory({ initialCategory = '', savedIds = [], onSave }: { initialCategory?: string; savedIds?: string[]; onSave?: (supplier: Supplier) => Promise<void> }) {
  const [items, setItems] = useState<Supplier[]>([])
  const [category, setCategory] = useState(initialCategory)
  const [search, setSearch] = useState('')
  const [city, setCity] = useState('')
  const [province, setProvince] = useState('')
  const [saving, setSaving] = useState('')
  const [saveError, setSaveError] = useState('')
  async function save(supplier: Supplier) {
    if (!onSave || saving || savedIds.includes(supplier.id)) return
    setSaving(supplier.id); setSaveError('')
    try { await onSave(supplier) } catch { setSaveError('We could not save this supplier. Please try again.') }
    finally { setSaving('') }
  }
  const [cursor, setCursor] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const controller = useRef<AbortController | null>(null)
  const endpoint = endpointUrl(import.meta.env.VITE_SUPPLIER_DIRECTORY_URL)
  async function load(next: string | null = null) {
    controller.current?.abort()
    const request = new AbortController(); controller.current = request
    setBusy(true); setError('')
    if (!next) { setItems([]); setCursor(null) }
    const timer = setTimeout(() => request.abort(), 15000)
    try {
      // Firebase callable protocol, deliberately without baker credentials.
      const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'omit', cache: 'no-store', signal: request.signal, body: JSON.stringify({ data: { limit: 25, ...(next ? { cursor: next } : {}) } }) })
      if (!response.ok) throw Error('Request failed')
      const payload = await response.json()
      if (payload.error) throw Error('Directory unavailable')
      const page = readPage(payload.result ?? payload.data)
      if (controller.current !== request) return
      setItems(old => Array.from(new Map<string, Supplier>((next ? [...old, ...page.suppliers] : page.suppliers).map(p => [p.id, p])).values()))
      setCursor(page.nextCursor)
    } catch { if (controller.current === request) setError('We could not load suppliers. Please try again.') }
    finally { clearTimeout(timer); if (controller.current === request) setBusy(false) }
  }
  useEffect(() => {
    if (endpoint) void load()
    return () => { controller.current?.abort(); controller.current = null }
  }, [endpoint])
  const shown = items.filter(p => (!category || p.category === category) && (!city.trim() || p.city.toLowerCase().includes(city.trim().toLowerCase())) && (!province || p.province.toLowerCase() === province.toLowerCase()) && `${p.businessName} ${p.city} ${p.province} ${p.description} ${p.delivery}`.toLowerCase().includes(search.trim().toLowerCase()))
  return <section className="content-card supplier-directory">
    <span className="eyebrow">BatchBoss supplier directory</span><h2>Find suppliers</h2>
    <p>Discover approved suppliers with an active listing for your next bake.</p>
    <div className="directory-filters">
      <label>Search businesses or location<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Business, city or province" /></label>
      <label>Category<select value={category} onChange={e => setCategory(e.target.value)}><option value="">All categories</option>{categories.map(c => <option key={c} value={c}>{c === 'Decorating' ? 'Cake decorations' : c}</option>)}</select></label>
      <label>City or town<input value={city} onChange={e => setCity(e.target.value)} placeholder="e.g. Cape Town" /></label>
      <label>Province<select value={province} onChange={e => setProvince(e.target.value)}><option value="">All provinces</option>{['Eastern Cape','Free State','Gauteng','KwaZulu-Natal','Limpopo','Mpumalanga','North West','Northern Cape','Western Cape'].map(p => <option key={p}>{p}</option>)}</select></label>
      {endpoint && <button className="outline-button" disabled={busy} onClick={() => void load()}>Refresh</button>}
    </div>
    {!endpoint ? <p>The supplier directory is being connected. Please check back soon.</p> : <>
      {busy && <p role="status">Loading suppliers…</p>}
      {error && <p role="alert">{error} <button onClick={() => void load(cursor)}>Try again</button></p>}
      {!busy && !error && !shown.length && <p>{items.length ? 'No matches in the loaded suppliers. Try another search or load more.' : 'No suppliers to display yet.'}</p>}
      {saveError && <p role="alert">{saveError}</p>}
      <div className="directory-grid">{shown.map(p => <article className="directory-card" key={p.id}>
        <span className="eyebrow">{p.category}</span><h3>{p.businessName}</h3><p>{p.city}, {p.province}</p><p>{p.description}</p>
        {p.delivery && <p><strong>Delivery / collection:</strong> {p.delivery}</p>}
        {onSave && <button className="outline-button" disabled={!!saving || savedIds.includes(p.id)} onClick={() => void save(p)}>{savedIds.includes(p.id) ? 'Saved to my suppliers' : saving === p.id ? 'Saving…' : 'Save to my suppliers'}</button>}
      </article>)}</div>
      {cursor && <button className="outline-button" disabled={busy} onClick={() => void load(cursor)}>Load more suppliers</button>}
      {cursor && <p>Search, city, province and category filters apply to the suppliers loaded so far.</p>}
    </>}
  </section>
}
