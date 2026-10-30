import type { IsoDate, IsoDateTime } from '../api/types'

// A mesa opera no horário de Brasília (SPEC, A4). Sem horário de verão desde
// 2019, o offset é fixo.
const TIME_ZONE = 'America/Sao_Paulo'
const OFFSET = '-03:00'

/** Data de hoje (AAAA-MM-DD) em Brasília. */
export function todayInSaoPaulo(): IsoDate {
  // en-CA formata como AAAA-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

/** Data `days` dias antes de `date`, em dias de calendário. */
export function daysBefore(date: IsoDate, days: number): IsoDate {
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - days)
  return day.toISOString().slice(0, 10)
}

/** Primeiro instante do dia em Brasília. */
export function startOfDaySaoPaulo(date: IsoDate): IsoDateTime {
  return `${date}T00:00:00${OFFSET}`
}

/** Último instante do dia em Brasília (o extrato filtra `to` inclusive). */
export function endOfDaySaoPaulo(date: IsoDate): IsoDateTime {
  return `${date}T23:59:59.999999${OFFSET}`
}

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('pt-BR', {
  timeZone: TIME_ZONE,
  dateStyle: 'short',
  timeStyle: 'short',
})

/** Instante ISO formatado em Brasília, ex.: 29/10/2026, 14:03. */
export function formatDateTime(value: IsoDateTime): string {
  return DATE_TIME_FORMAT.format(new Date(value))
}
