import { SimulationFormFields } from './SimulationFormFields'
import { SimulationResult } from './SimulationResult'
import { useSimulation, useSimulationForm } from './useSimulation'

export function PricingPanel() {
  const { form, setField } = useSimulationForm()
  const { request, simulation, error, isCalculating } = useSimulation(form)

  return (
    <div className="pricing-panel">
      <SimulationFormFields form={form} onChange={setField} />
      <SimulationResult
        simulation={simulation}
        error={error}
        isCalculating={isCalculating}
        hasValidInput={request !== null}
      />
    </div>
  )
}
