import { describe, expect, it } from 'vitest'
import { formatMoney } from './money'

// Intl usa espaço não separável entre o símbolo e o número.
const normalize = (text: string) => text.replace(/\u00a0/g, ' ')
const fmt = (value: string, currency: 'BRL' | 'USD' = 'BRL') =>
  normalize(formatMoney(value, currency))

describe('formatMoney', () => {
  it('formata BRL no padrão brasileiro', () => {
    expect(fmt('92859.94')).toBe('R$ 92.859,94')
  })

  it('formata USD com o símbolo do dólar', () => {
    expect(fmt('17094.67', 'USD')).toBe('US$ 17.094,67')
  })

  it('não perde precisão em valores acima de 2^53', () => {
    // 2^53 + 1 não é representável em double: como number viraria ...992.
    expect(fmt('9007199254740993.01')).toBe('R$ 9.007.199.254.740.993,01')
  })

  it('mantém todos os dígitos de um valor muito grande', () => {
    expect(fmt('98765432109876543210.99')).toBe(
      'R$ 98.765.432.109.876.543.210,99',
    )
  })

  it('não sofre erro de ponto flutuante nos centavos', () => {
    // Como number, 0.1 + 0.2 e 1.005 já mostram o problema; aqui é só texto.
    expect(fmt('0.30')).toBe('R$ 0,30')
    expect(fmt('1.01')).toBe('R$ 1,01')
  })

  it('completa duas casas quando o valor vem inteiro', () => {
    expect(fmt('100000')).toBe('R$ 100.000,00')
  })

  it('não arredonda casas extras: exibe o que o backend enviou', () => {
    expect(fmt('1.005')).toBe('R$ 1,005')
  })

  it('formata valores negativos', () => {
    expect(fmt('-1234.50')).toBe('-R$ 1.234,50')
  })

  it.each(['', 'abc', '1,00', '1e5', '1.', '.5', ' 10.00', 'NaN', 'Infinity'])(
    'rejeita entrada fora do formato decimal: %j',
    (value) => {
      expect(() => formatMoney(value, 'BRL')).toThrow(TypeError)
    },
  )
})
