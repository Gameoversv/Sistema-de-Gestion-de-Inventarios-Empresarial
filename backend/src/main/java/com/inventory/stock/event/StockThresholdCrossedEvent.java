package com.inventory.stock.event;

/**
 * Evento de dominio que se publica cuando el stock de un producto cae hasta o por debajo del umbral
 * mínimo configurado. Es procesado de forma transaccional por {@link
 * com.inventory.stock.event.StockThresholdListener} tras el commit.
 */
public record StockThresholdCrossedEvent(
    Long productId, String sku, String productName, int currentStock, int minimumStock) {}
