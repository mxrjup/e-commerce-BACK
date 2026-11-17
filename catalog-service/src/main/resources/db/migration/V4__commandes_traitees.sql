-- C21 (#10) : les commandes payees dont le stock a deja ete retire.
-- Kafka livre au moins une fois : un meme order.paid peut arriver deux fois
-- (rejeu, webhook Stripe rappele). La cle primaire empeche de retirer deux
-- fois le stock d'une meme commande.
-- SQL commun a PostgreSQL et a H2 en mode PostgreSQL.

CREATE TABLE commande_traitee (
    commande_id     BIGINT       PRIMARY KEY,
    numero_commande VARCHAR(50)  NOT NULL,
    date_traitement TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
