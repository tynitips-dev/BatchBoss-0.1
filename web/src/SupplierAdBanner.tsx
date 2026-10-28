import {useEffect,useState} from 'react'
import './supplier-ad-banner.css'
type Ad={id:string;title:string;description:string;businessName:string;city:string;province:string;artworkUrl:string}
const endpoint='https://us-central1-batchboss-suppliers.cloudfunctions.net/listPublicSupplierAds'
const artworkEndpoint='https://us-central1-batchboss-suppliers.cloudfunctions.net/getPublicSupplierAdArtwork'
export default function SupplierAdBanner(){
 const [ads,setAds]=useState<Ad[]>([])
 useEffect(()=>{
  let active=true;let current:AbortController|null=null
  async function refresh(){
   current?.abort();const controller=new AbortController();current=controller
   const timer=setTimeout(()=>controller.abort(),12000)
   try{
    const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},credentials:'omit',cache:'no-store',signal:controller.signal,body:JSON.stringify({data:{placement:'marketplace-banner'}})})
    if(!response.ok)throw Error('Unavailable')
    const body=await response.json(),rows=(body.result??body.data)?.ads
    if(!Array.isArray(rows))throw Error('Invalid feed')
    const valid=rows.filter((ad:Ad)=>{
     if(!ad||!['id','title','description','businessName','city','province','artworkUrl'].every(key=>typeof ad[key as keyof Ad]==='string'))return false
     try{const url=new URL(ad.artworkUrl);return url.origin+url.pathname===artworkEndpoint&&url.searchParams.get('ad')===ad.id}catch{return false}
    }).slice(0,3)
    if(active&&current===controller)setAds(valid)
   }catch{if(active&&current===controller)setAds([])}finally{clearTimeout(timer)}
  }
  void refresh();const interval=setInterval(()=>void refresh(),60000)
  return()=>{active=false;current?.abort();clearInterval(interval)}
 },[])
 if(!ads.length)return null
 return <section className="supplier-ad-banner" aria-label="Supplier offers">{ads.map(ad=><article key={ad.id}>
  <div><span className="eyebrow">Supplier advertisement · {ad.businessName}</span><h2>{ad.title}</h2>{ad.description&&<p>{ad.description}</p>}<small>{ad.city}, {ad.province}</small></div>
  <img src={ad.artworkUrl} alt={`${ad.businessName}: ${ad.title}`} loading="lazy" referrerPolicy="no-referrer" onError={()=>setAds(old=>old.filter(row=>row.id!==ad.id))}/>
 </article>)}</section>
}
