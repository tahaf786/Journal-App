package com.devtaha.journalapp;

import com.devtaha.journalapp.user.Role;
import com.devtaha.journalapp.user.User;
import com.devtaha.journalapp.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void registerLoginAndCallProtectedEndpoint() throws Exception {
        String username = uniqueName("alice");
        register(username);

        JsonNode tokens = login(username, PASSWORD);
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");

        JsonNode profile = getJson("/api/v1/users/me", tokens.get("accessToken").asText());
        assertThat(profile.get("username").asText()).isEqualTo(username);
        assertThat(profile.has("passwordHash")).isFalse();
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String username = uniqueName("hash");
        register(username);

        String stored = users.findByUsername(username).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = uniqueName("dup");
        register(username);

        perform(post("/api/v1/auth/register"), null, Map.of("username", username, "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidRegistrationReturnsFieldViolations() throws Exception {
        perform(post("/api/v1/auth/register"), null, Map.of("username", "a", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations", hasSize(2)));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String username = uniqueName("bob");
        register(username);

        perform(post("/api/v1/auth/login"), null, Map.of("username", username, "password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/entries")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        getAs("/api/v1/entries", "not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIssuesNewTokensButCannotCallTheApi() throws Exception {
        String username = uniqueName("carol");
        register(username);
        JsonNode tokens = login(username, PASSWORD);
        String refresh = tokens.get("refreshToken").asText();

        getAs("/api/v1/users/me", refresh).andExpect(status().isUnauthorized());

        String body = perform(post("/api/v1/auth/refresh"), null, Map.of("refreshToken", refresh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newAccess = json.readTree(body).get("accessToken").asText();
        getAs("/api/v1/users/me", newAccess).andExpect(status().isOk());
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() throws Exception {
        String username = uniqueName("dave");
        register(username);
        String access = login(username, PASSWORD).get("accessToken").asText();

        perform(post("/api/v1/auth/refresh"), null, Map.of("refreshToken", access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreForbiddenForRegularUsers() throws Exception {
        String token = newUserToken();

        getAs("/api/v1/admin/users", token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanListUsersAndGrantAndRevokeAdminRole() throws Exception {
        String adminName = uniqueName("root");
        User admin = new User(adminName, adminName, encoder.encode(PASSWORD));
        admin.setRoles(EnumSet.of(Role.USER, Role.ADMIN));
        users.save(admin);
        String adminToken = login(adminName, PASSWORD).get("accessToken").asText();

        String targetName = uniqueName("target");
        register(targetName);
        String targetId = users.findByUsername(targetName).orElseThrow().getId();

        getAs("/api/v1/admin/users", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        getAs("/api/v1/admin/users/" + targetId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(targetName));

        putAs("/api/v1/admin/users/" + targetId + "/admin", adminToken, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(2)));
        String promotedToken = login(targetName, PASSWORD).get("accessToken").asText();
        getAs("/api/v1/admin/users", promotedToken).andExpect(status().isOk());

        deleteAs("/api/v1/admin/users/" + targetId + "/admin", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(1)));
        String demotedToken = login(targetName, PASSWORD).get("accessToken").asText();
        getAs("/api/v1/admin/users", demotedToken).andExpect(status().isForbidden());
    }
}
