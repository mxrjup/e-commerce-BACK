package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AttributeRequest;
import com.ecommerce.catalog.dto.AttributeResponse;
import com.ecommerce.catalog.service.AttributeService;
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

/** Back-office management of the attributes and their values (C04). */
@RestController
@RequestMapping("/api/admin/catalog/attributes")
@RequiredArgsConstructor
public class AdminAttributeController {

    private final AttributeService attributeService;

    @GetMapping
    public List<AttributeResponse> list() {
        return attributeService.list();
    }

    @GetMapping("/{id}")
    public AttributeResponse get(@PathVariable Long id) {
        return attributeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeResponse create(@Valid @RequestBody AttributeRequest request) {
        return attributeService.create(request);
    }

    @PutMapping("/{id}")
    public AttributeResponse update(@PathVariable Long id, @Valid @RequestBody AttributeRequest request) {
        return attributeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attributeService.delete(id);
    }
}
