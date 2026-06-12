package com.inventory.report.service;

import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface ReportService {

  /** Returns current stock levels grouped by category. */
  Map<String, Object> stockSummary();

  /** Returns products below a minimum stock threshold. */
  Map<String, Object> lowStockAlert(int threshold, Pageable pageable);
}
