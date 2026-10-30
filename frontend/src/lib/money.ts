import type { Decimal, PaymentCurrency } from '../api/types'

// Formato que o backend envia: sinal opcional, dígitos, ponto e casas decimais.
const DECIMAL_PATTERN = /^-?\d+(\.\d+)?$/

function isDecimalString(value: string): value is Intl.StringNumericLiteral {
  return DECIMAL_PATTERN.test(value)
}

/**
 * Formata um valor monetário recebido como string, sem passar por number.
 *
 * `Intl.NumberFormat.format` aceita a string como decimal exato (ES2023),
 * então valores acima de 2^53 não perdem precisão. As casas que vierem do
 * backend são exibidas como estão: o front nunca arredonda dinheiro.
 */
export function formatMoney(value: Decimal, currency: PaymentCurrency): string {
  if (!isDecimalString(value)) {
    throw new TypeError(`Valor monetário inválido: "${value}"`)
  }

  const fractionDigits = value.split('.')[1]?.length ?? 0

  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: Math.max(2, fractionDigits),
  }).format(value)
}
