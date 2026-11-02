import React, { useEffect, useState } from 'react'
import { acceptAnalytics, declineAnalytics, hasAnalyticsConfigured, analyticsConsent, startAnalytics } from './visitorAnalytics'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles.css'

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
  </React.StrictMode>,
)

import './batchboss-theme.css'
import './workspace-extras.css'
