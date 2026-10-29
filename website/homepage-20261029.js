const slides=[...document.querySelectorAll('[data-slide]')];let hero=0;
function showHero(n){hero=(n+slides.length)%slides.length;slides.forEach((s,i)=>{s.hidden=i!==hero});document.querySelectorAll('[data-go]').forEach((b,i)=>b.setAttribute('aria-current',String(i===hero)));document.getElementById('hero-count').textContent=`0${hero+1} / 03`}
document.querySelectorAll('[data-go]').forEach(b=>b.addEventListener('click',()=>showHero(Number(b.dataset.go))));document.getElementById('hero-prev').onclick=()=>showHero(hero-1);document.getElementById('hero-next').onclick=()=>showHero(hero+1);
const screens=[{id:1,title:'Welcome & sign-in',description:'Start with your own bakery account.'},{id:2,title:'Bakery account setup',description:'Set up your workspace for the business you are building.'},{id:3,title:'Business dashboard',description:'A home for the figures and shortcuts behind your baking business.'},{id:4,title:'Recipes & pricing',description:'Your recipes, quantities and pricing details in one place.'},{id:8,title:'Invoice preview',description:'Bring your line items together in a document you can review and share.'},{id:6,title:'Ingredient scanning',description:'A look at the ingredient barcode screen in the developing app.'}];let current=0;
const tabs=document.querySelector('.gallery-tabs');screens.forEach((s,i)=>{let b=document.createElement('button');b.innerHTML=`<span>0${i+1}</span>${s.title}`;b.onclick=()=>showScreen(i);tabs.append(b)});
function showScreen(n){current=(n+screens.length)%screens.length;let s=screens[current];document.getElementById('gallery-image').src=`assets/screen-${s.id}.jpg`;document.getElementById('gallery-image').alt=`BatchBoss ${s.title}`;document.getElementById('gallery-title').textContent=s.title;document.getElementById('gallery-description').textContent=s.description;document.getElementById('gallery-number').textContent=`0${current+1} / 06`;[...tabs.children].forEach((b,i)=>b.setAttribute('aria-current',String(i===current)))}
document.getElementById('gallery-prev').onclick=()=>showScreen(current-1);document.getElementById('gallery-next').onclick=()=>showScreen(current+1);showScreen(0);
const dialog=document.getElementById('lightbox');document.getElementById('open-screen').onclick=()=>{let s=screens[current];dialog.querySelector('img').src=`assets/screen-${s.id}.jpg`;dialog.querySelector('img').alt=`BatchBoss ${s.title}`;dialog.querySelector('p').textContent=s.title;dialog.showModal()};document.getElementById('close-screen').onclick=()=>dialog.close();dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close()});
let startX=0;document.querySelector('.screen-button').addEventListener('touchstart',e=>startX=e.changedTouches[0].clientX,{passive:true});document.querySelector('.screen-button').addEventListener('touchend',e=>{let delta=e.changedTouches[0].clientX-startX;if(Math.abs(delta)>60)showScreen(current+(delta<0?1:-1))},{passive:true});
const costForm=document.getElementById('cost-form');
function updateCost(){const v=id=>document.getElementById(id).value;const result=ingredientCost(v('pack-price'),v('pack-size'),v('pack-unit'),v('used-size'),v('used-unit'));const output=document.getElementById('cost-result'),explanation=document.getElementById('cost-explanation');if(result.error){output.textContent='—';explanation.textContent=result.error;return}const num=new Intl.NumberFormat('en-ZA',{maximumFractionDigits:3});output.textContent=`R${result.cost.toFixed(2)}`;explanation.textContent=`R${result.price.toFixed(2)} ÷ ${num.format(result.packBase)} ${result.base} × ${num.format(result.usedBase)} ${result.base}`}
costForm.addEventListener('input',updateCost);costForm.addEventListener('change',updateCost);costForm.addEventListener('submit',e=>e.preventDefault());costForm.addEventListener('reset',()=>setTimeout(updateCost,0));updateCost();

// Public campaign feed only; never send bakery credentials from the homepage.
(()=>{
 const section=document.getElementById('homepage-sponsored');
 const endpoint='https://us-central1-batchboss-suppliers.cloudfunctions.net/listPublicSupplierAds';
 let revision=0;
 const hide=()=>{section.hidden=true;section.replaceChildren();};
 async function refresh(){
  const request=++revision;hide();
  try{
   const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},credentials:'omit',cache:'no-store',signal:AbortSignal.timeout(12000),body:JSON.stringify({data:{placement:'homepage-feature'}})});
   if(!response.ok) return;
   const payload=await response.json(),today=new Date().toISOString().slice(0,10);
   const ads=(payload.result||payload.data)?.ads;
   if(!Array.isArray(ads)||request!==revision)return;
   const ad=ads.find(a=>a&&a.placement==='homepage-feature'&&typeof a.title==='string'&&typeof a.businessName==='string'&&typeof a.startDate==='string'&&typeof a.endDate==='string'&&a.startDate<=today&&a.endDate>=today&&typeof a.artworkUrl==='string'&&/^https:\/\/us-central1-batchboss-suppliers\.cloudfunctions\.net\/getPublicSupplierAdArtwork\?ad=[0-9a-f-]{36}$/.test(a.artworkUrl));
   if(!ad)return;
   const image=document.createElement('img');image.alt=ad.businessName+' sponsored campaign';image.src=ad.artworkUrl;image.loading='eager';
   image.onerror=()=>{if(request===revision)hide();};
   image.onload=()=>{if(request===revision)section.hidden=false;};
   const copy=document.createElement('div'),label=document.createElement('small'),title=document.createElement('h2'),description=document.createElement('p'),link=document.createElement('a');
   label.textContent='Sponsored · '+ad.businessName;title.textContent=ad.title;description.textContent=typeof ad.description==='string'?ad.description.slice(0,180):'';
   link.href='https://suppliers.batchboss.co.za/directory';link.className='button';link.textContent='Explore suppliers ↗';copy.append(label,title,description,link);section.append(image,copy);
  }catch{if(request===revision)hide();}
 }
 void refresh();setInterval(()=>void refresh(),60000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)void refresh();});
})();
