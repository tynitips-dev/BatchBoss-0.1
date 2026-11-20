import {useEffect,useState} from 'react'
import {Mail} from 'lucide-react'
import {parseAnnouncement,type Announcement} from './AnnouncementBanner'
type Item={id:string;[key:string]:unknown}
type Message={id:string;title:string;body:string;href?:string;linkLabel?:string}
export default function BakerInbox({uid,items}:{uid:string;items:Record<string,Item[]>}){
 const [open,setOpen]=useState(false),[announcement,setAnnouncement]=useState<Announcement|null>(null),[read,setRead]=useState<string[]>(()=>{try{const v=JSON.parse(localStorage.getItem('bb-inbox-read:'+uid)||'[]');return Array.isArray(v)?v.filter(x=>typeof x==='string'):[]}catch{return []}})
 useEffect(()=>{const controller=new AbortController();let active=true;fetch('/announcements.json',{signal:controller.signal,cache:'no-store',credentials:'omit'}).then(r=>r.ok?r.json():null).then(v=>{if(active)setAnnouncement(parseAnnouncement(v))}).catch(()=>{});return()=>{active=false;controller.abort()}},[])
 const messages:Message[]=[]
 if(announcement)messages.push({id:'announcement:'+announcement.id,title:'BatchBoss update',body:announcement.message,href:announcement.href,linkLabel:announcement.linkLabel})
 for(const [kind,records] of [['Stock',items.inventory||[]],['Packaging',items.packaging||[]]] as const){
  for(const item of records){if(item.archived||item.alertEnabled===false||typeof item.currentStock!=='number'||typeof item.minStock!=='number'||item.currentStock>item.minStock)continue;messages.push({id:'stock:'+kind+':'+item.id+':'+item.currentStock+':'+item.minStock,title:kind+' running low',body:String(item.name||'Item')+' is at '+item.currentStock+' '+String(item.unit||'units')+'. Minimum: '+item.minStock+'.'})}
 }
 const unread=messages.filter(m=>!read.includes(m.id)).length
 function mark(ids:string[]){const next=Array.from(new Set([...read,...ids])).slice(-1000);setRead(next);try{localStorage.setItem('bb-inbox-read:'+uid,JSON.stringify(next))}catch{/* session state remains */}}
 return <><button type="button" className="plan-badge bb-inbox-button" onClick={()=>setOpen(true)}><Mail size={15}/> Inbox{unread>0&&<b aria-label={unread+' unread'}>{unread}</b>}</button>{open&&<div className="modal-backdrop" onClick={()=>setOpen(false)}><section className="modal bb-inbox" role="dialog" aria-modal="true" aria-label="Bakery inbox" onClick={e=>e.stopPropagation()} onKeyDown={e=>{if(e.key==='Escape')setOpen(false)}}><div className="bb-inbox-heading"><h2>Inbox</h2><button type="button" autoFocus className="outline-button" onClick={()=>setOpen(false)}>Close</button></div><p>BatchBoss updates and live bakery stock alerts.</p><button type="button" className="outline-button" disabled={!unread} onClick={()=>mark(messages.map(m=>m.id))}>Mark all as read</button>{messages.length?messages.map(m=><article key={m.id} className={read.includes(m.id)?'bb-message':'bb-message unread'}><h3>{m.title}</h3><p>{m.body}</p>{m.href&&<a href={m.href} target="_blank" rel="noopener noreferrer">{m.linkLabel||'Learn more'}</a>}{!read.includes(m.id)&&<button type="button" className="text-button" onClick={()=>mark([m.id])}>Mark as read</button>}</article>):<p>No current alerts or announcements.</p>}<small>Read status is saved on this browser. Older device-only Android messages are not available here.</small></section></div>}</>
}
