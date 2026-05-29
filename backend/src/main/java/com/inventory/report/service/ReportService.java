package com.inventory.report.service;

import com.inventory.report.dto.LowStockReportResponse;
import com.inventory.report.dto.StockSummaryResponse;

public interface ReportService {

  StockSummaryResponse stockSummary();

  LowStockReportResponse lowStockAlert(int threshold);
}
