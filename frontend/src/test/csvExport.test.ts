import { movementsToCSV } from '@/lib/csvExport'
import type { StockMovementResponse } from '@/types'

const HEADER = 'Tipo,Producto,SKU,Cantidad,Antes,Después,Usuario,Fecha,Motivo'

const baseMovement: StockMovementResponse = {
  id: 1,
  productId: 10,
  productName: 'Widget A',
  sku: 'WGT-001',
  type: 'IN',
  quantity: 50,
  quantityBefore: 100,
  quantityAfter: 150,
  performedBy: 'admin',
  reason: 'Restock',
  referenceId: null,
  createdAt: '2026-06-14T10:00:00Z',
}

describe('movementsToCSV', () => {
  it('returns only the header row for an empty array', () => {
    expect(movementsToCSV([])).toBe(HEADER)
  })

  it('returns header + 1 data row for a single movement', () => {
    const csv = movementsToCSV([baseMovement])
    const lines = csv.split('\n')
    expect(lines).toHaveLength(2)
    expect(lines[0]).toBe(HEADER)
    expect(lines[1]).toBe('IN,Widget A,WGT-001,50,100,150,admin,2026-06-14T10:00:00Z,Restock')
  })

  it('outputs an empty string in the reason column when reason is null', () => {
    const movement: StockMovementResponse = { ...baseMovement, reason: null }
    const csv = movementsToCSV([movement])
    const dataRow = csv.split('\n')[1]
    expect(dataRow.endsWith(',')).toBe(true)
  })

  it('quotes a product name that contains a comma', () => {
    const movement: StockMovementResponse = { ...baseMovement, productName: 'Bolt, Hex' }
    const csv = movementsToCSV([movement])
    const dataRow = csv.split('\n')[1]
    expect(dataRow).toContain('"Bolt, Hex"')
  })
})
