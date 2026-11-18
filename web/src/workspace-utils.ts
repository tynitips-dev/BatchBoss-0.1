export const money = { format: (value: number) => `R ${new Intl.NumberFormat('en-US', {minimumFractionDigits:2, maximumFractionDigits:2}).format(Number.isFinite(value) ? value : 0).replace(/,/g, ' ')}` }
export const isImportHeading = (name: string) => /^(name|ingredient|item name|packaging item|ready for batchboss.*|instructions?|notes?:.*)$/i.test(name.trim())
export function displayName(profile: { firstName?: string; surname?: string }) {
  const first = String(profile.firstName || '').trim()
  return first && !first.includes('@') ? first : 'Baker'
}
export const unitGroups: Record<string, { group: string; factor: number }> = {
  g:{group:'mass',factor:1}, kg:{group:'mass',factor:1000},
  ml:{group:'volume',factor:1}, L:{group:'volume',factor:1000},
  tsp:{group:'volume',factor:5}, tbsp:{group:'volume',factor:15}, cup:{group:'volume',factor:250},
  oz:{group:'mass',factor:28.349523125}, lb:{group:'mass',factor:453.59237},
  'cup_us':{group:'volume',factor:236.5882365}, 'cup_metric':{group:'volume',factor:250},
  'fl_oz_us':{group:'volume',factor:29.5735295625}, 'fl_oz_uk':{group:'volume',factor:28.4130625},
  'tsp_us':{group:'volume',factor:4.92892159375}, 'tbsp_us':{group:'volume',factor:14.78676478125}, 'tbsp_au':{group:'volume',factor:20},
}
export function convertUnits(amount:number, from:string, to:string):number|null {
  const a=unitGroups[from], b=unitGroups[to]
  return !a || !b || a.group!==b.group || !Number.isFinite(amount) || amount<0 ? null : amount*a.factor/b.factor
}
export function scaleQuantity(quantity:number, original:number, target:number) {
  if (![quantity,original,target].every(Number.isFinite) || original<=0 || target<=0 || quantity<0) return null
  return quantity*target/original
}
export function lineTotal(lines: {quantity:number; price:number}[]) { return Math.round(lines.reduce((n,l)=>n+l.quantity*l.price,0)*100)/100 }

/** Regional defaults for new workspaces only; legacy records retain their stored units and ZAR. */
export const regionPresets = {
 ZA:{currency:'ZAR',locale:'en-ZA',temperature:'C',measurement:'metric'},
 GB:{currency:'GBP',locale:'en-GB',temperature:'C',measurement:'metric'},
 US:{currency:'USD',locale:'en-US',temperature:'F',measurement:'us'},
 CA:{currency:'CAD',locale:'en-CA',temperature:'C',measurement:'metric'},
 AU:{currency:'AUD',locale:'en-AU',temperature:'C',measurement:'metric'},
 NZ:{currency:'NZD',locale:'en-NZ',temperature:'C',measurement:'metric'},
} as const
export type LaunchCountry = keyof typeof regionPresets
export function regionDefaults(country:string) {
 return Object.prototype.hasOwnProperty.call(regionPresets,country)
  ? regionPresets[country as LaunchCountry] : null
}
/** Format an amount already denominated in this currency; never perform FX or relabel stored ZAR. */
export function formatRegionalMoney(value:number,currency:string,locale:string):string {
 if(!Number.isFinite(value)) throw new RangeError('Invalid amount')
 if(!/^[A-Z]{3}$/.test(currency)) throw new RangeError('Invalid currency code')
 if(currency==='ZAR'&&locale==='en-ZA') return money.format(value)
 return new Intl.NumberFormat(locale,{style:'currency',currency,currencyDisplay:'code'}).format(value)
}
export function convertTemperature(value:number,from:'C'|'F',to:'C'|'F'):number|null {
 if(!Number.isFinite(value)||!['C','F'].includes(from)||!['C','F'].includes(to))return null
 return from===to?value:from==='C'?value*9/5+32:(value-32)*5/9
}
