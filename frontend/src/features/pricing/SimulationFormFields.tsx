import type { Cedente, PaymentCurrency, ReceivableType } from '../../api/types'
import type { PricingForm } from './useSimulation'

interface SimulationFormFieldsProps {
  form: PricingForm
  cedentes: Cedente[]
  onChange: <K extends keyof PricingForm>(field: K, value: PricingForm[K]) => void
}

export function SimulationFormFields({
  form,
  cedentes,
  onChange,
}: SimulationFormFieldsProps) {
  return (
    <fieldset className="simulation-form">
      <legend>Recebível</legend>

      <label>
        Cedente
        <select
          value={form.cedenteId}
          onChange={(event) => onChange('cedenteId', event.target.value)}
        >
          <option value="">Selecione</option>
          {cedentes.map((cedente) => (
            <option key={cedente.id} value={cedente.id}>
              {cedente.nome}
            </option>
          ))}
        </select>
      </label>

      <label>
        Valor de face (R$)
        <input
          type="text"
          inputMode="decimal"
          placeholder="100000,00"
          value={form.faceValue}
          onChange={(event) => onChange('faceValue', event.target.value)}
        />
      </label>

      <label>
        Vencimento
        <input
          type="date"
          value={form.dueDate}
          onChange={(event) => onChange('dueDate', event.target.value)}
        />
      </label>

      <label>
        Tipo
        <select
          value={form.receivableType}
          onChange={(event) =>
            onChange('receivableType', event.target.value as ReceivableType)
          }
        >
          <option value="DUPLICATA_MERCANTIL">Duplicata mercantil</option>
          <option value="CHEQUE">Cheque</option>
        </select>
      </label>

      <label>
        Moeda de pagamento
        <select
          value={form.paymentCurrency}
          onChange={(event) =>
            onChange('paymentCurrency', event.target.value as PaymentCurrency)
          }
        >
          <option value="BRL">Real (BRL)</option>
          <option value="USD">Dólar (USD)</option>
        </select>
      </label>
    </fieldset>
  )
}
