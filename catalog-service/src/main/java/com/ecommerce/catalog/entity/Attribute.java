package com.ecommerce.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * What makes the variants of a product differ: "Couleur", "Layout clavier",
 * "Capacité". Shared by every product that uses it.
 */
@Getter
@Setter
@Entity
@Table(name = "attribut")
public class Attribute extends AuditedEntity {

    @Column(name = "nom", nullable = false, length = 50)
    private String name;

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position, id")
    private List<AttributeValue> values = new ArrayList<>();

    public AttributeValue addValue(String value) {
        AttributeValue attributeValue = new AttributeValue();
        attributeValue.setAttribute(this);
        attributeValue.setValue(value);
        attributeValue.setPosition(values.size());
        values.add(attributeValue);
        return attributeValue;
    }
}
