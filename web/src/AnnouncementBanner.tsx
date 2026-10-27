import { useEffect, useState } from 'react';
type Announcement={id:string;message:string;linkLabel:string;href:string};
function parseAnnouncement(value:unknown):Announcement|null{
  if(!value||typeof value!=='object')return null;
  const item=value as Record<string,unknown>;
  if(item.enabled!==true||typeof item.id!=='string'||!item.id||item.id.length>100||typeof item.message!=='string'||!item.message.trim()||item.message.length>300)return null;
  for(const field of ['startsAt','expiresAt']){const date=item[field];if(date!==undefined&&date!==null&&date!==''){if(typeof date!=='string'||!Number.isFinite(Date.parse(date)))return null;const time=Date.parse(date);if(field==='startsAt'&&Date.now()<time)return null;if(field==='expiresAt'&&Date.now()>=time)return null;}}
  let href='';
  if(typeof item.href==='string'&&item.href.length<=2048){try{const url=new URL(item.href,window.location.origin);if(url.protocol==='https:'&&!url.username&&!url.password)href=url.href;}catch{/* Hide invalid links. */}}
  return{id:item.id,message:item.message.trim(),href,linkLabel:typeof item.linkLabel==='string'?item.linkLabel.slice(0,60):'Learn more'};
}
export default function AnnouncementBanner(){
  const [announcement,setAnnouncement]=useState<Announcement|null>(null);
  useEffect(()=>{const controller=new AbortController();const timeout=window.setTimeout(()=>controller.abort(),5000);void fetch('/announcements.json',{signal:controller.signal,cache:'no-store',credentials:'omit'}).then(response=>response.ok?response.json():null).then(value=>{if(controller.signal.aborted)return;const item=parseAnnouncement(value);if(item){try{if(localStorage.getItem('batchboss-dismissed-announcement')===item.id)return;}catch{/* Storage may be unavailable. */}setAnnouncement(item);}}).catch(()=>{/* Announcements must not interrupt the workspace. */}).finally(()=>window.clearTimeout(timeout));return()=>{controller.abort();window.clearTimeout(timeout);};},[]);
  if(!announcement)return null;
  return <section className="bb-announcement" aria-label="Updates and offers"><span className="bb-announcement-label">BATCHBOSS UPDATE</span><p>{announcement.message}</p>{announcement.href&&<a href={announcement.href} target="_blank" rel="noopener noreferrer">{announcement.linkLabel||'Learn more'} ↗</a>}<button type="button" aria-label="Dismiss announcement" onClick={()=>{try{localStorage.setItem('batchboss-dismissed-announcement',announcement.id);}catch{/* Keep dismissal working for this page. */}setAnnouncement(null);}}>×</button></section>;
}
