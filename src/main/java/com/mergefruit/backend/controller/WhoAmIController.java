package com.mergefruit.backend.controller;

import com.mergefruit.backend.dto.WhoAmIResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 Smoke-test endpoint for the Keycloak resource server config — call it with a
 Bearer token from any frontend to confirm the token is accepted end-to-end.
*/
@RestController
@RequestMapping("/api/whoami")
public class WhoAmIController {

    @GetMapping
    public WhoAmIResponse whoAmI(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = jwt.getSubject();
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return new WhoAmIResponse(username, roles);
    }
}
