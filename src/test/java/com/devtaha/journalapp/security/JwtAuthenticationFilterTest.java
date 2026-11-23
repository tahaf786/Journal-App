package com.devtaha.journalapp.security;

import com.devtaha.journalapp.config.AppProperties;
import com.devtaha.journalapp.user.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private final JwtService jwt = new JwtService(new AppProperties(
            new AppProperties.Jwt("unit-test-secret-0123456789abcdefghij", Duration.ofMinutes(15),
                    Duration.ofDays(7)), null, null, null), Clock.systemUTC());
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User user() {
        User user = new User("alice", "Alice", "hash");
        user.setId("u1");
        return user;
    }

    private Authentication run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validAccessTokenAuthenticatesTheRequest() throws Exception {
        Authentication auth = run("Bearer " + jwt.accessToken(user()));

        assertThat(auth.getPrincipal()).isEqualTo(new AuthUser("u1", "alice"));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void refreshTokenDoesNotAuthenticate() throws Exception {
        assertThat(run("Bearer " + jwt.refreshToken(user()))).isNull();
    }

    @Test
    void invalidOrMissingTokenLeavesRequestAnonymous() throws Exception {
        assertThat(run("Bearer nonsense")).isNull();
    }

    @Test
    void otherSchemesAreIgnored() throws Exception {
        assertThat(run("Basic abc")).isNull();
    }
}
