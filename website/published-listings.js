const published=document.querySelector('[data-published-listings]');
const grid=published.querySelector('.listing-grid'),status=published.querySelector('[role="status"]'),more=published.querySelector('button'),select=published.querySelector('[data-listing-country]');
const regions={ZA:['ZAR','en-ZA'],US:['USD','en-US'],GB:['GBP','en-GB'],CA:['CAD','en-CA'],AU:['AUD','en-AU'],NZ:['NZD','en-NZ']};
let cursor=null,generation=0,request=null;
function listingPrice(item,country){
  // Only missing metadata on an actual legacy SA listing defaults to ZAR.
  const actualCountry=item.countryCode===undefined?'ZA':item.countryCode;
  const code=item.currency===undefined?'ZAR':item.currency;
  if(actualCountry!==country||code!==regions[country][0])return null;
  if(item.priceCents===null)return '';
  if(!Number.isSafeInteger(item.priceCents)||item.priceCents<1||item.priceCents>100000000)return null;
  return new Intl.NumberFormat(regions[country][1],{style:'currency',currency:code,currencyDisplay:'code'}).format(item.priceCents/100);
}
async function loadPublished(){
 const current=++generation,country=select.value;
 if(!Object.hasOwn(regions,country))return;
 request?.abort();const controller=new AbortController();request=controller;
 more.disabled=true;status.textContent='Loading listings…';
 try{
  const response=await fetch('https://europe-west1-batch-boss-android.cloudfunctions.net/listPublishedBakerListings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({data:{kind:published.dataset.publishedListings,countryCode:country,cursor}}),signal:controller.signal,credentials:'omit',cache:'no-store'});
  if(!response.ok)throw Error('Listings unavailable');
  const envelope=await response.json(),result=envelope.result??envelope.data;
  if(current!==generation)return;
  if(!result||!Array.isArray(result.listings)||!(result.nextCursor===null||typeof result.nextCursor==='string'&&/^[0-9a-f]{64}$/.test(result.nextCursor)))throw Error('Invalid response');
  for(const item of result.listings){
   if(!item||typeof item.title!=='string'||typeof item.area!=='string'||typeof item.description!=='string')continue;
   const price=listingPrice(item,country);if(price===null)continue;
   const card=document.createElement('article');card.className='market-card';
   const fields=[['h3',item.title],['p',item.area],['p',item.description]];if(price)fields.push(['strong',price]);
   for(const [tag,value] of fields){const e=document.createElement(tag);e.textContent=value;card.append(e);}
   [item.logo,...(Array.isArray(item.photos)?item.photos.slice(0,3):[])].forEach((source,index)=>{
    if(typeof source!=='string'||source.length>65000||!/^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(source))return;
    const img=document.createElement('img');img.src=source;img.alt=item.title+(index===0?' logo':' photo '+index);img.loading='lazy';img.width=index===0?88:160;img.style.maxWidth='100%';card.append(img);
   });
   grid.append(card);
  }
  cursor=result.nextCursor;more.hidden=!cursor;more.textContent='Load more';
  status.textContent=grid.childElementCount?'':cursor?'No matching listings on this page. Load more to continue.':'No active listings in this country yet.';
 }catch{if(current===generation){status.textContent='Published listings are currently unavailable.';more.hidden=false;more.textContent='Try again';}}
 finally{if(current===generation)more.disabled=false;}
}
const requested=new URLSearchParams(location.search).get('country');
if(Object.hasOwn(regions,requested))select.value=requested;
select.addEventListener('change',()=>{cursor=null;grid.replaceChildren();void loadPublished();});
more.addEventListener('click',loadPublished);void loadPublished();
