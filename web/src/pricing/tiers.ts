export type Period = 'month' | 'year'
export interface Tier {
  name: 'Starter' | 'Pro' | 'Advanced'
  description: string
  features: string[]
  priceId: Record<Period, string>
}

const env = import.meta.env
export const tiers: Tier[] = [
  {
    name: 'Starter', description: 'Baker Finder',
    features: ['Publish a bakery profile', 'Appear in Find a Baker'],
    priceId: { month: env.VITE_PADDLE_STARTER_MONTH, year: env.VITE_PADDLE_STARTER_YEAR },
  }, {
    name: 'Pro', description: 'Batch Boss Pro',
    features: ['Cost recipes and track stock', 'Create quotes, invoices and receipts'],
    priceId: { month: env.VITE_PADDLE_PRO_MONTH, year: env.VITE_PADDLE_PRO_YEAR },
  }, {
    name: 'Advanced', description: 'Batch Boss Full Suite',
    features: ['Everything in Pro', 'Baker Finder profile', 'Three new Marketplace listings per month'],
    priceId: { month: env.VITE_PADDLE_ADVANCED_MONTH, year: env.VITE_PADDLE_ADVANCED_YEAR },
  },
]
