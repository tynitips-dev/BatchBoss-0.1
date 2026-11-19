import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { initializePaddle, type Paddle } from '@paddle/paddle-js'
import { auth } from '../firebase'
import { tiers, type Period, type Tier } from './tiers'
import './pricing.css'

const settings = { displayMode: 'overlay', variant: 'one-page', successUrl: new URL('/welcome', location.origin).href } as const
async function startPaddle() {
  const environment = import.meta.env.VITE_PADDLE_ENVIRONMENT
  const token = import.meta.env.VITE_PADDLE_CLIENT_TOKEN
  if (environment !== 'production' && environment !== 'sandbox') throw Error('Set VITE_PADDLE_ENVIRONMENT to production or sandbox.')
  if (!token?.startsWith(environment === 'production' ? 'live_' : 'test_')) throw Error('Set a Paddle client-side token for the selected environment.')
  const paddle = await initializePaddle({ environment, token, checkout: { settings } })
  if (!paddle) throw Error('Paddle failed to load. Refresh to try again.')
  return paddle
}

async function countryHint(): Promise<string | undefined> {
  const response = await fetch('/pricing/country.php', {cache:'no-store', signal:AbortSignal.timeout(5000)})
  if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) return
  const { countryCode } = await response.json()
  return typeof countryCode === 'string' && /^[A-Z]{2}$/.test(countryCode) && !['XX','ZZ'].includes(countryCode) ? countryCode : undefined
}

type Quote = { id: string, total: string }
type Preview = { period: Period, quotes: Map<string,Quote> }
function Pricing() {
  const [period,setPeriod] = useState<Period>('month')
  const [client,setClient] = useState<{ paddle: Paddle, country?: string }>()
  const [preview,setPreview] = useState<Preview>()
  const [error,setError] = useState('')
  const [email,setEmail] = useState('')
  const [attempt,setAttempt] = useState(0)
  useEffect(() => {
    let current = true
    Promise.all([startPaddle(), countryHint().catch(() => undefined), auth.authStateReady()]).then(([paddle,country]) => {
      if (current) { setEmail(auth.currentUser?.email ?? ''); setClient({paddle,country}) }
    }).catch(e => { if (current) setError(e.message) })
    return () => { current=false }
  },[])
  useEffect(() => {
    if (!client) return
    let current = true
    setPreview(undefined)
    setError('')
    async function load({paddle,country}: NonNullable<typeof client>) {
      const ids = tiers.map(t => t.priceId[period])
      if (ids.some(id => !/^pri_[a-z0-9]{26}$/.test(id || '')) || new Set(ids).size !== tiers.length)
        throw Error('Pricing configuration is incomplete. Each plan needs a distinct Paddle price for this billing period.')
      const result = await paddle.PricePreview({items:ids.map(priceId => ({priceId,quantity:1})), ...(country && {address:{countryCode:country}})})
      const quotes = new Map<string,Quote>()
      for (const {price,formattedTotals,quantity} of result.data.details.lineItems) {
        if (quantity === 1 && price.billingCycle?.interval === period && price.billingCycle.frequency === 1 && formattedTotals.total)
          quotes.set(price.id,{id:price.id,total:formattedTotals.total})
      }
      if (ids.some(id => !quotes.has(id))) throw Error('Paddle could not confirm all prices for this billing period.')
      if (current) setPreview({period,quotes})
    }
    load(client).catch(e => { if (current) setError(e.message || 'Unable to load prices.') })
    return () => { current=false }
  },[period,client,attempt])

  function subscribe(tier: Tier) {
    const quote = preview?.period === period && preview.quotes.get(tier.priceId[period])
    if (!client || !quote) return
    const contact = auth.currentUser?.email || email.trim()
    try {
      client.paddle.Checkout.open({
        settings, items:[{priceId:quote.id,quantity:1}],
        ...(contact && {customer:{email:contact,...(client.country && {address:{countryCode:client.country}})}}),
      })
    } catch { setError('Checkout could not open. Please try again.') }
  }
  return <main><a href="/">Batch Boss</a><h1>Choose your plan</h1>
    <div className="period" role="group" aria-label="Billing period">{(['month','year'] as const).map(value =>
      <button key={value} aria-pressed={period===value} onClick={() => setPeriod(value)}>{value==='month'?'Monthly':'Yearly'}</button>)}</div>
    <form onSubmit={event => event.preventDefault()}>
      {client?.country && !auth.currentUser?.email && <label>Email for checkout <input type="email" required value={email} onChange={e=>setEmail(e.target.value)} autoComplete="email"/></label>}
      {error && <p role="alert">{error} {client && <button type="button" onClick={()=>setAttempt(v=>v+1)}>Retry</button>}</p>}
      <div className="plans">{tiers.map(tier => {
        const quote = preview?.period === period && preview.quotes.get(tier.priceId[period])
        return <article key={tier.name}><h2>{tier.name}</h2><p>{tier.description}</p>
          <p className="price" aria-live="polite">{quote ? <>{quote.total}<small> / {period}</small></> : error?'Unavailable':'Loading…'}</p>
          <ul>{tier.features.map(feature=><li key={feature}>{feature}</li>)}</ul>
          <button type="button" disabled={!quote} onClick={e=>{if(e.currentTarget.form?.reportValidity()) subscribe(tier)}} aria-label={'Subscribe to '+tier.name}>Subscribe</button>
        </article>
      })}</div></form></main>
}

createRoot(document.getElementById('root')!).render(<Pricing/>)
