import type { SimulationResponse } from '../../api/types'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Money } from '../../components/Money'

interface SimulationResultProps {
  simulation: SimulationResponse | undefined
  error: Error | null
  isCalculating: boolean
  hasValidInput: boolean
}

export function SimulationResult({
  simulation,
  error,
  isCalculating,
  hasValidInput,
}: SimulationResultProps) {
  return (
    <section className="simulation-result" aria-live="polite" aria-busy={isCalculating}>
      <h2>Simulação</h2>

      {!hasValidInput && <p>Informe valor de face e vencimento para simular.</p>}
      {hasValidInput && isCalculating && <p>Calculando…</p>}
      <ErrorMessage error={error} />

      {simulation && !isCalculating && (
        <dl>
          <dt>Valor presente</dt>
          <dd>
            <Money value={simulation.presentValueBrl} currency="BRL" />
          </dd>
          <dt>Deságio</dt>
          <dd>
            <Money value={simulation.discountBrl} currency="BRL" />
          </dd>
          <dt>Valor líquido</dt>
          <dd className="net-amount">
            <Money value={simulation.netAmount} currency={simulation.paymentCurrency} />
          </dd>
        </dl>
      )}
    </section>
  )
}
