package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.AttributeValue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {

    @EntityGraph(attributePaths = "attribute")
    List<AttributeValue> findWithAttributeByIdIn(Collection<Long> ids);
}
