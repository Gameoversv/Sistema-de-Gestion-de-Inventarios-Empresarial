package com.inventory.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String kcIssuerUri;

  @Bean
  public OpenAPI inventoryOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Inventory Management API")
                .version("1.0.0")
                .description(
                    "Sistema de Gestión de Inventarios Empresarial — PUCMM. "
                        + "API REST para gestión de productos, stock, reportes y auditoría.")
                .contact(
                    new Contact().name("PUCMM Inventory Team").email("snipervargas37@gmail.com"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .addSecurityItem(new SecurityRequirement().addList("keycloak"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "keycloak",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("Keycloak OAuth2 Authorization Code + PKCE")
                        .flows(
                            new OAuthFlows()
                                .authorizationCode(
                                    new OAuthFlow()
                                        .authorizationUrl(
                                            kcIssuerUri + "/protocol/openid-connect/auth")
                                        .tokenUrl(kcIssuerUri + "/protocol/openid-connect/token")
                                        .scopes(
                                            new Scopes()
                                                .addString("product:view", "Read products")
                                                .addString(
                                                    "product:manage",
                                                    "Create, update, delete products")
                                                .addString("stock:view", "Read stock movements")
                                                .addString(
                                                    "stock:manage", "Register stock movements")
                                                .addString("audit:view", "Access audit history")
                                                .addString(
                                                    "report:view", "Access inventory reports"))))));
  }

  @Bean
  public GroupedOpenApi productosGroup() {
    return GroupedOpenApi.builder()
        .group("productos")
        .displayName("Productos y Categorías")
        .pathsToMatch("/products/**", "/categories/**")
        .build();
  }

  @Bean
  public GroupedOpenApi stockGroup() {
    return GroupedOpenApi.builder()
        .group("stock")
        .displayName("Control de Stock")
        .pathsToMatch("/api/stock/**")
        .build();
  }

  @Bean
  public GroupedOpenApi reportesGroup() {
    return GroupedOpenApi.builder()
        .group("reportes")
        .displayName("Reportes del Dashboard")
        .pathsToMatch("/api/reports/**")
        .build();
  }

  @Bean
  public GroupedOpenApi auditoriaGroup() {
    return GroupedOpenApi.builder()
        .group("auditoria")
        .displayName("Auditoría Envers")
        .pathsToMatch("/api/audit/**")
        .build();
  }
}
