package com.devtaha.journalapp.auth;

import com.devtaha.journalapp.exception.ConflictException;
import com.devtaha.journalapp.exception.UnauthorizedException;
import com.devtaha.journalapp.security.InvalidTokenException;
import com.devtaha.journalapp.security.JwtService;
import com.devtaha.journalapp.security.TokenClaims;
import com.devtaha.journalapp.security.TokenType;
import com.devtaha.journalapp.user.User;
import com.devtaha.journalapp.user.UserRepository;
import com.devtaha.journalapp.user.UserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken");
        }
        String displayName = request.displayName() != null ? request.displayName() : request.username();
        try {
            User saved = users.save(new User(request.username(), displayName, encoder.encode(request.password())));
            return UserResponse.from(saved);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Username already taken");
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username())
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));
        return tokensFor(user);
    }

    /** The user is reloaded so a deleted account cannot refresh and role changes take effect. */
    public TokenResponse refresh(RefreshRequest request) {
        TokenClaims claims;
        try {
            claims = jwt.parse(request.refreshToken());
        } catch (InvalidTokenException e) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        if (claims.type() != TokenType.REFRESH) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        User user = users.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        return tokensFor(user);
    }

    private TokenResponse tokensFor(User user) {
        return new TokenResponse(jwt.accessToken(user), jwt.refreshToken(user), "Bearer", jwt.accessTtlSeconds());
    }
}
