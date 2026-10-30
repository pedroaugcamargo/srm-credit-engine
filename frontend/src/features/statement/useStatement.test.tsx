// @vitest-environment jsdom
import {
  QueryClient,
  QueryClientProvider,
  notifyManager,
} from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchStatement } from '../../api/client'
import { ApiError } from '../../api/problem'
import type { StatementResponse } from '../../api/types'
import { STATEMENT_PAGE_SIZE, useStatement } from './useStatement'

vi.mock('../../api/client', () => ({ fetchStatement: vi.fn() }))
const fetchMock = vi.mocked(fetchStatement)

// Ver useSimulation.test.tsx: notificações do TanStack fora do relógio falso.
notifyManager.setScheduler(queueMicrotask)

function page(number: number, totalElements: number): StatementResponse {
  return { items: [], page: number, size: STATEMENT_PAGE_SIZE, totalElements }
}

function renderStatement() {
  const client = new QueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return renderHook(() => useStatement(), { wrapper })
}

async function flush() {
  await act(() => vi.advanceTimersByTimeAsync(0))
}

const lastQuery = () => fetchMock.mock.lastCall?.[0]

describe('useStatement', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-30T15:00:00Z'))
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('abre nos últimos 30 dias, primeira página, sem outros filtros', async () => {
    fetchMock.mockResolvedValue(page(0, 0))
    const { result } = renderStatement()
    await flush()

    expect(result.current.filters.fromDate).toBe('2026-09-30')
    expect(result.current.filters.toDate).toBe('2026-10-30')
    expect(lastQuery()).toEqual({
      from: '2026-09-30T00:00:00-03:00',
      to: '2026-10-30T23:59:59.999999-03:00',
      cedenteId: undefined,
      currency: undefined,
      page: 0,
      size: STATEMENT_PAGE_SIZE,
    })
  })

  it('pagina no servidor: próxima página pede page=1', async () => {
    fetchMock.mockImplementation(async (query) => page(query.page ?? 0, 45))
    const { result } = renderStatement()
    await flush()

    expect(result.current.totalPages).toBe(3)
    expect(result.current.hasPrevious).toBe(false)
    expect(result.current.hasNext).toBe(true)

    act(() => result.current.nextPage())
    await flush()

    expect(lastQuery()?.page).toBe(1)
    expect(result.current.page).toBe(1)
    expect(result.current.hasPrevious).toBe(true)
  })

  it('não avança além da última página', async () => {
    fetchMock.mockImplementation(async (query) => page(query.page ?? 0, 20))
    const { result } = renderStatement()
    await flush()

    expect(result.current.hasNext).toBe(false)
    act(() => result.current.nextPage())
    await flush()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('mudar um filtro volta para a primeira página', async () => {
    fetchMock.mockImplementation(async (query) => page(query.page ?? 0, 100))
    const { result } = renderStatement()
    await flush()
    act(() => result.current.nextPage())
    await flush()

    act(() => result.current.setFilter('currency', 'USD'))
    await flush()

    expect(lastQuery()).toMatchObject({ currency: 'USD', page: 0 })
    expect(result.current.page).toBe(0)
  })

  it('filtra por cedente', async () => {
    fetchMock.mockResolvedValue(page(0, 0))
    const { result } = renderStatement()
    await flush()

    act(() => result.current.setFilter('cedenteId', 'cedente-1'))
    await flush()

    expect(lastQuery()?.cedenteId).toBe('cedente-1')
  })

  it('expõe o detail do erro do backend', async () => {
    fetchMock.mockRejectedValue(
      new ApiError(
        400,
        'O período não pode ser maior que 366 dias.',
        'STATEMENT_QUERY_INVALID',
      ),
    )
    const { result } = renderStatement()
    await flush()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result.current.error?.message).toBe(
      'O período não pode ser maior que 366 dias.',
    )
  })
})
