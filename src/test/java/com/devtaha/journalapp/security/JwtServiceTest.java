package com.devtaha.journalapp.security;

import com.devtaha.journalapp.config.AppProperties;
import com.devtaha.journalapp.user.Role;
import com.devtaha.journalapp.user.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghij";
    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private JwtService service(String secret, Clock clock) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(7)), null, null, null);
        return new JwtService(props, clock);
    }

    private User user() {
        User user = new User("alice", "Alice", "hash");
        user.setId("u1");
        user.setRoles(EnumSet.of(Role.USER, Role.ADMIN));
        return user;
    }

    @Test
    void accessTokenRoundTripsClaims() {
        JwtService jwt = service(SECRET, clock);

        TokenClaims claims = jwt.parse(jwt.accessToken(user()));

        assertThat(claims.userId()).isEqualTo("u1");
        assertThat(claims.username()).isEqualTo("alice");
        assertThat(claims.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
    }

    @Test
    void refreshTokenIsMarkedAsRefresh() {
        JwtService jwt = service(SECRET, clock);

        assertThat(jwt.parse(jwt.refreshToken(user())).type()).isEqualTo(TokenType.REFRESH);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, clock).accessToken(user());
        JwtService later = service(SECRET, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("another-secret-0123456789abcdefghijkl", clock).accessToken(user());

        assertThatThrownBy(() -> service(SECRET, clock).parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        JwtService jwt = service(SECRET, clock);
        String token = jwt.accessToken(user());

        assertThatThrownBy(() -> jwt.parse(token + "x")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parse("garbage")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> service("too-short", clock)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesAccessTokenLifetimeInSeconds() {
        assertThat(service(SECRET, clock).accessTtlSeconds()).isEqualTo(900);
    }
}
