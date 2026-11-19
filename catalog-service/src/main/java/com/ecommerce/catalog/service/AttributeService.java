package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.AttributeRequest;
import com.ecommerce.catalog.dto.AttributeResponse;
import com.ecommerce.catalog.entity.Attribute;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.repository.AttributeRepository;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attributes and their values (C04, screen C17): what makes the variants of
 * a product differ. A value a variant uses cannot be removed, nor its
 * attribute deleted: the variant would lose what tells it apart.
 */
@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository attributeRepository;
    private final VariantRepository variantRepository;

    @Transactional(readOnly = true)
    public List<AttributeResponse> list() {
        return attributeRepository.findAll(Sort.by("name")).stream().map(AttributeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AttributeResponse get(Long id) {
        return AttributeResponse.from(find(id));
    }

    @Transactional
    public AttributeResponse create(AttributeRequest request) {
        Attribute attribute = new Attribute();
        attribute.setName(uniqueName(attribute, request.name()));
        for (AttributeRequest.Value value : distinct(request.values())) {
            if (value.id() != null) {
                throw new BadRequestException("A new attribute has no existing value: remove the id of '"
                        + value.value().trim() + "'");
            }
            attribute.addValue(value.value().trim());
        }
        return AttributeResponse.from(Persistence.orConflict(() -> attributeRepository.saveAndFlush(attribute),
                "An attribute with this name already exists"));
    }

    /**
     * Replaces the values in three steps, each flushed: removals, then
     * renames, then additions. Hibernate would otherwise insert before
     * deleting or renaming, and a value removed or renamed then added again
     * ("Noir") would hit {@code uk_valeur_attribut}.
     */
    @Transactional
    public AttributeResponse update(Long id, AttributeRequest request) {
        Attribute attribute = find(id);
        attribute.setName(uniqueName(attribute, request.name()));
        List<AttributeRequest.Value> requested = distinct(request.values());

        Map<Long, AttributeValue> current = new LinkedHashMap<>();
        attribute.getValues().forEach(value -> current.put(value.getId(), value));
        Set<Long> keptIds = new HashSet<>();
        for (AttributeRequest.Value value : requested) {
            if (value.id() != null && !current.containsKey(value.id())) {
                throw new BadRequestException("Value " + value.id() + " does not belong to attribute '"
                        + attribute.getName() + "'");
            }
            if (value.id() != null) {
                keptIds.add(value.id());
            }
        }
        // Une valeur renvoyee sans id mais deja presente (« Noir ») est la meme, pas une nouvelle.
        List<AttributeValue> targets = new ArrayList<>();
        for (AttributeRequest.Value value : requested) {
            AttributeValue target = value.id() != null ? current.get(value.id())
                    : current.values().stream()
                            .filter(existing -> !keptIds.contains(existing.getId()))
                            .filter(existing -> normalized(existing.getValue()).equals(normalized(value.value())))
                            .findFirst()
                            .orElse(null);
            if (target != null) {
                keptIds.add(target.getId());
            }
            targets.add(target);
        }

        List<AttributeValue> removed = current.values().stream()
                .filter(value -> !keptIds.contains(value.getId()))
                .toList();
        for (AttributeValue value : removed) {
            if (variantRepository.existsByValues_Id(value.getId())) {
                throw new ConflictException("Value '" + value.getValue() + "' of " + attribute.getName()
                        + " is used by variants and cannot be removed");
            }
        }
        attribute.getValues().removeAll(removed);
        attributeRepository.flush();

        for (int position = 0; position < requested.size(); position++) {
            AttributeValue target = targets.get(position);
            if (target != null) {
                target.setValue(requested.get(position).value().trim());
                target.setPosition(position);
            }
        }
        Persistence.orConflict(attributeRepository::flush,
                "Two values cannot swap their names in one save: rename one of them in a separate save");

        for (int position = 0; position < requested.size(); position++) {
            if (targets.get(position) == null) {
                attribute.addValue(requested.get(position).value().trim()).setPosition(position);
            }
        }
        attribute.getValues().sort(Comparator.comparingInt(AttributeValue::getPosition));
        return AttributeResponse.from(Persistence.orConflict(() -> attributeRepository.saveAndFlush(attribute),
                "An attribute with this name already exists"));
    }

    @Transactional
    public void delete(Long id) {
        Attribute attribute = find(id);
        if (variantRepository.existsByValues_Attribute_Id(id)) {
            throw new ConflictException("Attribute '" + attribute.getName()
                    + "' is used by variants and cannot be deleted");
        }
        Persistence.orConflict(() -> {
            attributeRepository.delete(attribute);
            attributeRepository.flush();
        }, "Attribute '" + attribute.getName() + "' is used by variants and cannot be deleted");
    }

    private String uniqueName(Attribute attribute, String requested) {
        String name = requested.trim();
        attributeRepository.findByNameIgnoreCase(name)
                .filter(other -> !Objects.equals(other.getId(), attribute.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("An attribute named '" + name + "' already exists");
                });
        return name;
    }

    /** The same value twice in one attribute ("Noir" and "noir") is a typing mistake. */
    private static List<AttributeRequest.Value> distinct(List<AttributeRequest.Value> values) {
        Set<String> seen = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (AttributeRequest.Value value : values) {
            if (!seen.add(normalized(value.value()))) {
                throw new BadRequestException("Value '" + value.value().trim() + "' appears twice");
            }
            if (value.id() != null && !ids.add(value.id())) {
                throw new BadRequestException("Value " + value.id() + " appears twice");
            }
        }
        return values;
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Attribute find(Long id) {
        return attributeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attribute " + id + " not found"));
    }
}
