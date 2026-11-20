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
 return new Intl.NumberFormat(locale,{style:'currency',currency,currencyDisplay:'code',numberingSystem:'latn'}).formatToParts(value).map(part=>part.type==='decimal'?'.':part.type==='group'?' ':part.value).join('')
}
export function convertTemperature(value:number,from:'C'|'F',to:'C'|'F'):number|null {
 if(!Number.isFinite(value)||!['C','F'].includes(from)||!['C','F'].includes(to))return null
 return from===to?value:from==='C'?value*9/5+32:(value-32)*5/9
}

export const launchCountries: ReadonlyArray<{code:LaunchCountry;name:string}> = [
 {code:'ZA',name:'South Africa'}, {code:'GB',name:'United Kingdom'},
 {code:'US',name:'United States'}, {code:'CA',name:'Canada'},
 {code:'AU',name:'Australia'}, {code:'NZ',name:'New Zealand'},
]
/** Registration remains gated to ZAR until every monetary record is currency-safe. */
export function regionalRegistration(country:string) {
 const preset=regionDefaults(country)
 if(!preset)return null
 return {countryCode:country,currency:preset.currency,locale:preset.locale,
  measurementSystem:preset.measurement,temperatureUnit:preset.temperature,regionalVersion:1,
  registrationEnabled:country==='ZA'}
}
export function regionalSignupFields(country:string) {
 const settings=regionalRegistration(country)
 if(!settings?.registrationEnabled)throw new Error('Registration for this country is not available yet.')
 const {registrationEnabled,...fields}=settings
 void registrationEnabled
 return fields
}
/** Read-only preview of stored data; never infer a replacement currency from country. */
export function regionalWorkspaceSummary(business:Record<string,unknown>) {
 const countryCode=typeof business.countryCode==='string'?business.countryCode:''
 const preset=regionDefaults(countryCode)
 return {
  country:launchCountries.find(c=>c.code===countryCode)?.name || 'Not recorded (legacy workspace)',
  currency:typeof business.currency==='string'&&business.currency.trim()?business.currency:'ZAR (legacy default)',
  measurement:business.measurementSystem==='us'?'US customary':business.measurementSystem==='metric'?'Metric':'Existing recipe units',
  temperature:business.temperatureUnit==='F'?'Fahrenheit':business.temperatureUnit==='C'?'Celsius':'Not recorded',
  locale:typeof business.locale==='string'?business.locale:preset?.locale||'Not recorded',
 }
}

/** Accept decimal-keyboard commas, keep fractional zeros and remove redundant leading zeros. */
export function normalizeDecimalDraft(raw:string):string {
 return raw.replace(/,/g,'.').replace(/^(-?)\./,(_match,sign:string)=>sign+'0.').replace(/^(-?)0+(?=\d)/,'$1')
}
export function isDecimalDraft(value:string):boolean {
 return /^-?\d+(?:\.\d*)?$/.test(value)
}

/** Missing currency denotes a legacy South African record; malformed explicit metadata is not ZAR. */
export function recordCurrency(record:Record<string,unknown>):string|null {
 const code=record.currency
 return code===undefined?'ZAR':typeof code==='string'&&/^[A-Z]{3}$/.test(code)?code:null
}
export function formatRecordMoney(value:number,record:Record<string,unknown>):string {
 const currency=recordCurrency(record)
 if(!currency||!Number.isFinite(value))return 'Amount or currency unavailable'
 const locale=Object.values(regionPresets).find(p=>p.currency===currency)?.locale||'en-US'
 return formatRegionalMoney(value,currency,locale)
}
/** Never add different currencies together or apply an implicit exchange rate. */
export function formatRecordTotals(records:ReadonlyArray<Record<string,unknown>>,key:string,emptyCurrency='ZAR'):string {
 const totals=new Map<string,number>()
 let unavailable=false
 for(const record of records){
  const currency=recordCurrency(record),value=Number(record[key]??0)
  if(!currency||!Number.isFinite(value)){unavailable=true;continue}
  totals.set(currency,(totals.get(currency)||0)+value)
 }
 const amounts=[...totals.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([currency,value])=>formatRecordMoney(value,{currency}))
 if(unavailable)amounts.push('Some amounts unavailable')
 return amounts.join(' / ')||formatRecordMoney(0,{currency:emptyCurrency})
}
/** These web writers are still ZAR-only until non-ZAR workspace rollout is ready. */
export const stagedCurrencyFields={currency:'ZAR'} as const

/** Accounting currency comes from the bakery document, never a browser locale or user preference. */
export function bakeryAccountingRegion(business:Record<string,unknown>) {
 const currency=recordCurrency(business)
 if(!currency)return null
 const preset=Object.values(regionPresets).find(p=>p.currency===currency)
 if(!preset)return null
 const country=business.countryCode===undefined?(Object.keys(regionPresets) as LaunchCountry[]).find(code=>regionPresets[code].currency===currency):business.countryCode
 if(typeof country!=='string'||regionDefaults(country)?.currency!==currency)return null
 return {currency,countryCode:country,locale:preset.locale,defaultTaxRate:currency==='ZAR'?15:0}
}

/** Do not apply South African dialing rules to an international customer. */
export function invoiceWhatsAppNumber(raw:unknown,countryCode:unknown,currency:unknown):string|null {
 const value=String(raw??'').trim()
 if(!value||!/^[+\d().\s-]+$/.test(value))return null
 let digits=value.replace(/\D/g,'')
 if(value.startsWith('+'))return /^[1-9]\d{6,14}$/.test(digits)?digits:null
 if(value.startsWith('00')){digits=digits.slice(2);return /^[1-9]\d{6,14}$/.test(digits)?digits:null}
 if((countryCode==='ZA'||!countryCode&&currency==='ZAR')&&/^0\d{9}$/.test(digits))return '27'+digits.slice(1)
 if((countryCode==='ZA'||!countryCode&&currency==='ZAR')&&/^27\d{9}$/.test(digits))return digits
 return null
}
