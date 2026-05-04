package com.wirs.inventory.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wirs.inventory.reservation.api.security.ApiKeyAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("unit")
class ApiKeyAuthenticationFilterTest {

    private final ApiKeyAuthenticationFilter filter =
        new ApiKeyAuthenticationFilter("dev-key-12345,another-key");

    @Test
    void missingApiKey_returns401WithUnauthorizedCode() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void validApiKey_proceedsToNextFilter() throws Exception {
        SecurityContextHolder.clearContext();
        var request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "dev-key-12345");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
