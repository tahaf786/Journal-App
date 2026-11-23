package com.devtaha.journalapp.rag;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/rag")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagAdminController {

    private final RagIndexService index;

    public RagAdminController(RagIndexService index) {
        this.index = index;
    }

    /** Rebuilds the points of one user, or of all users when {@code userId} is omitted. */
    @PostMapping("/reindex")
    public Map<String, Integer> reindex(
            @Parameter(description = "Limit the rebuild to this user") @RequestParam(required = false) String userId) {
        return Map.of("indexed", index.reindex(userId));
    }
}
