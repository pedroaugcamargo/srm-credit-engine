// Contratos da API (backend em /api/v1).
// Dinheiro, taxas e câmbio trafegam como string decimal ("92859.94") e
// continuam string no front: nunca viram number (SPEC, seção 3).

/** Número decimal em string, exatamente como o backend enviou. */
export type Decimal = string

export type PaymentCurrency = 'BRL' | 'USD'
export type ReceivableType = 'DUPLICATA_MERCANTIL' | 'CHEQUE'
export type ReceivableStatus = 'OPEN' | 'SETTLED'

/** Data no formato ISO (AAAA-MM-DD). */
export type IsoDate = string
/** Instante no formato ISO com offset. */
export type IsoDateTime = string

export interface SimulationRequest {
  faceValue: Decimal
  receivableType: ReceivableType
  dueDate: IsoDate
  paymentCurrency: PaymentCurrency
}

export interface SimulationResponse {
  faceValue: Decimal
  termMonths: Decimal
  baseRate: Decimal
  spread: Decimal
  presentValueBrl: Decimal
  discountBrl: Decimal
  paymentCurrency: PaymentCurrency
  fxRate: Decimal | null
  netAmount: Decimal
}

export interface Cedente {
  id: string
  nome: string
}

export interface ReceivableRequest {
  cedenteId: string
  type: ReceivableType
  faceValue: Decimal
  dueDate: IsoDate
}

export interface ReceivableResponse {
  id: string
  cedenteId: string
  type: ReceivableType
  faceValue: Decimal
  dueDate: IsoDate
  status: ReceivableStatus
  version: number
}

export interface SettlementRequest {
  receivableId: string
  paymentCurrency: PaymentCurrency
}

export interface SettlementResponse {
  id: string
  receivableId: string
  cedenteId: string
  idempotencyKey: string
  paymentCurrency: PaymentCurrency
  faceValue: Decimal
  termMonths: Decimal
  baseRate: Decimal
  spread: Decimal
  presentValueBrl: Decimal
  discountBrl: Decimal
  fxRateId: string | null
  fxRate: Decimal | null
  netAmount: Decimal
  settledAt: IsoDateTime
}

export interface StatementQuery {
  from: IsoDate
  to: IsoDate
  cedenteId?: string
  currency?: PaymentCurrency
  page?: number
  size?: number
}

export interface StatementItem {
  id: string
  cedenteId: string
  cedenteNome: string
  receivableId: string
  paymentCurrency: PaymentCurrency
  faceValue: Decimal
  presentValueBrl: Decimal
  fxRate: Decimal | null
  netAmount: Decimal
  settledAt: IsoDateTime
}

export interface StatementResponse {
  items: StatementItem[]
  page: number
  size: number
  totalElements: number
}

/** Erro no formato RFC 9457, com o `code` de negócio que o backend acrescenta. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
}
