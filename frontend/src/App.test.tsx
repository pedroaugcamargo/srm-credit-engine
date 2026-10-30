import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renderiza o título do painel', () => {
    expect(renderToString(<App />)).toContain('Painel do operador')
  })
})
