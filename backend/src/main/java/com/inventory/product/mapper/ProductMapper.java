package com.inventory.product.mapper;

import com.inventory.product.domain.Product;
import com.inventory.product.dto.ProductCreateRequest;
import com.inventory.product.dto.ProductPatchRequest;
import com.inventory.product.dto.ProductResponse;
import com.inventory.product.dto.ProductUpdateRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Interfaz MapStruct que gestiona las conversiones entre la entidad {@link
 * com.inventory.product.domain.Product} y sus DTOs de entrada (create, update, patch) y salida.
 * Generada en tiempo de compilación como bean de Spring. Los campos de auditoría y relaciones se
 * mapean manualmente.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

  @BeanMapping(builder = @Builder(disableBuilder = true))
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "category", ignore = true)
  @Mapping(target = "minimumStock", ignore = true)
  @Mapping(target = "active", ignore = true)
  Product toEntity(ProductCreateRequest request);

  @Mapping(target = "categoryId", source = "category.id")
  @Mapping(target = "categoryName", source = "category.name")
  ProductResponse toResponse(Product product);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "category", ignore = true)
  void updateEntity(ProductUpdateRequest request, @MappingTarget Product product);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "category", ignore = true)
  void patchEntity(ProductPatchRequest request, @MappingTarget Product product);
}
