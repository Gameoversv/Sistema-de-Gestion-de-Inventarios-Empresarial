package com.inventory.product.repository;

import com.inventory.product.domain.Product;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecification {

  private ProductSpecification() {}

  public static Specification<Product> hasSku(String sku) {
    return (root, query, cb) -> cb.equal(root.get("sku"), sku);
  }

  public static Specification<Product> skuContains(String fragment) {
    return (root, query, cb) ->
        cb.like(cb.lower(root.get("sku")), "%" + fragment.toLowerCase() + "%");
  }

  public static Specification<Product> nameContains(String fragment) {
    return (root, query, cb) ->
        cb.like(cb.lower(root.get("name")), "%" + fragment.toLowerCase() + "%");
  }

  public static Specification<Product> hasCategory(Long categoryId) {
    return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
  }

  public static Specification<Product> isActive(Boolean active) {
    return (root, query, cb) -> cb.equal(root.get("active"), active);
  }

  public static Specification<Product> nameOrSkuContains(String q) {
    return Specification.where(nameContains(q)).or(skuContains(q));
  }
}
