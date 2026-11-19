import { useState } from 'react'
import { useBakeryRegion } from './BakeryRegion'
import { formatRegionalMoney, launchCountries, regionPresets, type LaunchCountry } from './workspace-utils'
import catalog from './regional-plan-prices.json'

export default function RegionalPlanPrices() {
 const bakery=useBakeryRegion()
 const [country,setCountry]=useState<LaunchCountry>(bakery.countryCode as LaunchCountry)
 const {currency,locale}=regionPresets[country]
 const rows=catalog as ReadonlyArray<{name:string;interval:string;prices:Partial<Record<string,number>>}>
 return <section className="content-card" aria-labelledby="regional-prices"><h3 id="regional-prices">BatchBoss plans and regional prices</h3>
 <label>Compare prices for <select value={country} onChange={e=>setCountry(e.target.value as LaunchCountry)}>{launchCountries.map(({code,name})=><option key={code} value={code}>{name} ({regionPresets[code].currency})</option>)}</select></label>
 <p>Catalog preview only. Availability, promotional terms, taxes and final billing are confirmed separately. Existing Paystack payments remain in ZAR.</p>
 <table><thead><tr><th scope="col">Plan or service</th><th scope="col">Catalog price ({currency})</th></tr></thead><tbody>{rows.map(plan=>{const amount=plan.prices[currency];return amount===undefined?null:<tr key={plan.name}><th scope="row">{plan.name}{country!=='ZA'&&plan.name.includes('launch special')?' (proposed)':''}</th><td>{formatRegionalMoney(amount/100,currency,locale)} / {plan.interval}</td></tr>})}</tbody></table>
 </section>
}
