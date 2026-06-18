package com.inventory.stock.mapper;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.dto.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Interfaz MapStruct que convierte la entidad {@link com.inventory.stock.domain.StockMovement} a
 * {@link com.inventory.stock.dto.StockMovementResponse}, mapeando los campos del producto asociado
 * (id, SKU, nombre) al nivel raíz del DTO.
 */
@Mapper(componentModel = "spring")
public interface StockMovementMapper {

  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "sku", source = "product.sku")
  @Mapping(target = "productName", source = "product.name")
  StockMovementResponse toResponse(StockMovement movement);
}
