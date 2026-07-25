package com.inventory.common.config;

import com.inventory.common.observability.AuthenticatedUserMdcFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración central de Spring Security. Establece la política stateless con JWT de Keycloak,
 * CORS configurable, rutas públicas (Swagger, health, Prometheus) y extrae del token los roles de
 * realm y los scopes OAuth2 que Keycloak haya emitido.
 *
 * <p>La autoridad sobre qué permisos lleva un token es Keycloak, no este backend: los
 * scope-mappings por rol del realm gatean la emisión (G-8) y aquí se confía en el claim resultante.
 * Ver {@code docs/decisions/ADR-004-keycloak-autoridad-de-scopes.md}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${app.cors.allowed-origins:http://localhost:3000}")
  private List<String> corsAllowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Swagger / OpenAPI — public for development and grading convenience
                    .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html"))
                    .permitAll()
                    .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**"))
                    .permitAll()
                    .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**"))
                    .permitAll()
                    // /v3/api-docs.yaml es ruta hermana, no hija: /v3/api-docs/** no la cubre.
                    // El perfil generate-docs del pom la consume para volcar docs/api/openapi.yaml.
                    .requestMatchers(new AntPathRequestMatcher("/v3/api-docs.yaml"))
                    .permitAll()
                    // custom health endpoint — public for load balancers and probes
                    // AntPathRequestMatcher bypasses MvcRequestMatcher resolution in test slices
                    .requestMatchers(new AntPathRequestMatcher("/health"))
                    .permitAll()
                    // actuator health + info + prometheus public — healthcheck and Prometheus
                    // scrape
                    .requestMatchers(
                        EndpointRequest.to(
                            HealthEndpoint.class,
                            InfoEndpoint.class,
                            PrometheusScrapeEndpoint.class))
                    .permitAll()
                    // remaining actuator endpoints require authentication
                    .requestMatchers(EndpointRequest.toAnyEndpoint())
                    .authenticated()
                    // all API routes require authentication
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter())))
        // Detrás de la validación del token: solo a partir de aquí hay usuario que registrar en
        // el MDC. El correlationId y el endpoint los pone CorrelationIdFilter, que envuelve la
        // cadena entera y cubre también los 401.
        .addFilterAfter(new AuthenticatedUserMdcFilter(), BearerTokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(corsAllowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /**
   * Separate jwk-set-uri (internal Docker URL for key fetching) from issuer-uri (external URL
   * embedded in "iss" claim). Required in containerized dev setups where these differ.
   */
  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri), new JwtTimestampValidator());
    decoder.setJwtValidator(validator);
    return decoder;
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> keycloakJwtConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
    return converter;
  }

  // Extrae los roles de realm (prefijo ROLE_) y los scopes OAuth2 (prefijo SCOPE_) del JWT.
  //
  // Keycloak es la autoridad única sobre qué permisos lleva un token: los scope-mappings por rol
  // del realm (G-8, scripts/keycloak/init-users.sh) deciden qué scopes se emiten, y este backend
  // se limita a confiar en el claim resultante. Hasta G-8 existía aquí una tabla rol→scopes que
  // reintersectaba el claim, porque Keycloak concedía cualquier scope a cualquier usuario
  // autenticado (G-6, issue #43); esa contención dejó de ser necesaria y se retiró en G-2.
  // El razonamiento completo está en docs/decisions/ADR-004-keycloak-autoridad-de-scopes.md.
  //
  // El control de que un viewer no reciba scopes elevados vive ahora en el realm, y lo verifica
  // KeycloakAuthIT#keycloakGatesScopeEscalation contra un Keycloak real, dentro del check
  // obligatorio "Integration Tests (Testcontainers)". Ese test es la red: no debe relajarse.
  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null) {
      @SuppressWarnings("unchecked")
      List<String> roleList = (List<String>) realmAccess.get("roles");
      if (roleList != null) {
        roleList.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
      }
    }

    String scope = jwt.getClaimAsString("scope");
    if (scope != null && !scope.isBlank()) {
      for (String s : scope.split(" ")) {
        if (!s.isBlank()) {
          authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
      }
    }

    return List.copyOf(authorities);
  }
}
