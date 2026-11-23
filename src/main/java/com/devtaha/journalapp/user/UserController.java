package com.devtaha.journalapp.user;

import com.devtaha.journalapp.event.StatsResponse;
import com.devtaha.journalapp.event.StatsService;
import com.devtaha.journalapp.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Current user")
public class UserController {

    private final UserService users;
    private final StatsService stats;

    public UserController(UserService users, StatsService stats) {
        this.users = users;
        this.stats = stats;
    }

    @GetMapping
    public UserResponse profile(@AuthenticationPrincipal AuthUser user) {
        return users.get(user.id());
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(user.id(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user) {
        users.delete(user.id());
    }

    @GetMapping("/stats")
    public StatsResponse stats(@AuthenticationPrincipal AuthUser user) {
        return stats.get(user.id());
    }
}
