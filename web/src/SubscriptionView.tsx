import { useState } from 'react'
import { getFunctions, httpsCallable } from 'firebase/functions'
import { app } from './firebase'
import type { UserProfile } from './types'

type BillingPlan = 'monthly' | 'annual'

type CheckoutResponse = {
  success: boolean
  checkoutUrl: string
  fields: Record<string, string>
}

function sendToPayfast(checkoutUrl: string, fields: Record<string, string>) {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = checkoutUrl

  Object.entries(fields).forEach(([name, value]) => {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    form.appendChild(input)
  })

  document.body.appendChild(form)
  form.submit()
}

export default function SubscriptionView({
  isPro,
  profile,
}: {
  isPro: boolean
  profile: UserProfile
}) {
  const [busy, setBusy] = useState<BillingPlan | null>(null)
  const [error, setError] = useState('')

  async function startCheckout(plan: BillingPlan) {
    setBusy(plan)
    setError('')

    try {
      const functions = getFunctions(app, 'europe-west1')
      const createCheckout = httpsCallable<
        { plan: BillingPlan },
        CheckoutResponse
      >(functions, 'createPayfastCheckout')

      const result = await createCheckout({ plan })

      if (!result.data.checkoutUrl || !result.data.fields) {
        throw new Error('The secure checkout could not be prepared.')
      }

      sendToPayfast(result.data.checkoutUrl, result.data.fields)
    } catch (reason) {
      const message =
        reason instanceof Error
          ? reason.message
          : 'PayFast checkout could not be opened.'

      setError(
        message
          .replace('Firebase: ', '')
          .replace(/\(functions\/.+\)\.?/, '')
      )
      setBusy(null)
    }
  }

  const features = [
    'AI recipe and barcode scanning',
    'Unlimited recipes and costing',
    'Professional invoices and payment tracking',
    'Quotes, estimates and customer receipts',
    'Products, suppliers and specials',
  ]

  return (
    <section className="subscription-view">
      <div className="subscription-intro">
        <span className="eyebrow">Your plan</span>
        <h2>{isPro ? 'BatchBoss Pro is active' : 'Unlock BatchBoss Pro'}</h2>
        <p>
          Complete commercial tools for bakers who want to cost smarter and
          grow.
        </p>
      </div>

      <div className="feature-list">
        {features.map(feature => (
          <div key={feature}>
            <span className="feature-check">✓</span>
            {feature}
          </div>
        ))}
      </div>

      {isPro ? (
        <div className="success-note">
          Your Pro features are unlocked. Your subscription is linked to this
          BatchBoss account.
        </div>
      ) : (
        <div className="plans">
          <article>
            <span>Save 33%</span>
            <h3>Annual</h3>
            <strong>R1 199</strong>
            <small>per year · about R99/month</small>
            <button
              className="primary-button"
              disabled={busy !== null}
              onClick={() => startCheckout('annual')}
            >
              {busy === 'annual' ? 'Opening PayFast…' : 'Choose annual'}
            </button>
          </article>

          <article>
            <span>Flexible</span>
            <h3>Monthly</h3>
            <strong>R149</strong>
            <small>per month</small>
            <button
              className="primary-button"
              disabled={busy !== null}
              onClick={() => startCheckout('monthly')}
            >
              {busy === 'monthly' ? 'Opening PayFast…' : 'Choose monthly'}
            </button>
          </article>
        </div>
      )}

      {error && <div className="error-message">{error}</div>}

      <p className="billing-note">
        Current status: <strong>{profile.subscriptionStatus || 'free'}</strong>.
        Payments are processed securely by PayFast. BatchBoss never stores your
        card details.
      </p>
    </section>
  )
}
