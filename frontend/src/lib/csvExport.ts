/** Utility that serializes a list of stock movements into a CSV file and triggers a browser download. */
import type { StockMovementResponse } from '@/types'

const CSV_HEADER = 'Tipo,Producto,SKU,Cantidad,Antes,Después,Usuario,Fecha,Motivo'

function escapeCSVField(value: string): string {
  if (value.includes(',') || value.includes('"') || value.includes('\n')) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}

export function movementsToCSV(movements: StockMovementResponse[]): string {
  const rows = movements.map((m) => {
    const fields = [
      escapeCSVField(m.type),
      escapeCSVField(m.productName),
      escapeCSVField(m.sku),
      String(m.quantity),
      String(m.quantityBefore),
      String(m.quantityAfter),
      escapeCSVField(m.performedBy),
      escapeCSVField(m.createdAt),
      escapeCSVField(m.reason ?? ''),
    ]
    return fields.join(',')
  })

  return [CSV_HEADER, ...rows].join('\n')
}
