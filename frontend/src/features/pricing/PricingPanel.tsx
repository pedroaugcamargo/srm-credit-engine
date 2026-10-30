import { ErrorMessage } from '../../components/ErrorMessage'
import { SettlementAction } from './SettlementAction'
import { SimulationFormFields } from './SimulationFormFields'
import { SimulationResult } from './SimulationResult'
import { useCedentes } from './useCedentes'
import { toSettleInput, useSettle } from './useSettle'
import { useSimulation, useSimulationForm } from './useSimulation'

export function PricingPanel() {
  const { form, setField } = useSimulationForm()
  const cedentes = useCedentes()
  const { request, simulation, error, isCalculating } = useSimulation(form)
  // Só liquida o que está simulado na tela.
  const settlement = useSettle(
    simulation ? toSettleInput(form.cedenteId, request) : null,
  )

  return (
    <div className="pricing-panel">
      <ErrorMessage error={cedentes.error} />
      <SimulationFormFields
        form={form}
        cedentes={cedentes.cedentes}
        onChange={setField}
      />
      <SimulationResult
        simulation={simulation}
        error={error}
        isCalculating={isCalculating}
        hasValidInput={request !== null}
      />
      <SettlementAction
        canSettle={settlement.canSettle}
        canRetry={settlement.canRetry}
        isPending={settlement.isPending}
        settlement={settlement.settlement}
        error={settlement.error}
        onSettle={settlement.settle}
        onRetry={settlement.retry}
      />
    </div>
  )
}
