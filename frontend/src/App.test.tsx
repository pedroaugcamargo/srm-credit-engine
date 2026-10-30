import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renderiza o título do painel', () => {
    const html = renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <App />
      </QueryClientProvider>,
    )
    expect(html).toContain('Painel do operador')
  })
})
