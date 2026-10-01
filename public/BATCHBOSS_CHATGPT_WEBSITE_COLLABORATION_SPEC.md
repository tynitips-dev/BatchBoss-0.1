# BatchBoss™ — Comprehensive Website & Android Collaboration Specification

> **Copy & Paste this entire file into ChatGPT (or upload this `.md` file to ChatGPT):**
> 
> "ChatGPT, here is the official BatchBoss collaboration specification for our Android mobile app and online website. Please analyze this specification and help me implement, review, and verify that our online website collaborates 100% in real-time with the BatchBoss Android app. Make sure all schemas, API endpoints, packaging calculations, and invoice totals match exactly."

---

## 1. Executive Summary & Purpose

**BatchBoss™** is a professional bakery operations, recipe costing, batch production, packaging, and client invoicing system designed for artisan bakers, commercial bakeries, and home confectioners.

To provide seamless collaboration across mobile devices and desktop computers:
1. **The Android App** is used on the bakery floor for fast batch scaling, ingredient stock checks, timer management, barcode scanning, packaging counts, and customer order management.
2. **The Online Website** is used for desktop administration, customer order portal, packaging inventory management, and client invoice generation.
3. **Data Synchronicity:** Both platforms share the exact same database structure and business logic. A baker can restock cake boxes or create an invoice on the mobile app, and the website updates in real-time (and vice-versa).

---

## 2. Shared Multi-Tenant Architecture

BatchBoss organizes all data by **`bakeryId`** (e.g. `bakery_1`, `bakery_sweet_treats_1`, `bakery_cape_artisan_2`).

### 2.1 Synchronization Channels

The website and Android app support two synchronization layers:

1. **Firebase Cloud Firestore (Real-Time Cloud Sync):**
   - **Root Collection Path:** `bakeries/{bakeryId}`
   - **Subcollections:**
     - `bakeries/{bakeryId}/profile`
     - `bakeries/{bakeryId}/packaging`
     - `bakeries/{bakeryId}/invoices`
     - `bakeries/{bakeryId}/recipes`
     - `bakeries/{bakeryId}/ingredients`
     - `bakeries/{bakeryId}/customers`
     - `bakeries/{bakeryId}/batches`

2. **Web REST API (Self-Hosted / Direct Node.js Sync):**
   - Base URL: `http://<your-server-or-domain>:3000`
   - Data Store: Persistent JSON storage (`bakery_store.json`) synced with Android via HTTP JSON payloads.

3. **Currency & Localization:**
   - **Currency Code:** South African Rand (`ZAR`), displayed with prefix `R` (e.g. `R250.00`).
   - **Standard VAT:** 15.0% (configurable per bakery profile).
   - **No Dummy / Mock Data:** New accounts start with empty collections.

---

## 3. Packaging Module Specification (Schema & Logic)

The Packaging module tracks boxes, boards, ribbons, bags, and stickers used to package finished bakery goods.

### 3.1 Data Schema (`packaging`)

| Field Name | Type | Description | Required | Example |
| :--- | :--- | :--- | :---: | :--- |
| `id` | Number (Long) | Unique identifier (epoch ms or numeric ID) | Yes | `1790075000000` |
| `bakeryId` | String | Bakery workspace identifier | Yes | `"bakery_1"` |
| `userId` | Number (Long) | Baker account ID | No | `0` |
| `name` | String | Full name of packaging item | Yes | `"10-inch White Tall Cake Box"` |
| `category` | String | Packaging category (see 3.2) | Yes | `"Cake boxes"` |
| `unit` | String | Stock counting unit | Yes | `"pcs"`, `"boxes"`, `"rolls"` |
| `packagePrice` | Number (Float) | Bulk purchase price paid for whole pack | Yes | `250.00` |
| `packageQuantity` | Number (Float) | Units inside bulk pack | Yes | `25.0` |
| `gramsPerUnit` | Number (Float) | Optional weight in grams | No | `0.0` |
| `unitPrice` | Number (Float) | **Cost per unit** (`packagePrice / packageQuantity`) | Yes | `10.00` |
| `currentStock` | Number (Float) | Units currently on hand in bakery | Yes | `45.0` |
| `minStock` | Number (Float) | Low-stock threshold | Yes | `10.0` |
| `isLowStock` | Boolean | True if `currentStock <= minStock` | Yes | `false` |
| `alertEnabled` | Boolean | Whether low-stock alert is active | Yes | `true` |
| `barcode` | String | SKU or barcode string | No | `""` |
| `supplier` | String | Vendor or supplier name | No | `"BakePak Cape"` |
| `notes` | String | Dimension or usage notes | No | `"Fits 8\" and 10\" drip cakes"` |
| `createdAt` | Number (Long) | Epoch milliseconds created | Yes | `1790075000000` |
| `updatedAt` | Number (Long) | Epoch milliseconds last updated | Yes | `1790075000000` |

### 3.2 Standard Packaging Categories
The website and Android app share these 9 standardized categories:
1. `Cake boxes`
2. `Cupcake boxes`
3. `Bento boxes`
4. `Cake boards`
5. `Ribbon`
6. `Stickers and labels`
7. `Bags`
8. `Containers`
9. `Other`

### 3.3 Core Formulas
```javascript
// Cost per unit (single box, board, or meter)
const unitPrice = packageQuantity > 0 ? (packagePrice / packageQuantity) : 0;

// Low-stock flag
const isLowStock = currentStock <= minStock;

// Packaging valuation
const itemValuation = currentStock * unitPrice;
const totalPackagingValuation = packagingList.reduce((sum, item) => sum + (item.currentStock * item.unitPrice), 0);
```

---

## 4. Invoice & Billing Specification (with Packaging Integration)

When invoices are generated, bakers can attach both **Products (Baked Goods)** and **Packaging Items (Boxes, Ribbons, Boards)** so that the true cost and billable amounts are calculated accurately.

### 4.1 Data Schema (`invoices`)

| Field Name | Type | Description | Required | Example |
| :--- | :--- | :--- | :---: | :--- |
| `id` | Number (Long) | Unique invoice ID | Yes | `1790080000000` |
| `invoiceNumber` | String | Human readable invoice number | Yes | `"INV-1001"` |
| `bakeryId` | String | Bakery workspace identifier | Yes | `"bakery_1"` |
| `clientName` | String | Customer or company name | Yes | `"Sarah Jenkins"` |
| `clientPhone` | String | Contact phone number | No | `"+27 82 123 4567"` |
| `orderDescription`| String | Description or order notes | No | `"Custom 2-Tier Birthday Cake"` |
| `status` | String | Payment status (`"UNPAID"`, `"PAID"`, `"DRAFT"`, `"OVERDUE"`) | Yes | `"UNPAID"` |
| `dueDate` | String | Payment due date (`YYYY-MM-DD`) | Yes | `"2026-10-15"` |
| `productsSubtotal`| Number (Float) | Subtotal of baked goods / products | Yes | `850.00` |
| `packagingTotal` | Number (Float) | Subtotal of packaging items billed | Yes | `65.00` |
| `subtotal` | Number (Float) | `productsSubtotal + packagingTotal` | Yes | `915.00` |
| `discount` | Number (Float) | Discount amount subtracted from subtotal | No | `0.00` |
| `taxRate` | Number (Float) | Tax percentage (e.g. 15.0 for 15% VAT) | Yes | `15.0` |
| `taxAmount` | Number (Float) | Calculated VAT amount | Yes | `137.25` |
| `totalDue` | Number (Float) | Grand Total due from client | Yes | `1052.25` |
| `totalCost` | Number (Float) | Recipe ingredients cost + Packaging cost | Yes | `380.00` |
| `estimatedProfit`| Number (Float) | Estimated Profit (`totalDue - totalCost`) | Yes | `672.25` |
| `lineItems` | Array<Object> | List of baked goods / custom line items | Yes | `[...]` |
| `packagingItems` | Array<Object> | List of packaging items used | Yes | `[...]` |
| `createdAt` | Number (Long) | Creation timestamp (epoch ms) | Yes | `1790080000000` |
| `updatedAt` | Number (Long) | Last update timestamp (epoch ms) | Yes | `1790080000000` |

### 4.2 Invoice Line Item Schemas

#### Product Line Item:
```json
{
  "productId": 101,
  "name": "Red Velvet Celebration Cake (8-inch)",
  "quantity": 1,
  "unitPrice": 850.00,
  "costPerUnit": 320.00,
  "lineTotal": 850.00
}
```

#### Packaging Line Item:
```json
{
  "packagingId": 501,
  "name": "10-inch White Tall Cake Box",
  "category": "Cake boxes",
  "unit": "pcs",
  "quantity": 1,
  "unitPrice": 25.00,
  "lineTotal": 25.00
}
```

### 4.3 Financial Calculation Engine
```javascript
function calculateInvoiceTotals(products, packagingItems, discount = 0, taxRate = 15.0) {
  // 1. Calculate Products Subtotal
  const productsSubtotal = products.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
  
  // 2. Calculate Packaging Subtotal
  const packagingTotal = packagingItems.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
  
  // 3. Combined Subtotal
  const subtotal = productsSubtotal + packagingTotal;
  
  // 4. Taxable Amount (after discount)
  const taxableAmount = Math.max(0, subtotal - discount);
  
  // 5. VAT / Tax Amount
  const taxAmount = (taxRate > 0) ? (taxableAmount * (taxRate / 100)) : 0;
  
  // 6. Total Due (Grand Total)
  const totalDue = taxableAmount + taxAmount;
  
  // 7. Total Cost & Estimated Profit
  const productCosts = products.reduce((sum, item) => sum + (item.quantity * (item.costPerUnit || 0)), 0);
  const packagingCosts = packagingItems.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
  const totalCost = productCosts + packagingCosts;
  
  const estimatedProfit = totalDue - totalCost;
  const profitMarginPercent = totalDue > 0 ? (estimatedProfit / totalDue) * 100 : 0;

  return {
    productsSubtotal: Number(productsSubtotal.toFixed(2)),
    packagingTotal: Number(packagingTotal.toFixed(2)),
    subtotal: Number(subtotal.toFixed(2)),
    discount: Number(discount.toFixed(2)),
    taxRate: Number(taxRate.toFixed(2)),
    taxAmount: Number(taxAmount.toFixed(2)),
    totalDue: Number(totalDue.toFixed(2)),
    totalCost: Number(totalCost.toFixed(2)),
    estimatedProfit: Number(estimatedProfit.toFixed(2)),
    profitMarginPercent: Number(profitMarginPercent.toFixed(1))
  };
}
```

---

## 5. REST Web API Endpoints (`server.js`)

When syncing directly via HTTP without Firestore:

### 5.1 Full Sync Endpoint
- **URL:** `GET /api/bakery/:bakeryId/sync`
- **Response (200 OK):**
```json
{
  "profile": {
    "bakeryId": "bakery_1",
    "bakeryName": "Artisan Sweet Delights",
    "fullName": "Head Baker",
    "email": "orders@bakery.com",
    "phone": "+27 82 555 1234",
    "city": "Cape Town",
    "operatingModel": "Artisan Kitchen",
    "currency": "ZAR (R)"
  },
  "recipes": [],
  "inventory": [],
  "packaging": [],
  "invoices": [],
  "customers": []
}
```

- **URL:** `POST /api/bakery/:bakeryId/sync`
- **Request Body:** JSON object containing `{ profile, recipes, inventory, packaging, invoices, customers }`
- **Response (200 OK):** `{ "success": true, "message": "Synced successfully" }`

### 5.2 Packaging Endpoints
- **List Items:** `GET /api/bakery/:bakeryId/packaging` -> returns `Array<PackagingItem>`
- **Create / Update Item:** `POST /api/bakery/:bakeryId/packaging`
  - Body: Complete packaging JSON object. If `unitPrice` is omitted, server computes `packagePrice / packageQuantity`.
  - Response (200 OK): `{ "success": true, "packaging": { ... } }`
- **Delete Item:** `DELETE /api/bakery/:bakeryId/packaging`
  - Body: `{ "id": 1790075000000 }`
  - Response (200 OK): `{ "success": true }`

---

## 6. Client Implementation Guide (JavaScript / React / Vue)

### 6.1 Firestore Real-Time Synchronization Code

```javascript
import { initializeApp } from "firebase/app";
import { 
  getFirestore, 
  collection, 
  doc, 
  setDoc, 
  deleteDoc, 
  onSnapshot 
} from "firebase/firestore";

const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT.appspot.com",
  messagingSenderId: "SENDER_ID",
  appId: "APP_ID"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

// 1. Subscribe to real-time packaging inventory
export function listenToPackaging(bakeryId, callback) {
  const collRef = collection(db, "bakeries", bakeryId, "packaging");
  return onSnapshot(collRef, (snapshot) => {
    const items = snapshot.docs.map(doc => ({ id: Number(doc.id), ...doc.data() }));
    callback(items);
  });
}

// 2. Subscribe to real-time invoices
export function listenToInvoices(bakeryId, callback) {
  const collRef = collection(db, "bakeries", bakeryId, "invoices");
  return onSnapshot(collRef, (snapshot) => {
    const invoices = snapshot.docs.map(doc => ({ id: Number(doc.id), ...doc.data() }));
    callback(invoices);
  });
}

// 3. Save or update packaging item
export async function savePackagingItem(bakeryId, item) {
  const id = item.id || Date.now();
  const unitPrice = item.packageQuantity > 0 ? (item.packagePrice / item.packageQuantity) : 0;
  const isLowStock = Number(item.currentStock) <= Number(item.minStock);

  const payload = {
    id: Number(id),
    bakeryId,
    name: item.name.trim(),
    category: item.category || "Other",
    unit: item.unit || "pcs",
    packagePrice: Number(item.packagePrice) || 0,
    packageQuantity: Number(item.packageQuantity) || 1,
    unitPrice: Number(unitPrice.toFixed(4)),
    currentStock: Number(item.currentStock) || 0,
    minStock: Number(item.minStock) || 0,
    isLowStock,
    alertEnabled: item.alertEnabled ?? true,
    supplier: item.supplier || "",
    notes: item.notes || "",
    barcode: item.barcode || "",
    updatedAt: Date.now(),
    createdAt: item.createdAt || Date.now()
  };

  const docRef = doc(db, "bakeries", bakeryId, "packaging", String(id));
  await setDoc(docRef, payload, { merge: true });
  return payload;
}

// 4. Delete packaging item
export async function deletePackagingItem(bakeryId, itemId) {
  const docRef = doc(db, "bakeries", bakeryId, "packaging", String(itemId));
  await deleteDoc(docRef);
}
```

---

## 7. ChatGPT Verification Checklist

When ChatGPT audits or builds your website:

- [ ] **Exact Field Names:** All 18 packaging fields and 19 invoice fields match case-sensitively.
- [ ] **Packaging Unit Price Calculation:** `packagePrice / packageQuantity` correctly updates when either changes.
- [ ] **Packaging Total on Invoice:** Invoices show a distinct **Packaging Total** line item and include it in `subtotal`.
- [ ] **Stock Deduction on Invoice Paid:** When an invoice marked "PAID", the website can deduct the packaging quantities from `currentStock`.
- [ ] **Low-Stock Alerting:** Items with `currentStock <= minStock` display a red/amber low-stock badge.
- [ ] **Multi-Tenant Isolation:** All Firestore calls include `/bakeries/${bakeryId}/...`.
- [ ] **South African Currency:** Formatted as `R` (e.g. `R120.00`).
- [ ] **Clean Initial State:** Zero hardcoded fake items for new accounts.

---
*Generated by BatchBoss™ Unified Mobile & Web Architecture Engine.*
