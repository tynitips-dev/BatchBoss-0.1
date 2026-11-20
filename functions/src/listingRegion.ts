/** Seller prices are stored in minor units of the immutable accounting currency.
 * Missing metadata on legacy listings means South Africa, never the visitor's country.
 */
export const listingRegions = {
 ZA: {currency:'ZAR',locale:'en-ZA'}, US:{currency:'USD',locale:'en-US'},
 GB:{currency:'GBP',locale:'en-GB'}, CA:{currency:'CAD',locale:'en-CA'},
 AU:{currency:'AUD',locale:'en-AU'}, NZ:{currency:'NZD',locale:'en-NZ'},
} as const
export type ListingCountry = keyof typeof listingRegions
export function listingCountry(raw:unknown):ListingCountry|null {
 return typeof raw==='string'&&Object.prototype.hasOwnProperty.call(listingRegions,raw)?raw as ListingCountry:null
}
export function listingRegion(data:Record<string,unknown>|undefined){
 if(!data)return null
 const countryCode=listingCountry(data.countryCode===undefined?'ZA':data.countryCode)
 if(!countryCode)return null
 const currency=data.currency===undefined?'ZAR':data.currency
 const region=listingRegions[countryCode]
 if(currency!==region.currency)return null
 return {countryCode,currency:region.currency,locale:region.locale}
}
export function listingBelongsToCountry(data:Record<string,unknown>,country:ListingCountry){
 return listingRegion(data)?.countryCode===country
}
export function sameListingRegion(a:Record<string,unknown>|undefined,b:Record<string,unknown>|undefined){
 const first=listingRegion(a),second=listingRegion(b)
 return !!first&&!!second&&first.countryCode===second.countryCode&&first.currency===second.currency
}
export function marketplaceBillingAvailable(region:ReturnType<typeof listingRegion>){
 // A foreign local-currency asking price is not a currency conversion of the listing fee.
 return region?.countryCode==='ZA'&&region.currency==='ZAR'
}
