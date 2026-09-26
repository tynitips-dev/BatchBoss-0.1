export type UserProfile = {
  uid: string
  bakeryId: string
  firstName: string
  surname: string
  email: string
  role: 'owner' | 'staff' | 'admin'
  subscriptionPlan?: 'free' | 'monthly' | 'annual' | 'promo'
  subscriptionStatus?: 'free' | 'active' | 'trialing' | 'past_due' | 'cancelled' | 'expired'
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

export type ModuleKey =
  | 'home' | 'recipes' | 'ingredients' | 'inventory' | 'suppliers'
  | 'invoices' | 'quotes' | 'receipts' | 'products' | 'tasks'
  | 'customers' | 'orders' | 'tools' | 'subscription' | 'settings'
