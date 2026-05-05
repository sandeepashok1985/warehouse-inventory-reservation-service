package com.wirs.inventory.reservation.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the X-API-Key header on every protected request. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> PUBLIC_PATHS = List.of(
        "/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Set<String> validApiKeys;

    /**
     * @param apiKeysRaw Comma-separated list of valid API keys from application configuration.
     */
    public ApiKeyAuthenticationFilter(String apiKeysRaw) {
        this.validApiKeys = Set.of(apiKeysRaw.split(","));
    }

    /** Skips authentication for whitelisted public paths (health, Swagger, OpenAPI). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, request.getRequestURI()));
    }

    /**
     * Validates the {@code X-API-Key} header against the configured set of keys.
     * On failure, returns HTTP 401 with a JSON error body; on success, sets a
     * {@link PreAuthenticatedAuthenticationToken} in the security context.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(API_KEY_HEADER);

        if (key == null || !validApiKeys.contains(key.trim())) {
            writeUnauthorizedResponse(request, response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
            new PreAuthenticatedAuthenticationToken(key, null, List.of()));
        chain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletRequest request,
                                              HttpServletResponse response) throws IOException {
        log.warn("Authentication failed: missing or invalid X-API-Key header from {}",
            request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"data\":null,\"error\":{\"code\":\"UNAUTHORIZED\","
            + "\"message\":\"Missing or invalid X-API-Key header\"}}");
    }
}
