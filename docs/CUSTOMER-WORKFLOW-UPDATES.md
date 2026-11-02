# Customer and recipe workflow updates

Scope: web UI only. No Functions, subscription migration, entitlement, Firestore Rules or payment changes.

- Quotes, estimates and invoices can select a saved client, copy name/contact/address and store customerId alongside a document snapshot.
- Catalogue and custom quote items support quantities. Added quote and invoice quantities remain editable; line totals are recalculated and invalid quantities cannot be submitted.
- Recipes open read-only and support optional JPEG/PNG/WebP photo uploads, resized to a bounded JPEG saved with the recipe. Three illustrative costing examples have been removed; expense calculators remain.
- Inventory is grouped into ingredients, packaging and finished products. Finished products use the existing Pro catalogue, with manually maintained stock, location and best-before date. Sales do not automatically decrement this stock.
- Product categories are derived from saved records and can be entered freely when editing a product.
- Customer history shows linked orders, invoices and quotes/estimates with item quantities. Legacy records match only a unique email or phone; name-only records are not guessed.
- Reports retain existing calculations and date filter, with clearer sales rows and ingredient/packaging low-stock tables.

Validation: web production build passed; 10 Node checks passed. React form interaction checks with a mocked database passed for client autofill, 70 catalogue items at R14.50, 70 custom items at R10, invoice/order customer linkage and customer order history. Browser visual inspection could not run because the browser runtime download failed. No production writes were made for validation.

Integration: the uploaded web.zip contains additional entitlement refresh changes not yet present on the GitHub base. The scoped App.tsx and WorkspaceExtras.tsx patch was checked against that uploaded source and applies cleanly with whitespace differences ignored. Merge this branch into the current working checkout; preserve existing entitlement changes. Do not replace the entire web folder with this older GitHub base.

Build with the existing web/.env. Publish the resulting web/dist using the established web-hosting process after checking the merged app. No Firebase deployment is needed for this UI-only change.
