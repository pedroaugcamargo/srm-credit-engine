interface StatementPaginationProps {
  page: number
  totalPages: number
  totalElements: number
  hasPrevious: boolean
  hasNext: boolean
  onPrevious: () => void
  onNext: () => void
}

export function StatementPagination({
  page,
  totalPages,
  totalElements,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: StatementPaginationProps) {
  return (
    <nav className="statement-pagination" aria-label="Paginação do histórico">
      <button type="button" onClick={onPrevious} disabled={!hasPrevious}>
        Anterior
      </button>
      <span>
        Página {totalPages === 0 ? 0 : page + 1} de {totalPages} ({totalElements}{' '}
        liquidações)
      </span>
      <button type="button" onClick={onNext} disabled={!hasNext}>
        Próxima
      </button>
    </nav>
  )
}
