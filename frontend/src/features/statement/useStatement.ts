import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchStatement } from '../../api/client'
import { retryOnlyTransient } from '../../api/problem'
import type { IsoDate, PaymentCurrency, StatementQuery } from '../../api/types'
import {
  daysBefore,
  endOfDaySaoPaulo,
  startOfDaySaoPaulo,
  todayInSaoPaulo,
} from '../../lib/date'

export const STATEMENT_PAGE_SIZE = 20
const DEFAULT_PERIOD_DAYS = 30

/** Filtros como o operador escolheu. Vazio = sem filtro. */
export interface StatementFilters {
  fromDate: IsoDate
  toDate: IsoDate
  cedenteId: string
  currency: PaymentCurrency | ''
}

function defaultFilters(): StatementFilters {
  const today = todayInSaoPaulo()
  return {
    fromDate: daysBefore(today, DEFAULT_PERIOD_DAYS),
    toDate: today,
    cedenteId: '',
    currency: '',
  }
}

function toQuery(filters: StatementFilters, page: number): StatementQuery {
  return {
    from: startOfDaySaoPaulo(filters.fromDate),
    to: endOfDaySaoPaulo(filters.toDate),
    cedenteId: filters.cedenteId || undefined,
    currency: filters.currency || undefined,
    page,
    size: STATEMENT_PAGE_SIZE,
  }
}

/**
 * Histórico de liquidações com paginação no servidor. Período obrigatório e
 * limites (366 dias, tamanho de página) são validados pelo backend; o erro
 * chega com o `detail` para o operador.
 */
export function useStatement() {
  const [filters, setFilters] = useState(defaultFilters)
  const [page, setPage] = useState(0)

  const statementQuery = toQuery(filters, page)
  const query = useQuery({
    queryKey: ['statement', statementQuery],
    queryFn: ({ signal }) => fetchStatement(statementQuery, signal),
    // Mantém a página anterior na tela enquanto a próxima carrega.
    placeholderData: keepPreviousData,
    retry: retryOnlyTransient,
  })

  const totalElements = query.data?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / STATEMENT_PAGE_SIZE)
  const hasPrevious = page > 0
  const hasNext = page + 1 < totalPages

  function setFilter<K extends keyof StatementFilters>(
    field: K,
    value: StatementFilters[K],
  ) {
    setFilters((current) => ({ ...current, [field]: value }))
    setPage(0)
  }

  return {
    filters,
    setFilter,
    items: query.data?.items ?? [],
    page,
    totalPages,
    totalElements,
    hasPrevious,
    hasNext,
    previousPage: () => {
      if (hasPrevious) setPage(page - 1)
    },
    nextPage: () => {
      if (hasNext) setPage(page + 1)
    },
    isLoading: query.isPending,
    isUpdating: query.isPlaceholderData,
    error: query.error,
  }
}
