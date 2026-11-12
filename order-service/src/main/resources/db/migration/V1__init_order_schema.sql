CREATE TABLE IF NOT EXISTS commande (
    id BIGSERIAL PRIMARY KEY,
    numero VARCHAR(255) NOT NULL UNIQUE,
    utilisateur_id BIGINT NOT NULL,
    adresse_livraison_id BIGINT,
    statut VARCHAR(50) NOT NULL,
    mode_livraison VARCHAR(50),
    total_ht NUMERIC(10,2),
    total_ttc NUMERIC(10,2),
    stripe_session_id VARCHAR(255),
    session_token VARCHAR(255),
    invoice_url VARCHAR(2048),
    transporteur VARCHAR(255),
    numero_suivi VARCHAR(255),
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ligne_commande (
    id BIGSERIAL PRIMARY KEY,
    commande_id BIGINT NOT NULL,
    variante_id BIGINT NOT NULL,
    designation VARCHAR(255) NOT NULL,
    prix_unitaire_ht NUMERIC(10,2) NOT NULL,
    taux_tva NUMERIC(5,2) NOT NULL,
    quantite INTEGER NOT NULL,
    CONSTRAINT fk_ligne_commande_commande FOREIGN KEY (commande_id) REFERENCES commande(id)
);
