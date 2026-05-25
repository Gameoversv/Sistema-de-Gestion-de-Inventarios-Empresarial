package com.inventory.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.inventory.common.exception.BusinessException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Product;
import com.inventory.product.mapper.ProductMapper;
import com.inventory.product.repository.ProductRepository;
import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.domain.StockMovement.MovementType;
import com.inventory.stock.dto.StockMovementRequest;
import com.inventory.stock.dto.StockMovementResponse;
import com.inventory.stock.event.StockThresholdCrossedEvent;
import com.inventory.stock.mapper.StockMovementMapper;
import com.inventory.stock.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

  @Mock private StockMovementRepository movementRepository;
  @Mock private ProductRepository productRepository;
  @Mock private StockMovementMapper movementMapper;
  @Mock private ProductMapper productMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private StockServiceImpl stockService;

  private Product product;
  private StockMovementResponse dummyResponse;

  @BeforeEach
  void setUp() {
    product = new Product();
    product.setSku("SKU-001");
    product.setName("Widget");
    product.setPrice(BigDecimal.TEN);
    product.setStock(20);
    product.setMinimumStock(5);
    product.setActive(true);

    dummyResponse =
        new StockMovementResponse(
            1L,
            null,
            "SKU-001",
            "Widget",
            MovementType.IN,
            5,
            20,
            25,
            null,
            null,
            "alice",
            Instant.now());
  }

  private JwtAuthenticationToken jwt(String username) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("preferred_username", username)
            .claim("sub", username + "-sub")
            .build();
    return new JwtAuthenticationToken(jwt);
  }

  @Test
  @DisplayName("IN — increases stock by quantity")
  void registerMovement_in_increasesStock() {
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.IN, 5, "Purchase", null), jwt("alice"));

    assertThat(product.getStock()).isEqualTo(25);
    assertThat(captor.getValue().getQuantityBefore()).isEqualTo(20);
    assertThat(captor.getValue().getQuantityAfter()).isEqualTo(25);
  }

  @Test
  @DisplayName("IN — sets performedBy from JWT preferred_username")
  void registerMovement_in_setsPerformedBy() {
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.IN, 3, null, null), jwt("alice"));

    assertThat(captor.getValue().getPerformedBy()).isEqualTo("alice");
  }

  @Test
  @DisplayName("OUT — decreases stock by quantity")
  void registerMovement_out_decreasesStock() {
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 8, "Sale", null), jwt("bob"));

    assertThat(product.getStock()).isEqualTo(12);
    assertThat(captor.getValue().getQuantityBefore()).isEqualTo(20);
    assertThat(captor.getValue().getQuantityAfter()).isEqualTo(12);
  }

  @Test
  @DisplayName("OUT — throws BusinessException when stock insufficient")
  void registerMovement_out_insufficientStock_throws() {
    product.setStock(3);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(
            () ->
                stockService.registerMovement(
                    new StockMovementRequest(1L, MovementType.OUT, 5, null, null), jwt("bob")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Insufficient stock");

    verify(movementRepository, never()).save(any());
  }

  @Test
  @DisplayName("OUT — exact stock (not zero) succeeds")
  void registerMovement_out_exactStock_succeeds() {
    product.setStock(5);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 5, null, null), jwt("bob"));

    assertThat(product.getStock()).isEqualTo(0);
  }

  @Test
  @DisplayName("ADJUSTMENT — sets stock to absolute value")
  void registerMovement_adjust_setsAbsoluteStock() {
    product.setStock(20);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.ADJUSTMENT, 30, "Recount", null), jwt("alice"));

    assertThat(product.getStock()).isEqualTo(30);
    assertThat(captor.getValue().getQuantityBefore()).isEqualTo(20);
    assertThat(captor.getValue().getQuantityAfter()).isEqualTo(30);
  }

  @Test
  @DisplayName("ADJUSTMENT — stores target quantity on movement")
  void registerMovement_adjust_storesTargetQuantity() {
    product.setStock(20);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
    when(movementRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.ADJUSTMENT, 30, null, null), jwt("alice"));

    assertThat(captor.getValue().getQuantity()).isEqualTo(30);
  }

  @Test
  @DisplayName("publishes StockThresholdCrossedEvent when stock crosses minimum")
  void registerMovement_belowMinimum_publishesEvent() {
    product.setStock(10);
    product.setMinimumStock(8);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 5, null, null), jwt("alice"));

    ArgumentCaptor<StockThresholdCrossedEvent> eventCaptor =
        ArgumentCaptor.forClass(StockThresholdCrossedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().currentStock()).isEqualTo(5);
    assertThat(eventCaptor.getValue().minimumStock()).isEqualTo(8);
  }

  @Test
  @DisplayName("does NOT publish event when stock stays above minimum")
  void registerMovement_aboveMinimum_noEvent() {
    product.setStock(20);
    product.setMinimumStock(5);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 3, null, null), jwt("alice"));

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("throws ResourceNotFoundException when product missing")
  void registerMovement_productNotFound_throws() {
    when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                stockService.registerMovement(
                    new StockMovementRequest(99L, MovementType.IN, 5, null, null), jwt("alice")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }
}
