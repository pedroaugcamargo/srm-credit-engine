import { describe, expect, it } from 'vitest'
import { readProblem } from './problem'

const problemResponse = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

describe('readProblem', () => {
  it('usa o detail do ProblemDetail como mensagem para o operador', async () => {
    const error = await readProblem(
      problemResponse(
        {
          type: 'about:blank',
          title: 'Unprocessable Content',
          status: 422,
          detail: 'Taxa de câmbio desatualizada. Atualize antes de liquidar.',
          code: 'FX_RATE_STALE',
        },
        422,
      ),
    )

    expect(error.status).toBe(422)
    expect(error.code).toBe('FX_RATE_STALE')
    expect(error.message).toBe(
      'Taxa de câmbio desatualizada. Atualize antes de liquidar.',
    )
  })

  it('cai na mensagem genérica quando não há detail', async () => {
    const error = await readProblem(problemResponse({ status: 409 }, 409))

    expect(error.status).toBe(409)
    expect(error.message).toBe(
      'Não foi possível concluir a operação. Tente novamente.',
    )
  })

  it('cai na mensagem genérica quando o corpo não é JSON', async () => {
    const error = await readProblem(
      new Response('<html>Bad Gateway</html>', { status: 502 }),
    )

    expect(error.status).toBe(502)
    expect(error.code).toBeUndefined()
    expect(error.message).toBe(
      'Não foi possível concluir a operação. Tente novamente.',
    )
  })
})
