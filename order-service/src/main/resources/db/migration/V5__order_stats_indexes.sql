CREATE INDEX IF NOT EXISTS idx_commande_stats_date_status ON commande (date_creation, statut);
CREATE INDEX IF NOT EXISTS idx_ligne_commande_order ON ligne_commande (commande_id);
