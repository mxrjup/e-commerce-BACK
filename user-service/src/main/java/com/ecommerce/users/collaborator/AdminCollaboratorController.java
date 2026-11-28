package com.ecommerce.users.collaborator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office accounts. The gateway reserves this path to the {@code admin}
 * role, stricter than the {@code collaborator} role the rest of
 * {@code /api/admin/**} accepts.
 */
@RestController
@RequestMapping("/api/admin/users/collaborators")
@RequiredArgsConstructor
public class AdminCollaboratorController {

    /** Set by the gateway from the validated token; a client cannot forge it. */
    static final String SUBJECT_HEADER = "X-Auth-Subject";

    private final CollaboratorService collaboratorService;

    @GetMapping
    public List<CollaboratorResponse> list() {
        return collaboratorService.list();
    }

    @GetMapping("/{id}")
    public CollaboratorResponse get(@PathVariable String id) {
        return collaboratorService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollaboratorResponse create(@Valid @RequestBody CreateCollaboratorRequest request) {
        return collaboratorService.create(request);
    }

    @PutMapping("/{id}/role")
    public CollaboratorResponse changeRole(@PathVariable String id,
                                           @Valid @RequestBody UpdateCollaboratorRoleRequest request,
                                           @RequestHeader(value = SUBJECT_HEADER, required = false) String actorId) {
        return collaboratorService.changeRole(id, request.role(), actorId);
    }

    @PostMapping("/{id}/disable")
    public CollaboratorResponse disable(@PathVariable String id,
                                        @RequestHeader(value = SUBJECT_HEADER, required = false) String actorId) {
        return collaboratorService.disable(id, actorId);
    }

    @PostMapping("/{id}/enable")
    public CollaboratorResponse enable(@PathVariable String id) {
        return collaboratorService.enable(id);
    }
}
