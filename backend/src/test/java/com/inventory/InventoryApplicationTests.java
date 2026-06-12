package com.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("smoke")
class InventoryApplicationTests {

  // Prevents Spring from contacting Keycloak at startup to fetch JWKS
  @MockBean JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {}
}
