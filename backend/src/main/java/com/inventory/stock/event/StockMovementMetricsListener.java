package com.inventory.stock.event;

import com.inventory.stock.metrics.StockMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce los movimientos confirmados a métricas de negocio. Escucha tras el commit por el mismo
 * motivo que {@link StockThresholdListener}: un movimiento cuya transacción se deshace no debe
 * quedar contado en el dashboard.
 */
@Component
@RequiredArgsConstructor
public class StockMovementMetricsListener {

  private final StockMetrics stockMetrics;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMovementRecorded(StockMovementRecordedEvent event) {
    stockMetrics.recordMovement(event.type(), event.quantity());
  }
}
