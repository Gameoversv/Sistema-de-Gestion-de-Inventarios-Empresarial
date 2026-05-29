package com.inventory.report.service;

import com.inventory.product.domain.Product;
import com.inventory.product.repository.ProductRepository;
import com.inventory.report.dto.CategoryStockDto;
import com.inventory.report.dto.LowStockItemDto;
import com.inventory.report.dto.LowStockReportResponse;
import com.inventory.report.dto.StockSummaryResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

  private final ProductRepository productRepository;

  @Override
  @Transactional(readOnly = true)
  public StockSummaryResponse stockSummary() {
    List<Product> all = productRepository.findAll();
    List<Product> active = all.stream().filter(p -> Boolean.TRUE.equals(p.getActive())).toList();
    int lowStockCount =
        (int) active.stream().filter(p -> p.getStock() <= p.getMinimumStock()).count();

    BigDecimal totalValue =
        active.stream()
            .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getStock())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, List<Product>> grouped =
        active.stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getCategory() != null ? p.getCategory().getName() : "Sin categoría"));

    List<CategoryStockDto> byCategory =
        grouped.entrySet().stream()
            .map(e -> toCategoryDto(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(CategoryStockDto::categoryName))
            .toList();

    return new StockSummaryResponse(
        all.size(), active.size(), lowStockCount, totalValue, byCategory);
  }

  @Override
  @Transactional(readOnly = true)
  public LowStockReportResponse lowStockAlert(int threshold) {
    List<Product> lowStock = productRepository.findLowStockProducts();
    List<Product> filtered =
        threshold > 0
            ? lowStock.stream().filter(p -> p.getStock() <= threshold).toList()
            : lowStock;

    List<LowStockItemDto> items = filtered.stream().map(this::toLowStockItem).toList();
    return new LowStockReportResponse(threshold, items.size(), items);
  }

  private CategoryStockDto toCategoryDto(String name, List<Product> products) {
    long totalStock = products.stream().mapToLong(Product::getStock).sum();
    BigDecimal totalValue =
        products.stream()
            .map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getStock())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CategoryStockDto(name, products.size(), totalStock, totalValue);
  }

  private LowStockItemDto toLowStockItem(Product p) {
    return new LowStockItemDto(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getStock(),
        p.getMinimumStock(),
        p.getMinimumStock() - p.getStock(),
        p.getCategory() != null ? p.getCategory().getName() : "Sin categoría");
  }
}
