import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { simulatePricing } from '../../api/client'
import { retryOnlyTransient } from '../../api/problem'
import type {
  IsoDate,
  PaymentCurrency,
  ReceivableType,
  SimulationRequest,
} from '../../api/types'
import { useDebouncedValue } from '../../lib/useDebouncedValue'

export const SIMULATION_DEBOUNCE_MS = 300

/** Estado do formulário como o operador digitou: tudo string. */
export interface SimulationForm {
  faceValue: string
  dueDate: IsoDate
  receivableType: ReceivableType
  paymentCurrency: PaymentCurrency
}

export const EMPTY_SIMULATION_FORM: SimulationForm = {
  faceValue: '',
  dueDate: '',
  receivableType: 'DUPLICATA_MERCANTIL',
  paymentCurrency: 'BRL',
}

// Até duas casas, sem separador de milhar. Vírgula ou ponto como decimal.
const FACE_VALUE_PATTERN = /^\d+(\.\d{1,2})?$/
const NON_ZERO_DIGIT = /[1-9]/

/**
 * Converte o formulário na requisição da API, ou `null` se ainda não dá para
 * simular. Só manipula texto: o valor nunca passa por number. Regras de
 * negócio (prazo, limites) ficam no backend.
 */
export function toSimulationRequest(
  form: SimulationForm,
): SimulationRequest | null {
  const faceValue = form.faceValue.trim().replace(',', '.')
  if (!FACE_VALUE_PATTERN.test(faceValue) || !NON_ZERO_DIGIT.test(faceValue)) {
    return null
  }
  if (form.dueDate === '') return null

  return {
    faceValue,
    dueDate: form.dueDate,
    receivableType: form.receivableType,
    paymentCurrency: form.paymentCurrency,
  }
}

function sameRequest(
  a: SimulationRequest | null,
  b: SimulationRequest | null,
): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * Simulação em tempo real. A chave da query são os próprios inputs, então
 * cada resposta fica presa aos inputs que a geraram: uma resposta antiga que
 * chega atrasada nunca aparece como resultado dos inputs atuais.
 */
export function useSimulation(form: SimulationForm) {
  const { faceValue, dueDate, receivableType, paymentCurrency } = form
  const request = useMemo(
    () =>
      toSimulationRequest({ faceValue, dueDate, receivableType, paymentCurrency }),
    [faceValue, dueDate, receivableType, paymentCurrency],
  )
  const debounced = useDebouncedValue(request, SIMULATION_DEBOUNCE_MS)

  const query = useQuery({
    queryKey: ['pricing', 'simulation', debounced],
    queryFn: ({ signal }) => simulatePricing(debounced!, signal),
    enabled: debounced !== null,
    retry: retryOnlyTransient,
  })

  // Enquanto o debounce não alcança o que está na tela, o resultado
  // disponível é de outros inputs e não deve ser exibido.
  const isCurrent = sameRequest(request, debounced)

  return {
    request,
    simulation: isCurrent ? query.data : undefined,
    error: isCurrent ? query.error : null,
    isCalculating: request !== null && (!isCurrent || query.isFetching),
  }
}

/** Formulário do painel: os campos da simulação mais o cedente. */
export interface PricingForm extends SimulationForm {
  cedenteId: string
}

/** Estado do formulário do painel de precificação. */
export function useSimulationForm() {
  const [form, setForm] = useState<PricingForm>({
    ...EMPTY_SIMULATION_FORM,
    cedenteId: '',
  })

  function setField<K extends keyof PricingForm>(
    field: K,
    value: PricingForm[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  return { form, setField }
}
