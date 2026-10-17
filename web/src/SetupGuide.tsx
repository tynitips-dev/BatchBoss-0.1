import { useEffect, useRef, useState } from 'react'
import { doc, updateDoc } from 'firebase/firestore'
import { db } from './firebase'
import type { ModuleKey, UserProfile } from './types'
import './setup-guide.css'

const steps: {title:string; text:string; target:ModuleKey; action:string; selector:string}[] = [
 {title:'Make BatchBoss yours',text:'Add your business name, logo and contact details, then select Save business information. Your logo is optional.',target:'settings',action:'Open business settings',selector:'.business-form'},
 {title:'Add your ingredients',text:'Choose Add ingredient. Enter the pack price and pack size, for example R30 for 1 kg of flour, then save. Use your own prices.',target:'ingredients',action:'Open ingredients',selector:'.card-actions .primary-button'},
 {title:'Create your first recipe',text:'Choose Add recipe. Enter a recipe or import a text-based PDF, then review each ingredient, quantity and unit before saving. Cake flour can match flour in your stock.',target:'recipes',action:'Open recipes',selector:'.card-actions .primary-button'},
 {title:'Check your selling price',text:'Open your saved recipe using Edit. Review labour, packaging, overheads and profit margin, then save. Check the calculated cost before deciding what to charge.',target:'recipes',action:'Open recipes to edit',selector:'.content-card'},
 {title:'Add your first customer',text:'Add a customer’s name and contact details and save. You can skip this step until you receive your first order.',target:'customers',action:'Open customers',selector:'.customers-view, .app-section'},
 {title:'Ready for your next bake',text:'Use Orders to organise your work. Quotes, invoices and receipts are Pro features; you can keep using the free costing tools without upgrading.',target:'orders',action:'Explore orders',selector:'.app-section, .content-card'},
]
type Progress={step:number;status:'active'|'skipped'|'complete'}
export default function SetupGuide({profile,onOpen,active,suspended}:{profile:UserProfile;onOpen:(key:ModuleKey)=>void;active:ModuleKey;suspended:boolean}) {
 const stored=profile.setupGuide as Progress|undefined
 const [progress,setProgress]=useState<Progress>(()=>({step:Math.min(5,Math.max(0,Number(stored?.step)||0)),status:stored?.status||'active'}))
 const [visible,setVisible]=useState(stored?.status==='active')
 const [welcome,setWelcome]=useState(!stored && !new URLSearchParams(location.search).has('payment'))
 const [busy,setBusy]=useState(false)
 const [error,setError]=useState('')
 const dialog=useRef<HTMLDialogElement>(null)
 const step=steps[progress.step]
 useEffect(()=>{if(welcome)dialog.current?.showModal();else dialog.current?.close()},[welcome])
 useEffect(()=>{
  if(!visible||suspended||active!==step.target)return
  const target=document.querySelector(step.selector)
  target?.classList.add('setup-highlight')
  return ()=>target?.classList.remove('setup-highlight')
 },[visible,suspended,active,step])
 async function persist(next:Progress,show:boolean){
  setBusy(true);setError('')
  try{
   await updateDoc(doc(db,'users',profile.uid),{setupGuide:next})
   setProgress(next);setVisible(show);setWelcome(false)
  }catch{setError('Your guide progress could not be saved. Check your connection and try again.')}
  finally{setBusy(false)}
 }
 function openStep(){onOpen(step.target)}
 return <>
  <details className="setup-help"><summary>Help</summary><button className="outline-button" onClick={()=>{setVisible(true);setWelcome(false)}}>Setup guide</button></details>
  <dialog ref={dialog} className="setup-welcome" onCancel={event=>{event.preventDefault();if(!busy)void persist({...progress,status:'skipped'},false)}}>
   <span className="brand-mark">B</span><p className="eyebrow">Welcome to BatchBoss</p><h2>Let’s set up your bakery</h2>
   <p>Six simple steps, using your own business details and recipes. Take your time—you can return through Help → Setup guide.</p>
   {error&&<p role="alert">{error}</p>}
   <div className="setup-actions"><button className="primary-button" disabled={busy} onClick={()=>void persist({step:0,status:'active'},true)}>Let’s get started</button><button className="outline-button" disabled={busy} onClick={()=>void persist({step:0,status:'skipped'},false)}>Skip for now</button></div>
  </dialog>
  {visible&&!suspended&&<section className="setup-guide" aria-labelledby="setup-title">
   <p className="eyebrow">Your bakery setup · Step {progress.step+1} of {steps.length}</p>
   <progress max={steps.length} value={progress.step+1} aria-label="Setup guide progress"/>
   <h2 id="setup-title">{step.title}</h2><p>{step.text}</p>
   <button className="primary-button" onClick={openStep}>{step.action}</button>
   <p className="setup-hint">Save your changes on the page before selecting Next. Skipping the guide won’t change your bakery data.</p>
   {error&&<p role="alert">{error}</p>}
   <div className="setup-actions"><button className="outline-button" disabled={busy||progress.step===0} onClick={()=>void persist({step:progress.step-1,status:'active'},true)}>Back</button><button className="primary-button" disabled={busy} onClick={()=>void persist({step:Math.min(5,progress.step+1),status:progress.step===5?'complete':'active'},progress.step!==5)}>{busy?'Saving…':progress.step===5?'Finish guide':'Next'}</button><button className="outline-button" disabled={busy} onClick={()=>void persist({...progress,status:'skipped'},false)}>Skip for now</button></div>
  </section>}
 </>
}
