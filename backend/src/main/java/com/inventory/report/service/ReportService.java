package com.inventory.report.service;

import com.inventory.report.dto.CriticalStockResponse;
import com.inventory.report.dto.DashboardMetricsResponse;
import com.inventory.report.dto.LowStockReportResponse;
import com.inventory.report.dto.RecentMovementsResponse;
import com.inventory.report.dto.StockSummaryResponse;
import com.inventory.report.dto.TopProductsResponse;

/**
 * Contrato del servicio de reportes. Define las consultas analíticas disponibles: resumen de stock,
 * alertas de stock bajo, stock crítico, ranking de productos, métricas del dashboard y listado de
 * movimientos recientes.
 */
public interface ReportService {

  StockSummaryResponse stockSummary();

  LowStockReportResponse lowStockAlert(int threshold);

  CriticalStockResponse criticalStock();

  TopProductsResponse topProducts(int limit, String metric);

  DashboardMetricsResponse dashboardMetrics();

  RecentMovementsResponse recentMovements(int limit);
}
