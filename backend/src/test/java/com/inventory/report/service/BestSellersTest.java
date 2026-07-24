package com.inventory.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.product.repository.CategoryRepository;
import com.inventory.product.repository.ProductRepository;
import com.inventory.report.dto.BestSellerDto;
import com.inventory.report.dto.BestSellersResponse;
import com.inventory.stock.repository.StockMovementRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * D-1 — «productos más vendidos», que el enunciado exige en el dashboard.
 *
 * <p>Hasta ahora el dashboard mostraba «Top 8 — valor de inventario», que es precio × stock: mide
 * lo que hay guardado, no lo que sale. Un producto caro que no se vende nunca encabezaba ese
 * ranking. Lo más vendido solo se puede saber agregando los movimientos {@code OUT}.
 */
@ExtendWith(MockitoExtension.class)
class BestSellersTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private StockMovementRepository stockMovementRepository;

  @InjectMocks private ReportServiceImpl reportService;

  // Verifica que el ranking sale de la agregación de salidas y conserva el orden del repositorio.
  @Test
  void bestSellers_returnsRankingFromOutMovements() {
    when(stockMovementRepository.findBestSellers(any(Pageable.class)))
        .thenReturn(
            List.of(
                new BestSellerDto(2L, "MOUSE-001", "Mouse", 140L, 12L),
                new BestSellerDto(1L, "LAPTOP-001", "Laptop", 35L, 7L)));

    BestSellersResponse result = reportService.bestSellers(10);

    assertThat(result.products())
        .extracting(BestSellerDto::sku)
        .containsExactly("MOUSE-001", "LAPTOP-001");
    assertThat(result.products().get(0).unitsSold()).isEqualTo(140L);
    assertThat(result.count()).isEqualTo(2);
  }

  // Verifica que sin salidas registradas la respuesta es vacía y no nula: el panel debe poder
  // pintar «sin datos» en vez de romperse en la primera demo con la base recién levantada.
  @Test
  void bestSellers_noOutMovements_returnsEmpty() {
    when(stockMovementRepository.findBestSellers(any(Pageable.class))).thenReturn(List.of());

    BestSellersResponse result = reportService.bestSellers(10);

    assertThat(result.products()).isEmpty();
    assertThat(result.count()).isZero();
  }

  // Verifica que un límite ausente o absurdo cae al valor por defecto en vez de pedir 0 filas.
  @ParameterizedTest(name = "limit={0} cae al valor por defecto")
  @ValueSource(ints = {0, -5})
  void bestSellers_invalidLimit_fallsBackToDefault(int limit) {
    when(stockMovementRepository.findBestSellers(any(Pageable.class))).thenReturn(List.of());

    BestSellersResponse result = reportService.bestSellers(limit);

    assertThat(result.limit()).isEqualTo(10);
    verify(stockMovementRepository).findBestSellers(PageRequest.of(0, 10));
  }

  // Verifica que el límite pedido se traslada tal cual a la consulta: es la única defensa contra
  // que el dashboard pida 8 y la consulta traiga la tabla entera.
  @Test
  void bestSellers_explicitLimit_isPassedToTheQuery() {
    when(stockMovementRepository.findBestSellers(any(Pageable.class))).thenReturn(List.of());

    reportService.bestSellers(5);

    verify(stockMovementRepository).findBestSellers(PageRequest.of(0, 5));
  }
}
