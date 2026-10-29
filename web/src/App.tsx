import SupplierDirectory, { type Supplier as PublicSupplier } from './SupplierDirectory'
import { directorySupplierDocumentId, savedSupplierRecord } from './supplierRecord'
import { type ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import {
  BookOpen, Boxes, CalendarDays, ChevronRight, ClipboardList,
  Download, Edit3, Eye, FileCheck2, Gauge, LogOut, Mail, Menu, Package, Plus, Receipt,
  ReceiptText, Search, Settings, Share2, Sparkles, Store, Trash2, Users, WandSparkles, X,
} from 'lucide-react'
import {
  createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword,
  signOut, type User,
} from 'firebase/auth'
import { addDoc, collection, deleteDoc, doc, getDocs, getDocsFromServer, onSnapshot, serverTimestamp, setDoc, runTransaction, updateDoc, writeBatch } from 'firebase/firestore'
import { auth, db, firebaseConfigured, referralPilot, bakerFunctions } from './firebase'
import { BakeryTools, ReportsView, RecordsView, StockCards, ProfileName, money, displayName, isImportHeading } from './WorkspaceExtras'
import { httpsCallable } from 'firebase/functions'
import SubscriptionView from './SubscriptionView'
import SetupGuide from './SetupGuide'
import AnnouncementBanner from './AnnouncementBanner'
import { subscriptionAccess } from './subscriptionAccess'
import SupplierAdBanner from './SupplierAdBanner'
import type { ModuleKey, UserProfile } from './types'
import { calculateIngredientUsageCost } from './costing'
import { priceRecipe } from './recipe-pricing'
import { applianceElectricityCost, allocatedOverhead } from './recipe-expenses'
import { extractRecipePdf } from './recipePdfImport'
import { ClientFields, RecipePhoto, RecipeReader, CustomerHistory } from './WorkflowFeatures'
import { pricedQuantity } from './workflow-utils'
import * as XLSX from 'xlsx'

type ModuleDefinition = { key: ModuleKey; label: string; icon: typeof BookOpen; collection?: string; pro?: boolean }
const modules: ModuleDefinition[] = [
  { key: 'home', label: 'Home', icon: Gauge },
  { key: 'recipes', label: 'Recipes', icon: BookOpen, collection: 'recipes' },
  { key: 'ingredients', label: 'Ingredients', icon: Package, collection: 'inventory' },
  { key: 'inventory', label: 'Inventory', icon: Boxes, collection: 'inventory' },
  { key: 'packaging', label: 'Packaging', icon: Package, collection: 'packaging' },
  { key: 'supplierDirectory', label: 'Find suppliers', icon: Store },
  { key: 'suppliers', label: 'Suppliers', icon: Store, collection: 'suppliers', pro: true },
  { key: 'invoices', label: 'Invoices', icon: ReceiptText, collection: 'invoices', pro: true },
  { key: 'quotes', label: 'Quotes & estimates', icon: FileCheck2, collection: 'quotes', pro: true },
  { key: 'receipts', label: 'Receipts', icon: Receipt, collection: 'receipts', pro: true },
  { key: 'products', label: 'Products & services', icon: Package, collection: 'products', pro: true },
  { key: 'tasks', label: 'Tasks & calendar', icon: CalendarDays, collection: 'tasks' },
  { key: 'customers', label: 'Customers', icon: Users, collection: 'customers' },
  { key: 'orders', label: 'Orders', icon: ClipboardList, collection: 'orders' },
  { key: 'reports', label: 'Reports', icon: Gauge },
  { key: 'documents', label: 'Documents & templates', icon: Download },
  { key: 'tools', label: 'Bakery tools', icon: WandSparkles },
  { key: 'subscription', label: 'Subscription', icon: Sparkles },
  { key: 'settings', label: 'Business settings', icon: Settings },
]
const dataModules = modules.filter(module => module.collection)



function App() {
  const [user, setUser] = useState<User | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [authFlow, setAuthFlow] = useState({ started: false, busy: false })

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
  if (loading && !authFlow.started) return <div className="centre-screen"><div className="loader" /><p>Opening your bakery…</p></div>
  if (!user || !profile || authFlow.busy) return <AuthScreen onBusyChange={busy => setAuthFlow({ started: true, busy })} />
  return <Dashboard key={user.uid} profile={profile} />
}

function SetupNotice() {
  return <main className="centre-screen setup-card">
    <div className="brand-mark">B</div>
    <h1>Connect the BatchBoss website</h1>
    <p>The website foundation is ready. Copy <code>.env.example</code> to <code>.env</code> and add the Firebase web-app details.</p>
  </main>
}

function AuthScreen({ onBusyChange }: { onBusyChange: (busy: boolean) => void }) {
  const [mode, setMode] = useState<'login' | 'signup'>(() => new URLSearchParams(location.search).has('signup') || (referralPilot && new URLSearchParams(location.search).has('ref')) ? 'signup' : 'login')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    onBusyChange(true)
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
        const referralCode = referralPilot ? String(data.get('referralCode') || '').trim().toUpperCase() : ''
        if (referralCode && !/^BB-[A-F0-9]{12}$/.test(referralCode)) throw new Error('Please check the referral code.')
        const credential = referralPilot && referralCode && auth.currentUser?.email?.toLowerCase() === email.toLowerCase()
          ? { user: auth.currentUser }
          : await createUserWithEmailAndPassword(auth, email, password)
        if (referralCode) {
          await httpsCallable(bakerFunctions, 'registerReferredBakerTest')({ firstName, surname, bakeryName, referralCode })
          return
        }
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
      setError(message.includes('invalid-credential') ? 'Email or password not recognised. Please try again.' : message.replace('Firebase: ', ''))
    } finally { setBusy(false); onBusyChange(false) }
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
        {mode === 'signup' && referralPilot && <label>Sales partner referral (test)<input name="referralCode"
          defaultValue={new URLSearchParams(location.search).get('ref') || ''}
          pattern="BB-[A-Fa-f0-9]{12}" maxLength={15} /></label>}
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
  const [supplierCategory,setSupplierCategory]=useState('')
  const [active, setActive] = useState<ModuleKey>(new URLSearchParams(location.search).has('payment') ? 'subscription' : 'home')
  const [items, setItems] = useState<Record<string, WorkspaceItem[]>>({})
  const [showEditor, setShowEditor] = useState(false)
  const [editing, setEditing] = useState<WorkspaceItem | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [showImporter, setShowImporter] = useState(false)
  const [viewRecipe,setViewRecipe]=useState<WorkspaceItem|null>(null)
  const [inventoryFolder,setInventoryFolder]=useState<'ingredients'|'packaging'|'products'>('ingredients')

  const [syncing, setSyncing] = useState(false)
  const [syncMessage, setSyncMessage] = useState('')
  async function syncNow() {
    setSyncing(true); setSyncMessage('')
    try {
      const results = await Promise.all(dataModules.map(async m => { const snap = await getDocsFromServer(collection(db, 'bakeries', profile.bakeryId, m.collection!)); return [m.key, snap.docs.map(d => ({...d.data(), localId:d.data().id, id:d.id}))] }));
      setItems(Object.fromEntries(results)); setSyncMessage('Bakery data refreshed from the cloud. Open Sync in the Android app to refresh that device too.')
    } catch { setSyncMessage('Could not reach the cloud. Check your connection and try again.') } finally { setSyncing(false) }
  }
  const subscriptionExpiry = (profile.subscriptionExpiresAt as { toDate?: () => Date } | undefined)?.toDate?.()
  const isPro = subscriptionAccess(profile.subscriptionStatus,profile.subscriptionPlan,profile.subscriptionSource,subscriptionExpiry?.getTime())
  const counts = Object.fromEntries(dataModules.map(module => [module.key, items[module.key]?.filter(i=>!i.archived).length || 0])) as Record<ModuleKey, number>
  const selectedModule = modules.find(module => module.key === active) || modules[0]
  const selectedItems = items[active] || []

  useEffect(() => {
    const cleanups = dataModules.map(({ key, collection: collectionName }) => onSnapshot(
      collection(db, 'bakeries', profile.bakeryId, collectionName!),
      snapshot => {
        setItems(current => ({ ...current, [key]: snapshot.docs.map(item => {
          const data = item.data()
          return { ...data, localId: data.id, id: item.id }
        }) }))
      },
      () => setSyncMessage('Some bakery data could not sync. Check your connection or account permissions, then try Sync now.'),
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
      const recipeRecordId = Number(editing?.localId || Date.now())
      const recipeRef = editing
        ? doc(db, 'bakeries', profile.bakeryId, 'recipes', editing.id)
        : doc(db, 'bakeries', profile.bakeryId, 'recipes', String(recipeRecordId))
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
        id: recipeRecordId, bakeryId: profile.bakeryId,
        name: value('name'), category: value('category') || 'Cakes', description: value('description'),
        servings: number('servings', 1), batchSize: number('batchSize', 1), difficulty: value('difficulty') || 'Medium',
        laborHours, laborRatePerHour, labourCost,
        overheadsCost: number('overheadsCost'), utilitiesCost: number('utilitiesCost'), packagingCost: number('packagingCost'),
        profitMarginPercent: number('profitMarginPercent', 40), ingredientCost,
        photoDataUrl: value('photoDataUrl'), customSellingPrice: number('customSellingPrice'), instructions: value('instructions'),
        updatedAt: serverTimestamp(), ...(editing ? {} : { createdAt: serverTimestamp() }),
      }, { merge: true })
      ingredientNames.forEach((name, index) => {
        if (!name.trim()) return
        const ingredientId = Date.now() + index
        const ingredientRef = doc(recipeRef, 'ingredients', String(ingredientId))
        batch.set(ingredientRef, {
          id: ingredientId, recipeId: recipeRecordId, name: name.trim(),
          quantity: ingredientQuantities[index] || 0, unit: ingredientUnits[index] || 'g',
          cost: ingredientCosts[index] || 0,
        })
      })
      await batch.commit()
      setShowEditor(false)
      setEditing(null)
      return
    }

    if (active === 'packaging') {
      const packagePrice = number('packagePrice')
      const packageQuantity = number('packageQuantity', 1)
      const recordId = Number(editing?.localId || Date.now())
      const payload = {
        id: recordId, bakeryId: profile.bakeryId,
        name: value('name'), category: value('category') || 'Other', unit: value('unit') || 'pcs',
        packagePrice, packageQuantity, gramsPerUnit: number('gramsPerUnit'),
        unitPrice: packageQuantity > 0 ? packagePrice / packageQuantity : 0,
        currentStock: number('currentStock'), minStock: number('minStock'),
        isLowStock: number('currentStock') <= number('minStock'), alertEnabled: true,
        barcode: value('barcode'), supplier: value('supplier'), notes: value('notes'),
        updatedAt: Date.now(),
      }
      if (editing) await updateDoc(doc(db, 'bakeries', profile.bakeryId, collectionName, editing.id), payload)
      else await setDoc(doc(db, 'bakeries', profile.bakeryId, collectionName, String(recordId)), { ...payload, createdAt: Date.now() })
      setShowEditor(false)
      setEditing(null)
      return
    }

    if (active === 'ingredients' || active === 'inventory') {
      const packagePrice = number('packagePrice')
      const packageQuantity = number('packageQuantity', 1)
      const unit = value('unit') || 'g'
      const baseQuantity = unit === 'kg' || unit === 'L' ? packageQuantity * 1000 : packageQuantity
      const recordId = Number(editing?.localId || Date.now())
      const payload = {
        id: recordId, bakeryId: profile.bakeryId,
        name: value('name'), category: value('category') || 'Baking Staples', unit,
        packagePrice, packageQuantity, gramsPerUnit: baseQuantity,
        unitPrice: baseQuantity > 0 ? packagePrice / baseQuantity : 0,
        currentStock: number('currentStock'), minStock: number('minStock'),
        isLowStock: number('currentStock') <= number('minStock'), alertEnabled: true,
        barcode: value('barcode'), updatedAt: serverTimestamp(),
      }
      if (editing) await updateDoc(doc(db, 'bakeries', profile.bakeryId, collectionName, editing.id), payload)
      else await setDoc(doc(db, 'bakeries', profile.bakeryId, collectionName, String(recordId)), { ...payload, createdAt: serverTimestamp() })
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
    if (!selectedModule.collection || !window.confirm(`Permanently delete ${item.name || item.title || 'this item'}? This cannot be undone.`)) return
    try {
      // Recipe ingredient lines are stored as subcollection documents and must be
      // removed explicitly; Firestore does not cascade deletion to subcollections.
      if (selectedModule.collection === 'recipes') {
        const lines = await getDocs(collection(db, 'bakeries', profile.bakeryId, 'recipes', item.id, 'ingredients'))
        for (const ingredientLine of lines.docs) await deleteDoc(ingredientLine.ref)
      }
      await deleteDoc(doc(db, 'bakeries', profile.bakeryId, selectedModule.collection, item.id))
    } catch {
      window.alert('This item could not be deleted. Please retry or contact support.')
    }
  }

  const [resettingBakeryData, setResettingBakeryData] = useState(false)
  async function resetBakeryCostingData() {
    if (profile.role !== 'owner' || resettingBakeryData) return
    if (!window.confirm('Permanently delete ALL recipes, their ingredient lines, stock/ingredients and packaging from YOUR bakery? Orders, invoices, customers, account and subscription will be preserved. This cannot be undone.')) return
    if (window.prompt('Type DELETE to confirm permanently clearing your bakery recipes, ingredients and packaging:') !== 'DELETE') return
    setResettingBakeryData(true)
    try {
      const paths = ['recipes', 'inventory', 'packaging'] as const
      const documents: Array<ReturnType<typeof doc>> = []
      for (const path of paths) {
        const snapshot = await getDocs(collection(db, 'bakeries', profile.bakeryId, path))
        for (const record of snapshot.docs) {
          if (path === 'recipes') {
            const lines = await getDocs(collection(db, 'bakeries', profile.bakeryId, 'recipes', record.id, 'ingredients'))
            for (const line of lines.docs) documents.push(line.ref)
          }
          documents.push(record.ref)
        }
      }
      for (let index = 0; index < documents.length; index += 400) {
        const batch = writeBatch(db)
        for (const reference of documents.slice(index, index + 400)) batch.delete(reference)
        await batch.commit()
      }
      window.alert('Bakery recipes, ingredients, and packaging were permanently cleared.')
    } catch {
      window.alert('Reset did not fully complete. Some records may already be deleted. Please check your lists before trying again.')
    } finally {
      setResettingBakeryData(false)
    }
  }

  const savedDirectoryIds = (items.suppliers || []).map(item => String(item.publicListingId || '')).filter(Boolean)
  async function saveDirectorySupplier(supplier: PublicSupplier) {
    if (!isPro) { setActive('subscription'); return }
    if (savedDirectoryIds.includes(supplier.id)) return
    const target = doc(db, 'bakeries', profile.bakeryId, 'suppliers', directorySupplierDocumentId(supplier.id))
    await runTransaction(db, async transaction => {
      if ((await transaction.get(target)).exists()) return
      transaction.set(target, {
        ...savedSupplierRecord(supplier), createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
      })
    })
  }

  return <div className="dashboard-shell">
    <aside className={menuOpen ? 'sidebar open' : 'sidebar'}>
      <div className="brand"><span className="brand-mark">B</span><span>Batch<strong>Boss</strong></span></div>
      <button className="close-menu" onClick={() => setMenuOpen(false)}><X /></button>
      <nav>
        {modules.map(({ key, label, icon: Icon, collection: collectionName, pro }) => <button key={key} className={active === key ? 'nav-item active' : 'nav-item'} onClick={() => { setActive(key); setMenuOpen(false) }}><Icon />{label}{pro && !isPro ? <span>PRO</span> : collectionName ? <span>{counts[key] || 0}</span> : null}</button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="profile-avatar">{displayName(profile).slice(0, 1)}</div>
        <div><strong>{displayName(profile)}</strong><small>{profile.role}</small></div>
        <button title="Sign out" onClick={() => signOut(auth)}><LogOut /></button>
      </div>
    </aside>
    <main className="dashboard-main">
      <header><button className="menu-button" onClick={() => setMenuOpen(true)}><Menu /></button><div><span>{greeting},</span><h1>{displayName(profile)} 👋</h1></div><span className={isPro ? 'plan-badge pro' : 'plan-badge'}><Sparkles size={15} />{isPro ? 'BatchBoss Pro' : 'Free plan'}</span></header>
      <AnnouncementBanner />
      {['home','suppliers','supplierDirectory'].includes(active)&&<SupplierAdBanner/>}
      <div className="sync-bar"><button className="outline-button" disabled={syncing} onClick={syncNow}>{syncing ? 'Syncing…' : 'Sync now'}</button><span role="status">{syncMessage || 'Your bakery data syncs automatically while connected.'}</span></div>
      <SetupGuide profile={profile} active={active} suspended={showEditor || showImporter} onOpen={key => { setActive(key); setMenuOpen(false) }} />
      {active === 'inventory' && <section className="content-card"><h2>Inventory</h2><p>Choose a stock folder. Finished products can include bakes stored for later sale.</p><div className="inventory-folders">{(['ingredients','packaging','products'] as const).map(folder=><button key={folder} className={inventoryFolder===folder?'active':''} onClick={()=>setInventoryFolder(folder)}><strong>{folder==='products'?'Finished products':folder==='ingredients'?'Ingredients':'Packaging'}</strong><span>{(items[folder]||[]).filter(i=>!i.archived).length} records</span></button>)}</div>{inventoryFolder==='products'?(isPro?<ProductsView stockMode bakeryId={profile.bakeryId} items={items.products||[]}/>:<LockedView title="Finished products" onUpgrade={()=>setActive('subscription')}/>):<><button className="outline-button" onClick={()=>setActive(inventoryFolder)}>Manage {inventoryFolder}</button><StockCards key={inventoryFolder} items={items[inventoryFolder]||[]} onEdit={item=>{setActive(inventoryFolder);setEditing(item);setShowEditor(true)}} bakeryId={profile.bakeryId} collectionName={inventoryFolder==='ingredients'?'inventory':'packaging'}/></>}</section>}
      {active === 'supplierDirectory' && <SupplierDirectory initialCategory={supplierCategory} savedIds={savedDirectoryIds} onSave={saveDirectorySupplier} />}
      {active === 'reports' && <ReportsView items={items} />}
      {['orders','receipts'].includes(active) && (active !== 'receipts' || isPro) && <RecordsView kind={active as 'orders'|'receipts'} bakeryId={profile.bakeryId} items={selectedItems} invoices={items.invoices || []} customers={items.customers || []} />}
      {active === 'home' && <HomeView invoices={items.invoices || []} counts={counts} onOpen={setActive} />}
      {active === 'subscription' && <SubscriptionView isPro={isPro} profile={profile} />}
      {active === 'tools' && <BakeryTools bakeryId={profile.bakeryId} recipes={items.recipes || []} />}
      {active === 'documents' && <DocumentsView onOpen={setActive} />}
      {active === 'settings' && <SettingsView profile={profile} />}
      {active === 'settings' && profile.role === 'owner' && <section className="content-card" aria-label="Reset bakery costing data">
        <h2>Reset bakery costing data</h2>
        <p>Permanently delete all recipes, their ingredient lines, ingredients/stock and packaging for this bakery only. Your customers, orders, invoices, subscription and login remain unchanged.</p>
        <button className="danger-link" type="button" disabled={resettingBakeryData} onClick={resetBakeryCostingData}>
          <Trash2 size={18} /> {resettingBakeryData ? 'Deleting bakery data…' : 'Reset recipes, ingredients & packaging'}
        </button>
      </section>}
      {active === 'products' && isPro && <ProductsView bakeryId={profile.bakeryId} items={items.products || []} />}
      {active === 'invoices' && isPro && <InvoicesView customers={items.customers || []} bakeryId={profile.bakeryId} invoices={items.invoices || []} products={items.products || []} packaging={items.packaging || []} />}
      {active === 'quotes' && isPro && <QuotesView customers={items.customers || []} bakeryId={profile.bakeryId} quotes={items.quotes || []} products={items.products || []} />}
      {active === 'tasks' && <TasksView bakeryId={profile.bakeryId} items={items.tasks || []} />}
      {active === 'customers' && <CustomersView records={items} bakeryId={profile.bakeryId} items={items.customers || []} />}
      {selectedModule.collection && selectedModule.pro && !isPro
        ? <LockedView title={selectedModule.label} onUpgrade={() => setActive('subscription')} />
        : selectedModule.collection && !['inventory','products','invoices','quotes','tasks','customers','orders','receipts'].includes(active) && <section className="content-card">
          <div className="section-heading"><div><span className="eyebrow">Your workspace</span><h2>{selectedModule.label}</h2><p className="section-copy">Changes are saved to the same Firebase bakery workspace used by the app.</p></div><div className="card-actions">{['ingredients','packaging'].includes(active)&&<button className="outline-button" onClick={()=>{setSupplierCategory(active==='packaging'?'Packaging':'Ingredients');setActive('supplierDirectory')}}>Find suppliers</button>}{['ingredients','packaging'].includes(active)&&<button className="outline-button" onClick={()=>setShowImporter(true)}>Upload list</button>}<button className="primary-button compact" onClick={() => openEditor()}><Plus size={18} /> Add {selectedModule.label.replace(/s$/, '').toLowerCase()}</button></div></div>
          {['ingredients','inventory','packaging'].includes(active) ? <StockCards items={selectedItems} onEdit={openEditor} bakeryId={profile.bakeryId} collectionName={selectedModule.collection!} /> : <WorkspaceList onView={active==='recipes'?setViewRecipe:undefined} items={selectedItems} label={selectedModule.label} onAdd={() => openEditor()} onEdit={openEditor} onDelete={removeItem} />}
        </section>}
      {active === 'suppliers' && <SupplierDirectory savedIds={savedDirectoryIds} onSave={saveDirectorySupplier} />}
    </main>
    {viewRecipe && <RecipeReader recipe={viewRecipe} bakeryId={profile.bakeryId} close={()=>setViewRecipe(null)} edit={()=>{openEditor(viewRecipe);setViewRecipe(null)}}/>}
    {showEditor && <ItemEditor module={selectedModule} item={editing} bakeryId={profile.bakeryId} onClose={() => { setShowEditor(false); setEditing(null) }} onSave={saveItem} />}
    {showImporter&&selectedModule.collection&&<BulkListImporter bakeryId={profile.bakeryId} collectionName={selectedModule.collection} label={selectedModule.label} onClose={()=>setShowImporter(false)}/>}
  </div>
}

function DocumentsView({ onOpen }: { onOpen: (key: ModuleKey) => void }) {
  const templates: { key: ModuleKey; title: string; description: string; filename: string; format: string; action: string }[] = [
    { key: 'ingredients', title: 'Ingredients', description: 'Record ingredient pack prices, quantities and units. Import the completed CSV from the Ingredients tab.', filename: 'BatchBoss_Ingredients_Template.csv', format: 'CSV upload template', action: 'Open Ingredients' },
    { key: 'inventory', title: 'Inventory', description: 'Track stock quantities, minimum levels and reorder information. Import the completed CSV from the Inventory tab.', filename: 'BatchBoss_Inventory_Template.csv', format: 'CSV upload template', action: 'Open Inventory' },
    { key: 'recipes', title: 'Recipes', description: 'Plan recipe ingredients, instructions and costing before entering them in BatchBoss. For direct import, use a recipe PDF with selectable text in the recipe editor.', filename: 'BatchBoss_Recipe_Worksheet.txt', format: 'Editable planning worksheet', action: 'Open Recipes' },
    { key: 'packaging', title: 'Packaging', description: 'Keep packaging prices, pack sizes and quantities in a separate list. Import the completed CSV from the Packaging tab.', filename: 'BatchBoss_Packaging_Template.csv', format: 'CSV upload template', action: 'Open Packaging' },
  ]
  return <section className="content-card documents-view">
    <div className="section-heading"><div><span className="eyebrow">Downloads &amp; uploads</span><h2>Documents &amp; Templates</h2><p className="section-copy">Each bakery section has its own document. Download the template you need, then open the matching tab to work with your data.</p></div></div>
    <div className="template-grid">
      {templates.map(template => <article className="template-card" key={template.key}>
        <div className="template-icon"><Download /></div>
        <div><h3>{template.title}</h3><p>{template.description}</p><small>{template.format}</small></div>
        <div className="document-actions"><a className="primary-button compact" href={`/documents/${template.filename}`} download>Download {template.title}</a><button className="outline-button" onClick={() => onOpen(template.key)}>{template.action}</button></div>
      </article>)}
    </div>
    <p className="privacy-note">Ingredient, inventory and packaging CSV files contain column headings only. Fill in your own prices before importing. The recipe worksheet is a planning document, not an upload file. Imported records remain within your bakery account.</p>
  </section>
}

function HomeView({ counts, onOpen, invoices }: { counts: Record<ModuleKey, number>; onOpen: (key: ModuleKey) => void; invoices: WorkspaceItem[] }) {
  return <><section className="hero-card pro-hero"><div><span className="eyebrow">Bakery overview</span><h2>Your bakery at a glance</h2><p>Manage costing, stock, customers and sales from one connected workspace.</p></div><div className="profit"><small>Estimated profit</small><strong>{money.format(invoices.filter(i => i.status === 'paid').reduce((n,i) => n + Number(i.estimatedProfit || 0),0))}</strong><span>Estimated profit on paid invoices</span></div></section><section className="stat-grid">{[
    ['recipes', 'Recipes', BookOpen], ['inventory', 'Stock items', Boxes], ['customers', 'Customers', Users], ['orders', 'Orders', ClipboardList],
  ].map(([key, label, Icon]) => <button className="stat-card stat-button" key={String(key)} onClick={() => onOpen(key as ModuleKey)}><div className="stat-icon"><Icon /></div><span>{String(label)}</span><strong>{counts[key as ModuleKey] || 0}</strong><small>Live total</small></button>)}</section><section className="content-card quick-access"><div className="section-heading"><div><span className="eyebrow">Quick access</span><h2>Keep your bakery moving</h2></div></div><div className="quick-grid">{[['recipes','Recipes'],['ingredients','Ingredients & stock'],['invoices','Invoicing'],['products','Products & services']].map(([key,label])=><button key={key} onClick={()=>onOpen(key as ModuleKey)}><Sparkles/><strong>{label}</strong><ChevronRight/></button>)}</div></section></>
}

function WorkspaceList({ items, label, onAdd, onEdit, onDelete, onView }: { onView?: (item:WorkspaceItem)=>void; items: WorkspaceItem[]; label: string; onAdd: () => void; onEdit: (item: WorkspaceItem) => void; onDelete: (item: WorkspaceItem) => void }) {
  if (!items.length) return <EmptyState title={`No ${label.toLowerCase()} yet`} text={`Add your first ${label.replace(/s$/, '').toLowerCase()} when you are ready. No sample data is added.`} action="Add new" onAction={onAdd} />
  return <div className="workspace-grid">{items.map(item => <article className="workspace-card" key={item.id}><div><span className="item-status">{String(item.status || 'Active')}</span><h3>{onView?<button className="recipe-title-button" onClick={()=>onView(item)}>{String(item.name||'Untitled')}</button>:String(item.name || item.title || 'Untitled')}</h3>{!!item.photoDataUrl&&<img className="recipe-photo" src={String(item.photoDataUrl)} alt={String(item.name||'Recipe')}/>}<p>{String(item.description || item.notes || item.email || item.phone || 'Saved in your BatchBoss workspace')}</p>{typeof item.price === 'number' && item.price > 0 && <strong>{money.format(item.price)}</strong>}</div><div className="card-actions">{onView&&<button onClick={()=>onView(item)}>View recipe</button>}<button onClick={() => onEdit(item)}>Edit</button><button className="danger-link" onClick={() => onDelete(item)}>Delete</button></div></article>)}</div>
}

function ItemEditor({ module, item, bakeryId, onClose, onSave }: { module: ModuleDefinition; item: WorkspaceItem | null; bakeryId: string; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  if (module.key === 'recipes') return <RecipeEditor item={item} bakeryId={bakeryId} onClose={onClose} onSave={onSave} />
  if (module.key === 'packaging') return <PackagingEditor item={item} onClose={onClose} onSave={onSave} />
  if (module.key === 'ingredients' || module.key === 'inventory') return <IngredientEditor item={item} onClose={onClose} onSave={onSave} />
  const commercial = ['invoices','quotes','receipts','products'].includes(module.key)
  return <Modal title={`${item ? 'Edit' : 'Add'} ${module.label.replace(/s$/, '').toLowerCase()}`} onClose={onClose}><form className="modal-form" onSubmit={onSave}><label>Name or reference<input name="name" defaultValue={String(item?.name || item?.title || '')} required /></label><label>Description or notes<textarea name="description" rows={3} defaultValue={String(item?.description || item?.notes || '')} /></label>{module.key === 'customers' || module.key === 'suppliers' ? <div className="split-fields"><label>Phone<input name="phone" type="tel" defaultValue={String(item?.phone || '')} /></label><label>Email<input name="email" type="email" defaultValue={String(item?.email || '')} /></label></div> : null}{commercial && <div className="split-fields"><label>Price<input name="price" type="number" min="0" step="0.01" defaultValue={Number(item?.price || item?.total || 0)} /></label><label>Status<select name="status" defaultValue={String(item?.status || 'draft')}><option value="draft">Draft</option><option value="pending">Pending</option><option value="paid">Paid</option><option value="active">Active</option></select></label></div>}<button className="primary-button">Save changes</button></form></Modal>
}

type IngredientRow = { name: string; quantity: number; unit: string; cost: number }

type ImportRow={name:string;unit:string;price:number;quantity:number;stock:number;minimum:number;category:string;barcode:string}

const normaliseImportHeader = (value: unknown) => String(value ?? '').trim().toLowerCase().replace(/[^a-z0-9]/g, '')
const importNumber = (value: unknown, fallback = 0) => {
  if (typeof value === 'number') return Number.isFinite(value) ? value : fallback
  const cleaned = String(value ?? '').replace(/[^0-9,.-]/g, '').replace(',', '.')
  const parsed = Number(cleaned)
  return Number.isFinite(parsed) ? parsed : fallback
}

function mapImportTable(table: unknown[][]): ImportRow[] {
  const headerIndex = table.findIndex(row => row.some(cell => ['name','ingredient','packagingitem','productname'].includes(normaliseImportHeader(cell))))
  if (headerIndex < 0) return []
  const headers = table[headerIndex].map(normaliseImportHeader)
  const indexOf = (...names: string[]) => headers.findIndex(header => names.includes(header))
  const nameIndex = indexOf('name','ingredient','packagingitem','productname','itemname')
  const unitIndex = indexOf('unit','unitofmeasurement','packageunit','packunit')
  const priceIndex = indexOf('packageprice','packprice','referenceprice','referencepackprice','price','cost')
  const quantityIndex = indexOf('packagequantity','packquantity','quantityinpack','packsize','quantity','weight')
  const stockIndex = indexOf('currentstock','stock','stockquantity','quantityonhand','onhand')
  const minimumIndex = indexOf('minimumstock','minstock','minimumstockalert','reorderlevel','lowstocklevel')
  const categoryIndex = indexOf('category','type')
  const barcodeIndex = indexOf('barcode','sku','code')
  if (nameIndex < 0 || priceIndex < 0 || quantityIndex < 0 || unitIndex < 0) return []
  return table.slice(headerIndex + 1).map(row => ({
    name: String(row[nameIndex] ?? '').trim(),
    unit: String(unitIndex >= 0 ? row[unitIndex] ?? 'g' : 'g').trim() || 'g',
    price: importNumber(priceIndex >= 0 ? row[priceIndex] : 0),
    quantity: importNumber(quantityIndex >= 0 ? row[quantityIndex] : 1, 1),
    stock: importNumber(stockIndex >= 0 ? row[stockIndex] : 0),
    minimum: importNumber(minimumIndex >= 0 ? row[minimumIndex] : 0),
    category: String(categoryIndex >= 0 ? row[categoryIndex] ?? '' : '').trim(),
    barcode: String(barcodeIndex >= 0 ? row[barcodeIndex] ?? '' : '').trim(),
  })).filter(row => row.name && !isImportHeading(row.name) && row.quantity > 0 && row.price >= 0 && row.stock >= 0 && row.minimum >= 0)
}

function BulkListImporter({bakeryId,collectionName,label,onClose}:{bakeryId:string;collectionName:string;label:string;onClose:()=>void}){
 const [rows,setRows]=useState<ImportRow[]>([]);const [error,setError]=useState('');const [saving,setSaving]=useState(false)
 async function read(file:File){
  setError('');setRows([])
  try{
   let table:unknown[][]=[]
   if(/\.xlsx?$/i.test(file.name)){
    const workbook=XLSX.read(await file.arrayBuffer(),{type:'array'})
    const preferred=collectionName==='packaging'?'Packaging Upload':'Ingredients Upload'
    const sheetName=workbook.SheetNames.find(name=>name.toLowerCase()===preferred.toLowerCase())||workbook.SheetNames.find(name=>name.toLowerCase().includes(collectionName==='packaging'?'packaging':'ingredient'))||workbook.SheetNames[0]
    table=XLSX.utils.sheet_to_json(workbook.Sheets[sheetName],{header:1,defval:''}) as unknown[][]
   }else{
    const workbook=XLSX.read(await file.text(),{type:'string'}); table=XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]],{header:1,defval:''}) as unknown[][]
   }
   const result=mapImportTable(table);setRows(result);setError(result.length?'':'No valid items found. Use the BatchBoss upload template and keep the column headings.')
  }catch(e){setError(e instanceof Error?e.message:'The selected file could not be read.')}
 }
 async function save(){if(rows.length>450){setError('Import up to 450 rows at a time.');return}setSaving(true);try{const batch=writeBatch(db);const now=Date.now();rows.forEach((x,index)=>{const recordId=now+index;const ref=doc(db,'bakeries',bakeryId,collectionName,String(recordId));const normalisedUnit=x.unit.toLowerCase();const isPackaging=collectionName==='packaging';const base=normalisedUnit==='kg'||normalisedUnit==='l'?x.quantity*1000:x.quantity;batch.set(ref,{id:recordId,bakeryId,name:x.name,unit:x.unit,packagePrice:x.price,packageQuantity:x.quantity,gramsPerUnit:isPackaging?0:base,unitPrice:base>0?x.price/base:0,currentStock:x.stock,minStock:x.minimum,isLowStock:x.stock<=x.minimum,alertEnabled:true,category:x.category||(isPackaging?'Other':'Baking Staples'),barcode:x.barcode,status:'active',createdAt:now,updatedAt:now})});await batch.commit();onClose()}catch(e){setError(e instanceof Error?e.message:'Import failed')}finally{setSaving(false)}}
 return <Modal title={`Upload ${label} list`} onClose={onClose}><div className="modal-form"><p>Upload the BatchBoss Excel workbook, CSV or TXT file. Required headings: Name, Unit, Package Price, Package Quantity, Current Stock and Minimum Stock.</p><input type="file" accept=".xlsx,.xls,.csv,.txt" onChange={e=>{const f=e.target.files?.[0];if(f)void read(f)}}/>{error&&<div className="warning-note">{error}</div>}{rows.length>0&&<><h3>Preview ({rows.length} items)</h3><div className="catalogue-list">{rows.map((x,i)=><div className="customer-card" key={`${x.name}-${i}`}><div><strong>{x.name}</strong><small>{x.quantity} {x.unit} per pack</small></div><div><span>{money.format(x.price)} pack</span><small>Stock: {x.stock} · Minimum: {x.minimum} · Unit cost: {money.format((x.unit.toLowerCase()==='kg'||x.unit.toLowerCase()==='l'?x.quantity*1000:x.quantity)>0?x.price/(x.unit.toLowerCase()==='kg'||x.unit.toLowerCase()==='l'?x.quantity*1000:x.quantity):0)}</small></div></div>)}</div><button type="button" className="primary-button" disabled={saving} onClick={save}>{saving?'Importing…':`Confirm and import ${rows.length} items`}</button></>}</div></Modal>
}

function RecipeEditor({ item, bakeryId, onClose, onSave }: { item: WorkspaceItem | null; bakeryId: string; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const [photoBusy,setPhotoBusy]=useState(false)
  const [rows, setRows] = useState<IngredientRow[]>([{ name: '', quantity: 0, unit: 'g', cost: 0 }])
  const [importName, setImportName] = useState<string | null>(null)
  const [importInstructions, setImportInstructions] = useState<string | null>(null)
  const [importError, setImportError] = useState('')
  const [importBusy, setImportBusy] = useState(false)
  const [needsImportedCostRefresh, setNeedsImportedCostRefresh] = useState(false)
  async function importPdf(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setImportBusy(true)
    setImportError('')
    try {
      const imported = await extractRecipePdf(file)
      if (!window.confirm(`Import ${imported.ingredients.length} ingredient lines from ${file.name}? Review every quantity and cost before saving.`)) return
      setRows(imported.ingredients.map(row => ({ ...row, cost: calculateIngredientUsageCost(row, inventory) ?? 0 })))
      setNeedsImportedCostRefresh(true)
      setImportName(imported.name)
      setImportInstructions(imported.instructions)
    } catch (error) {
      setImportError(error instanceof Error ? error.message : 'Could not read this PDF.')
    } finally {
      setImportBusy(false)
      event.target.value = ''
    }
  }
  const [inventory, setInventory] = useState<WorkspaceItem[]>([])
  const [laborHours, setLaborHours] = useState(Number(item?.laborHours || 1.5))
  const [laborRate, setLaborRate] = useState(Number(item?.laborRatePerHour || 120))
  const [overheads, setOverheads] = useState(Number(item?.overheadsCost || 0))
  const [utilities, setUtilities] = useState(Number(item?.utilitiesCost || 0))
  const [packaging, setPackaging] = useState(Number(item?.packagingCost || 0))
  const [batchSize, setBatchSize] = useState(Number(item?.batchSize || 1))
  const [servingsPerUnit, setServingsPerUnit] = useState(Number(item?.servings ?? 1))
  const [profitMargin, setProfitMargin] = useState(Number(item?.profitMarginPercent ?? 40))
  const [applianceWatts,setApplianceWatts]=useState(2000)
  const [applianceHours,setApplianceHours]=useState(1)
  const [electricityRate,setElectricityRate]=useState(0)
  const [dutyPercent,setDutyPercent]=useState(100)
  const [waterCost,setWaterCost]=useState(0)
  const [monthlyOverheads,setMonthlyOverheads]=useState(0)
  const [monthlyProductionHours,setMonthlyProductionHours]=useState(0)
  const [batchProductionHours,setBatchProductionHours]=useState(2)
  const defaultsLoaded=useRef(false)
  useEffect(()=>onSnapshot(doc(db,'bakeries',bakeryId),snapshot=>{
    if(!snapshot.exists()||defaultsLoaded.current)return
    const info=snapshot.data()
    setElectricityRate(Number(info.electricityRatePerKwh ?? 0))
    setMonthlyOverheads(Number(info.monthlyOverheads ?? 0))
    setMonthlyProductionHours(Number(info.monthlyProductionHours ?? 0))
    defaultsLoaded.current=true
  }),[bakeryId])

  const electricityEstimate=applianceElectricityCost(applianceWatts,applianceHours,electricityRate,dutyPercent)
  const utilitiesEstimate=electricityEstimate+Math.max(0,waterCost)
  const overheadEstimate=allocatedOverhead(monthlyOverheads,monthlyProductionHours,batchProductionHours)


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

  useEffect(() => {
    if (!needsImportedCostRefresh || !inventory.length) return
    setRows(current => current.map(row => ({...row, cost: calculateIngredientUsageCost(row, inventory) ?? 0})))
    setNeedsImportedCostRefresh(false)
  }, [inventory, needsImportedCostRefresh])

  const ingredientsCost = rows.reduce((sum, row) => sum + Number(row.cost || 0), 0)
  const pricing = priceRecipe({ingredients:ingredientsCost,labourHours:laborHours,labourRate:laborRate,overheads,utilities,packaging,yieldCount:batchSize,servingsPerUnit,marginPercent:profitMargin})
  const {labour:labourCost,total:totalBatchCost,perUnit:costPerUnit}=pricing
  const updateRow = (index: number, patch: Partial<IngredientRow>) =>
    setRows(current => current.map((row, rowIndex) => {
      if (rowIndex !== index) return row
      const updatedRow = { ...row, ...patch }
      const shouldCalculate = 'name' in patch || 'quantity' in patch || 'unit' in patch
      if (!shouldCalculate) return updatedRow
      const calculatedCost = calculateIngredientUsageCost(updatedRow, inventory)
      return { ...updatedRow, cost: calculatedCost ?? 0 }
    }))

  return <Modal title={`${item ? 'Edit' : 'Create new'} recipe`} onClose={onClose}><form className="modal-form recipe-form" onSubmit={onSave}>
    <RecipePhoto initial={item?.photoDataUrl} onBusy={setPhotoBusy}/><section className="content-card">
      <h3>Import recipe from PDF</h3>
      <p>Upload a text-based PDF (up to 10 MB, 30 pages). Review all extracted values before saving. Scanned PDFs are not supported yet.</p>
      <label>Choose recipe PDF<input type="file" accept=".pdf,application/pdf" onChange={importPdf} disabled={importBusy} /></label>
      {importBusy && <p role="status">Reading recipe PDF…</p>}
      {importError && <p role="alert">{importError}</p>}
      {importName !== null && <p role="status">Recipe imported for review. Confirm ingredient names, quantities, prices and instructions before saving.</p>}{importName !== null && <p className="warning-note">Costs display as R0.00 when ingredients have no saved inventory price or require a mass-to-volume conversion. Check each ingredient, especially cups, before saving.</p>}
    </section>
    <label>Recipe name<input name="name" key={importName ?? "original"} defaultValue={importName ?? String(item?.name || '')} placeholder="e.g. Vanilla Cupcakes" required /></label>
    <label>Category<select name="category" defaultValue={String(item?.category || 'Cupcakes')}><option>Cupcakes</option><option>Cakes</option><option>Cookies</option><option>Breads</option><option>Pastries</option><option>Other</option></select></label>
    <label>Description<textarea name="description" rows={3} defaultValue={String(item?.description || '')} /></label>
    <label>Method / instructions<textarea name="instructions" rows={5} key={importInstructions ?? "original"} defaultValue={importInstructions ?? String(item?.instructions || '')} placeholder="Preparation steps and baking instructions" /></label>
    <div className="split-fields"><label>Servings<input name="servings" type="number" min="1" value={servingsPerUnit} onChange={event => setServingsPerUnit(Number(event.target.value))} /></label><label>Batch size<input name="batchSize" type="number" min="1" value={batchSize} onChange={event => setBatchSize(Number(event.target.value))} /></label></div>
    <section className="costing-panel"><div className="mini-heading"><div><h3>Operations, Overheads & Labour</h3><p>Configure complete batch-costing details.</p></div><span>Fully editable</span></div><div className="cost-grid"><label>Labour hours<input name="laborHours" type="number" min="0" step="0.25" value={laborHours} onChange={event => setLaborHours(Number(event.target.value))} /></label><label>Rate/hour<input name="laborRatePerHour" type="number" min="0" step="0.01" value={laborRate} onChange={event => setLaborRate(Number(event.target.value))} /></label><label>Overheads cost<input name="overheadsCost" type="number" min="0" step="0.01" value={overheads} onChange={event => setOverheads(Number(event.target.value))} /></label><label>Utilities cost<input name="utilitiesCost" type="number" min="0" step="0.01" value={utilities} onChange={event => setUtilities(Number(event.target.value))} /></label><label>Packaging cost<input name="packagingCost" type="number" min="0" step="0.01" value={packaging} onChange={event => setPackaging(Number(event.target.value))} /></label><label>Profit margin %<input name="profitMarginPercent" type="number" min="0" max="99.9" step="0.1" value={profitMargin} onChange={event=>setProfitMargin(Number(event.target.value))} /></label></div><p className="calculated-line">Calculated labour cost <strong>{money.format(labourCost)}</strong></p></section>
    <section className="recipe-ingredients"><div className="mini-heading"><div><h3>Recipe ingredients</h3><p>Use g, kg, ml, L, tsp or tablespoon. Equivalent saved ingredients, such as cake flour and flour, can match automatically.</p></div><strong>Total: {money.format(ingredientsCost)}</strong></div><datalist id="inventory-options">{inventory.map(stock => <option key={stock.id} value={String(stock.name || '')} />)}</datalist>{rows.map((row, index) => <div className="ingredient-row" key={index}><input aria-label="Ingredient name" name="ingredientName" list="inventory-options" placeholder="Ingredient" value={row.name} onChange={event => updateRow(index, { name: event.target.value })} /><input aria-label="Quantity" name="ingredientQuantity" type="number" min="0" step="0.01" value={row.quantity} onChange={event => updateRow(index, { quantity: Number(event.target.value) })} /><select aria-label="Unit" name="ingredientUnit" value={row.unit} onChange={event => updateRow(index, { unit: event.target.value })}><option>g</option><option>kg</option><option>ml</option><option>L</option><option value="tsp">tsp (teaspoon)</option><option value="tbsp">tablespoon</option><option>cup</option><option>unit</option></select><input aria-label="Ingredient cost" name="ingredientCost" type="number" min="0" step="0.01" value={row.cost} onChange={event => updateRow(index, { cost: Number(event.target.value) })} /><button type="button" aria-label="Remove ingredient" onClick={() => setRows(current => current.filter((_, rowIndex) => rowIndex !== index))}>×</button></div>)}<button className="outline-button" type="button" onClick={() => setRows(current => [...current, { name: '', quantity: 0, unit: 'g', cost: 0 }])}><Plus size={17} /> Add ingredient</button></section>
    <details className="content-card"><summary><strong>Calculate utilities & overheads</strong> · electricity, water and shared business costs</summary>
      <p>Use estimates from your appliance label and bills. Apply the result to the recipe using the buttons below. Your existing saved amounts are not changed until you apply and save.</p>
      <h4>Electricity & water per batch</h4>
      <div className="cost-grid">
        <label>Appliance power (watts)<input type="number" min="0" step="1" value={applianceWatts} onChange={e=>setApplianceWatts(Number(e.target.value))}/></label>
        <label>Hours used<input type="number" min="0" step="0.1" value={applianceHours} onChange={e=>setApplianceHours(Number(e.target.value))}/></label>
        <label>Electricity tariff (R/kWh)<input type="number" min="0" step="0.01" value={electricityRate} onChange={e=>setElectricityRate(Number(e.target.value))}/></label>
        <label>Average power usage (%)<input type="number" min="0" max="100" step="1" value={dutyPercent} onChange={e=>setDutyPercent(Number(e.target.value))}/></label>
        <label>Water/other direct utility cost (R)<input type="number" min="0" step="0.01" value={waterCost} onChange={e=>setWaterCost(Number(e.target.value))}/></label>
      </div>
      <p><strong>Estimated utilities: {money.format(utilitiesEstimate)} per batch</strong></p>
      <button className="outline-button" type="button" onClick={()=>setUtilities(Number(utilitiesEstimate.toFixed(2)))}>Use this utilities amount</button>
      <h4>Monthly business overheads</h4>
      <p>Add up rent, insurance, admin, equipment depreciation and other indirect expenses per month. Do not include electricity already calculated above, ingredients, packaging or direct labour.</p>
      <div className="cost-grid">
        <label>Monthly indirect expenses (R)<input type="number" min="0" step="0.01" value={monthlyOverheads} onChange={e=>setMonthlyOverheads(Number(e.target.value))}/></label>
        <label>Monthly productive baking hours<input type="number" min="0.01" step="0.1" value={monthlyProductionHours} onChange={e=>setMonthlyProductionHours(Number(e.target.value))}/></label>
        <label>Hours allocated to this batch<input type="number" min="0" step="0.1" value={batchProductionHours} onChange={e=>setBatchProductionHours(Number(e.target.value))}/></label>
      </div>
      <p><strong>Estimated overheads: {overheadEstimate===null?'Enter monthly hours above zero':money.format(overheadEstimate)} per batch</strong></p>
      <button className="outline-button" type="button" disabled={overheadEstimate===null} onClick={()=>{if(overheadEstimate!==null)setOverheads(Number(overheadEstimate.toFixed(2)))}}>Use this overhead amount</button>
      <p className="setup-hint">Estimates cover one batch. If multiple recipes share the oven simultaneously, allocate electricity between batches to avoid double-counting.</p>
    </details>
    <section className="cost-summary"><div><span>Ingredients cost</span><strong>{money.format(ingredientsCost)}</strong></div><div><span>Operations & labour</span><strong>{money.format(labourCost)}</strong></div><div><span>Overheads</span><strong>{money.format(overheads)}</strong></div><div><span>Utilities</span><strong>{money.format(utilities)}</strong></div><div><span>Packaging</span><strong>{money.format(packaging)}</strong></div><div className="summary-total"><span>Total batch cost</span><strong>{money.format(totalBatchCost)}</strong></div><div><span>Cost per unit ({batchSize} units)</span><strong>{costPerUnit===null?'Enter batch size':money.format(costPerUnit)}</strong></div><div><span>Cost per serving ({servingsPerUnit} servings per unit)</span><strong>{pricing.perServing===null?'Enter servings above zero':money.format(pricing.perServing)}</strong></div><div><span>Suggested selling price per unit ({profitMargin}% margin)</span><strong>{pricing.sellingUnit===null?'Check batch size and margin':money.format(pricing.sellingUnit)}</strong></div><div><span>Suggested selling price per batch</span><strong>{pricing.sellingBatch===null?'Check margin':money.format(pricing.sellingBatch)}</strong></div><small>Gross margin means profit as a percentage of selling price. Suggested prices are estimates before rounding and taxes.</small></section>
    <button className="primary-button" disabled={photoBusy}>{photoBusy?'Preparing photo…':'Save recipe'}</button>
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

const packagingCategories = ['Cake boxes','Cupcake boxes','Bento boxes','Cake boards','Ribbon','Stickers and labels','Bags','Containers','Other']

function PackagingEditor({ item, onClose, onSave }: { item: WorkspaceItem | null; onClose: () => void; onSave: (event: FormEvent<HTMLFormElement>) => void }) {
  const [packagePrice, setPackagePrice] = useState(Number(item?.packagePrice || 0))
  const [packageQuantity, setPackageQuantity] = useState(Number(item?.packageQuantity || 1))
  const unitPrice = packageQuantity > 0 ? packagePrice / packageQuantity : 0
  return <Modal title={`${item ? 'Edit' : 'Add'} packaging item`} onClose={onClose}><form className="modal-form ingredient-form" onSubmit={onSave}>
    <label>Packaging item name<input name="name" defaultValue={String(item?.name || '')} placeholder="e.g. 6-inch cake box" required /></label>
    <label>Category<select name="category" defaultValue={String(item?.category || 'Cake boxes')}>{packagingCategories.map(category => <option key={category}>{category}</option>)}</select></label>
    <label>Unit<select name="unit" defaultValue={String(item?.unit || 'pcs')}><option value="pcs">Pieces</option><option value="boxes">Boxes</option><option value="packs">Packs</option><option value="rolls">Rolls</option><option value="m">Metres</option><option value="sheets">Sheets</option></select></label>
    <div className="split-fields"><label>Package price (R)<input name="packagePrice" type="number" min="0" step="0.01" value={packagePrice} onChange={event => setPackagePrice(Number(event.target.value))} /></label><label>Units in package<input name="packageQuantity" type="number" min="0.01" step="0.01" value={packageQuantity} onChange={event => setPackageQuantity(Number(event.target.value))} /></label></div>
    <div className="calculated-cost"><span>Calculated cost per unit</span><strong>{money.format(unitPrice)}</strong></div>
    <div className="split-fields"><label>Current stock<input name="currentStock" type="number" min="0" step="0.01" defaultValue={Number(item?.currentStock || 0)} /></label><label>Minimum stock alert<input name="minStock" type="number" min="0" step="0.01" defaultValue={Number(item?.minStock || 0)} /></label></div>
    <label>Supplier<input name="supplier" defaultValue={String(item?.supplier || '')} placeholder="Optional supplier name" /></label>
    <label>Barcode or SKU<input name="barcode" defaultValue={String(item?.barcode || '')} /></label>
    <label>Notes<textarea name="notes" rows={3} defaultValue={String(item?.notes || '')} placeholder="Dimensions, colour or intended use" /></label>
    <button className="primary-button">{item ? 'Save packaging' : 'Add packaging'}</button>
  </form></Modal>
}

function ProductsView({ bakeryId, items, stockMode=false }: { bakeryId: string; items: WorkspaceItem[]; stockMode?:boolean }) {
  const [editor, setEditor] = useState<WorkspaceItem | null | undefined>()
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [activeOnly,setActiveOnly] = useState(false)
  const filtered = items.filter(item => (!stockMode || item.kind!=='service') && (!activeOnly || item.status !== 'inactive') && (category === 'All' || String(item.category||'Uncategorised') === category) && `${item.name || ''} ${item.sku || ''} ${item.category || ''}`.toLowerCase().includes(query.toLowerCase()))
  async function remove(item: WorkspaceItem) {
    if (window.confirm(`Delete ${item.name || 'this product'}?`)) await deleteDoc(doc(db, 'bakeries', bakeryId, 'products', item.id))
  }
  return <section className="app-section products-view">
    <div className="app-page-title"><div><span className="eyebrow">Master catalogue</span><h2>{stockMode?'Finished product stock':'Products & Services'}</h2></div><button className="primary-button compact" onClick={() => setEditor(null)}><Plus size={18}/> Add Product/Service</button></div>
    <label className="catalogue-search"><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search by name, SKU, category…" /></label>
    <div className="filter-chips">{['All',...Array.from(new Set(items.map(i=>String(i.category||'Uncategorised')))).sort()].map(value=><button className={category===value?'active':''} onClick={()=>setCategory(value)} key={value}>{value}</button>)}</div>
    <div className="catalogue-meta"><strong>{filtered.length} item{filtered.length===1?'':'s'} in master catalogue</strong><label className="check-label"><input type="checkbox" checked={activeOnly} onChange={e=>setActiveOnly(e.target.checked)}/> Active only</label></div>
    {filtered.length ? <div className="catalogue-list">{filtered.map(item=>{
      const cost=Number(item.costPrice||0), price=Number(item.sellingPrice||item.price||0), profit=price-cost, margin=price?profit/price*100:0
      return <article className="product-card" key={item.id}><div><span className="category-tag">{String(item.category||'Product')}</span><h3>{String(item.name||'Untitled')}</h3><p>{String(item.sku||'')} · per {String(item.unit||'Each')}</p></div>{stockMode&&<div><strong>{Number(item.currentStock||0)} {String(item.unit||'Each')} available</strong><p>{String(item.storageLocation||'Storage not set')}</p>{!!item.bestBefore&&<small>Best before: {String(item.bestBefore)}</small>}</div>}<div className="product-price"><strong>{money.format(price)}</strong><small>per {String(item.unit||'Each')}</small></div><div className="product-profit"><span>Cost: <b>{money.format(cost)}</b></span><span>Profit: <b>{money.format(profit)} ({margin.toFixed(0)}%)</b></span></div><div className="icon-actions"><button title={item.status==='inactive'?'Activate':'Deactivate'} onClick={()=>updateDoc(doc(db,'bakeries',bakeryId,'products',item.id),{status:item.status==='inactive'?'active':'inactive',updatedAt:serverTimestamp()})}>↻</button><button title="Edit" onClick={()=>setEditor(item)}><Edit3/></button><button className="danger-link" title="Delete" onClick={()=>remove(item)}><Trash2/></button></div></article>
    })}</div>:<EmptyState title="No products or services yet" text="Add products once and reuse their prices on invoices and quotes." action="Add product/service" onAction={()=>setEditor(null)}/>}
    {editor !== undefined && <ProductEditor bakeryId={bakeryId} item={editor} onClose={()=>setEditor(undefined)} />}
  </section>
}

function ProductEditor({ bakeryId, item, onClose }: { bakeryId:string; item:WorkspaceItem|null; onClose:()=>void }) {
  const [kind,setKind]=useState(String(item?.kind||'product'))
  const [cost,setCost]=useState(Number(item?.costPrice??0))
  const [price,setPrice]=useState(Number(item?.sellingPrice??item?.price??0))
  const [recipes,setRecipes]=useState<WorkspaceItem[]>([])
  const [recipeId,setRecipeId]=useState(String(item?.recipeId||''))
  const [recipeNotice,setRecipeNotice]=useState('')
  const [recipeError,setRecipeError]=useState('')
  const [productName,setProductName]=useState(String(item?.name||''))
  const [saving,setSaving]=useState(false)
  useEffect(()=>onSnapshot(collection(db,'bakeries',bakeryId,'recipes'),snapshot=>{
    setRecipes(snapshot.docs.map(entry=>({id:entry.id,...entry.data()} as WorkspaceItem)).filter(recipe=>!recipe.archived))
    setRecipeError('')
  },()=>setRecipeError('Could not load saved recipes. Please check your connection.')),[bakeryId])
  const profit=price-cost, margin=price?profit/price*100:0
  function selectRecipe(id:string){
    setRecipeId(id);setRecipeNotice('')
    if(!id)return
    const recipe=recipes.find(entry=>entry.id===id)
    if(!recipe)return
    const results=priceRecipe({
      ingredients:Number(recipe.ingredientCost??0),
      labourHours:Number(recipe.laborHours??0),
      labourRate:Number(recipe.laborRatePerHour??0),
      overheads:Number(recipe.overheadsCost??0),
      utilities:Number(recipe.utilitiesCost??0),
      packaging:Number(recipe.packagingCost??0),
      yieldCount:Number(recipe.batchSize??0),
      servingsPerUnit:Number(recipe.servings??1),
      marginPercent:Number(recipe.profitMarginPercent??40),
    })
    if(results.perUnit===null||results.sellingUnit===null){
      setRecipeNotice('This recipe needs a valid batch size and a profit margin below 100%. Check the recipe costing before importing its price.')
      return
    }
    setCost(Number(results.perUnit.toFixed(2)))
    setPrice(Number(results.sellingUnit.toFixed(2)))
    if(!item)setProductName(String(recipe.name||''))
    setRecipeNotice('Prices copied from the selected recipe per finished unit. You can adjust either price before saving. Re-select a recipe to refresh its prices later.')
  }
  async function save(event:FormEvent<HTMLFormElement>){
    event.preventDefault()
    setSaving(true)
    try{
      const data=new FormData(event.currentTarget)
      const payload={currentStock:Number(data.get('currentStock')||0),storageLocation:String(data.get('storageLocation')||''),bestBefore:String(data.get('bestBefore')||''),kind,name:productName.trim(),category:String(data.get('category')||'Cakes'),sku:String(data.get('sku')||''),unit:String(data.get('unit')||'Each'),costPrice:cost,sellingPrice:price,price,recipeId:kind==='product'?recipeId:'',status:String(item?.status||'active'),updatedAt:serverTimestamp()}
      if(item)await updateDoc(doc(db,'bakeries',bakeryId,'products',item.id),payload)
      else await addDoc(collection(db,'bakeries',bakeryId,'products'),{...payload,createdAt:serverTimestamp()})
      onClose()
    }catch{setRecipeError('Could not save this product. Check your connection and try again.')}
    finally{setSaving(false)}
  }
  return <Modal title={`${item?'Edit':'New'} Product / Service`} onClose={onClose}><form className="modal-form product-form" onSubmit={save}>
    <div className="type-toggle"><button type="button" className={kind==='product'?'active':''} onClick={()=>setKind('product')}>Product (Cake, Bakes)</button><button type="button" className={kind==='service'?'active':''} onClick={()=>{setKind('service');setRecipeId('');setRecipeNotice('')}}>Service (Delivery, Decor)</button></div>
    {kind==='product'&&<section className="content-card"><h3>Link a saved recipe</h3><label>Choose recipe<select value={recipeId} onChange={e=>selectRecipe(e.target.value)}><option value="">No recipe selected (enter prices manually)</option>{recipes.map(recipe=><option key={recipe.id} value={recipe.id}>{String(recipe.name||'Untitled recipe')}</option>)}</select></label><small>Selecting a recipe copies its calculated cost per unit and suggested selling price. Prices are saved with this product; later recipe edits do not silently overwrite them.</small>{recipeNotice&&<p role="status">{recipeNotice}</p>}</section>}
    {recipeError&&<p className="error-message" role="alert">{recipeError}</p>}
    <label>Product/Service name<input name="name" value={productName} onChange={e=>setProductName(e.target.value)} required/></label>
    <label>Category<input name="category" list="product-categories" defaultValue={String(item?.category||'Cakes')} required/></label><datalist id="product-categories">{['Cakes','Cupcakes','Cookies','Muffins','Services','Frozen bakes','Other'].map(c=><option key={c}>{c}</option>)}</datalist>
    {kind==='product'&&<section className="content-card"><h3>Finished stock</h3><p>Record bakes available for later sale. Quantities are managed manually.</p><label>Quantity in stock<input name="currentStock" type="number" min="0" step="0.01" defaultValue={Number(item?.currentStock||0)}/></label><label>Storage location<input name="storageLocation" placeholder="Freezer, shelf…" defaultValue={String(item?.storageLocation||'')}/></label><label>Best-before date<input name="bestBefore" type="date" defaultValue={String(item?.bestBefore||'')}/></label></section>}
    <div className="split-fields"><label>SKU / Code<input name="sku" defaultValue={String(item?.sku||'')}/></label><label>Unit<select name="unit" defaultValue={String(item?.unit||'Each')}><option>Each</option><option>Item</option><option>Dozen</option><option>Kg</option><option>Gram</option><option>Box</option></select></label></div>
    <div className="split-fields"><label>Cost price (R)<input type="number" min="0" step=".01" value={cost} onChange={e=>setCost(Number(e.target.value))}/></label><label>Selling price (R)<input type="number" min="0" step=".01" value={price} onChange={e=>setPrice(Number(e.target.value))} required/></label></div>
    <div className="profit-preview"><div><span>Estimated profit</span><strong>{money.format(profit)}</strong></div><div><span>Profit margin</span><strong>{margin.toFixed(1)}%</strong></div></div>
    <button className="primary-button" disabled={saving}>{saving?'Saving…':'Save to Products & Services'}</button>
  </form></Modal>
}

function InvoicesView({ bakeryId, invoices, products, packaging, customers }: { customers:WorkspaceItem[]; bakeryId:string; invoices:WorkspaceItem[]; products:WorkspaceItem[]; packaging:WorkspaceItem[] }) {
  const [creating,setCreating]=useState(false); const [preview,setPreview]=useState<WorkspaceItem|null>(null); const [filter,setFilter]=useState('all'); const [search,setSearch]=useState('')
  const total=invoices.reduce((sum,item)=>sum+Number(item.total||0),0), pending=invoices.filter(i=>i.status!=='paid').reduce((sum,i)=>sum+Number(i.total||0),0), collected=total-pending
  const shown=invoices.filter(item=>(filter==='all'||item.status===filter)&&`${item.invoiceNumber||item.name||''} ${item.clientName||''} ${item.summary||''}`.toLowerCase().includes(search.toLowerCase()))
  async function markPaid(item:WorkspaceItem){const batch=writeBatch(db);batch.update(doc(db,'bakeries',bakeryId,'invoices',item.id),{status:'paid',paidAt:serverTimestamp(),updatedAt:serverTimestamp()});if(item.orderId)batch.set(doc(db,'bakeries',bakeryId,'orders',String(item.orderId)),{status:'paid',paymentStatus:'paid',updatedAt:serverTimestamp()},{merge:true});if(item.taskId)batch.set(doc(db,'bakeries',bakeryId,'tasks',String(item.taskId)),{status:'completed',completedAt:serverTimestamp(),updatedAt:serverTimestamp()},{merge:true});await batch.commit()}
  async function remove(item:WorkspaceItem){if(window.confirm(`Delete ${item.invoiceNumber||'this invoice'}?`))await deleteDoc(doc(db,'bakeries',bakeryId,'invoices',item.id))}
  return <section className="app-section invoice-view"><div className="app-page-title"><div><span className="eyebrow">BatchBoss Pro</span><h2>Invoicing</h2></div><button className="circle-add" onClick={()=>setCreating(true)}><Plus/></button></div><div className="invoice-stats"><article><span>Total billed</span><strong>{money.format(total)}</strong></article><article><span>Pending</span><strong>{money.format(pending)}</strong></article><article><span>Collected</span><strong>{money.format(collected)}</strong></article></div><label className="catalogue-search"><Search/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search client, invoice # or order…"/></label><div className="filter-chips">{['all','pending','paid','overdue'].map(value=><button key={value} className={filter===value?'active':''} onClick={()=>setFilter(value)}>{value[0].toUpperCase()+value.slice(1)}</button>)}</div>{shown.length?<div className="invoice-list">{shown.map(item=><article className="invoice-card" key={item.id}><div><span className="invoice-number">{String(item.invoiceNumber||item.name||'Invoice')}</span><h3>{String(item.clientName||item.description||'Client')}</h3><p>{String(item.summary||'Invoice line items')}</p></div><span className={`status-pill ${item.status==='paid'?'paid':''}`}>{String(item.status||'pending')}</span><strong className="invoice-total">{money.format(Number(item.total||0))}</strong><div className="invoice-actions"><button onClick={()=>setPreview(item)}><Eye/> View</button>{item.status!=='paid'&&<button onClick={()=>markPaid(item)}>✓ Mark paid</button>}<button onClick={()=>navigator.share?.({title:String(item.invoiceNumber||'Invoice'),text:`Invoice total: ${money.format(Number(item.total||0))}`})}><Share2/></button><button className="danger-link" onClick={()=>remove(item)}><Trash2/></button></div></article>)}</div>:<EmptyState title="No matching invoices" text="Create a professional invoice using saved products and services." action="Create invoice" onAction={()=>setCreating(true)}/>} {creating&&<InvoiceEditor customers={customers} bakeryId={bakeryId} products={products} packaging={packaging} invoiceCount={invoices.length} onClose={()=>setCreating(false)}/>} {preview&&<InvoicePreview bakeryId={bakeryId} invoice={preview} onClose={()=>setPreview(null)}/>}</section>
}

type InvoiceLine={productId:string;name:string;quantity:number;unit:string;unitPrice:number;costPrice:number;lineTotal:number;kind?:'product'|'packaging'}
function InvoiceEditor({bakeryId,products,packaging,invoiceCount,onClose,customers}:{customers:WorkspaceItem[];bakeryId:string;products:WorkspaceItem[];packaging:WorkspaceItem[];invoiceCount:number;onClose:()=>void}){
  const [saving,setSaving]=useState(false);const [saveError,setSaveError]=useState('');
  const [productId,setProductId]=useState(products.find(p=>p.status!=='inactive')?.id||''); const [quantity,setQuantity]=useState(1); const [packagingId,setPackagingId]=useState(packaging.find(p=>p.status!=='inactive')?.id||''); const [packagingQuantity,setPackagingQuantity]=useState(1); const [lines,setLines]=useState<InvoiceLine[]>([]); const [discount,setDiscount]=useState(0); const [notes,setNotes]=useState('');
  const product=products.find(p=>p.id===productId); const selectedPackaging=packaging.find(p=>p.id===packagingId); const packagingTotal=lines.filter(line=>line.kind==='packaging').reduce((sum,line)=>sum+line.lineTotal,0); const subtotal=lines.reduce((sum,line)=>sum+line.lineTotal,0); const total=Math.max(0,subtotal-discount); const profit=lines.reduce((sum,line)=>sum+(line.unitPrice-line.costPrice)*line.quantity,0)-discount
  function addLine(){if(!product||!Number.isFinite(quantity)||quantity<=0)return;const unitPrice=Number(product.sellingPrice||product.price||0);setLines(current=>[...current,{productId:product.id,name:String(product.name||'Item'),quantity,unit:String(product.unit||'Each'),unitPrice,costPrice:Number(product.costPrice||0),lineTotal:pricedQuantity(quantity,unitPrice),kind:'product'}]);setQuantity(1)}
  function addPackaging(){if(!selectedPackaging||!Number.isFinite(packagingQuantity)||packagingQuantity<=0)return;const unitPrice=Number(selectedPackaging.unitPrice||selectedPackaging.packagePrice||0);setLines(current=>[...current,{productId:selectedPackaging.id,name:`Packaging: ${String(selectedPackaging.name||'Packaging')}`,quantity:packagingQuantity,unit:String(selectedPackaging.unit||'Item'),unitPrice,costPrice:unitPrice,lineTotal:pricedQuantity(packagingQuantity,unitPrice),kind:'packaging'}]);setPackagingQuantity(1)}
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();if(saving)return;setSaving(true);setSaveError('');try{const data=new FormData(event.currentTarget);if(!lines.length||lines.some(l=>!Number.isFinite(l.quantity)||l.quantity<=0))throw new Error('Check all quantities.');const client=String(data.get('clientName')||'');const phone=String(data.get('phone')||'');const clientDetails={customerId:String(data.get('customerId')||''),clientEmail:String(data.get('clientEmail')||''),clientAddress:String(data.get('clientAddress')||'')};const invoiceNumber=`INV-${String(invoiceCount+1).padStart(4,'0')}`;const dueDate=(document.getElementById('invoice-due') as HTMLInputElement)?.value;const invoiceRef=doc(collection(db,'bakeries',bakeryId,'invoices'));const orderRef=doc(collection(db,'bakeries',bakeryId,'orders'));const taskRef=doc(collection(db,'bakeries',bakeryId,'tasks'));const summary=lines.map(line=>`${line.quantity} × ${line.name}`).join(', ');const batch=writeBatch(db);batch.set(invoiceRef,{name:invoiceNumber,invoiceNumber,clientName:client,phone,...clientDetails,dueDate,items:lines,notes,summary,subtotal,packagingTotal,discount,vat:0,total,estimatedProfit:profit,status:'pending',orderId:orderRef.id,taskId:taskRef.id,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});batch.set(orderRef,{name:`Order for ${client}`,orderNumber:`ORD-${String(invoiceCount+1).padStart(4,'0')}`,invoiceId:invoiceRef.id,invoiceNumber,clientName:client,phone,...clientDetails,dueDate,items:lines,description:summary,packagingTotal,total,status:'pending',paymentStatus:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});batch.set(taskRef,{name:`Prepare order for ${client}`,orderRef:invoiceNumber,invoiceId:invoiceRef.id,orderId:orderRef.id,dueDate,dueTime:'12:00',priority:'high',description:summary,status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});await batch.commit();onClose()}catch{setSaveError('Could not save the invoice. Check your connection and try again.')}finally{setSaving(false)}}
  return <Modal title="Create Invoice" onClose={onClose}><form className="modal-form invoice-form" onSubmit={save}><p className="modal-subtitle">Automatic line items &amp; totals</p><span className="form-section-label">Client &amp; invoice details</span><ClientFields customers={customers}/><label>Due date<input id="invoice-due" type="date" required/></label><span className="form-section-label">Line items ({lines.length})</span>{products.length?<div className="add-line-row"><select value={productId} onChange={e=>setProductId(e.target.value)}>{products.filter(p=>p.status!=='inactive').map(p=><option key={p.id} value={p.id}>{String(p.name)} — {money.format(Number(p.sellingPrice||p.price||0))}</option>)}</select><input aria-label="Quantity" type="number" min="1" value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/><button className="primary-button compact" type="button" onClick={addLine}><Plus/> Add item</button></div>:<div className="warning-note">Add a Product or Service first, then it will be available here.</div>}{lines.map((line,index)=><div className="invoice-line-editor" key={`${line.productId}-${index}`}><div><strong>{line.name}</strong><small>{line.quantity} {line.unit} @ {money.format(line.unitPrice)}</small></div><label className="workflow-quantity">Quantity<input aria-label={`Quantity for ${line.name}`} type="number" required min="0.01" step="0.01" value={line.quantity} onChange={e=>{const q=Number(e.target.value);setLines(current=>current.map((l,i)=>i===index?{...l,quantity:q,lineTotal:Number.isFinite(q)&&q>0?pricedQuantity(q,l.unitPrice):0}:l))}}/></label><strong>{money.format(line.lineTotal)}</strong><button type="button" onClick={()=>setLines(current=>current.filter((_,i)=>i!==index))}><Trash2/></button></div>)}{packaging.length?<><span className="form-section-label">Packaging</span><div className="add-line-row"><select value={packagingId} onChange={e=>setPackagingId(e.target.value)}>{packaging.filter(p=>p.status!=='inactive').map(p=><option key={p.id} value={p.id}>{String(p.name)} — {money.format(Number(p.unitPrice||p.packagePrice||0))}</option>)}</select><input aria-label="Packaging quantity" type="number" min="1" value={packagingQuantity} onChange={e=>setPackagingQuantity(Number(e.target.value))}/><button className="primary-button compact" type="button" onClick={addPackaging}><Plus/> Add packaging</button></div></>:<div className="warning-note">Add items in the Packaging tab to select them here.</div>}<label>Invoice description / notes<textarea rows={2} value={notes} onChange={e=>setNotes(e.target.value)}/></label><section className="invoice-summary"><div><span>Packaging total</span><strong>{money.format(packagingTotal)}</strong></div><div><span>Subtotal</span><strong>{money.format(subtotal)}</strong></div><div><label>Discount (R)</label><input type="number" min="0" step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></div><div><span>VAT tax</span><strong>{money.format(0)}</strong></div><div className="summary-total"><span>Total due</span><strong>{money.format(total)}</strong></div><div className="estimated-profit"><span>Estimated profit</span><strong>{money.format(profit)}</strong></div></section><p role="alert">{saveError}</p><button className="primary-button" disabled={saving||!lines.length||lines.some(l=>!Number.isFinite(l.quantity)||l.quantity<=0)}>Generate invoice</button></form></Modal>
}

function InvoicePreview({bakeryId,invoice,onClose}:{bakeryId:string;invoice:WorkspaceItem;onClose:()=>void}){
  const items=Array.isArray(invoice.items)?invoice.items as Array<Record<string,unknown>>:[]
  const [business,setBusiness]=useState<Record<string,unknown>>({});useEffect(()=>onSnapshot(doc(db,'bakeries',bakeryId),s=>setBusiness(s.data()||{})),[bakeryId]);const message=`${business.name||business.businessName||'BatchBoss'} invoice ${invoice.invoiceNumber||invoice.name||''}\nAmount due: ${money.format(Number(invoice.total||0))}\nThank you for your business.`;const whatsapp=String(invoice.phone||'').replace(/\D/g,'').replace(/^0/,'27')
  return <Modal title="Tax Invoice" onClose={onClose}><div className="invoice-preview"><div className="invoice-brand"><div>{business.logoDataUrl?<img src={String(business.logoDataUrl)} alt="Business logo"/>:<span className="brand-mark">B</span>}<div><strong>{String(business.name||business.businessName||'Your Bakery')}</strong><small>{String(business.email||'')}</small></div></div><h3>Tax Invoice</h3></div><div className="preview-heading"><div><span>Invoice</span><strong>{String(invoice.invoiceNumber||invoice.name||'')}</strong></div><span className="status-pill">{String(invoice.status||'pending')}</span></div><div className="billed"><span>Billed to</span><strong>{String(invoice.clientName||'Client')}</strong><small>{String(invoice.phone||'')}</small><small>{String(invoice.clientEmail||'')}</small><small>{String(invoice.clientAddress||'')}</small></div>{items.map((item,index)=><div className="preview-line" key={index}><span>{String(item.quantity||1)} × {String(item.name||'Item')}</span><strong>{money.format(Number(item.lineTotal||0))}</strong></div>)}<div className="preview-total"><span>Total amount due</span><strong>{money.format(Number(invoice.total||0))}</strong></div><div className="payment-box"><strong>Banking &amp; payment details</strong><span>{String(business.bankName||'Bank details not added')}</span><span>Account: {String(business.accountNumber||'—')} · Branch: {String(business.branchCode||'—')}</span><span>Reference: {String(invoice.invoiceNumber||invoice.name||'Invoice')}</span></div><div className="preview-buttons"><button className="primary-button" onClick={()=>window.print()}><Download/> View / Save PDF</button><button className="outline-button" onClick={()=>navigator.share?.({title:String(invoice.invoiceNumber||'Invoice'),text:message})}><Share2/> Share PDF</button></div>{whatsapp&&<a className="whatsapp-button" href={`https://wa.me/${whatsapp}?text=${encodeURIComponent(message)}`} target="_blank" rel="noreferrer">Send invoice via WhatsApp</a>}<div className="quick-share"><button onClick={()=>navigator.clipboard.writeText(message)}>Copy text</button><button onClick={()=>navigator.share?.({text:message})}>Share text</button></div></div></Modal>
}

function CustomersView({bakeryId,items,records}:{records:Record<string,WorkspaceItem[]>;bakeryId:string;items:WorkspaceItem[]}){
  const [viewing,setViewing]=useState<WorkspaceItem|null>(null);
  const [adding,setAdding]=useState(false);const [editing,setEditing]=useState<WorkspaceItem|null>(null);const [search,setSearch]=useState('');const shown=items.filter(item=>`${item.name||''} ${item.phone||''} ${item.email||''}`.toLowerCase().includes(search.toLowerCase()));const totalOrders=items.reduce((sum,item)=>sum+Number(item.totalOrders||0),0);const totalSpend=items.reduce((sum,item)=>sum+Number(item.totalSpend||0),0)
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);const payload={name:String(data.get('name')||''),phone:String(data.get('phone')||''),email:String(data.get('email')||''),address:String(data.get('address')||''),preferences:String(data.get('preferences')||''),status:'active',updatedAt:serverTimestamp()};if(editing)await updateDoc(doc(db,'bakeries',bakeryId,'customers',editing.id),payload);else await addDoc(collection(db,'bakeries',bakeryId,'customers'),{...payload,totalOrders:0,totalSpend:0,createdAt:serverTimestamp()});setAdding(false);setEditing(null)}
  return <section className="app-section customers-view"><div className="app-page-title"><div><span className="eyebrow">Customer records</span><h2>Customer Directory</h2></div><button className="primary-button compact" onClick={()=>setAdding(true)}><Plus/> Add customer</button></div><div className="invoice-stats"><article><span>Total customers</span><strong>{items.length}</strong></article><article><span>Total orders</span><strong>{totalOrders}</strong></article><article><span>Total spend</span><strong>{money.format(totalSpend)}</strong></article></div><label className="catalogue-search"><Search/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search customers by name, phone or email…"/></label>{shown.length?<div className="catalogue-list">{shown.map(item=><article className="customer-card" key={item.id}><div><span className="profile-avatar">{String(item.name||'C').slice(0,1).toUpperCase()}</span><div><h3><button className="recipe-title-button" onClick={()=>setViewing(item)}>{String(item.name||'Customer')}</button></h3><p>{String(item.phone||'No phone')} · {String(item.email||'No email')}</p><small>{String(item.address||'')}</small></div></div><div className="card-actions"><button onClick={()=>setViewing(item)}>View history</button><button onClick={()=>setEditing(item)}>Edit</button><button className="danger-link" onClick={()=>window.confirm('Delete this customer?')&&deleteDoc(doc(db,'bakeries',bakeryId,'customers',item.id))}>Delete</button></div></article>)}</div>:<EmptyState title="No customers yet" text="Add your first customer and keep their contact and order details together." action="Add customer" onAction={()=>setAdding(true)}/>} {viewing&&<CustomerHistory customer={viewing} customers={items} records={records} close={()=>setViewing(null)}/>} {(adding||editing)&&<Modal title={editing?'Edit Customer':'Add New Customer'} onClose={()=>{setAdding(false);setEditing(null)}}><form className="modal-form" onSubmit={save}><label>Customer name<input name="name" defaultValue={String(editing?.name||'')} required/></label><label>Phone number<input name="phone" type="tel" defaultValue={String(editing?.phone||'')}/></label><label>Email address<input name="email" type="email" defaultValue={String(editing?.email||'')}/></label><label>Delivery address / city<input name="address" defaultValue={String(editing?.address||'')}/></label><label>Preferences &amp; allergies<textarea name="preferences" rows={3} defaultValue={String(editing?.preferences||'')}/></label><button className="primary-button">Save customer</button></form></Modal>}</section>
}

function QuotesView({bakeryId,quotes,products,customers}:{customers:WorkspaceItem[];bakeryId:string;quotes:WorkspaceItem[];products:WorkspaceItem[]}){
  const [saving,setSaving]=useState(false);const [saveError,setSaveError]=useState('');
  const [quantity,setQuantity]=useState(1);const [customQuantity,setCustomQuantity]=useState(1);
  const [adding,setAdding]=useState(false);const [kind,setKind]=useState('quote');const [productId,setProductId]=useState(products[0]?.id||'');const [lines,setLines]=useState<InvoiceLine[]>([]);const [customName,setCustomName]=useState('');const [customCost,setCustomCost]=useState(0);const [customPrice,setCustomPrice]=useState(0);const [discount,setDiscount]=useState(0);const [vat,setVat]=useState(false);const subtotal=lines.reduce((sum,line)=>sum+line.lineTotal,0);const vatAmount=vat?Math.max(0,subtotal-discount)*.15:0;const total=Math.max(0,subtotal-discount)+vatAmount;const estimatedCost=lines.reduce((sum,line)=>sum+line.costPrice*line.quantity,0)
  function addCatalog(){if(!Number.isFinite(quantity)||quantity<=0)return;const product=products.find(p=>p.id===productId);if(!product)return;const unitPrice=Number(product.sellingPrice||product.price||0);setLines(current=>[...current,{productId:product.id,name:String(product.name||'Item'),quantity,unit:String(product.unit||'Each'),unitPrice,costPrice:Number(product.costPrice||0),lineTotal:pricedQuantity(quantity,unitPrice)}]);setQuantity(1)}
  function addCustom(){if(!customName.trim()||!Number.isFinite(customPrice)||customPrice<=0||!Number.isFinite(customQuantity)||customQuantity<=0)return;setLines(current=>[...current,{productId:'custom',name:customName,quantity:customQuantity,unit:'Each',unitPrice:customPrice,costPrice:customCost,lineTotal:pricedQuantity(customQuantity,customPrice)}]);setCustomName('');setCustomCost(0);setCustomPrice(0);setCustomQuantity(1)}
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();if(saving)return;setSaving(true);setSaveError('');try{const data=new FormData(event.currentTarget);if(!lines.length||lines.some(l=>!Number.isFinite(l.quantity)||l.quantity<=0))throw new Error('Check all quantities.');await addDoc(collection(db,'bakeries',bakeryId,'quotes'),{name:`${kind==='quote'?'Quote':'Estimate'} for ${String(data.get('clientName'))}`,type:kind,customerId:String(data.get('customerId')||''),clientEmail:String(data.get('clientEmail')||''),clientAddress:String(data.get('clientAddress')||''),clientName:String(data.get('clientName')),phone:String(data.get('phone')||''),eventType:String(data.get('eventType')||''),eventDate:String(data.get('eventDate')||''),items:lines,description:lines.map(l=>`${l.quantity} × ${l.name}`).join(', '),subtotal,discount,vat:vatAmount,total,estimatedCost,status:'draft',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});setAdding(false);setLines([])}catch{setSaveError('Could not save the quote. Check your connection and try again.')}finally{setSaving(false)}}
  return <section className="app-section quotes-view"><div className="app-page-title"><div><span className="eyebrow">Plan the sale</span><h2>Quotes &amp; Estimates</h2></div><button className="circle-add" onClick={()=>setAdding(true)}><Plus/></button></div>{quotes.length?<div className="workspace-grid">{quotes.map(item=><article className="workspace-card" key={item.id}><div><span className="item-status">{String(item.type||'quote')}</span><h3>{String(item.name||'Quote')}</h3><p>{String(item.description||'')}</p><strong>{money.format(Number(item.total||0))}</strong></div><div className="card-actions"><button onClick={()=>window.print()}>Print</button><button className="danger-link" onClick={()=>window.confirm('Delete this quote?')&&deleteDoc(doc(db,'bakeries',bakeryId,'quotes',item.id))}>Delete</button></div></article>)}</div>:<EmptyState title="No quotes or estimates yet" text="Create a quote using your saved product catalogue or custom line items." action="Create quote" onAction={()=>setAdding(true)}/>} {adding&&<Modal title="New Quote" onClose={()=>setAdding(false)}><form className="modal-form quote-form" onSubmit={save}><div className="type-toggle"><button type="button" className={kind==='quote'?'active':''} onClick={()=>setKind('quote')}>Quote</button><button type="button" className={kind==='estimate'?'active':''} onClick={()=>setKind('estimate')}>Estimate</button></div><span className="form-section-label">Client &amp; event details</span><ClientFields customers={customers}/><div className="split-fields"><label>Event type<input name="eventType" placeholder="Wedding, birthday…"/></label><label>Event date<input name="eventDate" type="date"/></label></div><span className="form-section-label">Line items</span>{products.length>0&&<div className="add-line-row"><select value={productId} onChange={e=>setProductId(e.target.value)}>{products.map(p=><option key={p.id} value={p.id}>{String(p.name)} — {money.format(Number(p.sellingPrice||p.price||0))}</option>)}</select><label className="workflow-quantity">Quantity<input type="number" min="0.01" step="0.01" value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/></label><button className="primary-button compact" type="button" onClick={addCatalog}>From catalogue</button></div>}<div className="custom-line"><input placeholder="Custom item details" value={customName} onChange={e=>setCustomName(e.target.value)}/><input aria-label="Estimated cost" type="number" min="0" step=".01" placeholder="Cost" value={customCost||''} onChange={e=>setCustomCost(Number(e.target.value))}/><input aria-label="Selling price" type="number" min="0" step=".01" placeholder="Price" value={customPrice||''} onChange={e=>setCustomPrice(Number(e.target.value))}/><label className="workflow-quantity">Quantity<input type="number" min="0.01" step="0.01" value={customQuantity} onChange={e=>setCustomQuantity(Number(e.target.value))}/></label><button type="button" onClick={addCustom}>+ Custom</button></div>{lines.map((line,index)=><div className="invoice-line-editor" key={index}><div><strong>{line.name}</strong><small>Cost {money.format(line.costPrice)} · Price {money.format(line.lineTotal)}</small></div><label className="workflow-quantity">Quantity<input aria-label={`Quantity for ${line.name}`} type="number" required min="0.01" step="0.01" value={line.quantity} onChange={e=>{const q=Number(e.target.value);setLines(current=>current.map((l,i)=>i===index?{...l,quantity:q,lineTotal:Number.isFinite(q)&&q>0?pricedQuantity(q,l.unitPrice):0}:l))}}/></label><strong>{money.format(line.lineTotal)}</strong><button type="button" onClick={()=>setLines(current=>current.filter((_,i)=>i!==index))}><Trash2/></button></div>)}<span className="form-section-label">Discounts &amp; tax</span><div className="split-fields"><label>Discount (R)<input type="number" min="0" step=".01" value={discount} onChange={e=>setDiscount(Number(e.target.value))}/></label><label className="check-label"><input type="checkbox" checked={vat} onChange={e=>setVat(e.target.checked)}/> Add 15% VAT</label></div><section className="invoice-summary"><div><span>Subtotal</span><strong>{money.format(subtotal)}</strong></div><div className="summary-total"><span>Total {kind}</span><strong>{money.format(total)}</strong></div><div><span>Estimated cost</span><strong>{money.format(estimatedCost)}</strong></div></section><p role="alert">{saveError}</p><button className="primary-button" disabled={saving||!lines.length||lines.some(l=>!Number.isFinite(l.quantity)||l.quantity<=0)}>Generate {kind}</button></form></Modal>}</section>
}

function TasksView({bakeryId,items}:{bakeryId:string;items:WorkspaceItem[]}){
  const today=new Date(); const [viewMonth,setViewMonth]=useState(new Date(today.getFullYear(),today.getMonth(),1)); const [selected,setSelected]=useState(today.getDate()); const [adding,setAdding]=useState(false);const [filter,setFilter]=useState('all'); const year=viewMonth.getFullYear(), month=viewMonth.getMonth(); const days=new Date(year,month+1,0).getDate(); const first=new Date(year,month,1).getDay(); const selectedDate=`${year}-${String(month+1).padStart(2,'0')}-${String(selected).padStart(2,'0')}`; const dayItems=items.filter(i=>i.dueDate===selectedDate&&(filter==='all'||i.status===filter));const statusCount=(status:string)=>items.filter(i=>i.status===status).length
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);await addDoc(collection(db,'bakeries',bakeryId,'tasks'),{name:String(data.get('name')),orderRef:String(data.get('orderRef')||''),description:String(data.get('description')||''),dueDate:String(data.get('dueDate')),dueTime:String(data.get('dueTime')||'12:00'),priority:String(data.get('priority')||'medium'),status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});setAdding(false)}
  return <section className="app-section tasks-view"><div className="app-page-title"><div><span className="eyebrow">Bakery planner</span><h2>Tasks &amp; Calendar</h2><p>{items.length} total tasks recorded</p></div><button className="primary-button compact" onClick={()=>setAdding(true)}><Plus/> Add task</button></div><div className="calendar-card"><div className="calendar-toolbar"><button className="outline-button" aria-label="Previous month" onClick={()=>{setViewMonth(new Date(year,month-1,1));setSelected(1)}}>←</button><h3>{viewMonth.toLocaleDateString('en-ZA',{month:'long',year:'numeric'})}</h3><button className="outline-button" aria-label="Next month" onClick={()=>{setViewMonth(new Date(year,month+1,1));setSelected(1)}}>→</button><button className="outline-button" onClick={()=>{setViewMonth(new Date(today.getFullYear(),today.getMonth(),1));setSelected(today.getDate())}}>Today</button></div><div className="weekdays">{['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map(d=><span key={d}>{d}</span>)}</div><div className="calendar-grid">{Array.from({length:first},(_,i)=><span key={`blank-${i}`}/>)}{Array.from({length:days},(_,i)=>i+1).map(day=><button className={selected===day?'selected':''} onClick={()=>setSelected(day)} key={day}>{day}{items.some(i=>i.dueDate===`${year}-${String(month+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`)&&<small aria-label="Tasks scheduled">●</small>}</button>)}</div><div className="calendar-key"><strong>Viewing: {new Date(year,month,selected).toLocaleDateString('en-ZA',{weekday:'long',day:'numeric',month:'short',year:'numeric'})}</strong><span>● Pending &nbsp; <i>● Done</i></span></div></div><div className="task-counts"><article><strong>{statusCount('pending')}</strong><span>Pending</span></article><article><strong>{statusCount('in-progress')}</strong><span>In progress</span></article><article><strong>{statusCount('completed')}</strong><span>Completed</span></article></div><div className="task-filters">{[['all','All'],['pending','Pending'],['in-progress','In progress'],['completed','Completed']].map(([key,label])=><button className={filter===key?'active':''} onClick={()=>setFilter(key)} key={key}>{label}</button>)}</div>{dayItems.length?<div className="task-list">{dayItems.map(item=><article key={item.id}><CalendarDays/><div><strong>{String(item.name)}</strong><p>{String(item.orderRef||'')} {String(item.dueTime||'')} · {String(item.description||'')}</p></div><select value={String(item.status||'pending')} onChange={e=>updateDoc(doc(db,'bakeries',bakeryId,'tasks',item.id),{status:e.target.value,updatedAt:serverTimestamp()})}><option value="pending">Pending</option><option value="in-progress">In progress</option><option value="completed">Completed</option></select></article>)}</div>:<EmptyState title={`No tasks scheduled for ${selected} ${viewMonth.toLocaleDateString('en-ZA',{month:'short'})}`} text="Schedule cake baking, pastry batches, decorating, ingredient restocking or client order deliveries." action="Add task for this day" onAction={()=>setAdding(true)}/>} {adding&&<Modal title="Add Bakery Task" onClose={()=>setAdding(false)}><form className="modal-form" onSubmit={save}><label>Task name<input name="name" placeholder="e.g. Bake Red Velvet Cake" required/></label><label>Order reference<input name="orderRef" placeholder="e.g. Order #ORD-1028"/></label><label>Details<textarea name="description" rows={2}/></label><div className="split-fields"><label>Due date<input name="dueDate" type="date" defaultValue={selectedDate} required/></label><label>Due time<input name="dueTime" type="time" defaultValue="12:00"/></label></div><label>Priority<select name="priority" defaultValue="high"><option value="high">High</option><option value="medium">Medium</option><option value="low">Low</option></select></label><button className="primary-button">Add task</button></form></Modal>}</section>
}

function LockedView({ title, onUpgrade }: { title: string; onUpgrade: () => void }) { return <section className="content-card locked"><div className="lock-icon"><Sparkles /></div><span className="eyebrow">BatchBoss Pro</span><h2>Unlock {title}</h2><p>Upgrade to create professional invoices, quotes and receipts, save products and work with unlimited recipes and suppliers.</p><button className="primary-button compact" onClick={onUpgrade}>View Pro plans</button></section> }

function SettingsView({ profile }: { profile: UserProfile }) {
  const [business,setBusiness]=useState<Record<string,unknown>>({}); const [saved,setSaved]=useState(false); const [logoData,setLogoData]=useState(''); const [logoError,setLogoError]=useState('')
  useEffect(()=>onSnapshot(doc(db,'bakeries',profile.bakeryId),snapshot=>setBusiness(snapshot.exists()?snapshot.data():{})),[profile.bakeryId])
  useEffect(()=>setLogoData(String(business.logoDataUrl||'')),[business.logoDataUrl])
  function chooseLogo(file?:File){if(!file)return;setLogoError('');if(file.size>350000){setLogoError('Please choose a logo smaller than 350 KB.');return}const reader=new FileReader();reader.onload=()=>setLogoData(String(reader.result||''));reader.readAsDataURL(file)}
  async function save(event:FormEvent<HTMLFormElement>){event.preventDefault();const data=new FormData(event.currentTarget);await setDoc(doc(db,'bakeries',profile.bakeryId),{name:String(data.get('name')||''),businessName:String(data.get('name')||''),phone:String(data.get('phone')||''),email:String(data.get('email')||''),address:String(data.get('address')||''),bankName:String(data.get('bankName')||''),accountNumber:String(data.get('accountNumber')||''),branchCode:String(data.get('branchCode')||''),vatNumber:String(data.get('vatNumber')||''),hourlyLaborRate:Number(data.get('hourlyLaborRate')||0),electricityRatePerKwh:Number(data.get('electricityRatePerKwh')||0),monthlyOverheads:Number(data.get('monthlyOverheads')||0),monthlyProductionHours:Number(data.get('monthlyProductionHours')||0),logoDataUrl:logoData,logoEmblem:String(data.get('logoEmblem')||'batchboss'),updatedAt:serverTimestamp()},{merge:true});setSaved(true);setTimeout(()=>setSaved(false),2500)}
  return <section className="app-section business-settings"><div className="app-page-title"><div><span className="eyebrow">Bakery identity</span><h2>Business Logo &amp; Info</h2><p>Your business details are used on invoices, quotes and receipts.</p></div></div><ProfileName profile={profile} /><form className="business-form" onSubmit={save}><section className="logo-panel">{logoData?<img className="logo-preview" src={logoData} alt="Bakery logo"/>:<div className="brand-mark">B</div>}<div><strong>Bakery Logo</strong><p>{logoData?'Logo ready to save':'No logo uploaded'}</p></div><label className="upload-button">Upload<input type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>chooseLogo(e.target.files?.[0])}/></label></section>{logoError&&<div className="error-message">{logoError}</div>}<div><span className="field-heading">Or select an emblem</span><div className="emblem-row"><label><input type="radio" name="logoEmblem" value="batchboss" defaultChecked/> BatchBoss emblem</label><label><input type="radio" name="logoEmblem" value="toque"/> Chef toque</label><label><input type="radio" name="logoEmblem" value="cake"/> Artisan cake</label></div></div><h3>Bakery &amp; contact information</h3><label>Bakery / Business name<input name="name" defaultValue={String(business.name||business.businessName||'')} required/></label><div className="split-fields"><label>Phone / WhatsApp<input name="phone" defaultValue={String(business.phone||'')}/></label><label>Email<input name="email" type="email" defaultValue={String(business.email||profile.email||'')}/></label></div><label>Bakery physical address<input name="address" defaultValue={String(business.address||'')}/></label><h3>Banking &amp; invoice details</h3><label>Bank name<input name="bankName" defaultValue={String(business.bankName||'')}/></label><div className="split-fields"><label>Account number<input name="accountNumber" defaultValue={String(business.accountNumber||'')}/></label><label>Branch code<input name="branchCode" defaultValue={String(business.branchCode||'')}/></label></div><div className="split-fields"><label>VAT / Registration number<input name="vatNumber" defaultValue={String(business.vatNumber||'')}/></label><label>Hourly labour rate (R)<input name="hourlyLaborRate" type="number" min="0" step=".01" defaultValue={Number(business.hourlyLaborRate||120)}/></label></div><section className="content-card"><h3>Recipe costing defaults</h3><p>Save your electricity tariff and shared monthly expenses here. New recipe calculators will load these values automatically. Enter your own electricity rate from your bill.</p><div className="cost-grid"><label>Electricity tariff (R per kWh)<input name="electricityRatePerKwh" type="number" min="0" step="0.01" defaultValue={Number(business.electricityRatePerKwh ?? 0)} key={'tariff-'+String(business.electricityRatePerKwh??0)}/></label><label>Monthly indirect overheads (R)<input name="monthlyOverheads" type="number" min="0" step="0.01" defaultValue={Number(business.monthlyOverheads ?? 0)} key={'overheads-'+String(business.monthlyOverheads??0)}/></label><label>Monthly productive baking hours<input name="monthlyProductionHours" type="number" min="0" step="0.1" defaultValue={Number(business.monthlyProductionHours ?? 0)} key={'hours-'+String(business.monthlyProductionHours??0)}/></label></div><small>Do not include ingredients, direct labour, packaging or electricity already calculated per recipe in monthly overheads.</small></section>{saved&&<div className="success-note">Business information saved.</div>}<button className="primary-button">Save business information</button></form></section>
}
function EmptyState({ title, text, action, onAction }: { title: string; text: string; action?: string; onAction?: () => void }) { return <div className="empty-state"><div><Sparkles /></div><h3>{title}</h3><p>{text}</p>{action && <button className="primary-button compact" onClick={onAction}><Plus size={17} />{action}</button>}</div> }
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) { return <div className="modal-backdrop"><div className="modal-frame"><button className="modal-external-close" type="button" aria-label="Close dialog" onClick={onClose}><X size={23} /></button><div className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-title"><h2>{title}</h2></div>{children}</div></div></div> }

export default App
