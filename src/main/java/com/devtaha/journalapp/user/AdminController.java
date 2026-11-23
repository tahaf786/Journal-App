package com.devtaha.journalapp.user;

import com.devtaha.journalapp.entry.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final UserService users;

    public AdminController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @ParameterObject @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return users.list(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return users.get(id);
    }

    @PutMapping("/{id}/admin")
    public UserResponse grantAdmin(@PathVariable String id) {
        return users.changeAdminRole(id, true);
    }

    @DeleteMapping("/{id}/admin")
    public UserResponse revokeAdmin(@PathVariable String id) {
        return users.changeAdminRole(id, false);
    }
}
