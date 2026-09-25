import { FormEvent, useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CheckCircle2, LogOut, RefreshCw, Search, ShieldCheck, Trash2, UserRoundCheck, Users } from 'lucide-react'
import { onAuthStateChanged, signInWithEmailAndPassword, signOut, type User } from 'firebase/auth'
import { collection, onSnapshot, orderBy, query } from 'firebase/firestore'
import { httpsCallable } from 'firebase/functions'
import { auth, configured, db, functions } from './firebase'

type Account = {
  uid: string
  email: string
  displayName: string
  disabled: boolean
  emailVerified: boolean
  createdAt: string | null
  lastSignInAt: string | null
  bakeryId: string
  bakeryName: string
  firstName: string
  surname: string
  subscriptionPlan: string
  subscriptionStatus: string
  subscriptionExpiresAt: string | null
}

type DeletionRequest = {
  id: string
  uid: string
  email: string
  bakeryId: string
  reason: string
  status: string
}

type AuditLog = {
  id: string
  action: string
  targetEmail?: string
  actorEmail?: string
  status?: string
}

const bootstrapAdmin = httpsCallable(functions, 'bootstrapAdmin')
const listAccounts = httpsCallable<undefined, { accounts: Account[] }>(functions, 'listAccounts')
const setAccountDisabled = httpsCallable<{ uid: string; disabled: boolean }, { success: boolean }>(functions, 'setAccountDisabled')
const deleteAccountAndData = httpsCallable<{ uid: string; confirmation: string }, { success: boolean }>(functions, 'deleteAccountAndData')

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [checking, setChecking] = useState(true)

  useEffect(() => onAuthStateChanged(auth, async current => {
    setUser(current)
    setIsAdmin(current ? (await current.getIdTokenResult()).claims.admin === true : false)
    setChecking(false)
  }), [])

  if (!configured) return <Centre title="Firebase setup required" text="Add the Firebase web-app values to admin-web/.env before building." />
  if (checking) return <Centre title="Opening BatchBoss Admin" text="Verifying secure access…" />
  if (!user) return <Login />
  if (!isAdmin) return <ActivateAdmin user={user} onActivated={() => setIsAdmin(true)} />
  return <Dashboard user={user} />
}

function Login() {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      await signInWithEmailAndPassword(auth, String(form.get('email')).trim(), String(form.get('password')))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message.replace('Firebase: ', '') : 'Sign-in failed.')
    } finally {
      setBusy(false)
    }
  }

  return <main className="auth-layout">
    <section className="brand-panel">
      <Brand />
      <div><span className="eyebrow">Restricted access</span><h1>Master control for<br /><em>BatchBoss</em></h1><p>Securely manage bakery accounts, deletion requests and platform records.</p></div>
      <small>Authorised administrators only</small>
    </section>
    <section className="login-panel">
      <form className="login-card" onSubmit={submit}>
        <div className="shield"><ShieldCheck /></div>
        <h2>Administrator sign in</h2>
        <p>Use your approved BatchBoss administrator account.</p>
        <label>Email address<input name="email" type="email" required autoComplete="email" /></label>
        <label>Password<input name="password" type="password" required autoComplete="current-password" /></label>
        {error && <div className="error">{error}</div>}
        <button className="primary" disabled={busy}>{busy ? 'Checking access…' : 'Secure sign in'}</button>
      </form>
    </section>
  </main>
}

function ActivateAdmin({ user, onActivated }: { user: User; onActivated: () => void }) {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function activate() {
    setBusy(true)
    setMessage('')
    try {
      await bootstrapAdmin()
      await user.getIdToken(true)
      const token = await user.getIdTokenResult()
      if (token.claims.admin !== true) throw new Error('Administrator claim was not returned.')
      onActivated()
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : 'Administrator activation failed.')
    } finally {
      setBusy(false)
    }
  }

  return <Centre title="Administrator approval required" text="This account is signed in but does not yet have the BatchBoss administrator role.">
    <button className="primary" onClick={activate} disabled={busy}>{busy ? 'Activating…' : 'Activate approved administrator'}</button>
    {message && <div className="error">{message}</div>}
    <button className="text-button" onClick={() => signOut(auth)}>Use another account</button>
  </Centre>
}

function Dashboard({ user }: { user: User }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [requests, setRequests] = useState<DeletionRequest[]>([])
  const [audit, setAudit] = useState<AuditLog[]>([])
  const [search, setSearch] = useState('')
  const [busyUid, setBusyUid] = useState('')
  const [message, setMessage] = useState('')

  async function refreshAccounts() {
    setMessage('')
    try {
      const result = await listAccounts()
      setAccounts(result.data.accounts)
      setMessage(`Synced ${result.data.accounts.length} accounts.`)
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : 'Could not load accounts.')
    }
  }

  useEffect(() => {
    refreshAccounts()
    const stopRequests = onSnapshot(collection(db, 'deletionRequests'), snapshot => {
      setRequests(snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() } as DeletionRequest)))
    })
    const stopAudit = onSnapshot(query(collection(db, 'adminAuditLogs'), orderBy('createdAt', 'desc')), snapshot => {
      setAudit(snapshot.docs.slice(0, 10).map(doc => ({ id: doc.id, ...doc.data() } as AuditLog)))
    })
    return () => { stopRequests(); stopAudit() }
  }, [])

  const shown = useMemo(() => {
    const value = search.trim().toLowerCase()
    if (!value) return accounts
    return accounts.filter(account => [account.email, account.firstName, account.surname, account.bakeryName, account.uid]
      .some(field => field?.toLowerCase().includes(value)))
  }, [accounts, search])

  async function toggleDisabled(account: Account) {
    setBusyUid(account.uid)
    try {
      await setAccountDisabled({ uid: account.uid, disabled: !account.disabled })
      await refreshAccounts()
    } finally { setBusyUid('') }
  }

  async function deleteAccount(account: Account) {
    const confirmation = window.prompt(`Permanently delete ${account.email} and all bakery data? Type DELETE to continue.`)
    if (confirmation !== 'DELETE') return
    setBusyUid(account.uid)
    try {
      await deleteAccountAndData({ uid: account.uid, confirmation })
      await refreshAccounts()
      setMessage(`${account.email} and its associated data were deleted.`)
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : 'Deletion failed.')
    } finally { setBusyUid('') }
  }

  return <div className="admin-shell">
    <aside>
      <Brand />
      <nav><button className="nav-active"><Users /> Accounts</button><button><Trash2 /> Deletion requests <span>{requests.filter(item => item.status === 'pending').length}</span></button><button><ShieldCheck /> Audit trail</button></nav>
      <div className="admin-user"><div>{(user.email || 'A').slice(0, 1).toUpperCase()}</div><span><strong>Administrator</strong><small>{user.email}</small></span><button onClick={() => signOut(auth)} title="Sign out"><LogOut /></button></div>
    </aside>
    <main>
      <header><div><span className="eyebrow">BatchBoss control centre</span><h1>Account administration</h1></div><button className="secondary" onClick={refreshAccounts}><RefreshCw /> Sync now</button></header>
      {message && <div className="notice"><CheckCircle2 />{message}</div>}
      <section className="stats">
        <article><Users /><div><span>Total accounts</span><strong>{accounts.length}</strong></div></article>
        <article><UserRoundCheck /><div><span>Active accounts</span><strong>{accounts.filter(item => !item.disabled).length}</strong></div></article>
        <article><ShieldCheck /><div><span>Pro subscribers</span><strong>{accounts.filter(item => ['active','trialing'].includes(item.subscriptionStatus)).length}</strong></div></article>
        <article><AlertTriangle /><div><span>Deletion requests</span><strong>{requests.filter(item => item.status === 'pending').length}</strong></div></article>
      </section>
      <section className="panel">
        <div className="panel-title"><div><h2>Bakery accounts</h2><p>Authentication and workspace records from Firebase.</p></div><label className="search"><Search /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search users or bakeries…" /></label></div>
        <div className="table-wrap"><table><thead><tr><th>Account</th><th>Bakery</th><th>Last login</th><th>Subscription</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {shown.map(account => <tr key={account.uid}>
            <td><strong>{account.firstName} {account.surname}</strong><small>{account.email}</small></td>
            <td><strong>{account.bakeryName || '—'}</strong><small>{account.bakeryId || 'No workspace'}</small></td>
            <td>{account.lastSignInAt ? new Date(account.lastSignInAt).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : 'Never'}</td>
            <td><strong>{account.subscriptionPlan || 'free'}</strong><small className={`status ${account.subscriptionStatus === 'active' ? 'active' : 'pending'}`}>{account.subscriptionStatus || 'free'}</small></td>
            <td><span className={account.disabled ? 'status disabled' : 'status active'}>{account.disabled ? 'Disabled' : 'Active'}</span></td>
            <td className="actions"><button disabled={busyUid === account.uid} onClick={() => toggleDisabled(account)}>{account.disabled ? 'Enable' : 'Disable'}</button><button className="danger" disabled={busyUid === account.uid} onClick={() => deleteAccount(account)}><Trash2 /> Delete</button></td>
          </tr>)}
        </tbody></table></div>
      </section>
      <section className="lower-grid">
        <article className="panel"><h2>Pending deletion requests</h2>{requests.length ? requests.slice(0, 6).map(item => <div className="list-row" key={item.id}><div><strong>{item.email}</strong><small>{item.reason}</small></div><span className="status pending">{item.status}</span></div>) : <p>No deletion requests.</p>}</article>
        <article className="panel"><h2>Recent administrator activity</h2>{audit.length ? audit.map(item => <div className="list-row" key={item.id}><div><strong>{item.action.replaceAll('_', ' ')}</strong><small>{item.targetEmail || item.actorEmail || 'System action'}</small></div><span>{item.status || 'recorded'}</span></div>) : <p>No administrator activity yet.</p>}</article>
      </section>
    </main>
  </div>
}

function Brand() {
  return <div className="brand"><span className="brand-mark">B</span><span>Batch<strong>Boss</strong><small>ADMIN</small></span></div>
}

function Centre({ title, text, children }: { title: string; text: string; children?: React.ReactNode }) {
  return <main className="centre"><div className="login-card"><Brand /><h1>{title}</h1><p>{text}</p>{children}</div></main>
}

export default App
