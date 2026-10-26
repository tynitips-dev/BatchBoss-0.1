export type UserProfile = {
  setupGuide?: { step: number; status: 'active' | 'skipped' | 'complete' }
  uid: string
  bakeryId: string
  firstName: string
  surname: string
  email: string
  role: 'owner' | 'staff' | 'admin'
  subscriptionPlan?: 'free' | 'monthly' | 'annual' | 'promo'
  subscriptionStatus?: 'free' | 'active' | 'trialing' | 'past_due' | 'cancelled' | 'expired'
  subscriptionSource?: string
  subscriptionBillingInterval?: string
  paystackSubscriptionCode?: string
  subscriptionExpiresAt?: unknown
}

export type Customer = {
  id: string
  name: string
  phone: string
  email: string
  notes: string
  totalOrders: number
  totalSpend: number
  createdAt?: unknown
}

export type ModuleKey = 'supplierDirectory'
  | 'home' | 'recipes' | 'ingredients' | 'inventory' | 'packaging' | 'suppliers'
  | 'invoices' | 'quotes' | 'receipts' | 'products' | 'tasks'
  | 'customers' | 'orders' | 'tools' | 'subscription' | 'settings'
  | 'documents' | 'reports'

