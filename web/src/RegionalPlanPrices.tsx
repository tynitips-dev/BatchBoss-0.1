import { useState } from 'react'
import { useBakeryRegion } from './BakeryRegion'
import { formatRegionalMoney, launchCountries, regionPresets, type LaunchCountry } from './workspace-utils'
import catalog from './regional-plan-prices.json'

export default function RegionalPlanPrices() {
  const bakery = useBakeryRegion()
  const [country, setCountry] = useState(bakery.countryCode as LaunchCountry)
  const { currency, locale } = regionPresets[country]
  return <section aria-labelledby="regional-prices">
    <h3 id="regional-prices">Regional pricing</h3>
    <label>View prices for <select value={country} onChange={event => setCountry(event.target.value as LaunchCountry)}>
      {launchCountries.map(({ code, name }) =>
        <option key={code} value={code}>{name} ({regionPresets[code].currency})</option>)}
    </select></label>
    <p>Compare catalog prices below. Each checkout shows its billing currency and final total before payment.</p>
    <table>
      <thead><tr><th scope="col">Plan or service</th><th scope="col">Price ({currency})</th></tr></thead>
      <tbody>{catalog.map(plan => {
        const amount = plan.prices[currency]
        if (amount === undefined) return null
        return <tr key={plan.name}><th scope="row">{plan.name}</th>
          <td>{formatRegionalMoney(amount / 100, currency, locale)} per {plan.interval}</td></tr>
      })}</tbody>
    </table>
  </section>
}
