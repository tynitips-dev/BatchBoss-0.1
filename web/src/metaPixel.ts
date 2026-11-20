type PixelFn=((...args:unknown[])=>void)&{callMethod?:(...args:unknown[])=>void;queue:unknown[][];loaded:boolean;version:string;push?:PixelFn}
const pixelId='1046124128434846'
const key='batchboss_meta_consent_v1'
const w=window as Window & {fbq?:PixelFn;_fbq?:PixelFn}
let started=false
const pending:{event:string;params:Record<string,string|number>;once?:string}[]=[]
const seen=new Set<string>()
export function metaConsent(){try{return localStorage.getItem(key)}catch{return null}}
function safePage(){return location.protocol==='https:'&&location.hostname==='app.batchboss.co.za'&&![...new URLSearchParams(location.search).keys()].some(k=>!['signup','tab','payment','utm_source','utm_medium','utm_campaign','utm_content','utm_term','fbclid'].includes(k))}
export function startMeta(){
 if(metaConsent()!=='yes'||!safePage())return
 try{
  if(!started){
   if(!w.fbq){const f=((...args:unknown[])=>{if(f.callMethod)f.callMethod(...args);else f.queue.push(args)}) as PixelFn;f.queue=[];f.loaded=true;f.version='2.0';f.push=f;w.fbq=f;w._fbq=f;const script=document.createElement('script');script.async=true;script.src='https://connect.facebook.net/en_US/fbevents.js';document.head.appendChild(script)}
   w.fbq('set','autoConfig',false,pixelId);w.fbq('init',pixelId);w.fbq('consent','grant');w.fbq('trackSingle',pixelId,'PageView');started=true
  }
  for(const p of pending.splice(0))send(p.event,p.params,p.once)
 }catch{/* optional marketing must not block the app */}
}
export function setMetaConsent(allow:boolean){try{localStorage.setItem(key,allow?'yes':'no')}catch{return}if(allow)startMeta();else{pending.length=0;try{w.fbq?.('consent','revoke')}catch{}}}
function send(event:string,params:Record<string,string|number>,once?:string){
 if(!w.fbq||!safePage()||metaConsent()!=='yes')return
 const k=once?'bb-meta:'+event+':'+once:''
 if(k){if(seen.has(k))return;try{if(localStorage.getItem(k))return}catch{}}
 w.fbq('trackSingle',pixelId,event,params)
 if(k){seen.add(k);try{localStorage.setItem(k,'1')}catch{}}
}
export function trackMeta(event:'CompleteRegistration'|'StartTrial'|'InitiateCheckout'|'Purchase'|'Subscribe',params:Record<string,string|number>={},once?:string){
 if(metaConsent()!=='yes')return
 try{if(!safePage()){pending.push({event,params,once});if(pending.length>20)pending.shift();return}startMeta();send(event,params,once)}catch{/* best effort */}
}
