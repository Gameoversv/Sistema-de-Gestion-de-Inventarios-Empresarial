package com.inventory.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Integración contra la base de datos ya desplegada, no contra una efímera.
 *
 * <p>Existe por un fallo concreto: el paso «Run IT tests against live DB» de {@code staging.yml}
 * invocaba {@code StockServiceConcurrencyIT} pasándole la URL de la base desplegada y el secreto de
 * su contraseña, pero esa clase declara {@code @Container PostgreSQLContainer} y un
 * {@code @DynamicPropertySource}. Las propiedades dinámicas tienen la precedencia más alta de
 * Spring, por encima de los {@code -D} de la línea de comandos, así que el test levantaba su propio
 * Postgres efímero y jamás tocaba la base de staging. Verificado en el run 29973867778: «Container
 * postgres:16-alpine started».
 *
 * <p>Esta clase toma la configuración <b>solo del entorno</b> ({@code SPRING_DATASOURCE_URL} y
 * compañía). No hay Testcontainers ni {@code @DynamicPropertySource}: si alguien los reintroduce,
 * {@link #laConfiguracionExternaEsLaQueManda()} falla.
 *
 * <p>Todas las comprobaciones son de <b>solo lectura</b>. Flyway se desactiva para no aplicar
 * migraciones sobre un entorno desplegado desde un test; el estado de las migraciones se comprueba
 * leyendo {@code flyway_schema_history}. La validación más fuerte es implícita: con {@code
 * ddl-auto: validate}, si el esquema desplegado no cuadra con las entidades el contexto ni arranca.
 *
 * <p>El aislamiento lo da el perfil de Maven {@code live-db-it}, no una anotación condicional. La
 * primera versión de esta clase llevaba {@code @EnabledIfEnvironmentVariable}, y eso reproducía el
 * defecto que venía a corregir: sin la variable definida el paso terminaba en {@code BUILD SUCCESS}
 * con «Skipped: 3», verde sin haber probado nada. Ahora la configuración por defecto de failsafe la
 * excluye y solo el perfil la incluye, con {@code failIfNoTests}: si no hay base a la que
 * conectarse, el test <b>falla</b> en vez de saltarse.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.NONE,
    properties = {
      // No se migra desde un test contra un entorno desplegado: solo se lee el resultado.
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri="
    })
class LiveDatabaseIT {

  private static final List<String> TABLAS_DEL_DOMINIO =
      List.of(
          "products",
          "categories",
          "stock_movements",
          "audit_logs",
          "app_users",
          "products_aud",
          "stock_movements_aud",
          "revinfo");

  @Autowired private DataSource dataSource;

  @Value("${spring.datasource.url}")
  private String urlEnUso;

  // El resource server necesita un JwtDecoder aunque este test no autentique nada.
  @MockBean private JwtDecoder jwtDecoder;

  @Test
  @DisplayName("la URL de la base viene del entorno y no de un contenedor efímero")
  void laConfiguracionExternaEsLaQueManda() throws SQLException {
    String esperada = System.getenv("SPRING_DATASOURCE_URL");
    assertThat(esperada)
        .as("SPRING_DATASOURCE_URL debe estar definida; sin ella el test no prueba nada")
        .isNotBlank();

    assertThat(urlEnUso)
        .as("Spring resolvió una URL distinta de la del entorno: algo la está sobrescribiendo")
        .isEqualTo(esperada);

    try (Connection conexion = dataSource.getConnection()) {
      DatabaseMetaData meta = conexion.getMetaData();
      assertThat(meta.getURL())
          .as("la conexión real no apunta a la base configurada por entorno")
          .isEqualTo(esperada);
      assertThat(conexion.isValid(5)).isTrue();
    }
  }

  @Test
  @DisplayName("el esquema desplegado tiene las migraciones de Flyway aplicadas")
  void lasMigracionesEstanAplicadas() throws SQLException {
    List<String> versiones = new ArrayList<>();
    try (Connection conexion = dataSource.getConnection();
        Statement sentencia = conexion.createStatement();
        ResultSet filas =
            sentencia.executeQuery(
                "SELECT version, success FROM flyway_schema_history"
                    + " WHERE version IS NOT NULL ORDER BY installed_rank")) {
      while (filas.next()) {
        assertThat(filas.getBoolean("success"))
            .as("la migración %s quedó marcada como fallida", filas.getString("version"))
            .isTrue();
        versiones.add(filas.getString("version"));
      }
    }

    // V1..V7 son las que existen hoy en db/migration. Se comprueba que estén todas y no
    // solo que la tabla tenga filas: un despliegue a medias también deja historial.
    assertThat(versiones).contains("1", "2", "3", "4", "5", "6", "7");
  }

  @Test
  @DisplayName("las tablas del dominio existen en la base desplegada")
  void lasTablasDelDominioExisten() throws SQLException {
    List<String> encontradas = new ArrayList<>();
    try (Connection conexion = dataSource.getConnection()) {
      DatabaseMetaData meta = conexion.getMetaData();
      try (ResultSet tablas = meta.getTables(null, "public", "%", new String[] {"TABLE"})) {
        while (tablas.next()) {
          encontradas.add(tablas.getString("TABLE_NAME").toLowerCase());
        }
      }
    }
    assertThat(encontradas).containsAll(TABLAS_DEL_DOMINIO);
  }
}
