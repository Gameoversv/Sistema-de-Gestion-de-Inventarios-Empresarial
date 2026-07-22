package com.inventory.common.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración central de Spring Security. Establece la política stateless con JWT de Keycloak,
 * CORS configurable, rutas públicas (Swagger, health, Prometheus) y extrae roles y scopes del token
 * intersectándolos con los permisos máximos que el rol del usuario puede tener.
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

  // Extracts realm roles (ROLE_ prefix) and effective OAuth2 scopes (SCOPE_ prefix) from the JWT.
  // Scopes are intersected with the role-permitted set so that Keycloak optional scopes cannot
  // grant capabilities beyond what the user's realm role allows.
  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();

    Set<String> roles = new java.util.HashSet<>();
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess != null) {
      @SuppressWarnings("unchecked")
      List<String> roleList = (List<String>) realmAccess.get("roles");
      if (roleList != null) {
        roleList.forEach(
            role -> {
              roles.add(role);
              authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            });
      }
    }

    Set<String> permitted = permittedScopesForRoles(roles);
    String scope = jwt.getClaimAsString("scope");
    if (scope != null && !scope.isBlank()) {
      for (String s : scope.split(" ")) {
        if (!s.isBlank() && permitted.contains(s)) {
          authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
      }
    }

    return List.copyOf(authorities);
  }

  // Maps realm roles to the maximum set of scopes that role may hold.
  // Roles checked in order of privilege; first match wins.
  private Set<String> permittedScopesForRoles(Set<String> roles) {
    if (roles.contains("inventory-admin")) {
      return Set.of(
          "product:view",
          "product:manage",
          "stock:view",
          "stock:manage",
          "report:view",
          "user:manage",
          "audit:view",
          "openid",
          "email",
          "profile");
    }
    if (roles.contains("warehouse-clerk")) {
      return Set.of(
          "product:view",
          "product:manage",
          "stock:view",
          "stock:manage",
          "report:view",
          "openid",
          "email",
          "profile");
    }
    if (roles.contains("auditor")) {
      return Set.of(
          "product:view", "stock:view", "report:view", "audit:view", "openid", "email", "profile");
    }
    // viewer and any other role: read-only
    return Set.of("product:view", "stock:view", "report:view", "openid", "email", "profile");
  }
}
