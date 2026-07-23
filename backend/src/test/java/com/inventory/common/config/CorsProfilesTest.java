package com.inventory.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Fija el origen CORS de cada perfil.
 *
 * <p>Existe por P-2a: la demo se levantaba con el perfil {@code staging}, que apunta CORS a {@code
 * https://staging.inventory.example.com}. Contra un frontend en {@code localhost:3000} el navegador
 * bloqueaba toda petición, y la única salida era un override a mano en {@code .env} que no
 * sobrevivía a un {@code down -v && up}. El perfil {@code demo} lo resuelve de raíz.
 *
 * <p>Las dos aserciones que importan son las negativas: que {@code demo} permita localhost no sirve
 * de nada si mañana alguien "arregla" el problema añadiendo localhost a {@code staging} o a {@code
 * prod}, que es justo lo que este test impide.
 */
class CorsProfilesTest {

  // Verifica que el perfil demo permite el frontend local; sin esto P-3 se queda sin interfaz.
  @Test
  void demoProfile_allowsLocalFrontend() {
    assertThat(allowedOriginsFor("demo")).containsExactly("http://localhost:3000");
  }

  // Verifica que demo no muestrea trazas a la baja: en una demo corta, 0.5 deja paneles a medias.
  @Test
  void demoProfile_samplesEveryTrace() {
    assertThat(propertyFor("demo", "management.tracing.sampling.probability")).isEqualTo("1.0");
  }

  // Verifica que staging sigue sin admitir orígenes locales: espeja producción.
  @Test
  void stagingProfile_rejectsLocalOrigins() {
    assertThat(allowedOriginsFor("staging")).noneMatch(origin -> origin.contains("localhost"));
  }

  // Verifica lo mismo para prod, que es donde un origen local sí sería explotable.
  @Test
  void prodProfile_rejectsLocalOrigins() {
    assertThat(allowedOriginsFor("prod")).noneMatch(origin -> origin.contains("localhost"));
  }

  private List<String> allowedOriginsFor(String profile) {
    String raw = propertyFor(profile, "app.cors.allowed-origins");
    assertThat(raw).as("app.cors.allowed-origins en el perfil %s", profile).isNotBlank();
    return List.of(raw.split(","));
  }

  private String propertyFor(String profile, String key) {
    var holder =
        new Object() {
          String value;
        };
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(context -> holder.value = context.getEnvironment().getProperty(key));
    return holder.value;
  }
}
