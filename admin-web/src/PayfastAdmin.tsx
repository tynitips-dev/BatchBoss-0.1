import { useState } from 'react'
import { httpsCallable } from 'firebase/functions'
import { functions } from './firebase'

type Connection = { connected: boolean; merchantKeyPresent: boolean; merchantKeyVerified?: boolean; checkoutEnabled: boolean; status: string; httpStatus?: number }
type Payment = { id: string; uid: string; reference: string; plan: string; amountCents: number; currency: string; status: string; paidAt: string | null; requiresReview: boolean }
const check = httpsCallable<undefined, Connection>(functions, 'checkPayfastConnection')
const list = httpsCallable<undefined, { payments: Payment[] }>(functions, 'listPayfastPayments')
const money = (cents: number, currency: string) => currency + ' ' + (cents/100).toFixed(2)

export default function PayfastAdmin() {
  const [connection,setConnection]=useState<Connection|null>(null)
  const [payments,setPayments]=useState<Payment[]|null>(null)
  const [busy,setBusy]=useState('')
  const [error,setError]=useState('')
  async function refresh(kind:'connection'|'payments') {
    setError('');setBusy(kind)
    try {
      if(kind==='connection') {setConnection(null);setConnection((await check()).data)}
      else {setPayments(null);setPayments((await list()).data.payments)}
    } catch(e) {setError(e instanceof Error?e.message:'Payfast service unavailable.')}
    finally {setBusy('')}
  }
  return <section className="panel">
    <h2>Payfast payments</h2>
    <p>Check the connection without charging a customer. Your Payfast credentials remain in Firebase Secret Manager.</p>
    <button disabled={!!busy} onClick={()=>refresh('connection')}>{busy==='connection'?'Checking…':'Check Payfast connection'}</button>
    {connection&&<div role="status">
      <p>API: <strong>{connection.connected?'Connected':'Not confirmed'}</strong> · {connection.status}{connection.httpStatus?' (HTTP '+connection.httpStatus+')':''}</p>
      <p>Merchant key: {connection.merchantKeyPresent?'Present; checkout validation still required':'Missing'}</p>
      <p>Checkout: {connection.checkoutEnabled?'Enabled in backend':'Disabled in backend'}</p>
    </div>}
    <h3>Recent verified payment records</h3>
    <button disabled={!!busy} onClick={()=>refresh('payments')}>{busy==='payments'?'Loading…':'Load recent payments'}</button>
    {error&&<p role="alert">{error}</p>}
    {payments&&<><p>Showing up to 100 recent recorded transactions. This is not a complete revenue report, settlement report or commission balance.</p>
      {payments.length===0?<p>No payment records found.</p>:<div className="table-wrap"><table><thead><tr><th>Transaction</th><th>Account</th><th>Plan</th><th>Amount</th><th>Recorded at</th><th>Status</th></tr></thead><tbody>
      {payments.map(p=><tr key={p.id}><td>{p.id}</td><td>{p.uid}</td><td>{p.plan}</td><td>{money(p.amountCents,p.currency)}</td><td>{p.paidAt?new Date(p.paidAt).toLocaleString('en-ZA'):'Unknown'}</td><td>{p.requiresReview?'Review required':p.status}</td></tr>)}
      </tbody></table></div>}</>}
  </section>
}
