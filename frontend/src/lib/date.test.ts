import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  daysBefore,
  endOfDaySaoPaulo,
  formatDateTime,
  startOfDaySaoPaulo,
  todayInSaoPaulo,
} from './date'

describe('datas no fuso de São Paulo', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('o dia começa à meia-noite de Brasília', () => {
    expect(startOfDaySaoPaulo('2026-10-01')).toBe('2026-10-01T00:00:00-03:00')
  })

  it('o dia termina no último microssegundo, para o filtro inclusivo', () => {
    expect(endOfDaySaoPaulo('2026-10-31')).toBe(
      '2026-10-31T23:59:59.999999-03:00',
    )
  })

  it('hoje é a data de Brasília, não a de UTC', () => {
    vi.useFakeTimers()
    // 02:00 UTC do dia 31 ainda é 23:00 do dia 30 em Brasília.
    vi.setSystemTime(new Date('2026-10-31T02:00:00Z'))
    expect(todayInSaoPaulo()).toBe('2026-10-30')
  })

  it('volta dias atravessando mês e ano', () => {
    expect(daysBefore('2026-03-01', 1)).toBe('2026-02-28')
    expect(daysBefore('2026-01-10', 30)).toBe('2025-12-11')
  })

  it('formata o instante da liquidação no horário de Brasília', () => {
    expect(formatDateTime('2026-10-29T17:03:11Z')).toBe('29/10/2026, 14:03')
  })
})
