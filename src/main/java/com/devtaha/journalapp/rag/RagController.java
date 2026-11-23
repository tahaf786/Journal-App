package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ask")
@Tag(name = "Ask my journal")
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagController {

    private final RagService service;

    public RagController(RagService service) {
        this.service = service;
    }

    @PostMapping
    public AskResponse ask(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AskRequest request) {
        return service.ask(user.id(), request.question(), request.topK());
    }
}
