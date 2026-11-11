#!/usr/bin/env bash
# Cree les topics du projet. Idempotent : relancer ce script ne casse rien.
#
# C'est la liste de reference des topics. Les manifests Kubernetes du depot
# e-commerce-INFRA portent la meme liste : toute modification ici doit y etre
# reportee.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-3}"
# Un seul broker en local comme sur le cluster de l'ecole : pas de replique.
REPLICATION=1

# Trois topics metier. Le nom est le contrat entre un producteur et ses
# consommateurs : point comme separateur, jamais d'underscore (melanger les
# deux expose a des collisions de noms de metriques cote broker).
TOPICS=(
  # O03 : publiee a la validation du paiement, consommee par le panier (P06)
  # et les notifications (N02). Nom deja utilise par le code en cours.
  "order.paid"
  # C09 : baisse de prix et retour en stock, consommees par les alertes (N03).
  "catalogue.evenements"
  # S03 : reponse d'un collaborateur a un ticket, consommee par les
  # notifications (N02).
  "sav.reponse-admin"
)

# K02 : un topic de rebut par topic metier, nom = topic + ".dlt". Un message
# y arrive une fois ses tentatives epuisees (voir common-lib). Avec
# l'auto-creation coupee, un DLT absent ferait echouer la publication, et le
# message serait rejoue en boucle au lieu d'etre ecarte.
TOPICS+=("${TOPICS[@]/%/.dlt}")

for topic in "${TOPICS[@]}"; do
  echo "topic ${topic} : creation si absent"
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION}"
done

echo "topics presents sur le broker :"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list
