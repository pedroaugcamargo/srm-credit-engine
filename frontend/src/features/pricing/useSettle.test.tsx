// @vitest-environment jsdom
import {
  QueryClient,
  QueryClientProvider,
  notifyManager,
} from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerReceivable, settleReceivable } from '../../api/client'
import { ApiError } from '../../api/problem'
import type { ReceivableResponse, SettlementResponse } from '../../api/types'
import { useSettle, type SettleInput } from './useSettle'

vi.mock('../../api/client', () => ({
  registerReceivable: vi.fn(),
  settleReceivable: vi.fn(),
}))
const registerMock = vi.mocked(registerReceivable)
const settleMock = vi.mocked(settleReceivable)

// Ver useSimulation.test.tsx: notificações do TanStack fora do relógio falso.
notifyManager.setScheduler(queueMicrotask)

const input: SettleInput = {
  cedenteId: 'cedente-1',
  receivableType: 'DUPLICATA_MERCANTIL',
  faceValue: '100000.00',
  dueDate: '2027-01-24',
  paymentCurrency: 'BRL',
}

const receivable: ReceivableResponse = {
  id: 'receivable-1',
  cedenteId: 'cedente-1',
  type: 'DUPLICATA_MERCANTIL',
  faceValue: '100000.00',
  dueDate: '2027-01-24',
  status: 'OPEN',
  version: 0,
}

function settlement(idempotencyKey: string): SettlementResponse {
  return {
    id: 'settlement-1',
    receivableId: 'receivable-1',
    cedenteId: 'cedente-1',
    idempotencyKey,
    paymentCurrency: 'BRL',
    faceValue: '100000.00',
    termMonths: '3',
    baseRate: '0.01',
    spread: '0.015',
    presentValueBrl: '92859.94',
    discountBrl: '7140.06',
    fxRateId: null,
    fxRate: null,
    netAmount: '92859.94',
    settledAt: '2026-10-30T10:00:00-03:00',
  }
}

function renderSettle(initial: SettleInput | null = input) {
  const client = new QueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return renderHook(({ current }) => useSettle(current), {
    wrapper,
    initialProps: { current: initial },
  })
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms))
}

const networkError = () => ApiError.network(new TypeError('Failed to fetch'))

describe('useSettle', () => {
  let keys: number

  beforeEach(() => {
    vi.useFakeTimers()
    registerMock.mockReset().mockResolvedValue(receivable)
    settleMock.mockReset()
    keys = 0
    vi.spyOn(crypto, 'randomUUID').mockImplementation(
      () => `00000000-0000-4000-8000-00000000000${++keys}` as `${string}-${string}-${string}-${string}-${string}`,
    )
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('registra o recebível e liquida com uma chave gerada no clique', async () => {
    settleMock.mockImplementation(async (_, key) => ({
      settlement: settlement(key),
      replayed: false,
    }))
    const { result } = renderSettle()

    expect(crypto.randomUUID).not.toHaveBeenCalled()
    act(() => result.current.settle())
    await advance(0)

    expect(registerMock).toHaveBeenCalledWith({
      cedenteId: 'cedente-1',
      type: 'DUPLICATA_MERCANTIL',
      faceValue: '100000.00',
      dueDate: '2027-01-24',
    })
    expect(settleMock).toHaveBeenCalledWith(
      { receivableId: 'receivable-1', paymentCurrency: 'BRL' },
      '00000000-0000-4000-8000-000000000001',
    )
    expect(result.current.settlement?.netAmount).toBe('92859.94')
  })

  it('duplo clique gera uma única liquidação', async () => {
    let finish!: () => void
    settleMock.mockImplementation(
      (_, key) =>
        new Promise((resolve) => {
          finish = () => resolve({ settlement: settlement(key), replayed: false })
        }),
    )
    const { result } = renderSettle()

    act(() => {
      result.current.settle()
      result.current.settle()
    })
    await advance(0)
    expect(result.current.isPending).toBe(true)
    expect(result.current.canSettle).toBe(false)

    // Clique enquanto a requisição está pendente.
    act(() => result.current.settle())
    finish()
    await advance(0)

    expect(registerMock).toHaveBeenCalledTimes(1)
    expect(settleMock).toHaveBeenCalledTimes(1)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
  })

  it('retry automático após falha de rede reutiliza a mesma chave', async () => {
    settleMock
      .mockRejectedValueOnce(networkError())
      .mockImplementation(async (_, key) => ({
        settlement: settlement(key),
        replayed: true,
      }))
    const { result } = renderSettle()

    act(() => result.current.settle())
    await advance(10_000)

    expect(settleMock).toHaveBeenCalledTimes(2)
    expect(settleMock.mock.calls[1][1]).toBe(settleMock.mock.calls[0][1])
    expect(registerMock).toHaveBeenCalledTimes(1)
    expect(result.current.settlement).toBeDefined()
  })

  it('"tentar de novo" reutiliza a chave e o recebível já registrado', async () => {
    settleMock.mockRejectedValue(networkError())
    const { result } = renderSettle()

    act(() => result.current.settle())
    await advance(10_000)
    expect(result.current.error?.message).toMatch(/Sem conexão/)
    expect(result.current.canRetry).toBe(true)
    const firstKey = settleMock.mock.calls[0][1]

    settleMock.mockReset().mockImplementation(async (_, key) => ({
      settlement: settlement(key),
      replayed: true,
    }))
    act(() => result.current.retry())
    await advance(0)

    expect(settleMock).toHaveBeenCalledTimes(1)
    expect(settleMock.mock.calls[0][1]).toBe(firstKey)
    expect(registerMock).toHaveBeenCalledTimes(1)
    expect(result.current.settlement?.idempotencyKey).toBe(firstKey)
  })

  it('resposta 200 com Idempotent-Replayed mostra a mesma confirmação, sem erro', async () => {
    settleMock.mockImplementation(async (_, key) => ({
      settlement: settlement(key),
      replayed: true,
    }))
    const { result } = renderSettle()

    act(() => result.current.settle())
    await advance(0)

    expect(result.current.error).toBeNull()
    expect(result.current.settlement?.id).toBe('settlement-1')
  })

  it('erro de negócio mostra o detail e não repete sozinho', async () => {
    settleMock.mockRejectedValue(
      new ApiError(
        422,
        'Taxa de câmbio desatualizada. Atualize antes de liquidar.',
        'FX_RATE_STALE',
      ),
    )
    const { result } = renderSettle()

    act(() => result.current.settle())
    await advance(10_000)

    expect(settleMock).toHaveBeenCalledTimes(1)
    expect(result.current.error?.message).toBe(
      'Taxa de câmbio desatualizada. Atualize antes de liquidar.',
    )
  })

  it('depois de liquidar, só libera nova liquidação quando os dados mudam', async () => {
    settleMock.mockImplementation(async (_, key) => ({
      settlement: settlement(key),
      replayed: false,
    }))
    const { result, rerender } = renderSettle()

    act(() => result.current.settle())
    await advance(0)
    expect(result.current.canSettle).toBe(false)

    rerender({ current: { ...input, faceValue: '50000.00' } })
    expect(result.current.canSettle).toBe(true)
    expect(result.current.settlement).toBeUndefined()

    act(() => result.current.settle())
    await advance(0)
    expect(settleMock.mock.calls[1][1]).toBe(
      '00000000-0000-4000-8000-000000000002',
    )
  })

  it('não liquida sem dados completos', () => {
    const { result } = renderSettle(null)

    expect(result.current.canSettle).toBe(false)
    act(() => result.current.settle())
    expect(registerMock).not.toHaveBeenCalled()
  })
})
