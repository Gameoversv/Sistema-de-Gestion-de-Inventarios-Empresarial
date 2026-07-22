package com.inventory.stock.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.metrics.StockMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Listeners de eventos de stock")
class StockEventListenersTest {

  @Mock private StockMetrics stockMetrics;

  @InjectMocks private StockThresholdListener thresholdListener;
  @InjectMocks private StockMovementMetricsListener movementListener;

  @Test
  @DisplayName("una alerta de umbral incrementa el contador con el SKU del producto")
  void thresholdEventIncrementsAlertCounter() {
    thresholdListener.onThresholdCrossed(
        new StockThresholdCrossedEvent(1L, "SKU-001", "Widget", 2, 5));

    verify(stockMetrics).recordThresholdAlert("SKU-001");
    verifyNoMoreInteractions(stockMetrics);
  }

  @Test
  @DisplayName("un movimiento confirmado incrementa el contador con su tipo y cantidad")
  void movementEventIncrementsMovementCounter() {
    movementListener.onMovementRecorded(
        new StockMovementRecordedEvent(1L, "SKU-001", MovementType.OUT, 3));

    verify(stockMetrics).recordMovement(MovementType.OUT, 3);
    verifyNoMoreInteractions(stockMetrics);
  }
}
