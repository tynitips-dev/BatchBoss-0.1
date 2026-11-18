import { createHash } from 'node:crypto'
import { FieldValue, getFirestore } from 'firebase-admin/firestore'
import { HttpsError, onCall } from 'firebase-functions/v2/https'
import {listingCountry, listingRegion, listingBelongsToCountry, sameListingRegion, marketplaceBillingAvailable} from './listingRegion'

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

function listingImage(value: unknown): string {
  if (value == null || value === '') return ''
  if (typeof value !== 'string' || value.length > 65000 ||
      !/^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(value)) {
    throw new HttpsError('invalid-argument', 'Choose a valid listing photo.')
  }
  return value
}

async function draftCollection(uid: string | undefined) {
  if (!uid) throw new HttpsError('unauthenticated', 'Please sign in.')
  const db = getFirestore()
  const profile=(await db.collection('users').doc(uid).get()).data()
  if(!profile||typeof profile.bakeryId!=='string'||!profile.bakeryId||profile.bakeryId.includes('/')){
    throw new HttpsError('failed-precondition','Set up your Baker Portal account first.')
  }
  const bakery=(await db.collection('bakeries').doc(profile.bakeryId).get()).data()
  const region=listingRegion(bakery)
  if(!region)throw new HttpsError('failed-precondition','Verify your bakery country and accounting currency before editing listings.')
  return { drafts:db.collection('listingWorkspaces').doc(uid).collection('drafts'),region }
}

export const getListingRegion=onCall(options,async request=>{
 const {region}=await draftCollection(request.auth?.uid)
 return {...region,publicationEnabled:marketplaceBillingAvailable(region)}
})

export const listListingDrafts = onCall(options, async request => {
  const {drafts,region} = await draftCollection(request.auth?.uid)
  const kind = listingKind(request.data?.kind)
  const result = await drafts.where('kind', '==', kind).limit(100).get()
  return Promise.all(result.docs.map(async doc => {
    const publication=await drafts.parent!.collection('publications').doc(doc.id).get()
    const checkout=kind==='marketplace'?await drafts.parent!.collection('marketplaceCheckouts').doc(doc.id).get():null
    const status=publication.exists?(publication.data()?.status==='sold'?'sold':'published'):checkout?.data()?.status==='paid'?'paid':checkout?.data()?.status==='pending'?'pending':'draft'
    const details=doc.data(),storedRegion=listingRegion(details)
    const { title, area, description, priceCents, logo, photos } = details
    return { id: doc.id, title, area, description, priceCents: priceCents ?? null, logo: logo ?? '', photos: photos ?? [], status, countryCode:storedRegion?.countryCode??null,currency:storedRegion?.currency??null,regionMismatch:!sameListingRegion(details,region) }
  }))
})

export const saveListingDraft = onCall(options, async request => {
  const {drafts,region} = await draftCollection(request.auth?.uid)
  const input = request.data
  const kind = listingKind(input?.kind)
  const id = kind === 'directoryListing' ? 'directory' : input?.id
  if (typeof id !== 'string' || (kind === 'marketplace' && !/^[0-9a-f-]{36}$/.test(id))) {
    throw new HttpsError('invalid-argument', 'Invalid listing ID.')
  }
  const priceCents = kind === 'marketplace' ? input?.priceCents : null
  if (kind === 'marketplace' && (!Number.isInteger(priceCents) || priceCents < 1 || priceCents > 100_000_000)) {
    throw new HttpsError('invalid-argument', 'Enter a valid Marketplace item price in your bakery currency.')
  }
  const photos = input.photos ?? []
  if (!Array.isArray(photos) || photos.length > 3) {
    throw new HttpsError('invalid-argument', 'Choose up to three listing photos.')
  }
  const draft = {
    countryCode:region.countryCode,currency:region.currency,
    logo: kind === 'directoryListing' ? listingImage(input.logo) : '',
    photos: photos.map(listingImage).filter(Boolean),
    kind,
    title: requiredText(input.title, 'Name', 100),
    area: requiredText(input.area, 'Area', 100),
    description: requiredText(input.description, 'Description', 1000),
    priceCents,
    updatedAt: FieldValue.serverTimestamp(),
  }

  const db=getFirestore()
  await db.runTransaction(async t=>{
    const existing=await t.get(drafts.doc(id))
    const published=await t.get(drafts.parent!.collection('publications').doc(id))
    const checkout=await t.get(drafts.parent!.collection('marketplaceCheckouts').doc(id))
    for(const row of [existing,published]){
      if(row.exists&&!sameListingRegion(row.data(),region))throw new HttpsError('failed-precondition','The listing uses a different country or currency. Create a new listing; existing amounts cannot be relabelled.')
    }
    if(published.data()?.status==='sold')throw new HttpsError('failed-precondition','This item has already been sold.')
    if(checkout.data()?.status==='pending')throw new HttpsError('failed-precondition','A listing payment is pending. Resume its checkout or verify payment before editing.')
    t.set(drafts.doc(id),draft)
  })
  return { id }
})

export const publishBakerListing = onCall(options, async request => {
  const uid = request.auth?.uid
  const {drafts,region} = await draftCollection(uid)
  const kind = listingKind(request.data?.kind)
  const id = kind === 'directoryListing' ? 'directory' : request.data?.id
  if (typeof id !== 'string' || (kind === 'marketplace' && !/^[0-9a-f-]{36}$/.test(id))) {
    throw new HttpsError('invalid-argument', 'Invalid listing ID.')
  }

  if(!marketplaceBillingAvailable(region))throw new HttpsError('failed-precondition','Publication in your region is not enabled yet. You can save private drafts.')
  const db = getFirestore()
  const entitlement = db.collection('listingEntitlements').doc(uid!)
  const workspace = drafts.parent!
  const publication = workspace.collection('publications').doc(id)
  const publicId = createHash('sha256').update(JSON.stringify([uid, id])).digest('hex')
  const catalog = db.collection('listingCatalogs').doc(kind).collection('entries').doc(publicId)
  const month = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit',
  }).format(new Date())
  const allowance = workspace.collection('allowances').doc(month)

  await db.runTransaction(async transaction => {
    const [draft, access, existing, usage, checkout] = await Promise.all([
      transaction.get(drafts.doc(id)), transaction.get(entitlement),
      transaction.get(publication), transaction.get(allowance), transaction.get(workspace.collection('marketplaceCheckouts').doc(id)),
    ])
    const details = draft.data()
    if (!details || details.kind !== kind) throw new HttpsError('not-found', 'Save this draft before publishing.')
    if(!sameListingRegion(details,region)||existing.exists&&!sameListingRegion(existing.data(),region))throw new HttpsError('failed-precondition','Listing currency differs from your bakery. Do not relabel existing prices.')
    if(existing.data()?.status==='sold')throw new HttpsError('failed-precondition','Sold listings cannot be republished. Create a new listing for a different item.')
    if(kind==='marketplace'&&checkout.data()?.status==='pending')throw new HttpsError('failed-precondition','A payment is pending. Complete or verify it before publishing.')
    const paid = access.data()
    const now = Date.now()
    const suiteActive = (paid?.fullSuiteExpiresAt?.toMillis?.() ?? 0) > now
    const directoryActive = (paid?.directoryListingExpiresAt?.toMillis?.() ?? 0) > now
    const standalonePaid = kind === 'marketplace' && (checkout.data()?.status === 'paid' && checkout.data()?.domain === 'live' || existing.data()?.standalonePaid === true)
    if (kind === 'directoryListing' ? !suiteActive && !directoryActive : !suiteActive && !standalonePaid) {
      throw new HttpsError('failed-precondition', 'An active listing plan is required.')
    }

    if (kind === 'marketplace' && !existing.exists && !standalonePaid) {
      const used = usage.data()?.used ?? 0
      if (used >= 3) throw new HttpsError('resource-exhausted', 'You have used all three included Marketplace listings for this month.')
      transaction.set(allowance, { used: used + 1 })
    }

    const fields = {
      kind, countryCode:region.countryCode,currency:region.currency,title: details.title, area: details.area, description: details.description,
      priceCents: details.priceCents, logo: details.logo ?? '', photos: details.photos ?? [], sellerType: 'baker', standalonePaid, status: 'published', updatedAt: FieldValue.serverTimestamp(),
    }
    transaction.set(publication, fields)
    transaction.set(catalog, { ...fields, ownerUid: uid })

  })
  return { id }
})

export const markMarketplaceSold=onCall(options,async request=>{
 const uid=request.auth?.uid,{drafts}=await draftCollection(uid),id=request.data?.id
 if(typeof id!=='string'||!/^[0-9a-f-]{36}$/.test(id))throw new HttpsError('invalid-argument','Invalid listing ID.')
 const db=getFirestore(),publication=drafts.parent!.collection('publications').doc(id)
 const publicId=createHash('sha256').update(JSON.stringify([uid,id])).digest('hex')
 const catalog=db.doc(`listingCatalogs/marketplace/entries/${publicId}`)
 await db.runTransaction(async t=>{
  const existing=await t.get(publication)
  if(existing.data()?.kind!=='marketplace')throw new HttpsError('not-found','Published listing not found.')
  t.update(publication,{status:'sold',updatedAt:FieldValue.serverTimestamp()})
  t.delete(catalog)
 })
 return {id,status:'sold'}
})

export const listPublishedBakerListings = onCall({
  ...options, cors: ['https://batchboss.co.za', 'https://www.batchboss.co.za'],
}, async request => {
  const kind = listingKind(request.data?.kind)
  const country=listingCountry(request.data?.countryCode===undefined?'ZA':request.data.countryCode)
  if(!country)throw new HttpsError('invalid-argument','Choose a supported country.')
  const cursor = request.data?.cursor
  if (cursor != null && (typeof cursor !== 'string' || !/^[0-9a-f]{64}$/.test(cursor))) {
    throw new HttpsError('invalid-argument', 'Invalid page cursor.')
  }
  const db = getFirestore()
  const page = db.collection('listingCatalogs').doc(kind).collection('entries').orderBy('__name__').limit(25)
  const snapshots = await (cursor ? page.startAfter(cursor) : page).get()
  const nextCursor=snapshots.size===25?snapshots.docs.at(-1)!.id:null
  const rows=snapshots.docs.filter(doc=>{const row=doc.data();return listingBelongsToCountry(row,country)&&(!row.status||row.status==='published')&&typeof row.ownerUid==='string'&&!!row.ownerUid&&!row.ownerUid.includes('/')})
  const owners = [...new Set(rows.map(doc => doc.data().ownerUid as string))]
  if (!owners.length) return { listings: [], nextCursor }
  const reads = await db.getAll(...owners.flatMap(uid => [
    db.collection('users').doc(uid), db.collection('listingEntitlements').doc(uid),
  ]))
  const now = Date.now()
  const eligible = new Set(owners.filter((_, index) => {
    const access = reads[index * 2 + 1].data()
    return reads[index * 2].exists && ((access?.fullSuiteExpiresAt?.toMillis?.() ?? 0) > now
      || (kind === 'directoryListing' && (access?.directoryListingExpiresAt?.toMillis?.() ?? 0) > now))
  }))
  const listings = rows.flatMap(doc => {
    const { ownerUid, title, area, description, priceCents, logo, photos } = doc.data()
    const region=listingRegion(doc.data())!
    if(kind==='marketplace'&&(!Number.isSafeInteger(priceCents)||priceCents<1||priceCents>100_000_000))return []
    return (eligible.has(ownerUid) || (kind === 'marketplace' && doc.data().standalonePaid === true && reads[owners.indexOf(ownerUid) * 2]?.exists)) ? [{ title, area, description, priceCents:kind==='marketplace'?priceCents:null, logo, photos,countryCode:region.countryCode,currency:region.currency }] : []
  })
  return { listings, nextCursor }
})
