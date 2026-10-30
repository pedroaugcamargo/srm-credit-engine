// @vitest-environment jsdom
import {
  QueryClient,
  QueryClientProvider,
  notifyManager,
} from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { simulatePricing } from '../../api/client'
import { ApiError } from '../../api/problem'
import type { SimulationResponse } from '../../api/types'
import {
  EMPTY_SIMULATION_FORM,
  SIMULATION_DEBOUNCE_MS,
  toSimulationRequest,
  useSimulation,
  type SimulationForm,
} from './useSimulation'

vi.mock('../../api/client', () => ({ simulatePricing: vi.fn() }))
const simulateMock = vi.mocked(simulatePricing)

// O TanStack notifica os componentes via setTimeout(0), que o relógio falso
// seguraria. Microtask mantém o relógio só para o debounce.
notifyManager.setScheduler(queueMicrotask)

const baseForm: SimulationForm = {
  faceValue: '100000.00',
  dueDate: '2027-01-24',
  receivableType: 'DUPLICATA_MERCANTIL',
  paymentCurrency: 'BRL',
}

function response(netAmount: string): SimulationResponse {
  return {
    faceValue: '100000.00',
    termMonths: '3',
    baseRate: '0.01',
    spread: '0.015',
    presentValueBrl: netAmount,
    discountBrl: '0.00',
    paymentCurrency: 'BRL',
    fxRate: null,
    netAmount,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

// O painel abre com o formulário vazio; `form` é o que o operador digitou.
function renderSimulation(form: SimulationForm) {
  const client = new QueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  const rendered = renderHook(({ form }) => useSimulation(form), {
    wrapper,
    initialProps: { form: EMPTY_SIMULATION_FORM },
  })
  rendered.rerender({ form })
  return rendered
}

// Avança o relógio falso e deixa promessas e notificações do TanStack rodarem.
async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms))
}

describe('toSimulationRequest', () => {
  it('monta a requisição com o valor em string', () => {
    expect(toSimulationRequest(baseForm)).toEqual(baseForm)
  })

  it('aceita vírgula como separador decimal sem converter para number', () => {
    expect(
      toSimulationRequest({ ...baseForm, faceValue: ' 1234,5 ' })?.faceValue,
    ).toBe('1234.5')
  })

  it.each(['', '0', '0,00', 'abc', '1.000,00', '10.123', '-5', '1e5'])(
    'não simula com valor inválido: %j',
    (faceValue) => {
      expect(toSimulationRequest({ ...baseForm, faceValue })).toBeNull()
    },
  )

  it('não simula sem vencimento', () => {
    expect(toSimulationRequest({ ...baseForm, dueDate: '' })).toBeNull()
  })
})

describe('useSimulation', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    simulateMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('espera 300 ms sem digitação antes de chamar a API', async () => {
    simulateMock.mockResolvedValue(response('92859.94'))
    const { result } = renderSimulation(baseForm)

    await advance(SIMULATION_DEBOUNCE_MS - 1)
    expect(simulateMock).not.toHaveBeenCalled()
    expect(result.current.isCalculating).toBe(true)

    await advance(1)
    expect(simulateMock).toHaveBeenCalledTimes(1)
    expect(result.current.simulation?.netAmount).toBe('92859.94')
    expect(result.current.isCalculating).toBe(false)
  })

  it('digitação rápida gera uma única chamada, com o último valor', async () => {
    simulateMock.mockResolvedValue(response('1.00'))
    const { rerender } = renderSimulation({ ...baseForm, faceValue: '1' })

    for (const faceValue of ['10', '100', '1000']) {
      await advance(100)
      rerender({ form: { ...baseForm, faceValue } })
    }
    await advance(SIMULATION_DEBOUNCE_MS)

    expect(simulateMock).toHaveBeenCalledTimes(1)
    expect(simulateMock.mock.calls[0][0].faceValue).toBe('1000')
  })

  it('resposta antiga que chega depois não sobrescreve a simulação atual', async () => {
    const slow = deferred<SimulationResponse>()
    simulateMock
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce(response('200.00'))

    const { result, rerender } = renderSimulation({
      ...baseForm,
      faceValue: '100',
    })
    await advance(SIMULATION_DEBOUNCE_MS)

    rerender({ form: { ...baseForm, faceValue: '200' } })
    await advance(SIMULATION_DEBOUNCE_MS)
    expect(result.current.simulation?.netAmount).toBe('200.00')

    slow.resolve(response('100.00'))
    await advance(0)
    expect(result.current.simulation?.netAmount).toBe('200.00')
  })

  it('não mostra o resultado anterior enquanto os inputs mudaram', async () => {
    simulateMock.mockResolvedValue(response('100.00'))
    const { result, rerender } = renderSimulation(baseForm)
    await advance(SIMULATION_DEBOUNCE_MS)

    rerender({ form: { ...baseForm, paymentCurrency: 'USD' } })
    expect(result.current.simulation).toBeUndefined()
    expect(result.current.isCalculating).toBe(true)
  })

  it('não chama a API com formulário incompleto', async () => {
    const { result } = renderSimulation({ ...baseForm, faceValue: '' })
    await advance(SIMULATION_DEBOUNCE_MS)

    expect(simulateMock).not.toHaveBeenCalled()
    expect(result.current.isCalculating).toBe(false)
  })

  it('expõe o erro de negócio sem tentar de novo', async () => {
    simulateMock.mockRejectedValue(
      new ApiError(422, 'O vencimento precisa ser posterior a hoje.', 'INVALID_TERM'),
    )
    const { result } = renderSimulation(baseForm)
    await advance(SIMULATION_DEBOUNCE_MS)

    expect(simulateMock).toHaveBeenCalledTimes(1)
    expect(result.current.error?.message).toBe(
      'O vencimento precisa ser posterior a hoje.',
    )
  })
})
