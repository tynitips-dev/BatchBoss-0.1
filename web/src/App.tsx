import { FormEvent, useEffect, useMemo, useState } from 'react'
import {
  BarChart3, BookOpen, Boxes, ChevronRight, ClipboardList, LogOut,
  Mail, Menu, Plus, ReceiptText, Search, Sparkles, Users, X,
} from 'lucide-react'
import {
  createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword,
  signOut, type User,
} from 'firebase/auth'
import {
  addDoc, collection, doc, onSnapshot, serverTimestamp,
  writeBatch,
} from 'firebase/firestore'
import { auth, db, firebaseConfigured } from './firebase'
import type { Customer, ModuleKey, UserProfile } from './types'

const modules: { key: ModuleKey; label: string; icon: typeof BookOpen }[] = [
  { key: 'recipes', label: 'Recipes', icon: BookOpen },
  { key: 'inventory', label: 'Ingredients & stock', icon: Boxes },
  { key: 'customers', label: 'Customers', icon: Users },
  { key: 'orders', label: 'Orders', icon: ClipboardList },
  { key: 'invoices', label: 'Quotes & invoices', icon: ReceiptText },
]

const money = new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' })

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let stopProfile = () => {}
    const stopAuth = onAuthStateChanged(auth, current => {
      stopProfile()
      setUser(current)
      if (!current) {
        setProfile(null)
        setLoading(false)
        return
      }
      setLoading(true)
      stopProfile = onSnapshot(doc(db, 'users', current.uid), snapshot => {
        setProfile(snapshot.exists() ? snapshot.data() as UserProfile : null)
        setLoading(false)
      }, () => {
        setProfile(null)
        setLoading(false)
      })
    })
    return () => { stopProfile(); stopAuth() }
  }, [])

  if (!firebaseConfigured) return <SetupNotice />
  if (loading) return <div className="centre-screen"><div className="loader" /><p>Opening your bakery…</p></div>
  if (!user || !profile) return <AuthScreen />
  return <Dashboard profile={profile} />
}

function SetupNotice() {
  return <main className="centre-screen setup-card">
    <div className="brand-mark">B</div>
    <h1>Connect the BatchBoss website</h1>
    <p>The website foundation is ready. Copy <code>.env.example</code> to <code>.env</code> and add the Firebase web-app details.</p>
  </main>
}

function AuthScreen() {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    const data = new FormData(event.currentTarget)
    const email = String(data.get('email')).trim()
    const password = String(data.get('password'))
    try {
      if (mode === 'login') {
        await signInWithEmailAndPassword(auth, email, password)
      } else {
        const bakeryName = String(data.get('bakeryName')).trim()
        const firstName = String(data.get('firstName')).trim()
        const surname = String(data.get('surname')).trim()
        const credential = await createUserWithEmailAndPassword(auth, email, password)
        const uid = credential.user.uid
        const bakeryRef = doc(collection(db, 'bakeries'))
        const batch = writeBatch(db)
        batch.set(bakeryRef, {
          name: bakeryName,
          ownerUid: uid,
          currency: 'ZAR',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        })
        batch.set(doc(db, 'users', uid), {
          uid, bakeryId: bakeryRef.id, firstName, surname, email,
          role: 'owner', createdAt: serverTimestamp(),
        })
        batch.set(doc(db, 'bakeries', bakeryRef.id, 'members', uid), {
          uid, email, role: 'owner', joinedAt: serverTimestamp(),
        })
        await batch.commit()
      }
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : 'Something went wrong.'
      setError(message.replace('Firebase: ', '').replace(/\(auth\/.+\)\.?/, ''))
    } finally { setBusy(false) }
  }

  return <div className="auth-page">
    <section className="auth-story">
      <div className="brand"><span className="brand-mark">B</span><span>Batch<strong>Boss</strong></span></div>
      <div>
        <span className="eyebrow">Your bakery. Better organised.</span>
        <h1>Cost smarter.<br />Price confidently.<br /><em>Grow profitably.</em></h1>
        <p>Access your recipes, stock, customers and invoices from the same secure account.</p>
      </div>
      <small>© 2026 BatchBoss · Smart costing for every baker</small>
    </section>
    <section className="auth-panel">
      <form className="auth-form" onSubmit={submit}>
        <span className="pill">{mode === 'login' ? 'Welcome back, Baker' : 'Create your bakery workspace'}</span>
        <h2>{mode === 'login' ? 'Sign in to BatchBoss' : 'Start with BatchBoss'}</h2>
        <p>{mode === 'login' ? 'Use the same email address as your app account.' : 'Your dashboard starts empty and belongs only to your bakery.'}</p>
        {mode === 'signup' && <div className="split-fields">
          <label>First name<input name="firstName" required /></label>
          <label>Surname<input name="surname" required /></label>
        </div>}
        {mode === 'signup' && <label>Bakery name<input name="bakeryName" required /></label>}
        <label>Email address<input name="email" type="email" autoComplete="email" required /></label>
        <label>Password<input name="password" type="password" minLength={8} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required /></label>
        {error && <div className="error-message">{error}</div>}
        <button className="primary-button" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}<ChevronRight size={18} /></button>
        <button type="button" className="text-button" onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}>
          {mode === 'login' ? 'New to BatchBoss? Create an account' : 'Already registered? Sign in'}
        </button>
        <a className="support-link" href="mailto:info@batchboss.co.za"><Mail size={16} /> Need help? info@batchboss.co.za</a>
      </form>
    </section>
  </div>
}

function Dashboard({ profile }: { profile: UserProfile }) {
  const [active, setActive] = useState<ModuleKey>('customers')
  const [counts, setCounts] = useState<Record<ModuleKey, number>>({ recipes: 0, inventory: 0, customers: 0, orders: 0, invoices: 0 })
  const [customers, setCustomers] = useState<Customer[]>([])
  const [showCustomer, setShowCustomer] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    const cleanups = modules.map(({ key }) => onSnapshot(
      collection(db, 'bakeries', profile.bakeryId, key),
      snapshot => {
        setCounts(current => ({ ...current, [key]: snapshot.size }))
        if (key === 'customers') setCustomers(snapshot.docs.map(item => ({ id: item.id, ...item.data() } as Customer)))
      },
    ))
    return () => cleanups.forEach(cleanup => cleanup())
  }, [profile.bakeryId])

  const greeting = useMemo(() => {
    const hour = new Date().getHours()
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
  }, [])

  async function addCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    await addDoc(collection(db, 'bakeries', profile.bakeryId, 'customers'), {
      name: String(data.get('name')).trim(), phone: String(data.get('phone')).trim(),
      email: String(data.get('email')).trim(), notes: String(data.get('notes')).trim(),
      totalOrders: 0, totalSpend: 0, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    })
    setShowCustomer(false)
  }

  return <div className="dashboard-shell">
    <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
      <div className="brand"><span className="brand-mark">B</span><span>Batch<strong>Boss</strong></span></div>
      <button className="close-menu" onClick={() => setMenuOpen(false)}><X /></button>
      <nav>
        <button className="nav-item active"><BarChart3 /> Overview</button>
        {modules.map(({ key, label, icon: Icon }) => <button key={key} className={active === key ? 'nav-item active' : 'nav-item'} onClick={() => { setActive(key); setMenuOpen(false) }}><Icon />{label}<span>{counts[key]}</span></button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="profile-avatar">{profile.firstName.slice(0, 1)}{profile.surname.slice(0, 1)}</div>
        <div><strong>{profile.firstName} {profile.surname}</strong><small>{profile.role}</small></div>
        <button title="Sign out" onClick={() => signOut(auth)}><LogOut /></button>
      </div>
    </aside>
    <main className="dashboard-main">
      <header><button className="menu-button" onClick={() => setMenuOpen(true)}><Menu /></button><div><span>{greeting},</span><h1>{profile.firstName || 'Baker'} 👋</h1></div><button className="primary-button compact" onClick={() => active === 'customers' && setShowCustomer(true)}><Plus size={18} /> Add new</button></header>
      <section className="hero-card"><div><span className="eyebrow">This month</span><h2>Your bakery at a glance</h2><p>Your live web dashboard is connected to your secure BatchBoss workspace.</p></div><div className="profit"><small>Estimated profit</small><strong>{money.format(0)}</strong><span>Starts at zero until sales are recorded</span></div></section>
      <section className="stat-grid">
        {modules.slice(0, 4).map(({ key, label, icon: Icon }) => <article className="stat-card" key={key}><div className="stat-icon"><Icon /></div><span>{label}</span><strong>{counts[key]}</strong><small>Live total</small></article>)}
      </section>
      <section className="content-card">
        <div className="section-heading"><div><span className="eyebrow">Workspace</span><h2>{modules.find(module => module.key === active)?.label}</h2></div><label className="search"><Search size={18} /><input placeholder={`Search ${active}…`} /></label></div>
        {active === 'customers' ? <CustomerTable customers={customers} onAdd={() => setShowCustomer(true)} /> : <EmptyModule label={modules.find(module => module.key === active)?.label || active} />}
      </section>
    </main>
    {showCustomer && <Modal title="Add customer" onClose={() => setShowCustomer(false)}><form className="modal-form" onSubmit={addCustomer}><label>Customer name<input name="name" required /></label><div className="split-fields"><label>Phone<input name="phone" type="tel" /></label><label>Email<input name="email" type="email" /></label></div><label>Notes<textarea name="notes" rows={3} /></label><button className="primary-button">Save customer</button></form></Modal>}
  </div>
}

function CustomerTable({ customers, onAdd }: { customers: Customer[]; onAdd: () => void }) {
  if (!customers.length) return <EmptyState title="No customers yet" text="Add your first customer when you are ready. BatchBoss does not add any sample customers." action="Add customer" onAction={onAdd} />
  return <div className="table-wrap"><table><thead><tr><th>Customer</th><th>Contact</th><th>Orders</th><th>Total spend</th></tr></thead><tbody>{customers.map(customer => <tr key={customer.id}><td><strong>{customer.name}</strong><small>{customer.notes}</small></td><td>{customer.phone || customer.email || '—'}</td><td>{customer.totalOrders}</td><td>{money.format(customer.totalSpend)}</td></tr>)}</tbody></table></div>
}

function EmptyModule({ label }: { label: string }) { return <EmptyState title={`${label} are connected next`} text="The secure account and database foundation is ready. This module will be mapped to the matching Android app data." /> }
function EmptyState({ title, text, action, onAction }: { title: string; text: string; action?: string; onAction?: () => void }) { return <div className="empty-state"><div><Sparkles /></div><h3>{title}</h3><p>{text}</p>{action && <button className="primary-button compact" onClick={onAction}><Plus size={17} />{action}</button>}</div> }
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) { return <div className="modal-backdrop"><div className="modal"><div className="modal-title"><h2>{title}</h2><button onClick={onClose}><X /></button></div>{children}</div></div> }

export default App
