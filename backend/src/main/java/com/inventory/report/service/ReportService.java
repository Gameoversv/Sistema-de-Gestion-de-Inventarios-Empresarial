package com.inventory.report.service;

import com.inventory.report.dto.BestSellersResponse;
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

  /**
   * Ranking de productos más vendidos, agregando los movimientos de salida.
   *
   * <p>Distinto de {@link #topProducts}: aquel mide lo que hay en almacén —precio × stock o
   * unidades—, este mide lo que ha salido.
   */
  BestSellersResponse bestSellers(int limit);

  DashboardMetricsResponse dashboardMetrics();

  RecentMovementsResponse recentMovements(int limit);
}
