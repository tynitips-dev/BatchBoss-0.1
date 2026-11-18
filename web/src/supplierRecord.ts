import type { Supplier } from './SupplierDirectory'
const publicId = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
export function directorySupplierDocumentId(id: string) {
  if (!publicId.test(id)) throw new Error('Invalid public supplier identifier')
  return 'directory_' + id
}
export function savedSupplierRecord(supplier: Supplier) {
  directorySupplierDocumentId(supplier.id)
  const countryCode=supplier.countryCode===undefined?'ZA':supplier.countryCode
  if(!supplierCountryMatches(supplier,countryCode))throw Error('Invalid supplier country')
  return {
    countryCode,
    name: supplier.businessName, description: supplier.description, about: supplier.description,
    categories: supplier.category, city: supplier.city, province: supplier.province,
    delivery: supplier.delivery, publicListingId: supplier.id, isMySupplier: true,
    isFavorite: false, phone: '', email: '', website: '', openingHours: '', rating: 0,
    reviewCount: 0, status: 'active',
  }
}

// The public supplier feed predates country metadata; an absent field denotes
// the original South African listing. Never infer a country from city names.
export function supplierCountryMatches(listing:{countryCode?:unknown},countryCode:string):boolean {
 const code=listing.countryCode===undefined?'ZA':listing.countryCode
 return typeof code==='string'&&/^[A-Z]{2}$/.test(code)&&code===countryCode
}
