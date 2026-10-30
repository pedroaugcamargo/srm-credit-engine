import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchStatement, settleReceivable, simulatePricing } from './client'
import { ApiError } from './problem'

const json = (body: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(body), {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })

function mockFetch(response: Response | Error) {
  const fn = vi.fn<typeof fetch>(() =>
    response instanceof Error
      ? Promise.reject(response)
      : Promise.resolve(response),
  )
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('api client', () => {
  it('mantém os valores monetários como string', async () => {
    mockFetch(
      new Response(
        '{"faceValue":"100000.00","presentValueBrl":"92859.94","netAmount":"92859.94"}',
        { status: 200 },
      ),
    )

    const result = await simulatePricing({
      faceValue: '100000.00',
      receivableType: 'DUPLICATA_MERCANTIL',
      dueDate: '2026-12-30',
      paymentCurrency: 'BRL',
    })

    expect(result.presentValueBrl).toBe('92859.94')
    expect(typeof result.netAmount).toBe('string')
  })

  it('envia a Idempotency-Key na liquidação', async () => {
    const fetchMock = mockFetch(json({ id: 's1' }, { status: 201 }))

    await settleReceivable(
      { receivableId: 'r1', paymentCurrency: 'BRL' },
      'b6b1a0c2-7d0e-4c1a-9f7a-2a4d8e0f1c33',
    )

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/settlements')
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBe(
      'b6b1a0c2-7d0e-4c1a-9f7a-2a4d8e0f1c33',
    )
  })

  it('trata o 200 com Idempotent-Replayed como sucesso', async () => {
    mockFetch(
      json(
        { id: 's1' },
        { status: 200, headers: { 'Idempotent-Replayed': 'true' } },
      ),
    )

    const outcome = await settleReceivable(
      { receivableId: 'r1', paymentCurrency: 'BRL' },
      'b6b1a0c2-7d0e-4c1a-9f7a-2a4d8e0f1c33',
    )

    expect(outcome.replayed).toBe(true)
    expect(outcome.settlement.id).toBe('s1')
  })

  it('transforma ProblemDetail em ApiError com o detail', async () => {
    mockFetch(
      json(
        {
          status: 409,
          detail: 'Este recebível já foi liquidado.',
          code: 'RECEIVABLE_ALREADY_SETTLED',
        },
        { status: 409 },
      ),
    )

    const error = await settleReceivable(
      { receivableId: 'r1', paymentCurrency: 'BRL' },
      'k',
    ).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('RECEIVABLE_ALREADY_SETTLED')
    expect((error as ApiError).message).toBe('Este recebível já foi liquidado.')
  })

  it('transforma falha de rede em ApiError com status 0', async () => {
    mockFetch(new TypeError('Failed to fetch'))

    const error = await fetchStatement({
      from: '2026-10-01T00:00:00-03:00',
      to: '2026-10-31T23:59:59.999999-03:00',
    }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
  })

  it('monta a query do extrato só com os filtros informados', async () => {
    const fetchMock = mockFetch(
      json({ items: [], page: 0, size: 20, totalElements: 0 }),
    )

    await fetchStatement({
      from: '2026-10-01T00:00:00-03:00',
      to: '2026-10-31T23:59:59.999999-03:00',
      currency: 'USD',
      page: 0,
      size: 20,
    })

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/v1/settlements?from=2026-10-01T00%3A00%3A00-03%3A00&to=2026-10-31T23%3A59%3A59.999999-03%3A00&currency=USD&page=0&size=20',
    )
  })
})
