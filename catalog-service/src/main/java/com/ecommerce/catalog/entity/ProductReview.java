package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "avis_produit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview extends AuditedEntity {

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "auteur_subject", nullable = false, updatable = false, length = 255)
    private String authorSubject;

    @Column(name = "note", nullable = false)
    private int rating;

    @Column(name = "commentaire", nullable = false, length = 2000)
    private String comment;

    public ProductReview(Long productId, String authorSubject) {
        this.productId = productId;
        this.authorSubject = authorSubject;
    }

    public void revise(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }
}
