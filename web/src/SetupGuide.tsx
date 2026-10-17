import { useState } from 'react'
import { doc, updateDoc } from 'firebase/firestore'
import { db } from './firebase'
import type { ModuleKey, UserProfile } from './types'
import './setup-guide.css'

const steps: {title:string; text:string; target:ModuleKey; icon:string}[] = [
 {title:'Business details',text:'Add your bakery name, logo and contact details.',target:'settings',icon:'🏠'},
 {title:'Ingredients',text:'Save your ingredients, pack sizes and prices.',target:'ingredients',icon:'🥣'},
 {title:'First recipe',text:'Create a recipe and list its ingredients.',target:'recipes',icon:'🧁'},
 {title:'Selling price',text:'Check labour, packaging, costs and profit margin.',target:'recipes',icon:'💰'},
 {title:'Customers',text:'Add your first customer and contact details.',target:'customers',icon:'👥'},
 {title:'Orders',text:'Explore how to organise your baking orders.',target:'orders',icon:'📋'},
]
type Progress={step:number;status:'active'|'skipped'|'complete';completed?:number[]}
function completedFrom(progress:Progress | undefined):number[]{
 if(Array.isArray(progress?.completed))return progress.completed.filter((n):n is number=>Number.isInteger(n)&&n>=0&&n<steps.length)
 if(!progress)return []
 if(progress.status==='complete')return steps.map((_,i)=>i)
 return Array.from({length:Math.max(0,Math.min(steps.length,Number(progress.step)||0))},(_,i)=>i)
}
export default function SetupGuide({profile,onOpen,active,suspended}:{profile:UserProfile;onOpen:(key:ModuleKey)=>void;active:ModuleKey;suspended:boolean}) {
 const stored=profile.setupGuide as Progress|undefined
 const [completed,setCompleted]=useState<number[]>(()=>completedFrom(stored))
 const [visible,setVisible]=useState(!stored || stored.status==='active')
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const remaining=steps.length-completed.length
 async function save(done:number[],show:boolean,status:'active'|'skipped'|'complete'){
  setBusy(true);setError('')
  try{
   await updateDoc(doc(db,'users',profile.uid),{setupGuide:{step:done.length,completed:done,status}})
   setCompleted(done);setVisible(show)
  }catch{setError('Could not save your setup progress. Try again.')}
  finally{setBusy(false)}
 }
 function openStep(index:number){setVisible(false);onOpen(steps[index].target)}
 return <>
  <div className="setup-help"><button type="button" className="outline-button" onClick={()=>setVisible(v=>!v)}>{visible?'Hide bakery setup':'Your bakery setup'}{remaining>0?` · ${remaining} to go`:' · Complete'}</button></div>
  {visible&&!suspended&&<section className="setup-guide" aria-labelledby="setup-title">
   <div className="setup-header"><div><span className="eyebrow">GET STARTED WITH BATCHBOSS</span><h2 id="setup-title">Your bakery setup 🧁</h2><p>Six little steps to get your bakery ready. Open a section, save your work, then mark it Done.</p></div><strong className="setup-count">{completed.length} / {steps.length} done</strong></div>
   <progress max={steps.length} value={completed.length} aria-label="Bakery setup progress"/>
   <div className="setup-card-grid">
    {steps.map((step,index)=>{
     const done=completed.includes(index)
     return <article className={`setup-step-card${done?' is-done':''}`} key={step.title}>
      <div className="setup-step-top"><span className="setup-step-icon" aria-hidden="true">{step.icon}</span><span className="setup-step-number">{done?'✓':String(index+1).padStart(2,'0')}</span></div>
      <h3>{step.title}</h3><p>{step.text}</p>
      <div className="setup-step-actions"><button type="button" className="outline-button" onClick={()=>openStep(index)}>Open</button><button type="button" className="primary-button" disabled={busy} onClick={()=>void save(done?completed.filter(i=>i!==index):[...completed,index].sort((a,b)=>a-b),true,!done&&remaining===1?'complete':'active')}>{done?'Undo':'Done ✓'}</button></div>
     </article>
    }))}
   </div>
   {error&&<p role="alert">{error}</p>}
   {remaining===0?<p className="setup-finished">🎉 Your setup checklist is complete! You can revisit any section whenever you like.</p>:<p className="setup-hint">Tick Done only when you've finished that step. You can complete them at your own pace.</p>}
   <div className="setup-footer"><button type="button" className="outline-button" disabled={busy} onClick={()=>void save(completed,false,remaining===0?'complete':'skipped')}>{remaining===0?'Close checklist':'Finish for now'}</button></div>
  </section>}
 </>
}
