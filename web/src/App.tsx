import { FormEvent, useEffect, useMemo, useState } from 'react'
import {
  BookOpen, Boxes, CalendarDays, ChevronRight, ClipboardList,
  FileCheck2, Gauge, LogOut, Mail, Menu, Package, Plus, Receipt,
  ReceiptText, Search, Settings, Sparkles, Store, Users, WandSparkles, X,
} from 'lucide-react'
import {
  createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword,
  signOut, type User,
} from 'firebase/auth'
import { addDoc, collection, deleteDoc, doc, onSnapshot, serverTimestamp, updateDoc, writeBatch } from 'firebase/firestore'
import { auth, db, firebaseConfigured } from './firebase'
import type { ModuleKey, UserProfile } from './types'

type ModuleDefinition = { key: ModuleKey; label: string; icon: typeof BookOpen; collection?: string; pro?: boolean }
const modules: ModuleDefinition[] = [
  { key: 'home', label: 'Home', icon: Gauge },
  { key: 'recipes', label: 'Recipes', icon: BookOpen, collection: 'recipes' },
  { key: 'ingredients', label: 'Ingredients', icon: Package, collection: 'ingredients' },
  { key: 'inventory', label: 'Inventory', icon: Boxes, collection: 'inventory' },
  { key: 'suppliers', label: 'Suppliers', icon: Store, collection: 'suppliers', pro: true },
  { key: 'invoices', label: 'Invoices', icon: ReceiptText, collection: 'invoices', pro: true },
  { key: 'quotes', label: 'Quotes & estimates', icon: FileCheck2, collection: 'quotes', pro: true },
  { key: 'receipts', label: 'Receipts', icon: Receipt, collection: 'receipts', pro: true },
  { key: 'products', label: 'Products & services', icon: Package, collection: 'products', pro: true },
  { key: 'tasks', label: 'Tasks & calendar', icon: CalendarDays, collection: 'tasks' },
  { key: 'customers', label: 'Customers', icon: Users, collection: 'customers' },
  { key: 'orders', label: 'Orders', icon: ClipboardList, collection: 'orders' },
  { key: 'tools', label: 'Bakery tools', icon: WandSparkles },
  { key: 'subscription', label: 'Subscription', icon: Sparkles },
  { key: 'settings', label: 'Business settings', icon: Settings },
]
const dataModules = modules.filter(module => module.collection)

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
          uid, bakeryId: bakeryRef.id, bakeryName, firstName, surname, email,
          role: 'owner', subscriptionPlan: 'free', subscriptionStatus: 'free',
          createdAt: serverTimestamp(),
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

type WorkspaceItem = { id: string; name?: string; title?: string; description?: string; email?: string; phone?: string; notes?: string; status?: string; total?: number; price?: number; totalOrders?: number; totalSpend?: number; [key: string]: unknown }

function Dashboard({ profile }: { profile: UserProfile }) {
  const [active, setActive] = useState<ModuleKey>('home')
  const [items, setItems] = useState<Record<string, WorkspaceItem[]>>({})
  const [showEditor, setShowEditor] = useState(false)
  const [editing, setEditing] = useState<WorkspaceItem | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)

  const isPro = ['active', 'trialing'].includes(profile.subscriptionStatus || '')
  const counts = Object.fromEntries(dataModules.map(module => [module.key, items[module.key]?.length || 0])) as Record<ModuleKey, number>
  const selectedModule = modules.find(module => module.key === active) || modules[0]
  const selectedItems = items[active] || []

  useEffect(() => {
    const cleanups = dataModules.map(({ key, collection: collectionName }) => onSnapshot(
      collection(db, 'bakeries', profile.bakeryId, collectionName!),
      snapshot => {
        setItems(current => ({ ...current, [key]: snapshot.docs.map(item => ({ id: item.id, ...item.data() })) }))
      },
    ))
    return () => cleanups.forEach(cleanup => cleanup())
  }, [profile.bakeryId])

  const greeting = useMemo(() => {
    const hour = new Date().getHours()
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
  }, [])

  function openEditor(item: WorkspaceItem | null = null) {
    if (selectedModule.pro && !isPro) { setActive('subscription'); return }
    if (active === 'recipes' && !isPro && (counts.recipes || 0) >= 5 && !item) { setActive('subscription'); return }
    setEditing(item)
    setShowEditor(true)
  }

  async function saveItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const collectionName = selectedModule.collection
    if (!collectionName) return
    const payload = {
      name: String(data.get('name')).trim(),
      description: String(data.get('description')).trim(),
      email: String(data.get('email')).trim(), phone: String(data.get('phone')).trim(),
      status: String(data.get('status') || 'active'),
      price: Number(data.get('price') || 0), total: Number(data.get('total') || 0),
      updatedAt: serverTimestamp(),
    }
    if (editing) await updateDoc(doc(db, 'bakeries', profile.bakeryId, collectionName, editing.id), payload)
    else await addDoc(collection(db, 'bakeries', profile.bakeryId, collectionName), { ...payload, createdAt: serverTimestamp(), totalOrders: 0, totalSpend: 0 })
    setShowEditor(false)
    setEditing(null)
  }

  async function removeItem(item: WorkspaceItem) {
    if (!selectedModule.collection || !window.confirm(`Delete ${item.name || item.title || 'this item'}?`)) return
    await deleteDoc(doc(db, 'bakeries', profile.bakeryId, selectedModule.collection, item.id))
  }

  return <div className="dashboard-shell">
    <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
      <div className="brand"><span className="brand-mark">B</span><span>Batch<strong>Boss</strong></span></div>
      <button className="close-menu" onClick={() => setMenuOpen(false)}><X /></button>
      <nav>
        {modules.map(({ key, label, icon: Icon, collection: collectionName, pro }) => <button key={key} className={active === key ? 'nav-item active' : 'nav-item'} onClick={() => { setActive(key); setMenuOpen(false) }}><Icon />{label}{pro && !isPro ? <span>PRO</span> : collectionName ? <span>{counts[key] || 0}</span> : null}</button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="profile-avatar">{profile.firstName.slice(0, 1)}{profile.surname.slice(0, 1)}</div>
        <div><strong>{profile.firstName} {profile.surname}</strong><small>{profile.role}</small></div>
        <button title="Sign out" onClick={() => signOut(auth)}><LogOut /></button>
      </div>
    </aside>
    <main className="dashboard-main">
      <header><button className="menu-button" onClick={() => setMenuOpen(true)}><Menu /></button><div><span>{greeting},</span><h1>{profile.firstName || 'Baker'} 👋</h1></div><span className={isPro ? 'plan-badge pro' : 'plan-badge'}><Sparkles size={15} />{isPro ? 'BatchBoss Pro' : 'Free plan'}</span></header>
      {active === 'home' && <HomeView counts={counts} onOpen={setActive} />}
      {active === 'subscription' && <SubscriptionView isPro={isPro} profile={profile} />}
      {active === 'tools' && <ToolsView />}
      {active === 'settings' && <SettingsView profile={profile} />}
      {selectedModule.collection && selectedModule.pro && !isPro
        ? <LockedView title={selectedModule.label} onUpgrade={() => setActive('subscription')} />
        : selectedModule.collection && <section className="content-card">
          <div className="section-heading"><div><span className="eyebrow">Your workspace</span><h2>{selectedModule.label}</h2><p className="section-copy">Changes are saved to the same Firebase bakery workspace used by the app.</p></div><button className="primary-button compact" onClick={() => openEditor()}><Plus size={18} /> Add {selectedModule.label.replace(/s$/, '').toLowerCase()}</button></div>
          <WorkspaceList items={selectedItems} label={selectedModule.label} onAdd={() => openEditor()} onEdit={openEditor} onDelete={removeItem} />
        </section>}
    </main>
    {showEditor && <ItemEditor module={selectedModule} item={editing} onClose={() => { setShowEditor(false); setEditing(null) }} onSave={saveItem} />}
  </div>
}

function HomeView({ counts, onOpen }: { counts: Record<ModuleKey, number>; onOpen: (key: ModuleKey) => void }) {
  return <><section className="hero-card pro-hero"><div><span className="eyebrow">Bakery overview</span><h2>Your bakery at a glance</h2><p>Manage costing, stock, customers and sales from one connected workspace.</p></div><div className="profit"><small>Estimated profit</small><strong>{money.format(0)}</strong><span>Updates when sales are recorded</span></div></section><section className="stat-grid">{[
    ['recipes', 'Recipes', BookOpen], ['inventory', 'Stock items', Boxes], ['customers', 'Customers', Users], ['orders', 'Orders', ClipboardList],
  ].map(([key, label, Icon]) => <button className="stat-card stat-button" key={String(key)} onClick={() => onOpen(key as ModuleKey)}><div className="stat-icon"><Icon /></div><span>{String(label)}</span><strong>{counts[key as ModuleKey] || 0}</strong><small>Live total</small></button>)}</section><section className="content-card quick-access"><div className="section-heading"><div><span className="eyebrow">Quick access</span><h2>Keep your bakery moving</h2></div></div><div className="quick-grid">{[['recipes','Recipes'],['ingredients','Ingredients & stock'],['invoices','Invoicing'],['products','Products & services']].map(([key,label])=><button key={key} onClick={()=>onOpen(key as ModuleKey)}><Sparkles/><strong>{label}</strong><ChevronRight/></button>)}</div></section></>
}

function WorkspaceList({ items, label, onAdd, onEdit, onDelete }: { items: WorkspaceItem[]; label: string; onAdd: () => void; onEdit: (item: WorkspaceItem) => void; onDelete: (item: WorkspaceItem) => void }) {
  if (!items.length) return <EmptyState title={`No ${label.toLowerCase()} yet`} text={`Add your first ${label.replace(/s$/, '').toLowerCase()} when you are ready. No sample data is added.`} action="Add new" onAction={onAdd} />
  return <div className="workspace-grid">{items.map(item => <article className="workspace-card" key={item.id}><div><span className="item-status">{String(item.status || 'Active')}</span><h3>{String(item.name || item.title || 'Untitled')}</h3><p>{String(item.description || item.notes || item.email || item.phone || 'Saved in your BatchBoss workspace')}</p>{typeof item.price === 'number' && item.price > 0 && <strong>{money.format(item.price)}</strong>}</div><div className="card-actions"><button onClick={() => onEdit(item)}>Edit</button><button className="danger-link" onClick={() => onDelete(item)}>Delete</button></div></article>)}</div>
}

function ItemEditor({ module, item, onClose, onSave }: { module: ModuleDefinition; item: WorkspaceItem | null; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const commercial = ['invoices','quotes','receipts','products'].includes(module.key)
  return <Modal title={`${item ? 'Edit' : 'Add'} ${module.label.replace(/s$/, '').toLowerCase()}`} onClose={onClose}><form className="modal-form" onSubmit={onSave}><label>Name or reference<input name="name" defaultValue={String(item?.name || item?.title || '')} required /></label><label>Description or notes<textarea name="description" rows={3} defaultValue={String(item?.description || item?.notes || '')} /></label>{module.key === 'customers' || module.key === 'suppliers' ? <div className="split-fields"><label>Phone<input name="phone" type="tel" defaultValue={String(item?.phone || '')} /></label><label>Email<input name="email" type="email" defaultValue={String(item?.email || '')} /></label></div> : null}{commercial && <div className="split-fields"><label>Price<input name="price" type="number" min="0" step="0.01" defaultValue={Number(item?.price || item?.total || 0)} /></label><label>Status<select name="status" defaultValue={String(item?.status || 'draft')}><option value="draft">Draft</option><option value="pending">Pending</option><option value="paid">Paid</option><option value="active">Active</option></select></label></div>}<button className="primary-button">Save changes</button></form></Modal>
}

function LockedView({ title, onUpgrade }: { title: string; onUpgrade: () => void }) { return <section className="content-card locked"><div className="lock-icon"><Sparkles /></div><span className="eyebrow">BatchBoss Pro</span><h2>Unlock {title}</h2><p>Upgrade to create professional invoices, quotes and receipts, save products and work with unlimited recipes and suppliers.</p><button className="primary-button compact" onClick={onUpgrade}>View Pro plans</button></section> }

function SubscriptionView({ isPro, profile }: { isPro: boolean; profile: UserProfile }) { return <section className="subscription-view"><div className="subscription-intro"><span className="eyebrow">Your plan</span><h2>{isPro ? 'BatchBoss Pro is active' : 'Unlock BatchBoss Pro'}</h2><p>Complete commercial tools for bakers who want to cost smarter and grow.</p></div><div className="feature-list">{['AI recipe and barcode scanning','Unlimited recipes and costing','Professional invoices and payment tracking','Quotes, estimates and customer receipts','Products, suppliers and specials'].map(feature=><div key={feature}><CheckCircle2Icon />{feature}</div>)}</div><div className="plans"><article><span>Save 33%</span><h3>Annual</h3><strong>R1 199</strong><small>per year · about R99/month</small><a className="primary-button" href="https://play.google.com/store/apps/details?id=com.aistudio.batchboss.kqwxrv" target="_blank" rel="noreferrer">Choose annual</a></article><article><span>Flexible</span><h3>Monthly</h3><strong>R149</strong><small>per month</small><a className="primary-button" href="https://play.google.com/store/apps/details?id=com.aistudio.batchboss.kqwxrv" target="_blank" rel="noreferrer">Choose monthly</a></article></div><p className="billing-note">Current status: <strong>{profile.subscriptionStatus || 'free'}</strong>. Purchases are completed securely through Google Play. The website never stores card details.</p></section> }

function CheckCircle2Icon() { return <span className="feature-check">✓</span> }
function ToolsView() { return <section className="content-card"><div className="section-heading"><div><span className="eyebrow">Bakery tools</span><h2>Converter & recipe scaler</h2></div></div><div className="tools-grid"><article><h3>Baking unit converter</h3><p>Ingredient volume weights vary. Use the app converter for ingredient-specific measurements.</p><strong>1 cup cake flour ≈ 125 g</strong></article><article><h3>Batch & recipe scaler</h3><p>Scale a recipe from its original yield to the batch you need.</p><strong>Available in the BatchBoss app</strong></article></div></section> }
function SettingsView({ profile }: { profile: UserProfile }) { return <section className="content-card"><div className="section-heading"><div><span className="eyebrow">Bakery identity</span><h2>Business settings</h2><p className="section-copy">Your logo and business details will appear on Pro documents.</p></div></div><div className="settings-summary"><div className="profile-avatar large">{profile.firstName.slice(0,1)}{profile.surname.slice(0,1)}</div><div><h3>{profile.firstName} {profile.surname}</h3><p>{profile.email}</p><small>Workspace: {profile.bakeryId}</small></div></div></section> }
function EmptyState({ title, text, action, onAction }: { title: string; text: string; action?: string; onAction?: () => void }) { return <div className="empty-state"><div><Sparkles /></div><h3>{title}</h3><p>{text}</p>{action && <button className="primary-button compact" onClick={onAction}><Plus size={17} />{action}</button>}</div> }
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) { return <div className="modal-backdrop"><div className="modal"><div className="modal-title"><h2>{title}</h2><button onClick={onClose}><X /></button></div>{children}</div></div> }

export default App
