package com.inventory.stock.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
