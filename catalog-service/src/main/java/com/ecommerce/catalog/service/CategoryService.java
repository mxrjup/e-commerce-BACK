package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CategoryRequest;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.dto.CategoryTreeNode;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.catalog.support.Slugs;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryService {

    /** The order of the storefront menu: position first, then name. */
    private static final Sort DISPLAY_ORDER = Sort.by("position", "name");

    private final CategoryRepository categoryRepository;

    /** Every category, flat, with its parent's id: what the back-office edits. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll(DISPLAY_ORDER).stream().map(CategoryResponse::from).toList();
    }

    /** The whole tree in one query, for the storefront menu. */
    @Transactional(readOnly = true)
    public List<CategoryTreeNode> tree() {
        List<Category> categories = categoryRepository.findAll(DISPLAY_ORDER);
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
            }
        }
        return roots.stream().map(root -> node(root, childrenByParent)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category '" + slug + "' not found"));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        return CategoryResponse.from(save(new Category(), request));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        return CategoryResponse.from(save(find(id), request));
    }

    /**
     * Refuses to delete a category that still has sub-categories: deleting a
     * whole branch by accident is too easy to do from a back-office button.
     */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (categoryRepository.existsByParent_Id(id)) {
            throw new ConflictException("Category '" + category.getSlug()
                    + "' has sub-categories: move or delete them first");
        }
        Persistence.orConflict(() -> {
            categoryRepository.delete(category);
            categoryRepository.flush();
        }, "Category '" + category.getSlug() + "' is still used and cannot be deleted");
    }

    private Category save(Category category, CategoryRequest request) {
        String name = request.name().trim();
        String slug = Slugs.resolve(request.slug(), name);
        categoryRepository.findBySlug(slug)
                .filter(other -> !Objects.equals(other.getId(), category.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A category with slug '" + slug + "' already exists");
                });

        category.setParent(parentFor(category, request.parentId()));
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(request.description());
        category.setPosition(request.position() != null ? request.position() : 0);
        return Persistence.orConflict(() -> categoryRepository.saveAndFlush(category),
                "A category with this slug already exists");
    }

    /**
     * The new parent, after checking it would not put the category inside its
     * own branch: the tree would then have a cycle and no root to reach it.
     */
    private Category parentFor(Category category, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category " + parentId + " not found"));
        if (category.getId() != null) {
            for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
                if (Objects.equals(ancestor.getId(), category.getId())) {
                    throw new ConflictException("Category '" + category.getSlug()
                            + "' cannot be moved under itself or one of its sub-categories");
                }
            }
        }
        return parent;
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    private static CategoryTreeNode node(Category category, Map<Long, List<Category>> childrenByParent) {
        List<CategoryTreeNode> children = childrenByParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> node(child, childrenByParent))
                .toList();
        return new CategoryTreeNode(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getPosition(), children);
    }
}
