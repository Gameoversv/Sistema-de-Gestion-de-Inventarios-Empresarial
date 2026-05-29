package com.inventory.report.service;

import com.inventory.report.dto.CriticalStockResponse;
import com.inventory.report.dto.DashboardMetricsResponse;
import com.inventory.report.dto.LowStockReportResponse;
import com.inventory.report.dto.RecentMovementsResponse;
import com.inventory.report.dto.StockSummaryResponse;
import com.inventory.report.dto.TopProductsResponse;

public interface ReportService {

  StockSummaryResponse stockSummary();

  LowStockReportResponse lowStockAlert(int threshold);

  CriticalStockResponse criticalStock();

  TopProductsResponse topProducts(int limit, String metric);

  DashboardMetricsResponse dashboardMetrics();

  RecentMovementsResponse recentMovements(int limit);
}
