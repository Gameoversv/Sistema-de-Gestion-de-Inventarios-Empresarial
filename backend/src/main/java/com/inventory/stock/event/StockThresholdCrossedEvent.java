package com.inventory.stock.event;

public record StockThresholdCrossedEvent(
    Long productId, String sku, String productName, int currentStock, int minimumStock) {}
