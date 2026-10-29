import { initializeApp } from 'firebase/app'
import { getAuth, connectAuthEmulator } from 'firebase/auth'
import { getFirestore, connectFirestoreEmulator } from 'firebase/firestore'
import { getStorage, connectStorageEmulator } from 'firebase/storage'

import { getFunctions, connectFunctionsEmulator } from 'firebase/functions'
export const referralPilot = import.meta.env.VITE_PARTNER_REFERRAL_EMULATOR === 'true'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID,
}

export const firebaseConfigured = Boolean(firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId && firebaseConfig.storageBucket && firebaseConfig.messagingSenderId && firebaseConfig.appId)
if (referralPilot && (!['localhost','127.0.0.1'].includes(window.location.hostname) ||
    !String(firebaseConfig.projectId).startsWith('demo-'))) {
  throw new Error('Referral testing requires localhost and a demo Firebase project.')
}
export const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)
export const storage = getStorage(app)

export const bakerFunctions = getFunctions(app, 'europe-west1')
if (referralPilot) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099')
  connectFirestoreEmulator(db, '127.0.0.1', 8080)
  connectFunctionsEmulator(bakerFunctions, '127.0.0.1', 5001)
  connectStorageEmulator(storage, '127.0.0.1', 9199)
}
