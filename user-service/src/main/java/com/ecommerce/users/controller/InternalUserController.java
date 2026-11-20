package com.ecommerce.users.controller;

import com.ecommerce.users.entity.User;
import com.ecommerce.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Internal, authenticated MCP reads. No provisioning and no entity serialization. */
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalUserController {
    private final UserRepository repository;

    public record UserRead(Long id, String email, String firstName, String lastName) {
        static UserRead from(User user) {
            return new UserRead(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
        }
    }
    public record UserPage(List<UserRead> items, int page, int size, long totalElements, int totalPages) {}

    @GetMapping("/{id}")
    public UserRead get(@PathVariable Long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID must be positive");
        return repository.findById(id).map(UserRead::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new UserPage(result.getContent().stream().map(UserRead::from).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public record UserSubject(Long id, String subject) {
    }

    @GetMapping("/{id}/subject")
    public UserSubject getSubject(@PathVariable Long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID must be positive");
        return repository.findById(id)
                .map(user -> new UserSubject(user.getId(), user.getAuthProviderId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public record UserContact(Long id, String subject, String email, String firstName) {
    }

    @GetMapping("/{id}/contact")
    public UserContact getContact(@PathVariable Long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID must be positive");
        return repository.findById(id)
                .map(user -> new UserContact(
                        user.getId(),
                        user.getAuthProviderId(),
                        user.getEmail(),
                        user.getFirstName()
                ))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @GetMapping("/by-subject/{subject}")
    public UserContact getBySubject(@PathVariable String subject) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject is required");
        }
        return repository.findByAuthProviderId(subject)
                .map(user -> new UserContact(
                        user.getId(),
                        user.getAuthProviderId(),
                        user.getEmail(),
                        user.getFirstName()
                ))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

}
