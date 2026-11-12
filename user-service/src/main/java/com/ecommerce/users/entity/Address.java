package com.ecommerce.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "adresse")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User user;

    @Column(name = "libelle")
    private String label;

    @Column(name = "destinataire")
    private String recipient;

    @Column(name = "rue")
    private String street;

    private String complement;

    @Column(name = "code_postal", nullable = false)
    private String zipCode;

    @Column(name = "ville", nullable = false)
    private String city;

    @Column(name = "pays", nullable = false)
    private String country;

    @Column(name = "par_defaut", nullable = false)
    private boolean isDefault;
}