package com.inventory.common.web;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ping")
public class PingController {

  @GetMapping
  public Map<String, Object> ping(Authentication auth) {
    return Map.of(
        "status", "ok",
        "subject", auth.getName(),
        "authorities", auth.getAuthorities().toString());
  }
}
