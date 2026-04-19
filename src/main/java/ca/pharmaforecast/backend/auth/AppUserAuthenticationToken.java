package ca.pharmaforecast.backend.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class AppUserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUserPrincipal principal;
    private final Jwt credentials;

    public AppUserAuthenticationToken(
            AuthenticatedUserPrincipal principal,
            Jwt credentials,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUserPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return credentials;
    }
}
