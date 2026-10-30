import type { Cedente, PaymentCurrency } from '../../api/types'
import type { StatementFilters } from './useStatement'

interface StatementFiltersFieldsProps {
  filters: StatementFilters
  cedentes: Cedente[]
  onChange: <K extends keyof StatementFilters>(
    field: K,
    value: StatementFilters[K],
  ) => void
}

export function StatementFiltersFields({
  filters,
  cedentes,
  onChange,
}: StatementFiltersFieldsProps) {
  return (
    <fieldset className="statement-filters">
      <legend>Filtros</legend>

      <label>
        De
        <input
          type="date"
          required
          value={filters.fromDate}
          onChange={(event) => onChange('fromDate', event.target.value)}
        />
      </label>

      <label>
        Até
        <input
          type="date"
          required
          value={filters.toDate}
          onChange={(event) => onChange('toDate', event.target.value)}
        />
      </label>

      <label>
        Cedente
        <select
          value={filters.cedenteId}
          onChange={(event) => onChange('cedenteId', event.target.value)}
        >
          <option value="">Todos</option>
          {cedentes.map((cedente) => (
            <option key={cedente.id} value={cedente.id}>
              {cedente.nome}
            </option>
          ))}
        </select>
      </label>

      <label>
        Moeda
        <select
          value={filters.currency}
          onChange={(event) =>
            onChange('currency', event.target.value as PaymentCurrency | '')
          }
        >
          <option value="">Todas</option>
          <option value="BRL">Real (BRL)</option>
          <option value="USD">Dólar (USD)</option>
        </select>
      </label>
    </fieldset>
  )
}
