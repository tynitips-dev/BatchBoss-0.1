import {metaConsent,setMetaConsent,startMeta} from './metaPixel'
import React, { useEffect, useState } from 'react'
import { acceptAnalytics, declineAnalytics, hasAnalyticsConfigured, analyticsConsent, startAnalytics } from './visitorAnalytics'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles.css'

function MetaConsent(){
 const [choice,setChoice]=useState(()=>metaConsent())
 useEffect(()=>{startMeta()},[])
 return <>{choice===null?<div role="region" aria-label="Optional advertising tracking" style={{position:'fixed',bottom:16,left:16,right:16,margin:'auto',maxWidth:590,background:'#fff',color:'#233449',border:'1px solid #edc9d8',padding:18,borderRadius:14,zIndex:100000,boxShadow:'0 12px 36px #0003'}}><strong>Optional Meta advertising cookies</strong><p>Allow Meta to measure BatchBoss ads and conversion events. You can use BatchBoss without accepting.</p><div style={{display:'flex',gap:10,justifyContent:'flex-end'}}><button onClick={()=>{setMetaConsent(false);setChoice('no')}}>Decline</button><button onClick={()=>{setMetaConsent(true);setChoice('yes')}}>Allow advertising</button></div></div>:<button type="button" style={{position:'fixed',bottom:4,right:8,zIndex:1000,fontSize:11,background:'#fff',color:'#75415a',border:'1px solid #edc9d8',borderRadius:6,padding:'4px 8px'}} onClick={()=>{setMetaConsent(false);setChoice(null)}}>Advertising preferences</button>}</>
}
function AnalyticsConsent(){
 const [choice,setChoice]=useState(()=>typeof localStorage==='undefined'?true:localStorage.getItem('batchboss_analytics_consent')!==null)
 useEffect(()=>{void startAnalytics()},[])
 if(!hasAnalyticsConfigured()||choice)return null
 return <div role="region" aria-label="Optional website analytics" style={{position:'fixed',bottom:16,left:16,right:16,margin:'auto',maxWidth:590,background:'#14213c',color:'#fff',padding:18,borderRadius:14,zIndex:99999,boxShadow:'0 12px 36px #0005'}}><strong>Help us improve BatchBoss</strong><p>May we collect anonymous website-usage statistics? You can continue without accepting.</p><div style={{display:'flex',gap:10,justifyContent:'flex-end'}}><button type="button" onClick={()=>{declineAnalytics();setChoice(true)}}>Not now</button><button type="button" onClick={()=>{acceptAnalytics();setChoice(true)}}>Allow analytics</button></div></div>
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
    <AnalyticsConsent />
    <MetaConsent />
  </React.StrictMode>,
)

import './batchboss-theme.css'
import './workspace-extras.css'
