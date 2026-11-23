package com.devtaha.journalapp.security;

import com.devtaha.journalapp.config.AppProperties;
import com.devtaha.journalapp.user.Role;
import com.devtaha.journalapp.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Issues and verifies HS256 tokens. Access and refresh tokens differ by the "typ" claim. */
@Service
public class JwtService {

    private static final String TYPE = "typ";
    private static final String ROLES = "roles";
    private static final String USER_ID = "uid";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = props.jwt().accessTtl();
        this.refreshTtl = props.jwt().refreshTtl();
        this.clock = clock;
    }

    public String accessToken(User user) {
        return build(user, TokenType.ACCESS, accessTtl);
    }

    public String refreshToken(User user) {
        return build(user, TokenType.REFRESH, refreshTtl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                    .build().parseSignedClaims(token).getPayload();
            List<?> rawRoles = claims.get(ROLES, List.class);
            Set<Role> roles = rawRoles.stream().map(r -> Role.valueOf(r.toString())).collect(Collectors.toSet());
            return new TokenClaims(claims.get(USER_ID, String.class), claims.getSubject(), roles,
                    TokenType.valueOf(claims.get(TYPE, String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    private String build(User user, TokenType type, Duration ttl) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(USER_ID, user.getId())
                .claim(ROLES, user.getRoles().stream().map(Role::name).toList())
                .claim(TYPE, type.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }
}
