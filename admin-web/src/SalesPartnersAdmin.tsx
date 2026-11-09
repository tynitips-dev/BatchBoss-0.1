import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { getApps, initializeApp } from 'firebase/app'
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, type User } from 'firebase/auth'
import { getFunctions, httpsCallable } from 'firebase/functions'

// This is intentionally a SECOND Firebase app. A BatchBoss admin claim from
// the baker project must never grant permissions in the separate partner project.
const partnerConfig = {
  apiKey: import.meta.env.VITE_PARTNERS_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_PARTNERS_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_PARTNERS_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_PARTNERS_FIREBASE_APP_ID,
}
const configured = Object.values(partnerConfig).every(value => typeof value === 'string' && value.length > 0)
const app = configured
  ? (getApps().find(item => item.name === 'sales-partners-admin') || initializeApp(partnerConfig, 'sales-partners-admin'))
  : null
const partnerAuth = app ? getAuth(app) : null
const partnerFunctions = app ? getFunctions(app, 'us-central1') : null

type Partner = { uid: string; fullName: string; email: string; province: string; status: string; referralCode?: string | null }
type Commission = { id: string; partnerUid: string; businessName: string; planId: string; commissionCents: number; status: string }
type Payout = { id: string; partnerUid: string; amountCents: number; status: string; createdAtMillis?: number | null }
type Response<T> = T & { truncated?: boolean }

async function invoke<T>(name: string, data?: Record<string, string>): Promise<T> {
  if (!partnerFunctions) throw new Error('The private Sales Partners Firebase project is not configured.')
  const result = await httpsCallable<Record<string, string> | undefined, T>(partnerFunctions, name)(data)
  return result.data
}
const rand = (cents: number) => 'R' + (Number(cents || 0) / 100).toLocaleString('en-ZA', {
  minimumFractionDigits: 2, maximumFractionDigits: 2,
})
const errorText = (error: unknown) => error instanceof Error ? error.message : 'Unable to complete the request.'

export default function SalesPartnersAdmin() {
  const [partnerUser, setPartnerUser] = useState<User | null>(null)
  const [allowed, setAllowed] = useState(false)
  const [checking, setChecking] = useState(true)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')
  const [partners, setPartners] = useState<Partner[]>([])
  const [commissions, setCommissions] = useState<Commission[]>([])
  const [payouts, setPayouts] = useState<Payout[]>([])
  const [truncated, setTruncated] = useState(false)

  useEffect(() => {
    if (!partnerAuth) { setChecking(false); return }
    return onAuthStateChanged(partnerAuth, async user => {
      setPartnerUser(user)
      setAllowed(false)
      setChecking(true)
      if (user) {
        try {
          const token = await user.getIdTokenResult()
          setAllowed(user.emailVerified && token.claims.batchbossPartnerAdmin === true)
        } catch (reason) {
          setMessage(errorText(reason))
        }
      }
      setChecking(false)
    })
  }, [])

  async function refresh() {
    setLoading(true)
    setMessage('')
    try {
      const [a, b, c] = await Promise.all([
        invoke<Response<{ partners: Partner[] }>>('adminListPartners'),
        invoke<Response<{ commissions: Commission[] }>>('adminListCommissions'),
        invoke<Response<{ payouts: Payout[] }>>('adminListPayoutRequests'),
      ])
      setPartners(a.partners || [])
      setCommissions(b.commissions || [])
      setPayouts(c.payouts || [])
      setTruncated(Boolean(a.truncated || b.truncated || c.truncated))
    } catch (reason) { setMessage(errorText(reason)) }
    finally { setLoading(false) }
  }

  useEffect(() => { if (allowed) void refresh() }, [allowed])

  async function partnerSignIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!partnerAuth) return
    setBusy('signin')
    setMessage('')
    try {
      await signInWithEmailAndPassword(partnerAuth, email.trim(), password)
      setPassword('')
    } catch (reason) { setMessage(errorText(reason)) }
    finally { setBusy('') }
  }

  async function reviewPartner(partner: Partner, decision: 'approved' | 'rejected' | 'suspended') {
    if (!window.confirm('Set the partner application for ' + partner.fullName + ' to ' + decision + '?')) return
    setBusy(partner.uid)
    setMessage('')
    try {
      await invoke('adminReviewPartner', { partnerUid: partner.uid, decision })
      await refresh()
    } catch (reason) { setMessage(errorText(reason)) }
    finally { setBusy('') }
  }

  async function reviewPayout(payout: Payout, decision: 'approved' | 'rejected') {
    const note = window.prompt('Audit note explaining this payout decision (at least 8 characters):')
    if (!note || note.trim().length < 8) return
    if (!window.confirm('Record payout request as ' + decision + '? This does NOT transfer money.')) return
    setBusy(payout.id)
    setMessage('')
    try {
      await invoke('adminReviewPayoutRequest', { requestId: payout.id, decision, note: note.trim() })
      await refresh()
    } catch (reason) { setMessage(errorText(reason)) }
    finally { setBusy('') }
  }

  async function recordTransfer(payout: Payout) {
    const transferReference = window.prompt('Bank transfer reference for the EFT you have ALREADY completed:')
    if (!transferReference || transferReference.trim().length < 4) return
    const note = window.prompt('Audit note (at least 8 characters):')
    if (!note || note.trim().length < 8) return
    if (!window.confirm('Confirm that the EFT has cleared. This action marks the partner payout PAID but sends no money.')) return
    setBusy(payout.id)
    setMessage('')
    try {
      await invoke('adminRecordCompletedPayout', { requestId: payout.id, transferReference: transferReference.trim(), note: note.trim() })
      await refresh()
    } catch (reason) { setMessage(errorText(reason)) }
    finally { setBusy('') }
  }

  const byPartner = useMemo(() => {
    const values = new Map<string, { recorded: number; unpaid: number; paid: number }>()
    commissions.forEach(c => {
      if (c.status === 'reversed') return
      const current = values.get(c.partnerUid) || { recorded: 0, unpaid: 0, paid: 0 }
      const amount = Number(c.commissionCents || 0)
      current.recorded += amount
      if (c.status === 'paid') current.paid += amount
      else current.unpaid += amount
      values.set(c.partnerUid, current)
    })
    return values
  }, [commissions])
  const totals = useMemo(() => Array.from(byPartner.values()).reduce((sum, v) => ({
    recorded: sum.recorded + v.recorded, unpaid: sum.unpaid + v.unpaid, paid: sum.paid + v.paid,
  }), { recorded: 0, unpaid: 0, paid: 0 }), [byPartner])
  const partnerName = (uid: string) => partners.find(p => p.uid === uid)?.fullName || 'Partner'

  if (!configured) return <section className="panel">
    <h2>Sales Partners setup required</h2>
    <p>The partner system uses a separate Firebase project. Configure the four VITE_PARTNERS_FIREBASE values and deploy its protected functions before enabling this section.</p>
    <p><strong>No partner data is displayed and no approvals can be changed.</strong></p>
  </section>
  if (checking) return <section className="panel"><p>Verifying partner administrator permissions…</p></section>
  if (!partnerUser) return <section className="panel">
    <h2>Sales Partners administration</h2>
    <p>Sign in to the separate Sales Partners project with your verified partner administrator account. Your normal BatchBoss admin login remains protected independently.</p>
    <form onSubmit={partnerSignIn} style={{ display: 'grid', gap: 12, maxWidth: 420 }}>
      <label>Partner admin email<input type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} /></label>
      <label>Partner admin password<input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} /></label>
      <button className="primary" disabled={busy === 'signin'}>Sign in to Partners</button>
    </form>
    {message && <p role="alert">{message}</p>}
  </section>
  if (!allowed) return <section className="panel">
    <h2>Partner administrator permission required</h2>
    <p>This account must have a verified email and the <code>batchbossPartnerAdmin</code> custom claim in the separate Sales Partners Firebase project. Main-site admin rights alone do not grant access.</p>
    <button className="secondary" onClick={() => partnerAuth && signOut(partnerAuth)}>Use another partner admin account</button>
  </section>

  return <div>
    <section className="panel">
      <div className="panel-title"><div><h2>Sales partner control centre</h2><p>Applications, commission balances and manual EFT payout requests. Private pilot only.</p></div>
        <div className="actions"><button className="secondary" disabled={loading} onClick={() => void refresh()}>Refresh partners</button>
          <button className="secondary" onClick={() => partnerAuth && signOut(partnerAuth)}>Partner sign out</button></div></div>
      {message && <p role="alert">{message}</p>}
      {truncated && <p role="alert"><strong>Limited view:</strong> records exceed the current 200-record read limit. These totals are incomplete. Add pagination before operational use.</p>}
      <div className="stats">
        <article><div><span>Pending applications</span><strong>{partners.filter(p => p.status === 'pending').length}</strong></div></article>
        <article><div><span>Recorded commission*</span><strong>{rand(totals.recorded)}</strong></div></article>
        <article><div><span>Unpaid ledger*</span><strong>{rand(totals.unpaid)}</strong></div></article>
        <article><div><span>Recorded EFT payouts</span><strong>{rand(totals.paid)}</strong></div></article>
      </div>
      <p><small>* Ledger totals are not independently confirmed withdrawable balances. Pending or approved records may be ineligible for payout. No money is transferred by this admin site.</small></p>
    </section>
    <section className="panel">
      <h2>Approve or reject partners</h2>
      <div className="table-wrap"><table><thead><tr><th>Partner</th><th>Application</th><th>Recorded</th><th>Unpaid ledger</th><th>Paid</th><th>Actions</th></tr></thead>
        <tbody>{partners.map(p => {
          const amounts = byPartner.get(p.uid) || { recorded: 0, unpaid: 0, paid: 0 }
          return <tr key={p.uid}>
            <td><strong>{p.fullName}</strong><small>{p.email}</small><small>{p.province}</small></td>
            <td><span className={'status ' + (p.status === 'approved' ? 'active' : 'pending')}>{p.status}</span></td>
            <td>{rand(amounts.recorded)}</td><td>{rand(amounts.unpaid)}</td><td>{rand(amounts.paid)}</td>
            <td><div className="actions">
              {(p.status === 'pending' || p.status === 'suspended') && <button disabled={busy === p.uid} onClick={() => void reviewPartner(p, 'approved')}>Approve</button>}
              {p.status === 'pending' && <button className="danger" disabled={busy === p.uid} onClick={() => void reviewPartner(p, 'rejected')}>Reject</button>}
              {p.status === 'approved' && <button className="danger" disabled={busy === p.uid} onClick={() => void reviewPartner(p, 'suspended')}>Suspend</button>}
            </div></td>
          </tr>
        })}</tbody>
      </table>{!partners.length && <p>No partner records loaded.</p>}</div>
    </section>
    <section className="panel">
      <h2>Request Payout queue</h2>
      <p>Partners request their eligible commissions. You approve, pay by EFT outside BatchBoss, then record the bank reference.</p>
      <div className="table-wrap"><table><thead><tr><th>Partner</th><th>Requested amount</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>{payouts.map(p => <tr key={p.id}>
          <td>{partnerName(p.partnerUid)}</td><td>{rand(p.amountCents)}</td><td>{p.status}</td>
          <td><div className="actions">
            {p.status === 'requested' && <>
              <button disabled={busy === p.id} onClick={() => void reviewPayout(p, 'approved')}>Approve payout</button>
              <button className="danger" disabled={busy === p.id} onClick={() => void reviewPayout(p, 'rejected')}>Reject</button>
            </>}
            {p.status === 'approved' && <button disabled={busy === p.id} onClick={() => void recordTransfer(p)}>Record completed EFT</button>}
          </div></td>
        </tr>)}</tbody>
      </table>{!payouts.length && <p>No payout requests.</p>}</div>
    </section>
    <section className="panel">
      <h2>Commission detail</h2><p>Individual commission records and their status. Agents cannot edit these amounts.</p>
      <div className="table-wrap"><table><thead><tr><th>Partner</th><th>Customer</th><th>Plan</th><th>Commission</th><th>Status</th></tr></thead>
        <tbody>{commissions.map(c => <tr key={c.id}>
          <td>{partnerName(c.partnerUid)}</td><td>{c.businessName}</td><td>{c.planId}</td><td>{rand(c.commissionCents)}</td><td>{c.status}</td>
        </tr>)}</tbody></table>{!commissions.length && <p>No commission records.</p>}</div>
    </section>
  </div>
}
