package com.devtaha.journalapp.auth;

import com.devtaha.journalapp.config.AppProperties;
import com.devtaha.journalapp.exception.ConflictException;
import com.devtaha.journalapp.exception.UnauthorizedException;
import com.devtaha.journalapp.security.JwtService;
import com.devtaha.journalapp.user.User;
import com.devtaha.journalapp.user.UserRepository;
import com.devtaha.journalapp.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService(new AppProperties(
            new AppProperties.Jwt("unit-test-secret-0123456789abcdefghij", Duration.ofMinutes(15),
                    Duration.ofDays(7)), null, null, null), Clock.systemUTC());
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwt);
    }

    private User storedUser() {
        User user = new User("alice", "Alice", encoder.encode("password123"));
        user.setId("u1");
        return user;
    }

    @Test
    void registerStoresAHashedPassword() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse response = service.register(new RegisterRequest("alice", "password123", null));

        assertThat(response.displayName()).isEqualTo("alice");
        org.mockito.ArgumentCaptor<User> saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(encoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsTakenUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "password123", null)))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerMapsRaceOnTheUniqueIndexToConflict() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "password123", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginReturnsBothTokens() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(storedUser()));

        TokenResponse tokens = service.login(new LoginRequest("alice", "password123"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(tokens.accessToken());
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUser() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(storedUser()));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("alice", "nope")))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshIssuesNewTokensForAnExistingUser() {
        User user = storedUser();
        when(users.findById("u1")).thenReturn(Optional.of(user));

        TokenResponse tokens = service.refresh(new RefreshRequest(jwt.refreshToken(user)));

        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void refreshRejectsAccessTokensGarbageAndDeletedUsers() {
        User user = storedUser();
        when(users.findById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshRequest(jwt.accessToken(user))))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh(new RefreshRequest("garbage")))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh(new RefreshRequest(jwt.refreshToken(user))))
                .isInstanceOf(UnauthorizedException.class);
    }
}
