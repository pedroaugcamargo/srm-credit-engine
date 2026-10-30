import type { ProblemDetail } from './types'

const GENERIC_MESSAGE = 'Não foi possível concluir a operação. Tente novamente.'
const NETWORK_MESSAGE =
  'Sem conexão com o servidor. Verifique a rede e tente novamente.'

/**
 * Erro devolvido pela API. `message` é sempre texto para o operador:
 * o `detail` do ProblemDetail quando existe, senão uma mensagem genérica.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly problem: ProblemDetail | undefined

  constructor(
    status: number,
    message: string,
    code?: string,
    problem?: ProblemDetail,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.problem = problem
  }

  /** Falha antes de haver resposta HTTP (rede, servidor fora do ar). */
  static network(cause: unknown): ApiError {
    const error = new ApiError(0, NETWORK_MESSAGE)
    error.cause = cause
    return error
  }
}

function isProblemDetail(body: unknown): body is ProblemDetail {
  return typeof body === 'object' && body !== null && !Array.isArray(body)
}

function nonEmpty(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}

/** Lê uma resposta de erro e devolve o ApiError correspondente. */
export async function readProblem(response: Response): Promise<ApiError> {
  let body: unknown
  try {
    body = await response.json()
  } catch {
    // Corpo vazio ou que não é JSON (ex.: 502 do proxy). Não há detail para mostrar.
    return new ApiError(response.status, GENERIC_MESSAGE)
  }

  if (!isProblemDetail(body)) {
    return new ApiError(response.status, GENERIC_MESSAGE)
  }

  return new ApiError(
    response.status,
    nonEmpty(body.detail) ?? GENERIC_MESSAGE,
    nonEmpty(body.code),
    body,
  )
}
