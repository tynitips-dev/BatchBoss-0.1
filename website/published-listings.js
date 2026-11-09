const published = document.querySelector('[data-published-listings]');
const grid = published.querySelector('.listing-grid');
const status = published.querySelector('[role="status"]');
const more = published.querySelector('button');
let cursor = null;

async function loadPublished() {
  more.disabled = true;
  status.textContent = 'Loading listings…';
  try {
    const response = await fetch('https://europe-west1-batch-boss-android.cloudfunctions.net/listPublishedBakerListings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ data: { kind: published.dataset.publishedListings, cursor } }),
    });
    if (!response.ok) throw new Error('Listings unavailable');
    const { result } = await response.json();
    for (const item of result.listings) {
      const card = document.createElement('article');
      card.className = 'market-card';
      const fields = [['h3', item.title], ['p', item.area], ['p', item.description]];
      if (item.priceCents !== null) fields.push(['strong', new Intl.NumberFormat('en-ZA', {
        style: 'currency', currency: 'ZAR',
      }).format(item.priceCents / 100)]);
      for (const [tag, value] of fields) {
        const element = document.createElement(tag);
        element.textContent = value;
        card.append(element);
      }
      grid.append(card);
    }
    cursor = result.nextCursor;
    more.hidden = !cursor;
    status.textContent = grid.childElementCount ? '' : 'No active listings on this page.';
  } catch {
    status.textContent = 'Published listings are currently unavailable.';
    more.hidden = false;
    more.textContent = 'Try again';
  } finally {
    more.disabled = false;
  }
}

more.addEventListener('click', loadPublished);
loadPublished();
