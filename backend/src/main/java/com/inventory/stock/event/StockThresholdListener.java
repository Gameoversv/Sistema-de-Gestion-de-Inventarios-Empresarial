package com.inventory.stock.event;

import com.inventory.stock.metrics.StockMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener de eventos de Spring que reacciona a {@link StockThresholdCrossedEvent} tras el commit
 * de la transacción: deja constancia en el log e incrementa el contador de alertas, que es lo que
 * lleva el evento de negocio más relevante del sistema al dashboard y a Alertmanager. Puede
 * extenderse para enviar notificaciones externas (email, webhook, etc.).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockThresholdListener {

  private final StockMetrics stockMetrics;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onThresholdCrossed(StockThresholdCrossedEvent event) {
    log.warn(
        "STOCK ALERT — product={} sku={} currentStock={} minimumStock={}",
        event.productId(),
        event.sku(),
        event.currentStock(),
        event.minimumStock());

    stockMetrics.recordThresholdAlert(event.sku());
  }
}
