# BatchBoss Administrator Portal

Secure React/Vite administrator UI for `admin.batchboss.co.za`.

## Setup

1. Copy `.env.example` to `.env`.
2. Use the same Firebase Web App values as the BatchBoss customer portal.
3. Install and build:

```bash
npm install
npm run build
```

4. Upload the contents of `dist/` to the Xneelo document root for `admin.batchboss.co.za`.

The Firebase functions must be deployed first from the repository root. Set the `ADMIN_EMAIL` parameter to the approved administrator email during deployment. On first sign-in, select **Activate approved administrator** and then sign in again if prompted.

Never put a Firebase service-account JSON file, private key or Admin SDK credentials in this folder or on Xneelo.
