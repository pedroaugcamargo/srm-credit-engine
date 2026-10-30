import type { Decimal, PaymentCurrency } from '../api/types'
import { formatMoney } from '../lib/money'

interface MoneyProps {
  value: Decimal
  currency: PaymentCurrency
}

export function Money({ value, currency }: MoneyProps) {
  return <span className="money">{formatMoney(value, currency)}</span>
}
