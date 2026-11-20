-- C23 : associations produit -> accessoires pour le cross-selling panier.
-- Le lien est orienté : un produit source suggère un produit accessoire.

CREATE TABLE produit_accessoire (
    produit_id BIGINT NOT NULL,
    accessoire_produit_id BIGINT NOT NULL,
    PRIMARY KEY (produit_id, accessoire_produit_id),
    CONSTRAINT fk_produit_accessoire_source
        FOREIGN KEY (produit_id) REFERENCES produit (id) ON DELETE CASCADE,
    CONSTRAINT fk_produit_accessoire_cible
        FOREIGN KEY (accessoire_produit_id) REFERENCES produit (id) ON DELETE CASCADE,
    CONSTRAINT ck_produit_accessoire_not_self
        CHECK (produit_id <> accessoire_produit_id)
);

CREATE INDEX idx_produit_accessoire_cible ON produit_accessoire (accessoire_produit_id);
