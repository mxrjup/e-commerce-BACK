package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.UniverseRequest;
import com.ecommerce.catalog.dto.UniverseResponse;
import com.ecommerce.catalog.entity.Universe;
import com.ecommerce.catalog.repository.UniverseRepository;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.catalog.support.Slugs;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UniverseService {

    /** The order of the storefront menu: position first, then name. */
    private static final Sort DISPLAY_ORDER = Sort.by("position", "name");

    private final UniverseRepository universeRepository;

    @Transactional(readOnly = true)
    public List<UniverseResponse> list() {
        return universeRepository.findAll(DISPLAY_ORDER).stream().map(UniverseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UniverseResponse get(Long id) {
        return UniverseResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public UniverseResponse getBySlug(String slug) {
        return universeRepository.findBySlug(slug)
                .map(UniverseResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Universe '" + slug + "' not found"));
    }

    @Transactional
    public UniverseResponse create(UniverseRequest request) {
        return UniverseResponse.from(save(new Universe(), request));
    }

    @Transactional
    public UniverseResponse update(Long id, UniverseRequest request) {
        return UniverseResponse.from(save(find(id), request));
    }

    @Transactional
    public void delete(Long id) {
        Universe universe = find(id);
        Persistence.orConflict(() -> {
            universeRepository.delete(universe);
            universeRepository.flush();
        }, "Universe '" + universe.getSlug() + "' is still used and cannot be deleted");
    }

    private Universe save(Universe universe, UniverseRequest request) {
        String name = request.name().trim();
        String slug = universe.getId() == null
                ? Slugs.forCreation(request.slug(), name)
                : Slugs.forUpdate(request.slug(), universe.getSlug());
        universeRepository.findByNameIgnoreCase(name)
                .filter(other -> !Objects.equals(other.getId(), universe.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A universe named '" + name + "' already exists");
                });
        universeRepository.findBySlug(slug)
                .filter(other -> !Objects.equals(other.getId(), universe.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A universe with slug '" + slug + "' already exists");
                });

        universe.setName(name);
        universe.setSlug(slug);
        universe.setDescription(request.description());
        universe.setImageUrl(request.imageUrl());
        universe.setPosition(request.position() != null ? request.position() : 0);
        return Persistence.orConflict(() -> universeRepository.saveAndFlush(universe),
                "A universe with this name or slug already exists");
    }

    private Universe find(Long id) {
        return universeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Universe " + id + " not found"));
    }
}
