import { FieldValue, getFirestore } from 'firebase-admin/firestore'
import { HttpsError, onCall } from 'firebase-functions/v2/https'

const options = { region: 'europe-west1', invoker: 'public' }
type Kind = 'directoryListing' | 'marketplace'

function listingKind(value: unknown): Kind {
  if (value === 'directoryListing' || value === 'marketplace') return value
  throw new HttpsError('invalid-argument', 'Choose a listing type.')
}

function requiredText(value: unknown, label: string, limit: number) {
  if (typeof value !== 'string' || !value.trim() || value.trim().length > limit) {
    throw new HttpsError('invalid-argument', `${label} must contain 1 to ${limit} characters.`)
  }
  return value.trim()
}

async function draftCollection(uid: string | undefined) {
  if (!uid) throw new HttpsError('unauthenticated', 'Please sign in.')
  const db = getFirestore()
  if (!(await db.collection('users').doc(uid).get()).exists) {
    throw new HttpsError('failed-precondition', 'Set up your Baker Portal account first.')
  }
  return db.collection('listingWorkspaces').doc(uid).collection('drafts')
}

export const listListingDrafts = onCall(options, async request => {
  const drafts = await draftCollection(request.auth?.uid)
  const kind = listingKind(request.data?.kind)
  const result = await drafts.where('kind', '==', kind).limit(100).get()
  return result.docs.map(doc => {
    const { title, area, description, priceCents } = doc.data()
    return { id: doc.id, title, area, description, priceCents: priceCents ?? null }
  })
})

export const saveListingDraft = onCall(options, async request => {
  const drafts = await draftCollection(request.auth?.uid)
  const input = request.data
  const kind = listingKind(input?.kind)
  const id = kind === 'directoryListing' ? 'directory' : input?.id
  if (typeof id !== 'string' || (kind === 'marketplace' && !/^[0-9a-f-]{36}$/.test(id))) {
    throw new HttpsError('invalid-argument', 'Invalid listing ID.')
  }
  const priceCents = kind === 'marketplace' ? input?.priceCents : null
  if (kind === 'marketplace' && (!Number.isInteger(priceCents) || priceCents < 1 || priceCents > 100_000_000)) {
    throw new HttpsError('invalid-argument', 'Enter a valid Marketplace item price in rand.')
  }
  const draft = {
    kind,
    title: requiredText(input.title, 'Name', 100),
    area: requiredText(input.area, 'Area', 100),
    description: requiredText(input.description, 'Description', 1000),
    priceCents,
    updatedAt: FieldValue.serverTimestamp(),
  }

  await drafts.doc(id).set(draft)
  return { id }
})

export const publishBakerListing = onCall(options, async request => {
  const uid = request.auth?.uid
  const drafts = await draftCollection(uid)
  const kind = listingKind(request.data?.kind)
  const id = kind === 'directoryListing' ? 'directory' : request.data?.id
  if (typeof id !== 'string' || (kind === 'marketplace' && !/^[0-9a-f-]{36}$/.test(id))) {
    throw new HttpsError('invalid-argument', 'Invalid listing ID.')
  }

  const db = getFirestore()
  const entitlement = db.collection('listingEntitlements').doc(uid!)
  const workspace = drafts.parent!
  const publication = workspace.collection('publications').doc(id)
  const month = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit',
  }).format(new Date())
  const allowance = workspace.collection('allowances').doc(month)

  await db.runTransaction(async transaction => {
    const [draft, access, existing, usage] = await Promise.all([
      transaction.get(drafts.doc(id)), transaction.get(entitlement),
      transaction.get(publication), transaction.get(allowance),
    ])
    const details = draft.data()
    if (!details || details.kind !== kind) throw new HttpsError('not-found', 'Save this draft before publishing.')
    const paid = access.data()
    const now = Date.now()
    const suiteActive = (paid?.fullSuiteExpiresAt?.toMillis?.() ?? 0) > now
    const directoryActive = (paid?.directoryListingExpiresAt?.toMillis?.() ?? 0) > now
    if (kind === 'directoryListing' ? !suiteActive && !directoryActive : !suiteActive) {
      throw new HttpsError('failed-precondition', 'An active listing plan is required.')
    }

    if (kind === 'marketplace' && !existing.exists) {
      const used = usage.data()?.used ?? 0
      if (used >= 3) throw new HttpsError('resource-exhausted', 'You have used all three included Marketplace listings for this month.')
      transaction.set(allowance, { used: used + 1 })
    }

    transaction.set(publication, {
      kind, title: details.title, area: details.area, description: details.description,
      priceCents: details.priceCents, sellerType: 'baker', updatedAt: FieldValue.serverTimestamp(),
    })
  })
  return { id }
})
