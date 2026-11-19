import { useState } from 'react'
import { httpsCallable } from 'firebase/functions'
import { functions } from './firebase'

type Connection = { connected: boolean; merchantKeyPresent: boolean; merchantKeyVerified?: boolean; checkoutEnabled: boolean; status: string; httpStatus?: number }
type Payment = { id: string; uid: string; reference: string; plan: string; amountCents: number; currency: string; status: string; paidAt: string | null; requiresReview: boolean }
const check = httpsCallable<undefined, Connection>(functions, 'checkPayfastConnection')
const list = httpsCallable<undefined, { payments: Payment[] }>(functions, 'listPayfastPayments')
type SandboxResult = {reference:string;environment:string;plan:string;amount:string;billingDate:string;status:string;signatureVerified:boolean;serverValidated:boolean;subscriptionTokenReceived:boolean}
const sandboxCheckout=httpsCallable<{plan:string;confirmTest:boolean},{environment:string;reference:string;checkoutUrl:string;fieldOrder?:string[];fields:Record<string,string>}>(functions,'createPayfastSandboxCheckout')
const sandboxResult=httpsCallable<{reference:string},SandboxResult>(functions,'getPayfastSandboxResult')
const money = (cents: number, currency: string) => currency + ' ' + (cents/100).toFixed(2)

export default function PayfastAdmin() {
  const [connection,setConnection]=useState<Connection|null>(null)
  const [payments,setPayments]=useState<Payment[]|null>(null)
  const [busy,setBusy]=useState('')
  const [error,setError]=useState('')
  const [testPlan,setTestPlan]=useState('weekly')
  const [reference,setReference]=useState(()=>sessionStorage.getItem('batchboss:payfastSandboxReference')||'')
  const [testResult,setTestResult]=useState<SandboxResult|null>(null)
  async function startSandbox(){
    setError('');setBusy('sandbox');setTestResult(null)
    try {
      const data=(await sandboxCheckout({plan:testPlan,confirmTest:true})).data
      if(data.environment!=='sandbox'||data.checkoutUrl!=='https://sandbox.payfast.co.za/eng/process')throw Error('Unexpected checkout destination.')
      setReference(data.reference);sessionStorage.setItem('batchboss:payfastSandboxReference',data.reference)
      const form=document.createElement('form');form.action=data.checkoutUrl;form.method='POST'
      const fieldOrder = data.fieldOrder ?? Object.keys(data.fields)
      for (const name of fieldOrder) {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = name
        input.value = data.fields[name]
        form.appendChild(input)
      }
      document.body.appendChild(form);form.submit()
    }catch(e){setError(e instanceof Error?e.message:'Sandbox checkout failed.')}
    finally{setBusy('')}
  }
  async function checkSandbox(){
    setError('');setBusy('sandbox-result')
    try{setTestResult((await sandboxResult({reference})).data)}
    catch(e){setError(e instanceof Error?e.message:'Unable to load sandbox result.')}
    finally{setBusy('')}
  }
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
    <p>Check the live PayFast API connection without charging a customer. Your Payfast credentials remain in Firebase Secret Manager.</p>
    <button disabled={!!busy} onClick={()=>refresh('connection')}>{busy==='connection'?'Checking…':'Check live PayFast connection'}</button>
    {connection&&<div role="status">
      <p>API: <strong>{connection.connected?'Connected':'Not confirmed'}</strong> · {connection.status}{connection.httpStatus?' (HTTP '+connection.httpStatus+')':''}</p>
      <p>Merchant key: {connection.merchantKeyPresent?'Present; checkout validation still required':'Missing'}</p>
      <p>Checkout: {connection.checkoutEnabled?'Enabled in backend':'Disabled in backend'}</p>
    </div>}
    <h3>Subscription checkout test (sandbox only)</h3>
    <p>Use the merchant ID, key and passphrase from the same PayFast sandbox account. A test uses no real money. A completed test must be confirmed by a validated provider notification.</p>
    <label>Test plan <select value={testPlan} onChange={e=>setTestPlan(e.target.value)} disabled={!!busy}><option value="weekly">Weekly R39.99</option><option value="monthly">Monthly R149.99</option><option value="annual">Annual R1199.00</option></select></label>
    <button disabled={!!busy} onClick={startSandbox}>Start sandbox checkout</button>
    {reference&&<div><p>Test reference: {reference}</p><button disabled={!!busy} onClick={checkSandbox}>Check test result</button></div>}
    {testResult&&<div role="status"><p>Payment status: {testResult.status}</p><p>Expected amount: R{testResult.amount} · Next date: {testResult.billingDate}</p><p>Signature: {testResult.signatureVerified?'Verified':'Not verified'} · Server validation: {testResult.serverValidated?'Verified':'Not verified'} · Subscription token: {testResult.subscriptionTokenReceived?'Received':'Not received'}</p></div>}
    <h3>Recent verified payment records</h3>
    <button disabled={!!busy} onClick={()=>refresh('payments')}>{busy==='payments'?'Loading…':'Load recent payments'}</button>
    {error&&<p role="alert">{error}</p>}
    {payments&&<><p>Showing up to 100 recent recorded transactions. This is not a complete revenue report, settlement report or commission balance.</p>
      {payments.length===0?<p>No payment records found.</p>:<div className="table-wrap"><table><thead><tr><th>Transaction</th><th>Account</th><th>Plan</th><th>Amount</th><th>Recorded at</th><th>Status</th></tr></thead><tbody>
      {payments.map(p=><tr key={p.id}><td>{p.id}</td><td>{p.uid}</td><td>{p.plan}</td><td>{money(p.amountCents,p.currency)}</td><td>{p.paidAt?new Date(p.paidAt).toLocaleString('en-ZA'):'Unknown'}</td><td>{p.requiresReview?'Review required':p.status}</td></tr>)}
      </tbody></table></div>}</>}
  </section>
}
