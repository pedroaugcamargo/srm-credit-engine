import type { SettlementResponse } from '../../api/types'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Money } from '../../components/Money'

interface SettlementActionProps {
  canSettle: boolean
  canRetry: boolean
  isPending: boolean
  settlement: SettlementResponse | undefined
  error: Error | null
  onSettle: () => void
  onRetry: () => void
}

export function SettlementAction({
  canSettle,
  canRetry,
  isPending,
  settlement,
  error,
  onSettle,
  onRetry,
}: SettlementActionProps) {
  return (
    <section className="settlement-action" aria-live="polite">
      <button type="button" onClick={onSettle} disabled={!canSettle}>
        {isPending ? 'Liquidando…' : 'Liquidar'}
      </button>

      <ErrorMessage error={error} />
      {canRetry && (
        <button type="button" onClick={onRetry}>
          Tentar de novo
        </button>
      )}

      {settlement && (
        <div role="status" className="settlement-confirmation">
          <p>Liquidação registrada.</p>
          <dl>
            <dt>Valor líquido pago</dt>
            <dd>
              <Money value={settlement.netAmount} currency={settlement.paymentCurrency} />
            </dd>
            <dt>Liquidação</dt>
            <dd>{settlement.id}</dd>
            <dt>Data</dt>
            <dd>{settlement.settledAt}</dd>
          </dl>
        </div>
      )}
    </section>
  )
}
