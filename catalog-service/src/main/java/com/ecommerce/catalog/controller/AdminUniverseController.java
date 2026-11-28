package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.UniverseRequest;
import com.ecommerce.catalog.dto.UniverseResponse;
import com.ecommerce.catalog.service.UniverseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office management of universes. The gateway reserves {@code /api/admin/**}
 * to the {@code collaborator} and {@code admin} roles.
 */
@RestController
@RequestMapping("/api/admin/catalog/universes")
@RequiredArgsConstructor
public class AdminUniverseController {

    private final UniverseService universeService;

    @GetMapping
    public List<UniverseResponse> list() {
        return universeService.list();
    }

    @GetMapping("/{id}")
    public UniverseResponse get(@PathVariable Long id) {
        return universeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UniverseResponse create(@Valid @RequestBody UniverseRequest request) {
        return universeService.create(request);
    }

    @PutMapping("/{id}")
    public UniverseResponse update(@PathVariable Long id, @Valid @RequestBody UniverseRequest request) {
        return universeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        universeService.delete(id);
    }
}
