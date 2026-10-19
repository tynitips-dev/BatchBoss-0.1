import { getAnalytics, isSupported, logEvent, setAnalyticsCollectionEnabled } from 'firebase/analytics'
import { app } from './firebase'

const measurementId=String(import.meta.env.VITE_FIREBASE_MEASUREMENT_ID||'').trim()
const consentKey='batchboss_analytics_consent'
let started=false
export function hasAnalyticsConfigured(){return /^G-[A-Z0-9]+$/.test(measurementId)}
export function analyticsConsent(){return localStorage.getItem(consentKey)==='yes'}
export async function startAnalytics(){
 if(started||!hasAnalyticsConfigured()||!analyticsConsent())return
 if(!(await isSupported()))return
 const analytics=getAnalytics(app)
 setAnalyticsCollectionEnabled(analytics,true)
 logEvent(analytics,'page_view',{page_title:'BatchBoss Web App',page_location:location.origin+location.pathname})
 started=true
}
export function acceptAnalytics(){
 localStorage.setItem(consentKey,'yes')
 void startAnalytics()
}
export function declineAnalytics(){localStorage.setItem(consentKey,'no')}
