import test from 'node:test'
import assert from 'node:assert/strict'
import { directorySupplierDocumentId, savedSupplierRecord } from '../web/src/supplierRecord.ts'
const supplier = { id:'12345678-1234-4234-8234-123456789abc', businessName:'Example Packaging', category:'Packaging', city:'Cape Town', province:'Western Cape', description:'Boxes and cake boards', delivery:'Collection', uid:'private-account-id', adminNotes:'Private review' }
test('public listing creates the same stable bakery document key on both platforms', () => {
  assert.equal(directorySupplierDocumentId(supplier.id), 'directory_12345678-1234-4234-8234-123456789abc')
  assert.equal(directorySupplierDocumentId(supplier.id), directorySupplierDocumentId(supplier.id))
})
test('account IDs and path injection cannot become a public supplier save key', () => {
  for(const id of ['private-account-id','../other-bakery','abc/def','',supplier.id.toUpperCase()]) assert.throws(() => directorySupplierDocumentId(id))
})
test('saved record contains interoperable supplier fields and excludes private input', () => {
  const record=savedSupplierRecord(supplier)
  assert.equal(record.name,supplier.businessName)
  assert.equal(record.about,record.description)
  assert.equal(record.categories,'Packaging')
  assert.equal(record.publicListingId,supplier.id)
  assert.equal(record.city,'Cape Town')
  assert.equal(record.province,'Western Cape')
  assert.equal(record.isMySupplier,true)
  assert.equal(record.rating,0)
  assert.equal(record.reviewCount,0)
  assert.equal(record.openingHours,'')
  assert.equal(Object.hasOwn(record,'uid'),false)
  assert.equal(Object.hasOwn(record,'adminNotes'),false)
})
