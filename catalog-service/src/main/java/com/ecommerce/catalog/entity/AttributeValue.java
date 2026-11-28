package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One possible value of an attribute: "Noir" for "Couleur", "AZERTY" for "Layout clavier". */
@Getter
@Setter
@Entity
@Table(name = "valeur_attribut")
public class AttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribut_id", nullable = false)
    private Attribute attribute;

    @Column(name = "valeur", nullable = false, length = 50)
    private String value;

    @Column(nullable = false)
    private int position;
}
