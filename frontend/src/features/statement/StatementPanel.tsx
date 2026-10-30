import { ErrorMessage } from '../../components/ErrorMessage'
import { useCedentes } from '../pricing/useCedentes'
import { StatementFiltersFields } from './StatementFiltersFields'
import { StatementPagination } from './StatementPagination'
import { StatementTable } from './StatementTable'
import { useStatement } from './useStatement'

export function StatementPanel() {
  const cedentes = useCedentes()
  const statement = useStatement()

  return (
    <section className="statement-panel" aria-busy={statement.isUpdating}>
      <h2>Histórico de liquidações</h2>
      <StatementFiltersFields
        filters={statement.filters}
        cedentes={cedentes.cedentes}
        onChange={statement.setFilter}
      />
      <ErrorMessage error={statement.error} />
      {statement.isLoading ? (
        <p>Carregando…</p>
      ) : (
        <StatementTable items={statement.items} />
      )}
      <StatementPagination
        page={statement.page}
        totalPages={statement.totalPages}
        totalElements={statement.totalElements}
        hasPrevious={statement.hasPrevious}
        hasNext={statement.hasNext}
        onPrevious={statement.previousPage}
        onNext={statement.nextPage}
      />
    </section>
  )
}
