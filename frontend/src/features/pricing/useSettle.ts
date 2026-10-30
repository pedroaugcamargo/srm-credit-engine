import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { registerReceivable, settleReceivable } from '../../api/client'
import { retryOnlyTransient } from '../../api/problem'
import type {
  IsoDate,
  PaymentCurrency,
  ReceivableType,
  SimulationRequest,
} from '../../api/types'

/** Tudo o que a liquidação precisa: o recebível simulado e o cedente. */
export interface SettleInput {
  cedenteId: string
  receivableType: ReceivableType
  faceValue: string
  dueDate: IsoDate
  paymentCurrency: PaymentCurrency
}

/** Uma tentativa = um clique em "Liquidar". Retries reutilizam a tentativa. */
interface SettlementAttempt {
  idempotencyKey: string
  input: SettleInput
}

export function toSettleInput(
  cedenteId: string,
  request: SimulationRequest | null,
): SettleInput | null {
  if (cedenteId === '' || request === null) return null
  return { cedenteId, ...request }
}

function sameInput(a: SettleInput | undefined, b: SettleInput | null): boolean {
  return a !== undefined && JSON.stringify(a) === JSON.stringify(b)
}

/**
 * Registra o recebível e liquida, de forma segura contra duplo clique e retry.
 *
 * A `Idempotency-Key` nasce no clique e acompanha a tentativa em todo retry,
 * automático ou pelo botão "Tentar de novo". O recebível também é registrado
 * uma vez só por tentativa: um retry depois de registrar vai direto à
 * liquidação. Assim, se a primeira resposta se perdeu na rede, o backend
 * devolve a liquidação original (`Idempotent-Replayed`) em vez de pagar outra.
 */
export function useSettle(input: SettleInput | null) {
  const queryClient = useQueryClient()
  // Atualizado no clique, antes do re-render: barra o segundo clique do duplo.
  const inFlight = useRef(false)
  // Recebível registrado por chave de idempotência.
  const registered = useRef(new Map<string, string>())

  const mutation = useMutation({
    mutationFn: async ({ idempotencyKey, input }: SettlementAttempt) => {
      let receivableId = registered.current.get(idempotencyKey)
      if (receivableId === undefined) {
        const receivable = await registerReceivable({
          cedenteId: input.cedenteId,
          type: input.receivableType,
          faceValue: input.faceValue,
          dueDate: input.dueDate,
        })
        receivableId = receivable.id
        registered.current.set(idempotencyKey, receivableId)
      }
      return settleReceivable(
        { receivableId, paymentCurrency: input.paymentCurrency },
        idempotencyKey,
      )
    },
    retry: retryOnlyTransient,
    // A liquidação nova precisa aparecer no histórico. Sem await: a
    // confirmação não espera o extrato recarregar.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['statement'] })
    },
    onSettled: () => {
      inFlight.current = false
    },
  })

  const attempt = mutation.variables
  // Já houve tentativa para estes dados: nova liquidação só com outros dados.
  // Depois de um erro, o caminho é "Tentar de novo" com a mesma chave; uma
  // chave nova registraria outro recebível para o mesmo título.
  const isCurrent = sameInput(attempt?.input, input)

  function run(next: SettlementAttempt) {
    inFlight.current = true
    mutation.mutate(next)
  }

  function settle() {
    if (inFlight.current || input === null || isCurrent) return
    run({ idempotencyKey: crypto.randomUUID(), input })
  }

  function retry() {
    if (inFlight.current || attempt === undefined) return
    if (!isCurrent || !mutation.isError) return
    run(attempt)
  }

  return {
    settle,
    retry,
    isPending: mutation.isPending,
    canSettle: input !== null && !mutation.isPending && !isCurrent,
    canRetry: isCurrent && mutation.isError,
    settlement: isCurrent ? mutation.data?.settlement : undefined,
    error: isCurrent ? mutation.error : null,
  }
}
