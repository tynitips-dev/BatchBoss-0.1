export type UserProfile = {
  uid: string
  bakeryId: string
  firstName: string
  surname: string
  email: string
  role: 'owner' | 'staff' | 'admin'
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

export type ModuleKey = 'recipes' | 'inventory' | 'customers' | 'orders' | 'invoices'
