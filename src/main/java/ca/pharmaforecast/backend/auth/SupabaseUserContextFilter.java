package ca.pharmaforecast.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SupabaseUserContextFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SupabaseUserContextFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "/auth/logout".equals(path) || "/auth/bootstrap".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            UUID userId = parseSubject(jwt.getSubject(), response);
            if (userId == null) {
                return;
            }

            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT_EMAIL_MISSING");
                return;
            }

            User user = userRepository.findByIdAndEmail(userId, email)
                    .orElse(null);
            if (user == null) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "USER_PROFILE_NOT_BOOTSTRAPPED");
                return;
            }

            AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                    user.getId(),
                    user.getEmail(),
                    user.getOrganizationId(),
                    user.getRole()
            );
            AppUserAuthenticationToken authentication = new AppUserAuthenticationToken(
                    principal,
                    jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
            authentication.setDetails(jwtAuthentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private UUID parseSubject(String subject, HttpServletResponse response) throws IOException {
        if (subject == null || subject.isBlank()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT_SUBJECT_MISSING");
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT_SUBJECT_INVALID");
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int status, String code) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", code));
    }
}
