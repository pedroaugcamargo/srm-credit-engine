import { useQuery } from '@tanstack/react-query'
import { fetchCedentes } from '../../api/client'
import { retryOnlyTransient } from '../../api/problem'

/** Cedentes para o operador escolher. A lista muda pouco: não recarrega sozinha. */
export function useCedentes() {
  const query = useQuery({
    queryKey: ['cedentes'],
    queryFn: ({ signal }) => fetchCedentes(signal),
    staleTime: Infinity,
    retry: retryOnlyTransient,
  })

  return {
    cedentes: query.data ?? [],
    isLoading: query.isPending,
    error: query.error,
  }
}
