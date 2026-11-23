package com.devtaha.journalapp.user;

import com.devtaha.journalapp.entry.JournalEntryRepository;
import com.devtaha.journalapp.event.ActivityStatsRepository;
import com.devtaha.journalapp.exception.BadRequestException;
import com.devtaha.journalapp.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final ActivityStatsRepository stats = mock(ActivityStatsRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserService(users, entries, stats, encoder);
        user = new User("alice", "Alice", encoder.encode("old-password"));
        user.setId("u1");
        when(users.findById("u1")).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void unknownUserIsNotFound() {
        when(users.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("ghost")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void displayNameCanChangeWithoutTouchingThePassword() {
        String hash = user.getPasswordHash();

        UserResponse response = service.updateProfile("u1", new UpdateProfileRequest("Alice B", null, null));

        assertThat(response.displayName()).isEqualTo("Alice B");
        assertThat(user.getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() {
        assertThatThrownBy(() -> service.updateProfile("u1",
                new UpdateProfileRequest("Alice", "wrong", "new-password-1")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateProfile("u1",
                new UpdateProfileRequest("Alice", null, "new-password-1")))
                .isInstanceOf(BadRequestException.class);
        verify(users, never()).save(any());
    }

    @Test
    void passwordChangeStoresANewHash() {
        service.updateProfile("u1", new UpdateProfileRequest("Alice", "old-password", "new-password-1"));

        assertThat(encoder.matches("new-password-1", user.getPasswordHash())).isTrue();
    }

    @Test
    void deleteRemovesEntriesStatsAndUser() {
        service.delete("u1");

        var order = inOrder(entries, stats, users);
        order.verify(entries).deleteByUserId("u1");
        order.verify(stats).deleteById("u1");
        order.verify(users).delete(user);
    }

    @Test
    void grantAndRevokeAdminRole() {
        assertThat(service.changeAdminRole("u1", true).roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(service.changeAdminRole("u1", false).roles()).isEqualTo(EnumSet.of(Role.USER));
    }
}
