import type { StatementItem } from '../../api/types'
import { Money } from '../../components/Money'
import { Table, type Column } from '../../components/Table'
import { formatDateTime } from '../../lib/date'

const COLUMNS: Column<StatementItem>[] = [
  { header: 'Data', render: (item) => formatDateTime(item.settledAt) },
  { header: 'Cedente', render: (item) => item.cedenteNome },
  {
    header: 'Valor de face',
    align: 'right',
    render: (item) => <Money value={item.faceValue} currency="BRL" />,
  },
  {
    header: 'Valor presente',
    align: 'right',
    render: (item) => <Money value={item.presentValueBrl} currency="BRL" />,
  },
  { header: 'Moeda', render: (item) => item.paymentCurrency },
  {
    header: 'Câmbio',
    align: 'right',
    render: (item) => item.fxRate ?? '—',
  },
  {
    header: 'Valor líquido',
    align: 'right',
    render: (item) => <Money value={item.netAmount} currency={item.paymentCurrency} />,
  },
]

interface StatementTableProps {
  items: StatementItem[]
}

export function StatementTable({ items }: StatementTableProps) {
  return (
    <Table
      columns={COLUMNS}
      rows={items}
      rowKey={(item) => item.id}
      emptyMessage="Nenhuma liquidação no período."
    />
  )
}
