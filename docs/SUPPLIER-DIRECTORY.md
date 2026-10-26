# Approved supplier directory

The customer portal adds Find suppliers for all plans, plus Ingredients and Packaging shortcuts. It keeps bakery-owned supplier records separate. Search filters loaded pages by business/location and category; Load more continues paging.

## Release dependency

Deploy the companion supplier privacy fix BEFORE connecting this UI. It keeps the existing callable `listPublicSuppliers` contract and uses random public listing IDs rather than supplier account IDs. An approved supplier must also have an active, recognised, unexpired entitlement and a published complete profile. Existing profiles must be republished by a supplier administrator to receive their public listing ID. No payment or entitlement is created by publication.

Configure `web/.env` with the exact deployed callable URL:

```dotenv
VITE_SUPPLIER_DIRECTORY_URL=https://YOUR-DEPLOYED-listPublicSuppliers-URL
```

Keep the existing baker Firebase configuration. The customer makes a Firebase callable-format POST with credentials omitted; it does not reuse baker authentication in the supplier project. Blank configuration shows a connection-pending state. No demo suppliers or private contact fields are displayed.

Build `web` and publish `web/dist` through the existing Xneelo process. There is no Firebase Hosting target in this repository. Customer Firestore rules need no changes.

Before release, check an approved paid published supplier appears; suspended, expired and unpublished suppliers do not. Check pagination, category/location search, retry and empty states. Refresh to see revocations; existing on-screen results remain until refresh or navigation. This first integration displays business name, category, location, description and delivery details; public contact sharing and artwork are separate supplier features.

Code validation: TypeScript compilation and supplier backend regression tests. Production authentication, Firebase deployment and Xneelo smoke tests must be verified separately.
