package com.inventory.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.inventory.common.exception.ConflictException;
import com.inventory.common.exception.ResourceNotFoundException;
import com.inventory.product.domain.Category;
import com.inventory.product.dto.CategoryCreateRequest;
import com.inventory.product.dto.CategoryResponse;
import com.inventory.product.dto.CategoryUpdateRequest;
import com.inventory.product.repository.CategoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;

  @InjectMocks private CategoryServiceImpl categoryService;

  private Category category;

  @BeforeEach
  void setUp() {
    category = new Category();
    category.setId(1L);
    category.setName("Electronics");
    category.setDescription("Electronic products");
    category.setCreatedAt(Instant.now());
    category.setUpdatedAt(Instant.now());
  }

  // ── create ────────────────────────────────────────────────────────────────

  // Verifica que se crea y retorna una categoría cuando el nombre es único en el repositorio.
  @Test
  @DisplayName("create - creates category when name is unique")
  void create_uniqueName_returnsCategoryResponse() {
    var request = new CategoryCreateRequest("Electronics", "Electronic products");
    when(categoryRepository.existsByName("Electronics")).thenReturn(false);
    when(categoryRepository.save(any(Category.class))).thenReturn(category);

    CategoryResponse result = categoryService.create(request);

    assertThat(result.name()).isEqualTo("Electronics");
    assertThat(result.description()).isEqualTo("Electronic products");
    verify(categoryRepository).save(any(Category.class));
  }

  // Verifica que un nombre duplicado lanza ConflictException sin persistir nada.
  @Test
  @DisplayName("create - throws ConflictException when name already exists")
  void create_duplicateName_throwsConflict() {
    var request = new CategoryCreateRequest("Electronics", null);
    when(categoryRepository.existsByName("Electronics")).thenReturn(true);

    assertThatThrownBy(() -> categoryService.create(request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Electronics");
    verify(categoryRepository, never()).save(any());
  }

  // Verifica que se puede crear una categoría con descripción nula (campo opcional).
  @Test
  @DisplayName("create - creates category with null description")
  void create_withNullDescription_savesWithoutDescription() {
    var request = new CategoryCreateRequest("Furniture", null);
    Category saved = new Category();
    saved.setId(2L);
    saved.setName("Furniture");
    when(categoryRepository.existsByName("Furniture")).thenReturn(false);
    when(categoryRepository.save(any(Category.class))).thenReturn(saved);

    CategoryResponse result = categoryService.create(request);

    assertThat(result.name()).isEqualTo("Furniture");
    assertThat(result.description()).isNull();
  }

  // ── findById ──────────────────────────────────────────────────────────────

  // Verifica que findById retorna el DTO de la categoría cuando el ID existe.
  @Test
  @DisplayName("findById - returns category when exists")
  void findById_existingId_returnsCategoryResponse() {
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

    CategoryResponse result = categoryService.findById(1L);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.name()).isEqualTo("Electronics");
  }

  // Verifica que findById lanza ResourceNotFoundException cuando la categoría no existe.
  @Test
  @DisplayName("findById - throws ResourceNotFoundException when not found")
  void findById_missingId_throwsNotFound() {
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.findById(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── findAll ───────────────────────────────────────────────────────────────

  // Verifica que findAll retorna la lista completa de todas las categorías existentes.
  @Test
  @DisplayName("findAll - returns list of all categories")
  void findAll_returnsAllCategories() {
    Category c2 = new Category();
    c2.setId(2L);
    c2.setName("Furniture");
    when(categoryRepository.findAll()).thenReturn(List.of(category, c2));

    List<CategoryResponse> result = categoryService.findAll();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("Electronics");
    assertThat(result.get(1).name()).isEqualTo("Furniture");
  }

  // Verifica que findAll retorna lista vacía cuando no hay categorías en el repositorio.
  @Test
  @DisplayName("findAll - returns empty list when no categories")
  void findAll_noCategories_returnsEmptyList() {
    when(categoryRepository.findAll()).thenReturn(List.of());

    List<CategoryResponse> result = categoryService.findAll();

    assertThat(result).isEmpty();
  }

  // ── update ────────────────────────────────────────────────────────────────

  // Verifica que se puede actualizar la descripción de una categoría sin cambiar su nombre.
  @Test
  @DisplayName("update - updates category when name does not change")
  void update_sameName_updatesDescription() {
    var request = new CategoryUpdateRequest("Electronics", "Updated description");
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    when(categoryRepository.save(category)).thenReturn(category);

    CategoryResponse result = categoryService.update(1L, request);

    assertThat(result).isNotNull();
    verify(categoryRepository).save(category);
  }

  // Verifica que se puede actualizar a un nombre nuevo cuando este no está en uso.
  @Test
  @DisplayName("update - updates category when new name is unique")
  void update_newUniqueName_updatesSuccessfully() {
    var request = new CategoryUpdateRequest("Gadgets", "Updated description");
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    when(categoryRepository.existsByName("Gadgets")).thenReturn(false);
    when(categoryRepository.save(category)).thenReturn(category);

    CategoryResponse result = categoryService.update(1L, request);

    assertThat(result).isNotNull();
    verify(categoryRepository).save(category);
  }

  // Verifica que actualizar a un nombre ya en uso lanza ConflictException sin persistir.
  @Test
  @DisplayName("update - throws ConflictException when new name already taken")
  void update_newNameConflict_throwsConflict() {
    var request = new CategoryUpdateRequest("Furniture", "desc");
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    when(categoryRepository.existsByName("Furniture")).thenReturn(true);

    assertThatThrownBy(() -> categoryService.update(1L, request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Furniture");
    verify(categoryRepository, never()).save(any());
  }

  // Verifica que actualizar una categoría inexistente lanza ResourceNotFoundException.
  @Test
  @DisplayName("update - throws ResourceNotFoundException when category not found")
  void update_missingCategory_throwsNotFound() {
    var request = new CategoryUpdateRequest("Electronics", null);
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.update(99L, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
  }

  // ── delete ────────────────────────────────────────────────────────────────

  // Verifica que una categoría existente se elimina invocando deleteById en el repositorio.
  @Test
  @DisplayName("delete - deletes category when it exists")
  void delete_existingCategory_deletesSuccessfully() {
    when(categoryRepository.existsById(1L)).thenReturn(true);

    categoryService.delete(1L);

    verify(categoryRepository).deleteById(1L);
  }

  // Verifica que eliminar una categoría inexistente lanza ResourceNotFoundException sin borrar.
  @Test
  @DisplayName("delete - throws ResourceNotFoundException when category does not exist")
  void delete_missingCategory_throwsNotFound() {
    when(categoryRepository.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> categoryService.delete(99L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("99");
    verify(categoryRepository, never()).deleteById(any());
  }
}
