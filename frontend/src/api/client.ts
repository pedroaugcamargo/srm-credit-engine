import { ApiError, readProblem } from './problem'
import type {
  Cedente,
  ReceivableRequest,
  ReceivableResponse,
  SettlementRequest,
  SettlementResponse,
  SimulationRequest,
  SimulationResponse,
  StatementQuery,
  StatementResponse,
} from './types'

// Mesma origem: em dev o Vite faz o proxy, em produção o Nginx.
const BASE_PATH = '/api/v1'

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

interface ApiResponse<T> {
  data: T
  status: number
  headers: Headers
}

async function request<T>(
  path: string,
  { method = 'GET', body, headers, signal }: RequestOptions = {},
): Promise<ApiResponse<T>> {
  let response: Response
  try {
    response = await fetch(`${BASE_PATH}${path}`, {
      method,
      signal,
      headers: {
        Accept: 'application/json, application/problem+json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    // Cancelamento não é erro de rede: deixa o TanStack Query tratar.
    if (signal?.aborted) throw cause
    throw ApiError.network(cause)
  }

  if (!response.ok) {
    throw await readProblem(response)
  }

  return {
    data: (await response.json()) as T,
    status: response.status,
    headers: response.headers,
  }
}

export async function fetchCedentes(signal?: AbortSignal): Promise<Cedente[]> {
  const { data } = await request<Cedente[]>('/cedentes', { signal })
  return data
}

export async function simulatePricing(
  input: SimulationRequest,
  signal?: AbortSignal,
): Promise<SimulationResponse> {
  const { data } = await request<SimulationResponse>('/pricing/simulations', {
    method: 'POST',
    body: input,
    signal,
  })
  return data
}

export async function registerReceivable(
  input: ReceivableRequest,
): Promise<ReceivableResponse> {
  const { data } = await request<ReceivableResponse>('/receivables', {
    method: 'POST',
    body: input,
  })
  return data
}

export interface SettlementOutcome {
  settlement: SettlementResponse
  /** true quando o backend devolveu a liquidação original (retry com a mesma chave). */
  replayed: boolean
}

export async function settleReceivable(
  input: SettlementRequest,
  idempotencyKey: string,
): Promise<SettlementOutcome> {
  const { data, headers } = await request<SettlementResponse>('/settlements', {
    method: 'POST',
    body: input,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return {
    settlement: data,
    replayed: headers.get('Idempotent-Replayed') === 'true',
  }
}

export async function fetchStatement(
  query: StatementQuery,
  signal?: AbortSignal,
): Promise<StatementResponse> {
  const params = new URLSearchParams({ from: query.from, to: query.to })
  if (query.cedenteId) params.set('cedenteId', query.cedenteId)
  if (query.currency) params.set('currency', query.currency)
  if (query.page !== undefined) params.set('page', String(query.page))
  if (query.size !== undefined) params.set('size', String(query.size))

  const { data } = await request<StatementResponse>(
    `/settlements?${params.toString()}`,
    { signal },
  )
  return data
}
