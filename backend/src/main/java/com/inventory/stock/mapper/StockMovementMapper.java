package com.inventory.stock.mapper;

import com.inventory.stock.domain.StockMovement;
import com.inventory.stock.dto.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "sku", source = "product.sku")
  @Mapping(target = "productName", source = "product.name")
  StockMovementResponse toResponse(StockMovement movement);
}
