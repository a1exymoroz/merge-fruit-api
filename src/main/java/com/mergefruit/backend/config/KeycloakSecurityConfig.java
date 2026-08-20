package com.mergefruit.backend.config;

import com.mergefruit.backend.security.KeycloakRealmRoleConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/*
 Keycloak-backed OAuth2 resource server auth, kept separate from the existing custom
 JWT stack in SecurityConfig (both use the same "Authorization: Bearer <token>"
 convention, so they can't safely share one filter chain — the resource server filter
 would try to validate every bearer token against Keycloak, including this app's own
 custom-issued tokens, and reject them before the custom filter gets a chance).

 This chain is scoped via securityMatcher() to only the new Keycloak-protected
 endpoints; every other request falls through to the existing SecurityConfig chain
 unchanged.
*/
@Configuration
public class KeycloakSecurityConfig {

    private final String issuerUri;
    private final String requiredAudience;
    private final CorsConfigurationSource corsConfigurationSource;

    public KeycloakSecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.required-audience}") String requiredAudience,
            CorsConfigurationSource corsConfigurationSource) {
        this.issuerUri = issuerUri;
        this.requiredAudience = requiredAudience;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    JwtDecoder keycloakJwtDecoder() {
        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(requiredAudience));

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator));
        return jwtDecoder;
    }

    @Bean
    JwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    @Order(1)
    SecurityFilterChain keycloakSecurityFilterChain(
            HttpSecurity http, JwtDecoder keycloakJwtDecoder, JwtAuthenticationConverter keycloakJwtAuthenticationConverter)
            throws Exception {
        http
                .securityMatcher("/api/whoami/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(keycloakJwtDecoder)
                        .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)));

        return http.build();
    }
}
