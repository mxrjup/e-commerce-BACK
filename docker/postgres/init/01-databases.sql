-- Une base par service, plus celle de Keycloak.
-- Ce script ne tourne qu'au PREMIER demarrage, sur un volume vide :
-- apres avoir ajoute une ligne, relancer `docker compose down -v && docker compose up -d`.
CREATE DATABASE keycloak;
CREATE DATABASE ecommerce_cart;
