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
import com.inventory.stock.event.StockMovementRecordedEvent;
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

  // Verifica que un movimiento IN incrementa el stock del producto en la cantidad recibida.
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

  // Verifica que el campo performedBy del movimiento se extrae del claim preferred_username del
  // JWT.
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

  // Verifica que un movimiento OUT disminuye el stock del producto en la cantidad indicada.
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

  // Verifica que un movimiento OUT con cantidad mayor al stock lanza BusinessException sin guardar.
  @Test
  @DisplayName("OUT — throws BusinessException when stock insufficient")
  void registerMovement_out_insufficientStock_throws() {
    product.setStock(3);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    StockMovementRequest request = new StockMovementRequest(1L, MovementType.OUT, 5, null, null);
    JwtAuthenticationToken auth = jwt("bob");

    assertThatThrownBy(() -> stockService.registerMovement(request, auth))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Insufficient stock");

    verify(movementRepository, never()).save(any());
  }

  // Verifica que un movimiento OUT por exactamente el stock disponible deja el producto en cero.
  @Test
  @DisplayName("OUT — exact stock (not zero) succeeds")
  void registerMovement_out_exactStock_succeeds() {
    product.setStock(5);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 5, null, null), jwt("bob"));

    assertThat(product.getStock()).isZero();
  }

  // Verifica que ADJUSTMENT establece el stock en la cantidad absoluta indicada, no relativa.
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

  // Verifica que el movimiento ADJUSTMENT guarda la cantidad objetivo en el campo quantity.
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

  // Verifica que cuando el stock cae por debajo del mínimo se publica StockThresholdCrossedEvent.
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

    StockThresholdCrossedEvent published = capturePublished(StockThresholdCrossedEvent.class);
    assertThat(published.currentStock()).isEqualTo(5);
    assertThat(published.minimumStock()).isEqualTo(8);
  }

  // Verifica que no se publica el evento de umbral cuando el stock se mantiene por encima del
  // mínimo. El de movimiento sí se publica siempre: alimenta las métricas de negocio.
  @Test
  @DisplayName("does NOT publish StockThresholdCrossedEvent when stock stays above minimum")
  void registerMovement_aboveMinimum_noThresholdEvent() {
    product.setStock(20);
    product.setMinimumStock(5);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 3, null, null), jwt("alice"));

    verify(eventPublisher, never()).publishEvent(any(StockThresholdCrossedEvent.class));
  }

  // Verifica que cada movimiento confirmado publica StockMovementRecordedEvent, que es lo que
  // convierte el movimiento en métrica de negocio (inventory_stock_movements_total).
  @Test
  @DisplayName("publishes StockMovementRecordedEvent with type and quantity")
  void registerMovement_always_publishesMovementEvent() {
    product.setStock(20);
    product.setMinimumStock(5);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(movementMapper.toResponse(any())).thenReturn(dummyResponse);

    stockService.registerMovement(
        new StockMovementRequest(1L, MovementType.OUT, 3, null, null), jwt("alice"));

    StockMovementRecordedEvent published = capturePublished(StockMovementRecordedEvent.class);
    assertThat(published.type()).isEqualTo(MovementType.OUT);
    assertThat(published.quantity()).isEqualTo(3);
    assertThat(published.sku()).isEqualTo("SKU-001");
  }

  // registerMovement publica dos eventos distintos; este helper aísla el que interesa a cada test.
  private <T> T capturePublished(Class<T> eventType) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
    return captor.getAllValues().stream()
        .filter(eventType::isInstance)
        .map(eventType::cast)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("No se publicó ningún evento de tipo " + eventType.getName()));
  }

  // Verifica que cuando el producto no existe se lanza ResourceNotFoundException con el ID.
  @Test
  @DisplayName("throws ResourceNotFoundException when product missing")
  void registerMovement_productNotFound_throws() {
    when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    StockMovementRequest request = new StockMovementRequest(99L, MovementType.IN, 5, null, null);
    JwtAuthenticationToken auth = jwt("alice");

    assertThatThrownBy(() -> stockService.registerMovement(request, auth))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }
}
