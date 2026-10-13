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
