const publicPreview = 'https://www.batchboss.co.za/batchboss-preview/'
const listingPrices = {
  directory: 'R49 per month',
  marketplace: 'R20 per listing',
  weeklyPro: 'R39.99 per week',
  fullSuite: 'R220 per month',
}

export function ListingView({ tab, onViewPlans }: {
  tab: 'directoryListing' | 'marketplace'
  onViewPlans: () => void
}) {
  const isDirectory = tab === 'directoryListing'

  return <section className="content-card">
    <h2>{isDirectory ? 'Directory Listing' : 'Marketplace'}</h2>
    <p>{isDirectory
      ? 'Show customers what you bake, where you’re based and how they can enquire.'
      : 'List pre-owned baking equipment and supplies for other bakers to discover.'}</p>
    <p><strong>{isDirectory ? listingPrices.directory : listingPrices.marketplace}</strong></p>
    <p>{isDirectory ? 'Directory Listing is included with Full Suite.' : 'Full Suite includes 3 new Marketplace listings per month. Extra listings cost R20 each.'} Listing submissions open when the service launches.</p>
    <div className="card-actions">
      <a className="outline-button" href={publicPreview + (isDirectory ? 'baker-finder.html' : 'marketplace.html')}>
        {isDirectory ? 'Preview Find a Baker' : 'Preview Marketplace'}
      </a>
      <button className="primary-button" type="button" onClick={onViewPlans}>View listing plans</button>
    </div>
  </section>
}

export function UpcomingListingPlans() {
  const plans = [
    { title: 'Weekly BatchBoss Pro', price: listingPrices.weeklyPro, detail: 'Seven days of BatchBoss Pro access.' },
    { title: 'Directory Listing', price: listingPrices.directory, detail: 'A bakery profile in Find a Baker.' },
    { title: 'Marketplace Listing', price: listingPrices.marketplace, detail: 'Pay per item listing.' },
    { title: 'BatchBoss Full Suite', price: listingPrices.fullSuite, detail: 'BatchBoss Pro, a Directory Listing and 3 new Marketplace listings per month. Extra listings cost R20 each.' },
  ]

  return <section className="content-card">
    <h2>Upcoming plans and listing prices</h2>
    <p>Preview pricing. Checkout and listing submissions are coming soon.</p>
    <div className="plans">
      {plans.map(plan => <article key={plan.title}>
        <h3>{plan.title}</h3>
        <strong>{plan.price}</strong>
        <p>{plan.detail}</p>
      </article>)}
    </div>
  </section>
}
