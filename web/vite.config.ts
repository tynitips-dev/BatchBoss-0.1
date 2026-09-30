import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

const firebaseSettings = [
  'VITE_FIREBASE_API_KEY',
  'VITE_FIREBASE_AUTH_DOMAIN',
  'VITE_FIREBASE_PROJECT_ID',
  'VITE_FIREBASE_STORAGE_BUCKET',
  'VITE_FIREBASE_MESSAGING_SENDER_ID',
  'VITE_FIREBASE_APP_ID',
] as const

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_FIREBASE_')
  const missing = firebaseSettings.filter(name => !env[name]?.trim())

  if (command === 'build' && missing.length) {
    throw new Error(
      `Missing Firebase settings: ${missing.join(', ')}. ` +
      'Create web/.env from web/.env.example with the Firebase Web Portal settings, then rebuild.',
    )
  }

  return {
    plugins: [react()],
    build: { outDir: 'dist', emptyOutDir: true },
  }
})
