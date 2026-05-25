package com.inventory.stock.service;

import com.inventory.common.exception.BusinessException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import com.inventory.stock.event.StockThresholdCrossedEvent;
import com.inventory.stock.mapper.StockMovementMapper;
import com.inventory.stock.repository.StockMovementRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockServiceImpl implements StockService {

  private final StockMovementRepository movementRepository;
  private final ProductRepository productRepository;
  private final StockMovementMapper movementMapper;
  private final ProductMapper productMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public StockMovementResponse registerMovement(
      StockMovementRequest request, Authentication authentication) {

    String username = extractUsername(authentication);

    Product product =
        productRepository
            .findById(request.productId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Product not found: " + request.productId()));

    int quantityBefore = product.getStock();
    int newStock = computeNewStock(request.type(), quantityBefore, request.quantity());

    product.setStock(newStock);

    StockMovement movement =
        StockMovement.builder()
            .product(product)
            .type(request.type())
            .quantity(request.quantity())
            .quantityBefore(quantityBefore)
            .quantityAfter(newStock)
            .performedBy(username)
            .reason(request.reason())
            .referenceId(request.referenceId())
            .build();

    StockMovement saved = movementRepository.save(movement);

    if (newStock <= product.getMinimumStock()) {
      eventPublisher.publishEvent(
          new StockThresholdCrossedEvent(
              product.getId(),
              product.getSku(),
              product.getName(),
              newStock,
              product.getMinimumStock()));
    }

    return movementMapper.toResponse(saved);
  }

  @Override
  public int currentStock(Long productId) {
    return productRepository
        .findById(productId)
        .map(Product::getStock)
        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
  }

  @Override
  public Page<StockMovementResponse> getMovements(
      Long productId, MovementType type, Instant from, Instant to, Pageable pageable) {
    return movementRepository
        .findFiltered(productId, type, from, to, pageable)
        .map(movementMapper::toResponse);
  }

  @Override
  public List<ProductResponse> getLowStockAlerts() {
    return productRepository.findLowStockProducts().stream()
        .map(productMapper::toResponse)
        .toList();
  }

  private int computeNewStock(MovementType type, int current, int quantity) {
    return switch (type) {
      case IN -> current + quantity;
      case OUT -> {
        int result = current - quantity;
        if (result < 0) {
          throw new BusinessException(
              "Insufficient stock: current=" + current + ", requested=" + quantity);
        }
        yield result;
      }
      case ADJUSTMENT -> quantity;
    };
  }

  private String extractUsername(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String username = jwt.getClaimAsString("preferred_username");
      return (username != null && !username.isBlank()) ? username : jwtAuth.getName();
    }
    return authentication != null ? authentication.getName() : "system";
  }
}
