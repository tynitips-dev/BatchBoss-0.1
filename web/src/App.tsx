import { FormEvent, useEffect, useMemo, useState } from 'react'
import {
  BookOpen, Boxes, CalendarDays, ChevronRight, ClipboardList,
  Download, Edit3, Eye, FileCheck2, Gauge, LogOut, Mail, Menu, Package, Plus, Receipt,
  ReceiptText, Search, Settings, Share2, Sparkles, Store, Trash2, Users, WandSparkles, X,
} from 'lucide-react'
import {
  createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword,
  signOut, type User,
} from 'firebase/auth'
import { addDoc, collection, deleteDoc, doc, getDocs, onSnapshot, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { auth, db, firebaseConfigured } from './firebase'
import type { ModuleKey, UserProfile } from './types'

type ModuleDefinition = { key: ModuleKey; label: string; icon: typeof BookOpen; collection?: string; pro?: boolean }
const modules: ModuleDefinition[] = [
  { key: 'home', label: 'Home', icon: Gauge },
  { key: 'recipes', label: 'Recipes', icon: BookOpen, collection: 'recipes' },
  { key: 'ingredients', label: 'Ingredients', icon: Package, collection: 'inventory' },
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

  const subscriptionExpiry = (profile.subscriptionExpiresAt as { toDate?: () => Date } | undefined)?.toDate?.()
  const promoValid = profile.subscriptionPlan !== 'promo' || (!!subscriptionExpiry && subscriptionExpiry.getTime() > Date.now())
  const isPro = ['active', 'trialing'].includes(profile.subscriptionStatus || '') && promoValid
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
    const value = (name: string) => String(data.get(name) || '').trim()
    const number = (name: string, fallback = 0) => Number(data.get(name) || fallback)

    if (active === 'recipes') {
      const recipeRef = editing
        ? doc(db, 'bakeries', profile.bakeryId, 'recipes', editing.id)
        : doc(collection(db, 'bakeries', profile.bakeryId, 'recipes'))
      const ingredientNames = data.getAll('ingredientName').map(String)
      const ingredientQuantities = data.getAll('ingredientQuantity').map(Number)
      const ingredientUnits = data.getAll('ingredientUnit').map(String)
      const ingredientCosts = data.getAll('ingredientCost').map(Number)
      const laborHours = number('laborHours', 0)
      const laborRatePerHour = number('laborRatePerHour', 0)
      const labourCost = laborHours * laborRatePerHour
      const ingredientCost = ingredientCosts.reduce((sum, cost) => sum + (Number.isFinite(cost) ? cost : 0), 0)
      const batch = writeBatch(db)
      if (editing) {
        const existingIngredients = await getDocs(collection(recipeRef, 'ingredients'))
        existingIngredients.docs.forEach(ingredient => batch.delete(ingredient.ref))
      }
      batch.set(recipeRef, {
        id: Number(editing?.localId || Date.now()), bakeryId: profile.bakeryId,
        name: value('name'), category: value('category') || 'Cakes', description: value('description'),
        servings: number('servings', 1), batchSize: number('batchSize', 1), difficulty: value('difficulty') || 'Medium',
        laborHours, laborRatePerHour, labourCost,
        overheadsCost: number('overheadsCost'), utilitiesCost: number('utilitiesCost'), packagingCost: number('packagingCost'),
        profitMarginPercent: number('profitMarginPercent', 40), ingredientCost,
        customSellingPrice: number('customSellingPrice'), instructions: value('instructions'),
        updatedAt: serverTimestamp(), ...(editing ? {} : { createdAt: serverTimestamp() }),
      }, { merge: true })
      ingredientNames.forEach((name, index) => {
        if (!name.trim()) return
        const ingredientRef = doc(collection(recipeRef, 'ingredients'))
        batch.set(ingredientRef, {
          id: Date.now() + index, recipeId: Number(editing?.localId || 0), name: name.trim(),
          quantity: ingredientQuantities[index] || 0, unit: ingredientUnits[index] || 'g',
          cost: ingredientCosts[index] || 0,
        })
      })
      await batch.commit()
      setShowEditor(false)
      setEditing(null)
      return
    }

    if (active === 'ingredients' || active === 'inventory') {
      const packagePrice = number('packagePrice')
      const packageQuantity = number('packageQuantity', 1)
      const unit = value('unit') || 'g'
      const baseQuantity = unit === 'kg' || unit === 'L' ? packageQuantity * 1000 : packageQuantity
      const payload = {
        id: Number(editing?.localId || Date.now()), bakeryId: profile.bakeryId,
        name: value('name'), category: value('category') || 'Baking Staples', unit,
        packagePrice, packageQuantity, gramsPerUnit: baseQuantity,
        unitPrice: baseQuantity > 0 ? packagePrice / baseQuantity : 0,
        currentStock: number('currentStock'), minStock: number('minStock'),
        isLowStock: number('currentStock') <= number('minStock'), alertEnabled: true,
        barcode: value('barcode'), updatedAt: serverTimestamp(),
      }
      if (editing) await updateDoc(doc(db, 'bakeries', profile.bakeryId, 'inventory', editing.id), payload)
      else await addDoc(collection(db, 'bakeries', profile.bakeryId, 'inventory'), { ...payload, createdAt: serverTimestamp() })
      setShowEditor(false)
      setEditing(null)
      return
    }

    const payload = {
      name: value('name'), description: value('description'),
      email: value('email'), phone: value('phone'), status: value('status') || 'active',
      price: number('price'), total: number('total'),
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
      {active === 'products' && isPro && <ProductsView bakeryId={profile.bakeryId} items={items.products || []} />}
      {active === 'invoices' && isPro && <InvoicesView bakeryId={profile.bakeryId} invoices={items.invoices || []} products={items.products || []} />}
      {active === 'quotes' && isPro && <QuotesView bakeryId={profile.bakeryId} quotes={items.quotes || []} products={items.products || []} />}
      {active === 'tasks' && <TasksView bakeryId={profile.bakeryId} items={items.tasks || []} />}
      {active === 'customers' && <CustomersView bakeryId={profile.bakeryId} items={items.customers || []} />}
      {selectedModule.collection && selectedModule.pro && !isPro
        ? <LockedView title={selectedModule.label} onUpgrade={() => setActive('subscription')} />
        : selectedModule.collection && !['products','invoices','quotes','tasks','customers'].includes(active) && <section className="content-card">
          <div className="section-heading"><div><span className="eyebrow">Your workspace</span><h2>{selectedModule.label}</h2><p className="section-copy">Changes are saved to the same Firebase bakery workspace used by the app.</p></div><button className="primary-button compact" onClick={() => openEditor()}><Plus size={18} /> Add {selectedModule.label.replace(/s$/, '').toLowerCase()}</button></div>
          <WorkspaceList items={selectedItems} label={selectedModule.label} onAdd={() => openEditor()} onEdit={openEditor} onDelete={removeItem} />
        </section>}
    </main>
    {showEditor && <ItemEditor module={selectedModule} item={editing} bakeryId={profile.bakeryId} onClose={() => { setShowEditor(false); setEditing(null) }} onSave={saveItem} />}
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

function ItemEditor({ module, item, bakeryId, onClose, onSave }: { module: ModuleDefinition; item: WorkspaceItem | null; bakeryId: string; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  if (module.key === 'recipes') return <RecipeEditor item={item} bakeryId={bakeryId} onClose={onClose} onSave={onSave} />
  if (module.key === 'ingredients' || module.key === 'inventory') return <IngredientEditor item={item} onClose={onClose} onSave={onSave} />
  const commercial = ['invoices','quotes','receipts','products'].includes(module.key)
  return <Modal title={`${item ? 'Edit' : 'Add'} ${module.label.replace(/s$/, '').toLowerCase()}`} onClose={onClose}><form className="modal-form" onSubmit={onSave}><label>Name or reference<input name="name" defaultValue={String(item?.name || item?.title || '')} required /></label><label>Description or notes<textarea name="description" rows={3} defaultValue={String(item?.description || item?.notes || '')} /></label>{module.key === 'customers' || module.key === 'suppliers' ? <div className="split-fields"><label>Phone<input name="phone" type="tel" defaultValue={String(item?.phone || '')} /></label><label>Email<input name="email" type="email" defaultValue={String(item?.email || '')} /></label></div> : null}{commercial && <div className="split-fields"><label>Price<input name="price" type="number" min="0" step="0.01" defaultValue={Number(item?.price || item?.total || 0)} /></label><label>Status<select name="status" defaultValue={String(item?.status || 'draft')}><option value="draft">Draft</option><option value="pending">Pending</option><option value="paid">Paid</option><option value="active">Active</option></select></label></div>}<button className="primary-button">Save changes</button></form></Modal>
}

type IngredientRow = { name: string; quantity: number; unit: string; cost: number }

function RecipeEditor({ item, bakeryId, onClose, onSave }: { item: WorkspaceItem | null; bakeryId: string; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const [rows, setRows] = useState<IngredientRow[]>([{ name: '', quantity: 0, unit: 'g', cost: 0 }])
  const [inventory, setInventory] = useState<WorkspaceItem[]>([])
  const [laborHours, setLaborHours] = useState(Number(item?.laborHours || 1.5))
  const [laborRate, setLaborRate] = useState(Number(item?.laborRatePerHour || 120))
  const [overheads, setOverheads] = useState(Number(item?.overheadsCost || 0))
  const [utilities, setUtilities] = useState(Number(item?.utilitiesCost || 0))
  const [packaging, setPackaging] = useState(Number(item?.packagingCost || 0))
  const [batchSize, setBatchSize] = useState(Number(item?.batchSize || 1))

  useEffect(() => onSnapshot(collection(db, 'bakeries', bakeryId, 'inventory'), snapshot => {
    setInventory(snapshot.docs.map(entry => ({ id: entry.id, ...entry.data() })))
  }), [bakeryId])

  useEffect(() => {
    if (!item) return
    getDocs(collection(db, 'bakeries', bakeryId, 'recipes', item.id, 'ingredients')).then(snapshot => {
      const saved = snapshot.docs.map(entry => entry.data() as IngredientRow)
      if (saved.length) setRows(saved)
    })
  }, [bakeryId, item])

  const ingredientsCost = rows.reduce((sum, row) => sum + Number(row.cost || 0), 0)
  const labourCost = laborHours * laborRate
  const totalBatchCost = ingredientsCost + labourCost + overheads + utilities + packaging
  const costPerUnit = batchSize > 0 ? totalBatchCost / batchSize : 0
  const updateRow = (index: number, patch: Partial<IngredientRow>) => setRows(current => current.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row))

  return <Modal title={`${item ? 'Edit' : 'Create new'} recipe`} onClose={onClose}><form className="modal-form recipe-form" onSubmit={onSave}>
    <label>Recipe name<input name="name" defaultValue={String(item?.name || '')} placeholder="e.g. Vanilla Cupcakes" required /></label>
    <label>Category<select name="category" defaultValue={String(item?.category || 'Cupcakes')}><option>Cupcakes</option><option>Cakes</option><option>Cookies</option><option>Breads</option><option>Pastries</option><option>Other</option></select></label>
    <label>Description<textarea name="description" rows={3} defaultValue={String(item?.description || '')} /></label>
    <div className="split-fields"><label>Servings<input name="servings" type="number" min="1" defaultValue={Number(item?.servings || 1)} /></label><label>Batch size<input name="batchSize" type="number" min="1" value={batchSize} onChange={event => setBatchSize(Number(event.target.value))} /></label></div>
    <section className="costing-panel"><div className="mini-heading"><div><h3>Operations, Overheads & Labour</h3><p>Configure complete batch-costing details.</p></div><span>Fully editable</span></div><div className="cost-grid"><label>Labour hours<input name="laborHours" type="number" min="0" step="0.25" value={laborHours} onChange={event => setLaborHours(Number(event.target.value))} /></label><label>Rate/hour<input name="laborRatePerHour" type="number" min="0" step="0.01" value={laborRate} onChange={event => setLaborRate(Number(event.target.value))} /></label><label>Overheads cost<input name="overheadsCost" type="number" min="0" step="0.01" value={overheads} onChange={event => setOverheads(Number(event.target.value))} /></label><label>Utilities cost<input name="utilitiesCost" type="number" min="0" step="0.01" value={utilities} onChange={event => setUtilities(Number(event.target.value))} /></label><label>Packaging cost<input name="packagingCost" type="number" min="0" step="0.01" value={packaging} onChange={event => setPackaging(Number(event.target.value))} /></label><label>Profit margin %<input name="profitMarginPercent" type="number" min="0" step="0.1" defaultValue={Number(item?.profitMarginPercent || 40)} /></label></div><p className="calculated-line">Calculated labour cost <strong>{money.format(labourCost)}</strong></p></section>
    <section className="recipe-ingredients"><div className="mini-heading"><div><h3>Recipe ingredients</h3><p>Add quantities in g, kg, ml, L, tsp, tbsp or units.</p></div><strong>Total: {money.format(ingredientsCost)}</strong></div><datalist id="inventory-options">{inventory.map(stock => <option key={stock.id} value={String(stock.name || '')} />)}</datalist>{rows.map((row, index) => <div className="ingredient-row" key={index}><input aria-label="Ingredient name" name="ingredientName" list="inventory-options" placeholder="Ingredient" value={row.name} onChange={event => updateRow(index, { name: event.target.value })} /><input aria-label="Quantity" name="ingredientQuantity" type="number" min="0" step="0.01" value={row.quantity} onChange={event => updateRow(index, { quantity: Number(event.target.value) })} /><select aria-label="Unit" name="ingredientUnit" value={row.unit} onChange={event => updateRow(index, { unit: event.target.value })}><option>g</option><option>kg</option><option>ml</option><option>L</option><option>tsp</option><option>tbsp</option><option>unit</option></select><input aria-label="Ingredient cost" name="ingredientCost" type="number" min="0" step="0.01" value={row.cost} onChange={event => updateRow(index, { cost: Number(event.target.value) })} /><button type="button" aria-label="Remove ingredient" onClick={() => setRows(current => current.filter((_, rowIndex) => rowIndex !== index))}>×</button></div>)}<button className="outline-button" type="button" onClick={() => setRows(current => [...current, { name: '', quantity: 0, unit: 'g', cost: 0 }])}><Plus size={17} /> Add ingredient</button></section>
    <section className="cost-summary"><div><span>Ingredients cost</span><strong>{money.format(ingredientsCost)}</strong></div><div><span>Operations & labour</span><strong>{money.format(labourCost)}</strong></div><div><span>Overheads</span><strong>{money.format(overheads)}</strong></div><div><span>Utilities</span><strong>{money.format(utilities)}</strong></div><div><span>Packaging</span><strong>{money.format(packaging)}</strong></div><div className="summary-total"><span>Total batch cost</span><strong>{money.format(totalBatchCost)}</strong></div><div><span>Cost per unit ({batchSize} units)</span><strong>{money.format(costPerUnit)}</strong></div></section>
    <button className="primary-button">Save recipe</button>
  </form></Modal>
}

function IngredientEditor({ item, onClose, onSave }: { item: WorkspaceItem | null; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const [name, setName] = useState(String(item?.name || ''))
  const [unit, setUnit] = useState(String(item?.unit || 'g'))
  const [packagePrice, setPackagePrice] = useState(Number(item?.packagePrice || 0))
  const [packageQuantity, setPackageQuantity] = useState(Number(item?.packageQuantity || item?.gramsPerUnit || 0))
  const baseQuantity = unit === 'kg' || unit === 'L' ? packageQuantity * 1000 : packageQuantity
  const unitCost = baseQuantity > 0 ? packagePrice / baseQuantity : 0
  const presets = ['Flour', 'Sugar', 'Chocolate', 'Baking Soda', 'Butter', 'Eggs']
  return <Modal title={`${item ? 'Edit' : 'Add'} stock & ingredient`} onClose={onClose}><form className="modal-form ingredient-form" onSubmit={onSave}><div><span className="field-heading">Ingredient presets</span><div className="preset-row">{presets.map(preset => <button type="button" key={preset} className={name === preset ? 'active' : ''} onClick={() => setName(preset)}>{preset}</button>)}</div></div><label>Ingredient/item name<input name="name" value={name} onChange={event => setName(event.target.value)} required /></label><label>Category<select name="category" defaultValue={String(item?.category || 'Baking Staples')}><option>Baking Staples</option><option>Dairy</option><option>Chocolate</option><option>Flavourings</option><option>Packaging</option><option>Other</option></select></label><div><span className="field-heading">Unit of measurement</span><div className="unit-row">{['g','kg','ml','L','unit','bottle'].map(value => <button type="button" key={value} className={unit === value ? 'active' : ''} onClick={() => setUnit(value)}>{value}</button>)}</div><input type="hidden" name="unit" value={unit} /></div><div className="split-fields"><label>Pack price<input name="packagePrice" type="number" min="0" step="0.01" value={packagePrice} onChange={event => setPackagePrice(Number(event.target.value))} /></label><label>Quantity in pack<input name="packageQuantity" type="number" min="0" step="0.01" value={packageQuantity} onChange={event => setPackageQuantity(Number(event.target.value))} /></label></div><div className="calculated-cost"><span>Calculated cost / {unit === 'kg' ? 'g' : unit === 'L' ? 'ml' : unit}</span><strong>{money.format(unitCost)}</strong></div><div className="split-fields"><label>Current stock<input name="currentStock" type="number" min="0" step="0.01" defaultValue={Number(item?.currentStock || 0)} /></label><label>Minimum stock alert<input name="minStock" type="number" min="0" step="0.01" defaultValue={Number(item?.minStock || 0)} /></label></div><label>Barcode (optional)<input name="barcode" defaultValue={String(item?.barcode || '')} /></label><button className="primary-button">{item ? 'Save ingredient' : 'Add item'}</button></form></Modal>
}

function ProductsView({ bakeryId, items }: { bakeryId: string; items: WorkspaceItem[] }) {
  const [editor, setEditor] = useState<WorkspaceItem | null | undefined>()
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [activeOnly,setActiveOnly] = useState(false)
  const filtered = items.filter(item => (!activeOnly || item.status !== 'inactive') && (category === 'All' || item.category === category) && `${item.name || ''} ${item.sku || ''} ${item.category || ''}`.toLowerCase().includes(query.toLowerCase()))
  async function remove(item: WorkspaceItem) {
    if (window.confirm(`Delete ${item.name || 'this product'}?`)) await deleteDoc(doc(db, 'bakeries', bakeryId, 'products', item.id))
  }
  return <section className="app-section products-view">
    <div className="app-page-title"><div><span className="eyebrow">Master catalogue</span><h2>Products &amp; Services</h2></div><button className="primary-button compact" onClick={() => setEditor(null)}><Plus size={18}/> Add Product/Service</button></div>
    <label className="catalogue-search"><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search by name, SKU, category…" /></label>
    <div className="filter-chips">{['All','Cakes','Cupcakes','Cookies','Muffins','Services'].map(value=><button className={category===value?'active':''} onClick={()=>setCategory(value)} key={value}>{value}</button>)}</div>
    <div className="catalogue-meta"><strong>{filtered.length} item{filtered.length===1?'':'s'} in master catalogue</strong><label className="check-label"><input type="checkbox" checked={activeOnly} onChange={e=>setActiveOnly(e.target.checked)}/> Active only</label></div>
    {filtered.length ? <div className="catalogue-list">{filtered.map(item=>{
      const cost=Number(item.costPrice||0), price=Number(item.sellingPrice||item.price||0), profit=price-cost, margin=price?profit/price*100:0
      return <article className="product-card" key={item.id}><div><span className="category-tag">{String(item.category||'Product')}</span><h3>{String(item.name||'Untitled')}</h3><p>{String(item.sku||'')} · per {String(item.unit||'Each')}</p></div><div className="product-price"><strong>{money.format(price)}</strong><small>per {String(item.unit||'Each')}</small></div><div className="product-profit"><span>Cost: <b>{money.format(cost)}</b></span><span>Profit: <b>{money.format(profit)} ({margin.toFixed(0)}%)</b></span></div><div className="icon-actions"><button title={item.status==='inactive'?'Activate':'Deactivate'} onClick={()=>updateDoc(doc(db,'bakeries',bakeryId,'products',item.id),{status:item.status==='inactive'?'active':'inactive',updatedAt:serverTimestamp()})}>↻</button><button title="Edit" onClick={()=>setEditor(item)}><Edit3/></button><button className="danger-link" title="Delete" onClick={()=>remove(item)}><Trash2/></button></div></article>
    })}</div>:<EmptyState title="No products or services yet" text="Add products once and reuse their prices on invoices and quotes." action="Add product/service" onAction={()=>setEditor(null)}/>}
    {editor !== undefined && <ProductEditor bakeryId={bakeryId} item={editor} onClose={()=>setEditor(undefined)} />}
  </section>
}

function ProductEditor({ bakeryId, item, onClose }: { bakeryId:string; item:WorkspaceItem|null; onClose:()=>void }) {
  const [kind,setKind]=useState(String(item?.kind||'product'))
  const [cost,setCost]=useState(Number(item?.costPrice||0)); const [price,setPrice]=useState(Number(item?.sellingPrice||item?.price||0))
  const profit=price-cost, margin=price?profit/price*100:0
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault(); const data=new FormData(event.currentTarget); const payload={kind,name:String(data.get('name')||''),category:String(data.get('category')||'Cakes'),sku:String(data.get('sku')||''),unit:String(data.get('unit')||'Each'),costPrice:cost,sellingPrice:price,price,status:String(item?.status||'active'),updatedAt:serverTimestamp()}; if(item) await updateDoc(doc(db,'bakeries',bakeryId,'products',item.id),payload); else await addDoc(collection(db,'bakeries',bakeryId,'products'),{...payload,createdAt:serverTimestamp()}); onClose()}
  return <Modal title={`${item?'Edit':'New'} Product / Service`} onClose={onClose}><form className="modal-form product-form" onSubmit={save}><div className="type-toggle"><button type="button" className={kind==='product'?'active':''} onClick={()=>setKind('product')}>Product (Cake, Bakes)</button><button type="button" className={kind==='service'?'active':''} onClick={()=>setKind('service')}>Service (Delivery, Decor)</button></div><label>Product/Service name<input name="name" defaultValue={String(item?.name||'')} required/></label><label>Category<select name="category" defaultValue={String(item?.category||'Cupcakes')}><option>Cakes</option><option>Cupcakes</option><option>Cookies</option><option>Muffins</option><option>Services</option><option>Other</option></select></label><div className="split-fields"><label>SKU / Code<input name="sku" defaultValue={String(item?.sku||'')}/></label><label>Unit<select name="unit" defaultValue={String(item?.unit||'Each')}><option>Each</option><option>Item</option><option>Dozen</option><option>Kg</option><option>Gram</option><option>Box</option></select></label></div><div className="split-fields"><label>Cost price (R)<input type="number" min="0" step=".01" value={cost} onChange={e=>setCost(Number(e.target.value))}/></label><label>Selling price (R)<input type="number" min="0" step=".01" value={price} onChange={e=>setPrice(Number(e.target.value))} required/></label></div><div className="profit-preview"><div><span>Estimated profit</span><strong>{money.format(profit)}</strong></div><div><span>Profit margin</span><strong>{margin.toFixed(1)}%</strong></div></div><button className="primary-button">Save to Products &amp; Services</button></form></Modal>
}

function InvoicesView({ bakeryId, invoices, products }: { bakeryId:string; invoices:WorkspaceItem[]; products:WorkspaceItem[] }) {
  const [creating,setCreating]=useState(false); const [preview,setPreview]=useState<WorkspaceItem|null>(null); const [filter,setFilter]=useState('all'); const [search,setSearch]=useState('')
  const total=invoices.reduce((sum,item)=>sum+Number(item.total||0),0), pending=invoices.filter(i=>i.status!=='paid').reduce((sum,i)=>sum+Number(i.total||0),0), collected=total-pending
  const shown=invoices.filter(item=>(filter==='all'||item.status===filter)&&`${item.invoiceNumber||item.name||''} ${item.clientName||''} ${item.summary||''}`.toLowerCase().includes(search.toLowerCase()))
  async function markPaid(item:WorkspaceItem){const batch=writeBatch(db);batch.update(doc(db,'bakeries',bakeryId,'invoices',item.id),{status:'paid',paidAt:serverTimestamp(),updatedAt:serverTimestamp()});if(item.orderId)batch.set(doc(db,'bakeries',bakeryId,'orders',String(item.orderId)),{status:'paid',paymentStatus:'paid',updatedAt:serverTimestamp()},{merge:true});if(item.taskId)batch.set(doc(db,'bakeries',bakeryId,'tasks',String(item.taskId)),{status:'completed',completedAt:serverTimestamp(),updatedAt:serverTimestamp()},{merge:true});await batch.commit()}
  async function remove(item:WorkspaceItem){if(window.confirm(`Delete ${item.invoiceNumber||'this invoice'}?`))await deleteDoc(doc(db,'bakeries',bakeryId,'invoices',item.id))}
  return <section className="app-section invoice-view"><div className="app-page-title"><div><span className="eyebrow">BatchBoss Pro</span><h2>Invoicing</h2></div><button className="circle-add" onClick={()=>setCreating(true)}><Plus/></button></div><div className="invoice-stats"><article><span>Total billed</span><strong>{money.format(total)}</strong></article><article><span>Pending</span><strong>{money.format(pending)}</strong></article><article><span>Collected</span><strong>{money.format(collected)}</strong></article></div><label className="catalogue-search"><Search/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search client, invoice # or order…"/></label><div className="filter-chips">{['all','pending','paid','overdue'].map(value=><button key={value} className={filter===value?'active':''} onClick={()=>setFilter(value)}>{value[0].toUpperCase()+value.slice(1)}</button>)}</div>{shown.length?<div className="invoice-list">{shown.map(item=><article className="invoice-card" key={item.id}><div><span className="invoice-number">{String(item.invoiceNumber||item.name||'Invoice')}</span><h3>{String(item.clientName||item.description||'Client')}</h3><p>{String(item.summary||'Invoice line items')}</p></div><span className={`status-pill ${item.status==='paid'?'paid':''}`}>{String(item.status||'pending')}</span><strong className="invoice-total">{money.format(Number(item.total||0))}</strong><div className="invoice-actions"><button onClick={()=>setPreview(item)}><Eye/> View</button>{item.status!=='paid'&&<button onClick={()=>markPaid(item)}>✓ Mark paid</button>}<button onClick={()=>navigator.share?.({title:String(item.invoiceNumber||'Invoice'),text:`Invoice total: ${money.format(Number(item.total||0))}`})}><Share2/></button><button className="danger-link" onClick={()=>remove(item)}><Trash2/></button></div></article>)}</div>:<EmptyState title="No matching invoices" text="Create a professional invoice using saved products and services." action="Create invoice" onAction={()=>setCreating(true)}/>} {creating&&<InvoiceEditor bakeryId={bakeryId} products={products} invoiceCount={invoices.length} onClose={()=>setCreating(false)}/>} {preview&&<InvoicePreview bakeryId={bakeryId} invoice={preview} onClose={()=>setPreview(null)}/>}</section>
}

type InvoiceLine={productId:string;name:string;quantity:number;unit:string;unitPrice:number;costPrice:number;lineTotal:number}
function InvoiceEditor({bakeryId,products,invoiceCount,onClose}:{bakeryId:string;products:WorkspaceItem[];invoiceCount:number;onClose:()=>void}){
  const [productId,setProductId]=useState(products.find(p=>p.status!=='inactive')?.id||''); const [quantity,setQuantity]=useState(1); const [lines,setLines]=useState<InvoiceLine[]>([]); const [discount,setDiscount]=useState(0); const [client,setClient]=useState(''); const [phone,setPhone]=useState(''); const [notes,setNotes]=useState('');
  const product=products.find(p=>p.id===productId); const subtotal=lines.reduce((sum,line)=>sum+line.lineTotal,0); const total=Math.max(0,subtotal-discount); const profit=lines.reduce((sum,line)=>sum+(line.unitPrice-line.costPrice)*line.quantity,0)-discount
  function addLine(){if(!product)return;const unitPrice=Number(product.sellingPrice||product.price||0);setLines(current=>[...current,{productId:product.id,name:String(product.name||'Item'),quantity,unit:String(product.unit||'Each'),unitPrice,costPrice:Number(product.costPrice||0),lineTotal:unitPrice*quantity}]);setQuantity(1)}
  async function save(event:FormEvent){event.preventDefault();const invoiceNumber=`INV-${String(invoiceCount+1).padStart(4,'0')}`;const dueDate=(document.getElementById('invoice-due') as HTMLInputElement)?.value;const invoiceRef=doc(collection(db,'bakeries',bakeryId,'invoices'));const orderRef=doc(collection(db,'bakeries',bakeryId,'orders'));const taskRef=doc(collection(db,'bakeries',bakeryId,'tasks'));const summary=lines.map(line=>`${line.quantity} × ${line.name}`).join(', ');const batch=writeBatch(db);batch.set(invoiceRef,{name:invoiceNumber,invoiceNumber,clientName:client,phone,dueDate,items:lines,notes,summary,subtotal,discount,vat:0,total,estimatedProfit:profit,status:'pending',orderId:orderRef.id,taskId:taskRef.id,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});batch.set(orderRef,{name:`Order for ${client}`,orderNumber:`ORD-${String(invoiceCount+1).padStart(4,'0')}`,invoiceId:invoiceRef.id,invoiceNumber,clientName:client,phone,dueDate,items:lines,description:summary,total,status:'pending',paymentStatus:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});batch.set(taskRef,{name:`Prepare order for ${client}`,orderRef:invoiceNumber,invoiceId:invoiceRef.id,orderId:orderRef.id,dueDate,dueTime:'12:00',priority:'high',description:summary,status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});await batch.commit();onClose()}
  return <Modal title="Create Invoice" onClose={onClose}><form className="modal-form invoice-form" onSubmit={save}><p className="modal-subtitle">Automatic line items &amp; totals</p><span className="form-section-label">Client &amp; invoice details</span><label>Client name<input value={client} onChange={e=>setClient(e.target.value)} required/></label><div className="split-fields"><label>Phone / WhatsApp<input value={phone} onChange={e=>setPhone(e.target.value)}/></label><label>Due date<input id="invoice-due" type="date" required/></label></div><span className="form-section-label">Line items ({lines.length})</span>{products.length?<div className="add-line-row"><select value={productId} onChange={e=>setProductId(e.target.value)}>{products.filter(p=>p.status!=='inactive').map(p=><option key={p.id} value={p.id}>{String(p.name)} — {money.format(Number(p.sellingPrice||p.price||0))}</option>)}</select><input aria-label="Quantity" type="number" min="1" value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/><button className="primary-button compact" type="button" onClick={addLine}><Plus/> Add item</button></div>:<div className="warning-note">Add a Product or Service first, then it will be available here.</div>}{lines.map((line,index)=><div className="invoice-line-editor" key={`${line.productId}-${index}`}><div><strong>{line.name}</strong><small>{line.quantity} {line.unit} @ {money.format(line.unitPrice)}</small></div><strong>{money.format(line.lineTotal)}</strong><button type="button" onClick={()=>setLines(current=>current.filter((_,i)=>i!==index))}><Trash2/></button></div>)}<label>Invoice description / notes<textarea rows={2} value={notes} onChange={e=>setNotes(e.target.value)}/></label><section className="invoice-summary"><div><span>Subtotal</span><strong>{money.format(subtotal)}</strong></div><div><label>Discount (R)</label><input type="number" min="0" step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></div><div><span>VAT tax</span><strong>{money.format(0)}</strong></div><div className="summary-total"><span>Total due</span><strong>{money.format(total)}</strong></div><div className="estimated-profit"><span>Estimated profit</span><strong>{money.format(profit)}</strong></div></section><button className="primary-button" disabled={!lines.length}>Generate invoice</button></form></Modal>
}

function InvoicePreview({bakeryId,invoice,onClose}:{bakeryId:string;invoice:WorkspaceItem;onClose:()=>void}){
  const items=Array.isArray(invoice.items)?invoice.items as Array<Record<string,unknown>>:[]
  const [business,setBusiness]=useState<Record<string,unknown>>({});useEffect(()=>onSnapshot(doc(db,'bakeries',bakeryId),s=>setBusiness(s.data()||{})),[bakeryId]);const message=`${business.name||business.businessName||'BatchBoss'} invoice ${invoice.invoiceNumber||invoice.name||''}\nAmount due: ${money.format(Number(invoice.total||0))}\nThank you for your business.`;const whatsapp=String(invoice.phone||'').replace(/\D/g,'').replace(/^0/,'27')
  return <Modal title="Tax Invoice" onClose={onClose}><div className="invoice-preview"><div className="invoice-brand"><div>{business.logoDataUrl?<img src={String(business.logoDataUrl)} alt="Business logo"/>:<span className="brand-mark">B</span>}<div><strong>{String(business.name||business.businessName||'Your Bakery')}</strong><small>{String(business.email||'')}</small></div></div><h3>Tax Invoice</h3></div><div className="preview-heading"><div><span>Invoice</span><strong>{String(invoice.invoiceNumber||invoice.name||'')}</strong></div><span className="status-pill">{String(invoice.status||'pending')}</span></div><div className="billed"><span>Billed to</span><strong>{String(invoice.clientName||'Client')}</strong><small>{String(invoice.phone||'')}</small></div>{items.map((item,index)=><div className="preview-line" key={index}><span>{String(item.quantity||1)} × {String(item.name||'Item')}</span><strong>{money.format(Number(item.lineTotal||0))}</strong></div>)}<div className="preview-total"><span>Total amount due</span><strong>{money.format(Number(invoice.total||0))}</strong></div><div className="payment-box"><strong>Banking &amp; payment details</strong><span>{String(business.bankName||'Bank details not added')}</span><span>Account: {String(business.accountNumber||'—')} · Branch: {String(business.branchCode||'—')}</span><span>Reference: {String(invoice.invoiceNumber||invoice.name||'Invoice')}</span></div><div className="preview-buttons"><button className="primary-button" onClick={()=>window.print()}><Download/> View / Save PDF</button><button className="outline-button" onClick={()=>navigator.share?.({title:String(invoice.invoiceNumber||'Invoice'),text:message})}><Share2/> Share PDF</button></div>{whatsapp&&<a className="whatsapp-button" href={`https://wa.me/${whatsapp}?text=${encodeURIComponent(message)}`} target="_blank" rel="noreferrer">Send invoice via WhatsApp</a>}<div className="quick-share"><button onClick={()=>navigator.clipboard.writeText(message)}>Copy text</button><button onClick={()=>navigator.share?.({text:message})}>Share text</button></div></div></Modal>
}

function CustomersView({bakeryId,items}:{bakeryId:string;items:WorkspaceItem[]}){
  const [adding,setAdding]=useState(false);const [editing,setEditing]=useState<WorkspaceItem|null>(null);const [search,setSearch]=useState('');const shown=items.filter(item=>`${item.name||''} ${item.phone||''} ${item.email||''}`.toLowerCase().includes(search.toLowerCase()));const totalOrders=items.reduce((sum,item)=>sum+Number(item.totalOrders||0),0);const totalSpend=items.reduce((sum,item)=>sum+Number(item.totalSpend||0),0)
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);const payload={name:String(data.get('name')||''),phone:String(data.get('phone')||''),email:String(data.get('email')||''),address:String(data.get('address')||''),preferences:String(data.get('preferences')||''),status:'active',updatedAt:serverTimestamp()};if(editing)await updateDoc(doc(db,'bakeries',bakeryId,'customers',editing.id),payload);else await addDoc(collection(db,'bakeries',bakeryId,'customers'),{...payload,totalOrders:0,totalSpend:0,createdAt:serverTimestamp()});setAdding(false);setEditing(null)}
  return <section className="app-section customers-view"><div className="app-page-title"><div><span className="eyebrow">Customer records</span><h2>Customer Directory</h2></div><button className="primary-button compact" onClick={()=>setAdding(true)}><Plus/> Add customer</button></div><div className="invoice-stats"><article><span>Total customers</span><strong>{items.length}</strong></article><article><span>Total orders</span><strong>{totalOrders}</strong></article><article><span>Total spend</span><strong>{money.format(totalSpend)}</strong></article></div><label className="catalogue-search"><Search/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search customers by name, phone or email…"/></label>{shown.length?<div className="catalogue-list">{shown.map(item=><article className="customer-card" key={item.id}><div><span className="profile-avatar">{String(item.name||'C').slice(0,1).toUpperCase()}</span><div><h3>{String(item.name||'Customer')}</h3><p>{String(item.phone||'No phone')} · {String(item.email||'No email')}</p><small>{String(item.address||'')}</small></div></div><div className="card-actions"><button onClick={()=>setEditing(item)}>Edit</button><button className="danger-link" onClick={()=>window.confirm('Delete this customer?')&&deleteDoc(doc(db,'bakeries',bakeryId,'customers',item.id))}>Delete</button></div></article>)}</div>:<EmptyState title="No customers yet" text="Add your first customer and keep their contact and order details together." action="Add customer" onAction={()=>setAdding(true)}/>} {(adding||editing)&&<Modal title={editing?'Edit Customer':'Add New Customer'} onClose={()=>{setAdding(false);setEditing(null)}}><form className="modal-form" onSubmit={save}><label>Customer name<input name="name" defaultValue={String(editing?.name||'')} required/></label><label>Phone number<input name="phone" type="tel" defaultValue={String(editing?.phone||'')}/></label><label>Email address<input name="email" type="email" defaultValue={String(editing?.email||'')}/></label><label>Delivery address / city<input name="address" defaultValue={String(editing?.address||'')}/></label><label>Preferences &amp; allergies<textarea name="preferences" rows={3} defaultValue={String(editing?.preferences||'')}/></label><button className="primary-button">Save customer</button></form></Modal>}</section>
}

function QuotesView({bakeryId,quotes,products}:{bakeryId:string;quotes:WorkspaceItem[];products:WorkspaceItem[]}){
  const [adding,setAdding]=useState(false);const [kind,setKind]=useState('quote');const [productId,setProductId]=useState(products[0]?.id||'');const [lines,setLines]=useState<InvoiceLine[]>([]);const [customName,setCustomName]=useState('');const [customCost,setCustomCost]=useState(0);const [customPrice,setCustomPrice]=useState(0);const [discount,setDiscount]=useState(0);const [vat,setVat]=useState(false);const subtotal=lines.reduce((sum,line)=>sum+line.lineTotal,0);const vatAmount=vat?Math.max(0,subtotal-discount)*.15:0;const total=Math.max(0,subtotal-discount)+vatAmount;const estimatedCost=lines.reduce((sum,line)=>sum+line.costPrice*line.quantity,0)
  function addCatalog(){const product=products.find(p=>p.id===productId);if(!product)return;const unitPrice=Number(product.sellingPrice||product.price||0);setLines(current=>[...current,{productId:product.id,name:String(product.name||'Item'),quantity:1,unit:String(product.unit||'Each'),unitPrice,costPrice:Number(product.costPrice||0),lineTotal:unitPrice}])}
  function addCustom(){if(!customName||customPrice<=0)return;setLines(current=>[...current,{productId:'custom',name:customName,quantity:1,unit:'Each',unitPrice:customPrice,costPrice:customCost,lineTotal:customPrice}]);setCustomName('');setCustomCost(0);setCustomPrice(0)}
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);await addDoc(collection(db,'bakeries',bakeryId,'quotes'),{name:`${kind==='quote'?'Quote':'Estimate'} for ${String(data.get('clientName'))}`,type:kind,clientName:String(data.get('clientName')),phone:String(data.get('phone')||''),eventType:String(data.get('eventType')||''),eventDate:String(data.get('eventDate')||''),items:lines,description:lines.map(l=>l.name).join(', '),subtotal,discount,vat:vatAmount,total,estimatedCost,status:'draft',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});setAdding(false);setLines([])}
  return <section className="app-section quotes-view"><div className="app-page-title"><div><span className="eyebrow">Plan the sale</span><h2>Quotes &amp; Estimates</h2></div><button className="circle-add" onClick={()=>setAdding(true)}><Plus/></button></div>{quotes.length?<div className="workspace-grid">{quotes.map(item=><article className="workspace-card" key={item.id}><div><span className="item-status">{String(item.type||'quote')}</span><h3>{String(item.name||'Quote')}</h3><p>{String(item.description||'')}</p><strong>{money.format(Number(item.total||0))}</strong></div><div className="card-actions"><button onClick={()=>window.print()}>Print</button><button className="danger-link" onClick={()=>window.confirm('Delete this quote?')&&deleteDoc(doc(db,'bakeries',bakeryId,'quotes',item.id))}>Delete</button></div></article>)}</div>:<EmptyState title="No quotes or estimates yet" text="Create a quote using your saved product catalogue or custom line items." action="Create quote" onAction={()=>setAdding(true)}/>} {adding&&<Modal title="New Quote" onClose={()=>setAdding(false)}><form className="modal-form quote-form" onSubmit={save}><div className="type-toggle"><button type="button" className={kind==='quote'?'active':''} onClick={()=>setKind('quote')}>Quote</button><button type="button" className={kind==='estimate'?'active':''} onClick={()=>setKind('estimate')}>Estimate</button></div><span className="form-section-label">Client &amp; event details</span><label>Client name<input name="clientName" required/></label><label>Client phone / WhatsApp<input name="phone"/></label><div className="split-fields"><label>Event type<input name="eventType" placeholder="Wedding, birthday…"/></label><label>Event date<input name="eventDate" type="date"/></label></div><span className="form-section-label">Line items</span>{products.length&&<div className="add-line-row"><select value={productId} onChange={e=>setProductId(e.target.value)}>{products.map(p=><option key={p.id} value={p.id}>{String(p.name)} — {money.format(Number(p.sellingPrice||p.price||0))}</option>)}</select><span/><button className="primary-button compact" type="button" onClick={addCatalog}>From catalogue</button></div>}<div className="custom-line"><input placeholder="Custom item details" value={customName} onChange={e=>setCustomName(e.target.value)}/><input aria-label="Estimated cost" type="number" min="0" step=".01" placeholder="Cost" value={customCost||''} onChange={e=>setCustomCost(Number(e.target.value))}/><input aria-label="Selling price" type="number" min="0" step=".01" placeholder="Price" value={customPrice||''} onChange={e=>setCustomPrice(Number(e.target.value))}/><button type="button" onClick={addCustom}>+ Custom</button></div>{lines.map((line,index)=><div className="invoice-line-editor" key={index}><div><strong>{line.name}</strong><small>Cost {money.format(line.costPrice)} · Price {money.format(line.lineTotal)}</small></div><strong>{money.format(line.lineTotal)}</strong><button type="button" onClick={()=>setLines(current=>current.filter((_,i)=>i!==index))}><Trash2/></button></div>)}<span className="form-section-label">Discounts &amp; tax</span><div className="split-fields"><label>Discount (R)<input type="number" min="0" step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></label><label className="check-label"><input type="checkbox" checked={vat} onChange={e=>setVat(e.target.checked)}/> Add 15% VAT</label></div><section className="invoice-summary"><div><span>Subtotal</span><strong>{money.format(subtotal)}</strong></div><div className="summary-total"><span>Total {kind}</span><strong>{money.format(total)}</strong></div><div><span>Estimated cost</span><strong>{money.format(estimatedCost)}</strong></div></section><button className="primary-button" disabled={!lines.length}>Generate {kind}</button></form></Modal>}</section>
}

function TasksView({bakeryId,items}:{bakeryId:string;items:WorkspaceItem[]}){
  const today=new Date(); const [selected,setSelected]=useState(today.getDate()); const [adding,setAdding]=useState(false);const [filter,setFilter]=useState('all'); const year=today.getFullYear(), month=today.getMonth(); const days=new Date(year,month+1,0).getDate(); const first=new Date(year,month,1).getDay(); const selectedDate=`${year}-${String(month+1).padStart(2,'0')}-${String(selected).padStart(2,'0')}`; const dayItems=items.filter(i=>i.dueDate===selectedDate&&(filter==='all'||i.status===filter));const statusCount=(status:string)=>items.filter(i=>i.status===status).length
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);await addDoc(collection(db,'bakeries',bakeryId,'tasks'),{name:String(data.get('name')),orderRef:String(data.get('orderRef')||''),description:String(data.get('description')||''),dueDate:String(data.get('dueDate')),dueTime:String(data.get('dueTime')||'12:00'),priority:String(data.get('priority')||'medium'),status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});setAdding(false)}
  return <section className="app-section tasks-view"><div className="app-page-title"><div><span className="eyebrow">Bakery planner</span><h2>Tasks &amp; Calendar</h2><p>{items.length} total tasks recorded</p></div><button className="primary-button compact" onClick={()=>setAdding(true)}><Plus/> Add task</button></div><div className="calendar-card"><h3>{today.toLocaleDateString('en-ZA',{month:'long',year:'numeric'})}</h3><div className="weekdays">{['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map(d=><span key={d}>{d}</span>)}</div><div className="calendar-grid">{Array.from({length:first},(_,i)=><span key={`blank-${i}`}/>)}{Array.from({length:days},(_,i)=>i+1).map(day=><button className={selected===day?'selected':''} onClick={()=>setSelected(day)} key={day}>{day}</button>)}</div><div className="calendar-key"><strong>Viewing: {new Date(year,month,selected).toLocaleDateString('en-ZA',{weekday:'long',day:'numeric',month:'short',year:'numeric'})}</strong><span>● Pending &nbsp; <i>● Done</i></span></div></div><div className="task-counts"><article><strong>{statusCount('pending')}</strong><span>Pending</span></article><article><strong>{statusCount('in-progress')}</strong><span>In progress</span></article><article><strong>{statusCount('completed')}</strong><span>Completed</span></article></div><div className="task-filters">{[['all','All'],['pending','Pending'],['in-progress','In progress'],['completed','Completed']].map(([key,label])=><button className={filter===key?'active':''} onClick={()=>setFilter(key)} key={key}>{label}</button>)}</div>{dayItems.length?<div className="task-list">{dayItems.map(item=><article key={item.id}><CalendarDays/><div><strong>{String(item.name)}</strong><p>{String(item.orderRef||'')} {String(item.dueTime||'')} · {String(item.description||'')}</p></div><select value={String(item.status||'pending')} onChange={e=>updateDoc(doc(db,'bakeries',bakeryId,'tasks',item.id),{status:e.target.value,updatedAt:serverTimestamp()})}><option value="pending">Pending</option><option value="in-progress">In progress</option><option value="completed">Completed</option></select></article>)}</div>:<EmptyState title={`No tasks scheduled for ${selected} ${today.toLocaleDateString('en-ZA',{month:'short'})}`} text="Schedule cake baking, pastry batches, decorating, ingredient restocking or client order deliveries." action="Add task for this day" onAction={()=>setAdding(true)}/>} {adding&&<Modal title="Add Bakery Task" onClose={()=>setAdding(false)}><form className="modal-form" onSubmit={save}><label>Task name<input name="name" placeholder="e.g. Bake Red Velvet Cake" required/></label><label>Order reference<input name="orderRef" placeholder="e.g. Order #ORD-1028"/></label><label>Details<textarea name="description" rows={2}/></label><div className="split-fields"><label>Due date<input name="dueDate" type="date" defaultValue={selectedDate} required/></label><label>Due time<input name="dueTime" type="time" defaultValue="12:00"/></label></div><label>Priority<select name="priority" defaultValue="high"><option value="high">High</option><option value="medium">Medium</option><option value="low">Low</option></select></label><button className="primary-button">Add task</button></form></Modal>}</section>
}

function LockedView({ title, onUpgrade }: { title: string; onUpgrade: () => void }) { return <section className="content-card locked"><div className="lock-icon"><Sparkles /></div><span className="eyebrow">BatchBoss Pro</span><h2>Unlock {title}</h2><p>Upgrade to create professional invoices, quotes and receipts, save products and work with unlimited recipes and suppliers.</p><button className="primary-button compact" onClick={onUpgrade}>View Pro plans</button></section> }

function SubscriptionView({ isPro, profile }: { isPro: boolean; profile: UserProfile }) { return <section className="subscription-view"><div className="subscription-intro"><span className="eyebrow">Your plan</span><h2>{isPro ? 'BatchBoss Pro is active' : 'Unlock BatchBoss Pro'}</h2><p>Complete commercial tools for bakers who want to cost smarter and grow.</p></div><div className="feature-list">{['AI recipe and barcode scanning','Unlimited recipes and costing','Professional invoices and payment tracking','Quotes, estimates and customer receipts','Products, suppliers and specials'].map(feature=><div key={feature}><CheckCircle2Icon />{feature}</div>)}</div><div className="plans"><article><span>Save 33%</span><h3>Annual</h3><strong>R1 199</strong><small>per year · about R99/month</small><a className="primary-button" href="https://play.google.com/store/apps/details?id=com.aistudio.batchboss.kqwxrv" target="_blank" rel="noreferrer">Choose annual</a></article><article><span>Flexible</span><h3>Monthly</h3><strong>R149</strong><small>per month</small><a className="primary-button" href="https://play.google.com/store/apps/details?id=com.aistudio.batchboss.kqwxrv" target="_blank" rel="noreferrer">Choose monthly</a></article></div><p className="billing-note">Current status: <strong>{profile.subscriptionStatus || 'free'}</strong>. Purchases are completed securely through Google Play. The website never stores card details.</p></section> }

function CheckCircle2Icon() { return <span className="feature-check">✓</span> }
function ToolsView() { return <section className="content-card"><div className="section-heading"><div><span className="eyebrow">Bakery tools</span><h2>Converter & recipe scaler</h2></div></div><div className="tools-grid"><article><h3>Baking unit converter</h3><p>Ingredient volume weights vary. Use the app converter for ingredient-specific measurements.</p><strong>1 cup cake flour ≈ 125 g</strong></article><article><h3>Batch & recipe scaler</h3><p>Scale a recipe from its original yield to the batch you need.</p><strong>Available in the BatchBoss app</strong></article></div></section> }
function SettingsView({ profile }: { profile: UserProfile }) {
  const [business,setBusiness]=useState<Record<string,unknown>>({}); const [saved,setSaved]=useState(false); const [logoData,setLogoData]=useState(''); const [logoError,setLogoError]=useState('')
  useEffect(()=>onSnapshot(doc(db,'bakeries',profile.bakeryId),snapshot=>setBusiness(snapshot.exists()?snapshot.data():{})),[profile.bakeryId])
  useEffect(()=>setLogoData(String(business.logoDataUrl||'')),[business.logoDataUrl])
  function chooseLogo(file?:File){if(!file)return;setLogoError('');if(file.size>350000){setLogoError('Please choose a logo smaller than 350 KB.');return}const reader=new FileReader();reader.onload=()=>setLogoData(String(reader.result||''));reader.readAsDataURL(file)}
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);await setDoc(doc(db,'bakeries',profile.bakeryId),{name:String(data.get('name')||''),businessName:String(data.get('name')||''),phone:String(data.get('phone')||''),email:String(data.get('email')||''),address:String(data.get('address')||''),bankName:String(data.get('bankName')||''),accountNumber:String(data.get('accountNumber')||''),branchCode:String(data.get('branchCode')||''),vatNumber:String(data.get('vatNumber')||''),hourlyLaborRate:Number(data.get('hourlyLaborRate')||0),logoDataUrl:logoData,logoEmblem:String(data.get('logoEmblem')||'batchboss'),updatedAt:serverTimestamp()},{merge:true});setSaved(true);setTimeout(()=>setSaved(false),2500)}
  return <section className="app-section business-settings"><div className="app-page-title"><div><span className="eyebrow">Bakery identity</span><h2>Business Logo &amp; Info</h2><p>Your business details are used on invoices, quotes and receipts.</p></div></div><form className="business-form" onSubmit={save}><section className="logo-panel">{logoData?<img className="logo-preview" src={logoData} alt="Bakery logo"/>:<div className="brand-mark">B</div>}<div><strong>Bakery Logo</strong><p>{logoData?'Logo ready to save':'No logo uploaded'}</p></div><label className="upload-button">Upload<input type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>chooseLogo(e.target.files?.[0])}/></label></section>{logoError&&<div className="error-message">{logoError}</div>}<div><span className="field-heading">Or select an emblem</span><div className="emblem-row"><label><input type="radio" name="logoEmblem" value="batchboss" defaultChecked/> BatchBoss emblem</label><label><input type="radio" name="logoEmblem" value="toque"/> Chef toque</label><label><input type="radio" name="logoEmblem" value="cake"/> Artisan cake</label></div></div><h3>Bakery &amp; contact information</h3><label>Bakery / Business name<input name="name" defaultValue={String(business.name||business.businessName||'')} required/></label><div className="split-fields"><label>Phone / WhatsApp<input name="phone" defaultValue={String(business.phone||'')}/></label><label>Email<input name="email" type="email" defaultValue={String(business.email||profile.email||'')}/></label></div><label>Bakery physical address<input name="address" defaultValue={String(business.address||'')}/></label><h3>Banking &amp; invoice details</h3><label>Bank name<input name="bankName" defaultValue={String(business.bankName||'')}/></label><div className="split-fields"><label>Account number<input name="accountNumber" defaultValue={String(business.accountNumber||'')}/></label><label>Branch code<input name="branchCode" defaultValue={String(business.branchCode||'')}/></label></div><div className="split-fields"><label>VAT / Registration number<input name="vatNumber" defaultValue={String(business.vatNumber||'')}/></label><label>Hourly labour rate (R)<input name="hourlyLaborRate" type="number" min="0" step=".01" defaultValue={Number(business.hourlyLaborRate||120)}/></label></div>{saved&&<div className="success-note">Business information saved.</div>}<button className="primary-button">Save business information</button></form></section>
}
function EmptyState({ title, text, action, onAction }: { title: string; text: string; action?: string; onAction?: () => void }) { return <div className="empty-state"><div><Sparkles /></div><h3>{title}</h3><p>{text}</p>{action && <button className="primary-button compact" onClick={onAction}><Plus size={17} />{action}</button>}</div> }
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) { return <div className="modal-backdrop"><div className="modal"><div className="modal-title"><h2>{title}</h2><button onClick={onClose}><X /></button></div>{children}</div></div> }

export default App
