package com.devtaha.journalapp.entry;

import com.devtaha.journalapp.security.AuthUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entries")
@Tag(name = "Journal entries")
public class JournalEntryController {

    private final JournalEntryService service;

    public JournalEntryController(JournalEntryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody EntryRequest request) {
        return service.create(user.id(), request);
    }

    @GetMapping
    public PageResponse<EntryResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = "Full-text search over title and content") @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(user.id(), q, pageable);
    }

    @GetMapping("/{id}")
    public EntryResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return service.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public EntryResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                @Valid @RequestBody EntryRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        service.delete(user.id(), id);
    }
}
