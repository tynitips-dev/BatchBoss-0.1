# BatchBoss Web Portal

The web portal uses the same Firebase project as the Android app. Each account is linked to a bakery workspace and all operational data lives below `bakeries/{bakeryId}`.

## Firebase Console setup

1. In Firebase Authentication, enable **Email/Password**.
2. Create a Firestore database in production mode.
3. Create a Firebase Web App and copy its configuration values into a new `.env` file based on `.env.example`.
4. Install the Firebase CLI, sign in and deploy the rules from this folder:

   ```bash
   firebase use batch-boss-8a6c5
   firebase deploy --only firestore:rules,storage
   ```

5. Download the Android `google-services.json` from the same Firebase project and place it at `app/google-services.json` for local/release builds. Do not commit that file.

## Local development

```bash
npm install
npm run dev
```

## Production build for Xneelo

```bash
npm run build
```

Upload the **contents** of `dist/` to the intended web subdomain, such as `app.batchboss.co.za`. Keep the public marketing website at `www.batchboss.co.za`.

## Data layout

```text
users/{uid}
bakeries/{bakeryId}
bakeries/{bakeryId}/members/{uid}
bakeries/{bakeryId}/customers/{customerId}
bakeries/{bakeryId}/recipes/{recipeId}
bakeries/{bakeryId}/inventory/{inventoryId}
bakeries/{bakeryId}/orders/{orderId}
bakeries/{bakeryId}/invoices/{invoiceId}
```

Firebase Authentication and Firestore are the shared source of truth. The Android Room database remains the offline cache; version 11 assigns records to the same `bakeryId` used by the website. The app's cloud-sync action uploads recipes, recipe ingredients, stock and customers to the signed-in bakery workspace.

The retired `server.js`/JSON-file backend must not be deployed. It had no account-level authorisation and was only suitable for local prototyping.
