package ca.pharmaforecast.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InfoController {

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    @Value("${pharmaforecast.security.supabase.jwk-set-uri:unknown}")
    private String jwkSetUri;

    @Value("${pharmaforecast.security.supabase.issuer:unknown}")
    private String issuer;

    @Value("${pharmaforecast.security.supabase.audiences:unknown}")
    private String audiences;

    @GetMapping("/debug")
    public Map<String, Object> info() {
        return Map.of(
            "profile", activeProfile,
            "jwkSetUri", jwkSetUri,
            "issuer", issuer,
            "audiences", audiences
        );
    }
}