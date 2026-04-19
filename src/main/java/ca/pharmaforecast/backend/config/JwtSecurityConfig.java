package ca.pharmaforecast.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(SupabaseJwtProperties.class)
public class JwtSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(SupabaseJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (properties.getIssuer() != null && !properties.getIssuer().isBlank()) {
            validators.add(new JwtIssuerValidator(properties.getIssuer()));
        }
        if (properties.getAudiences() != null && !properties.getAudiences().isEmpty()) {
            validators.add(audienceValidator(properties.getAudiences()));
        }

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(List<String> acceptedAudiences) {
        return jwt -> {
            List<String> tokenAudiences = jwt.getAudience();
            boolean accepted = acceptedAudiences.stream().anyMatch(tokenAudiences::contains);
            if (accepted) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "The required Supabase audience is missing",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
