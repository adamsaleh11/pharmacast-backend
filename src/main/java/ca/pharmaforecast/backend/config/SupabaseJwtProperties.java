package ca.pharmaforecast.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pharmaforecast.security.supabase")
public class SupabaseJwtProperties {

    private String jwkSetUri = "http://localhost:54321/auth/v1/.well-known/jwks.json";

    private String issuer;

    private List<String> audiences = List.of("authenticated");

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences;
    }
}
