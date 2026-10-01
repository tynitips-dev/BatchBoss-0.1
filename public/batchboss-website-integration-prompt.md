# BatchBoss Packaging & Website Integration Specification

> **Instructions for ChatGPT / Developer:**
> Use this specification to implement or verify the Packaging & Inventory module and Invoice Packaging integration on the BatchBoss website. The website must seamlessly collaborate and synchronize in real-time with the BatchBoss Android application.

---

## 1. System Overview & Architecture

BatchBoss uses a centralized multi-tenant workspace architecture where every bakery has its own workspace identified by `bakeryId` (e.g. `bakery_1`, `bakery_cape_sweet_treats_1`).

- **Firebase Firestore Path:**
  `bakeries/{bakeryId}/packaging/{packagingId}`
- **Web API Endpoint (if using REST):**
  `/api/bakery/:bakeryId/packaging`
- **Currency:** South African Rand (`ZAR`, formatted as `R0.00`)

Both the Android application and the website access and modify the same Firestore collections and fields.

---

## 2. Standard Packaging Schema & Field Names

Every packaging record MUST use the exact field names and types listed below:

| Field Name | Type | Description | Required | Example |
| :--- | :--- | :--- | :---: | :--- |
| `id` | Number / Long | Unique identifier (epoch milliseconds or auto-id) | Yes | `1790075000000` |
| `bakeryId` | String | Bakery workspace identifier | Yes | `"bakery_1"` |
| `userId` | Number / Long | ID of baker who created the item | No | `0` |
| `name` | String | Packaging item name | Yes | `"10-inch White Cake Box (Tall)"` |
| `category` | String | Categorization | Yes | `"Cake boxes"` |
| `unit` | String | Unit of measurement | Yes | `"pcs"` (or `"boxes"`, `"rolls"`, `"m"`) |
| `packagePrice`| Number (Float) | Bulk purchase price paid for the package | Yes | `250.00` |
| `packageQuantity` | Number (Float) | Number of units inside the bulk package | Yes | `25.0` |
| `gramsPerUnit`| Number (Float) | Optional weight in grams | No | `0.0` |
| `unitPrice` | Number (Float) | **Cost per unit** (`packagePrice / packageQuantity`)| Yes | `10.00` |
| `currentStock`| Number (Float) | Current units on hand | Yes | `45.0` |
| `minStock` | Number (Float) | Low-stock threshold | Yes | `10.0` |
| `isLowStock` | Boolean | `true` if `currentStock <= minStock` | Yes | `false` |
| `alertEnabled`| Boolean | Whether low-stock alerts are active | Yes | `true` |
| `barcode` | String | Optional barcode or SKU | No | `""` |
| `supplier` | String | Vendor / supplier name | No | `"BakePak Supplies"` |
| `notes` | String | Dimensions, capacity, or notes | No | `"Fits 8\" and 10\" drip cakes"` |
| `createdAt` | Number (Long) | Epoch milliseconds of creation | Yes | `1790075000000` |
| `updatedAt` | Number (Long) | Epoch milliseconds of last edit | Yes | `1790075000000` |

### Calculation Formulas:
1. **Cost per unit (`unitPrice`):**
   ```js
   const unitPrice = packageQuantity > 0 ? (packagePrice / packageQuantity) : 0;
   ```
2. **Low-stock condition (`isLowStock`):**
   ```js
   const isLowStock = currentStock <= minStock;
   ```

---

## 3. Standard Packaging Categories

The website UI should offer these 9 pre-defined categories (with filter pills and dropdown selection):
1. `Cake boxes`
2. `Cupcake boxes`
3. `Bento boxes`
4. `Cake boards`
5. `Ribbon`
6. `Stickers and labels`
7. `Bags`
8. `Containers`
9. `Other`

---

## 4. Website Packaging Section Requirements

1. **Navigation:** Add a dedicated **Packaging** section or sub-tab under Stock & Inventory.
2. **List & Cards:**
   - Display items with Name, Category badge, Current Stock vs Minimum Stock, Cost per unit, and Low-stock indicator.
   - Total Packaging Valuation = `sum(currentStock * unitPrice)`.
3. **Filtering & Search:**
   - Filter by category pills (*All*, *Cake boxes*, *Cupcake boxes*, etc.).
   - Tabs: *All Items*, *Low Stock Alerts*, *In Stock*.
   - Live search matching `name`, `category`, `supplier`, and `notes`.
4. **CRUD Actions:**
   - **Add Packaging:** Modal/form capturing all fields, with live calculation of `unitPrice`.
   - **Edit Packaging:** Full edit dialog.
   - **Quick Restock:** Dialog to adjust package price, package quantity, current stock, and min stock.
   - **Delete Packaging:** Confirmation dialog with deletion from Firestore.
5. **No Sample Data:** New accounts start with an empty packaging list.

---

## 5. Invoice Integration Requirements

When creating or editing invoices on the website:

1. **Packaging Selection:**
   - Allow users to click **"+ Add Packaging"** to pick from saved packaging items.
2. **Quantity & Line Total:**
   - Each selected packaging item has:
     - `quantity` (editable or stepper)
     - `unitPrice` (cost/price per unit)
     - `lineTotal = quantity * unitPrice`
3. **Calculations & Summary:**
   - **Products Subtotal:** Total of all baked goods / custom products.
   - **Packaging Total:** Separate summary row showing total packaging costs billed:
     ```js
     const packagingTotal = packagingItems.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
     ```
   - **Subtotal:** `productsSubtotal + packagingTotal`
   - **Discount:** Subtracted from subtotal.
   - **VAT / Tax (15% in SA):** Applied to `(subtotal - discount)`.
   - **Total Due (Grand Total):** `(subtotal - discount) + taxAmount`.
   - **Total Cost:** Includes both recipe/product ingredient costs AND packaging costs.
   - **Estimated Profit:** `totalDue - totalCost`.
4. **Invoice Storage:**
   - Save `packagingTotal` (Number) and `packagingItemsJson` (or `packagingItems` Array) directly on the invoice record.

---

## 6. Example Firestore Integration Code (JavaScript / TypeScript)

```javascript
import { getFirestore, collection, doc, setDoc, deleteDoc, onSnapshot, getDocs } from "firebase/firestore";

const db = getFirestore();

// 1. Listen to real-time packaging updates for a bakery
export function subscribeToPackaging(bakeryId, callback) {
  const colRef = collection(db, "bakeries", bakeryId, "packaging");
  return onSnapshot(colRef, (snapshot) => {
    const items = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    callback(items);
  });
}

// 2. Save or update packaging item
export async function savePackaging(bakeryId, item) {
  const itemId = item.id ? String(item.id) : String(Date.now());
  const docRef = doc(db, "bakeries", bakeryId, "packaging", itemId);
  
  const unitPrice = item.packageQuantity > 0 ? (item.packagePrice / item.packageQuantity) : 0;
  const isLowStock = item.currentStock <= item.minStock;

  const payload = {
    ...item,
    id: Number(itemId),
    bakeryId,
    unitPrice,
    isLowStock,
    updatedAt: Date.now(),
    createdAt: item.createdAt || Date.now()
  };

  await setDoc(docRef, payload, { merge: true });
  return payload;
}

// 3. Delete packaging item
export async function deletePackaging(bakeryId, itemId) {
  const docRef = doc(db, "bakeries", bakeryId, "packaging", String(itemId));
  await deleteDoc(docRef);
}
```

---

## 7. Direct Collaboration Checklist

- [x] Firestore collection: `bakeries/{bakeryId}/packaging`
- [x] All 14 field names match the Android app exactly.
- [x] Unit price formula is `packagePrice / packageQuantity`.
- [x] Invoices calculate and store `packagingTotal`.
- [x] Profit margin formulas include packaging costs.
- [x] Zero initial mock/sample packaging for clean new baker accounts.
