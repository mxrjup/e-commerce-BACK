package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.BrandRequest;
import com.ecommerce.catalog.dto.BrandResponse;
import com.ecommerce.catalog.entity.Brand;
import com.ecommerce.catalog.repository.BrandRepository;
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
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<BrandResponse> list() {
        return brandRepository.findAll(Sort.by("name")).stream().map(BrandResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse get(Long id) {
        return BrandResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public BrandResponse getBySlug(String slug) {
        return brandRepository.findBySlug(slug)
                .map(BrandResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Brand '" + slug + "' not found"));
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        return BrandResponse.from(save(new Brand(), request));
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        return BrandResponse.from(save(find(id), request));
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = find(id);
        Persistence.orConflict(() -> {
            brandRepository.delete(brand);
            brandRepository.flush();
        }, "Brand '" + brand.getSlug() + "' is still used and cannot be deleted");
    }

    private Brand save(Brand brand, BrandRequest request) {
        String name = request.name().trim();
        String slug = brand.getId() == null
                ? Slugs.forCreation(request.slug(), name)
                : Slugs.forUpdate(request.slug(), brand.getSlug());
        brandRepository.findByNameIgnoreCase(name)
                .filter(other -> !Objects.equals(other.getId(), brand.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A brand named '" + name + "' already exists");
                });
        brandRepository.findBySlug(slug)
                .filter(other -> !Objects.equals(other.getId(), brand.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A brand with slug '" + slug + "' already exists");
                });

        brand.setName(name);
        brand.setSlug(slug);
        brand.setDescription(request.description());
        brand.setLogoUrl(request.logoUrl());
        return Persistence.orConflict(() -> brandRepository.saveAndFlush(brand),
                "A brand with this name or slug already exists");
    }

    private Brand find(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand " + id + " not found"));
    }
}
