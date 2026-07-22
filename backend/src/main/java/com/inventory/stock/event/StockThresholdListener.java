package com.inventory.stock.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener de eventos de Spring que reacciona a {@link StockThresholdCrossedEvent} tras el commit
 * de la transacción. Actualmente emite una advertencia en el log; puede extenderse para enviar
 * notificaciones externas (email, webhook, etc.).
 */
@Component
@Slf4j
public class StockThresholdListener {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onThresholdCrossed(StockThresholdCrossedEvent event) {
    log.warn(
        "STOCK ALERT — product={} sku={} currentStock={} minimumStock={}",
        event.productId(),
        event.sku(),
        event.currentStock(),
        event.minimumStock());
  }
}
