export interface ProductResponse {
  id: number
  sku: string
  name: string
  description: string | null
  price: number
  stock: number
  minimumStock: number
  active: boolean
  categoryId: number
  categoryName: string
  createdAt: string
  updatedAt: string
}

export interface ProductCreateRequest {
  sku: string
  name: string
  description?: string
  price: number
  stock: number
  minimumStock: number
  categoryId: number
}

export interface ProductUpdateRequest {
  sku: string
  name: string
  description?: string
  price: number
  minimumStock: number
  categoryId: number
  active: boolean
}

export interface CategoryResponse {
  id: number
  name: string
  description: string | null
}

export interface StockMovementResponse {
  id: number
  productId: number
  sku: string
  productName: string
  type: 'IN' | 'OUT' | 'ADJUSTMENT'
  quantity: number
  quantityBefore: number
  quantityAfter: number
  reason: string | null
  referenceId: string | null
  performedBy: string
  createdAt: string
}

export interface StockMovementRequest {
  productId: number
  type: 'IN' | 'OUT' | 'ADJUSTMENT'
  quantity: number
  reason?: string
  referenceId?: string
}

export interface DashboardMetricsResponse {
  totalProducts: number
  activeProducts: number
  inactiveProducts: number
  totalCategories: number
  totalStockMovements: number
  totalInventoryValue: number
  lowStockCount: number
  criticalStockCount: number
  lastMovementAt: string | null
}

export interface LowStockItemDto {
  productId: number
  sku: string
  name: string
  categoryName: string
  currentStock: number
  minimumStock: number
  deficit: number
}

export interface LowStockReportResponse {
  generatedAt: string
  threshold: number
  count: number
  items: LowStockItemDto[]
}

export interface CriticalStockResponse {
  generatedAt: string
  count: number
  products: ProductResponse[]
}

export interface TopProductDto {
  productId: number
  sku: string
  name: string
  categoryName: string
  currentStock: number
  price: number
  inventoryValue: number
}

export interface TopProductsResponse {
  generatedAt: string
  metric: string
  limit: number
  products: TopProductDto[]
}

export interface RecentMovementDto {
  id: number
  productName: string
  sku: string
  type: 'IN' | 'OUT' | 'ADJUSTMENT'
  quantity: number
  performedBy: string
  createdAt: string
}

export interface RecentMovementsResponse {
  generatedAt: string
  limit: number
  movements: RecentMovementDto[]
}

export interface AuditRevisionResponse {
  revisionNumber: number
  revisionTimestamp: string
  revisedBy: string
  revisionType: 'ADD' | 'MOD' | 'DEL'
  movementId: number
  productId: number
  sku: string
  productName: string
  movementType: string
  quantity: number
  quantityBefore: number
  quantityAfter: number
  performedBy: string
  reason: string | null
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
