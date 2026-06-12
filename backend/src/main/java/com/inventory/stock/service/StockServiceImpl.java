package com.inventory.stock.service;

import com.inventory.common.exception.BusinessException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Product;
import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockServiceImpl implements StockService {

  private final StockMovementRepository movementRepository;
  private final ProductRepository productRepository;

  @Override
  @Transactional
  public StockMovement register(Long productId, MovementType type, int quantity, String reason) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

    int delta = type == MovementType.OUT ? -quantity : quantity;
    int newStock = product.getStock() + delta;
    if (newStock < 0) {
      throw new BusinessException(
          "Insufficient stock for product " + productId + ": current=" + product.getStock());
    }
    product.setStock(newStock);

    StockMovement movement =
        StockMovement.builder()
            .product(product)
            .type(type)
            .quantity(quantity)
            .reason(reason)
            .build();
    return movementRepository.save(movement);
  }

  @Override
  public int currentStock(Long productId) {
    return productRepository
        .findById(productId)
        .map(Product::getStock)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
  }

  @Override
  public Page<StockMovement> movements(Long productId, Pageable pageable) {
    return movementRepository.findByProductId(productId, pageable);
  }
}
