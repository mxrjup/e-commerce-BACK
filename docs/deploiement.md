# Déploiement et mise en production

Ce document suit une version du back-end depuis `dev` jusqu'au cluster, et
recense les variables d'environnement de chaque service. Il complète, sans les
répéter :

- le [README](../README.md) : construction, stack locale, images Docker, CI, et
  le fonctionnement interne du déploiement (« Déploiement en production ») ;
- le README de **`e-commerce-INFRA`** : mise en place de la plateforme sur le
  cluster (PostgreSQL, Keycloak, Kafka, Ingress), Secrets et hôte public ;
- `CLAUDE.md` : branches, commits, PR et tags, la source unique pour Git.

## Vue d'ensemble

```
feature/* ──PR──▶ dev ──PR « release: vX.Y.Z »──▶ main ──tag vX.Y.Z──▶ Images ──▶ Deploy ──▶ cluster
                   │                                                  (GHCR)     (production)
                   └── chaque merge : images sha-<commit> et dev, rien n'est déployé
```

| Étape | Qui | Ce qui se passe |
|---|---|---|
| merge sur `dev` | relecteur | CI, puis images `dev` et `sha-<commit>` sur GHCR ; **pas de déploiement** |
| merge `dev` → `main` | relecteur | images `main` ; **pas de déploiement** |
| push du tag `vX.Y.Z` sur `main` | un humain | images `X.Y.Z`, `X.Y`, `latest`, puis **déploiement de cette version** |
| *Deploy* → *Run workflow* | un humain | déploiement d'une version existante : rollback ou redéploiement |

**Seul un tag déploie.** Un merge sur `main` prépare la release, il ne la met
pas en ligne.

## Prérequis, une fois pour toutes

1. **La plateforme tourne sur le cluster** : PostgreSQL, Keycloak, Kafka,
   Ingress et un Deployment par service, appliqués depuis
   `e-commerce-INFRA` (`kubectl apply -k k8s/`), avec ses Secrets et son
   `params.env`. Voir son README, « Avant le premier déploiement ».
2. **Chaque module du POM racine a son Deployment.** `deploy.sh` déploie tous
   les services du dépôt et s'arrête, avant de rien toucher, s'il en manque un.
   Un nouveau service se déclare donc dans INFRA avant la release qui l'apporte.
3. **L'environnement GitHub `production`** existe (*Settings* →
   *Environments*) avec le secret **`KUBECONFIG`**, le contenu du kubeconfig
   du namespace. Des *Required reviewers* y font approuver chaque déploiement.
4. **Le workflow Deploy est sur `main`.** GitHub ne lance un workflow
   `workflow_run` ou `workflow_dispatch` que depuis la branche par défaut : il
   n'existe donc qu'à partir de la première release.

## Mettre une version en production

**1. Préparer `dev`.** La CI y est verte et tout ce qui est prévu pour la
release est mergé. Relire ce qui partira :

```bash
git fetch origin --tags
git log --oneline origin/main..origin/dev
```

**2. Choisir la version** (SemVer, `CLAUDE.md` §4) d'après les types de
commits : un `!` ou un `BREAKING CHANGE` → MAJEUR, au moins un `feat` →
MINEUR, sinon (`fix`, `perf`) → CORRECTIF.

```bash
git describe --tags --abbrev=0 origin/main      # la version actuelle
```

Aucun tag n'existe encore : la première release fixe le point de départ
(`v0.1.0` tant que l'API peut encore changer sans prévenir, `v1.0.0` sinon), à
décider en équipe.

**3. Ouvrir la PR `dev` → `main`**, titrée `release: vX.Y.Z`. Une approbation,
CI verte, puis **merge commit** (pas de squash : l'historique de `dev` est
gardé).

**4. Poser le tag** sur le commit de merge, et le pousser :

```bash
git switch main
git pull origin main
git tag -a vX.Y.Z -m "release: vX.Y.Z"
git push origin vX.Y.Z
```

Un tag poussé ne se déplace, ne se supprime ni ne se réutilise jamais : une
release ratée se corrige par une nouvelle version CORRECTIF.

**5. Suivre le déploiement** dans l'onglet *Actions* : le workflow *Images* du
tag (toutes les images de la version), puis *Deploy*, qui démarre dès qu'il a
réussi. Avec des *Required reviewers*, *Deploy* attend leur approbation. Son
résumé liste l'image de chaque service.

Si l'API du cluster n'est pas joignable depuis GitHub, le même script se lance
depuis un poste, une fois *Images* terminé :

```bash
KUBECONFIG=~/.kube/ecole.yaml .github/scripts/deploy.sh vX.Y.Z
```

**6. Vérifier** :

```bash
kubectl get configmap ecommerce-release -o jsonpath='{.data.version}'   # vX.Y.Z
kubectl get deployments -o wide                                          # images en X.Y.Z@sha256:…
kubectl rollout history deployment/order-service                         # « deploy vX.Y.Z »
curl -i https://<PUBLIC_HOST>/api/orders                                 # 401 : la gateway répond
```

puis les contrôles du README d'INFRA (« Vérifier »), et un parcours réel :
connexion, panier, paiement de test.

**7. Les fronts ensuite.** Quand une release du back-end change une API
utilisée par le web ou le mobile, le back-end part **d'abord**, les fronts
ensuite (`CLAUDE.md` §4) : une ancienne application doit continuer de marcher
contre le nouveau back-end le temps de sa mise à jour.

## Revenir en arrière

**Automatiquement.** Si un seul service n'arrive pas à démarrer pendant un
déploiement, `deploy.sh` ramène **tous** les services du déploiement à leur
version précédente, et le workflow échoue. Rien à faire, sinon comprendre
pourquoi.

**À la main**, quand une version déployée se révèle mauvaise après coup :
*Actions* → *Deploy* → *Run workflow*, avec la version précédente (par
exemple `v1.4.0` si `v1.4.1` pose problème). Ou, depuis un poste :

```bash
KUBECONFIG=~/.kube/ecole.yaml .github/scripts/deploy.sh v1.4.0
```

**Ce qu'un rollback ne défait pas** : il change les images, pas les données.

- Une colonne ou une table ajoutée par la version reste en base. Une
  évolution de schéma doit donc rester lisible par la version précédente :
  colonne nullable ou avec une valeur par défaut, jamais de renommage ni de
  suppression dans la même release que le code qui l'utilise.
- Un événement Kafka publié par la nouvelle version peut être relu par
  l'ancienne. Un champ ajouté à un événement de `common-lib` doit être
  optionnel pour ses consommateurs.

## Correctif urgent (hotfix)

Pour un bug de production qui ne peut pas attendre la prochaine release
(`CLAUDE.md` §2) :

1. branche `hotfix/<sujet>` **depuis `main`** ;
2. PR vers `main`, *squash and merge* ;
3. tag **CORRECTIF** (`v1.4.0` → `v1.4.1`) sur `main` : il se déploie comme une
   release ;
4. **aussitôt**, ramener le correctif sur `dev` (merge de `main` dans `dev`
   par une PR), sinon la release suivante le perd.

## Variables d'environnement

Chaque service a des valeurs par défaut qui suffisent pour le lancer depuis
l'IDE contre la stack Docker Compose. Le compose et les manifests d'INFRA ne
surchargent que ce qui change dans leur réseau.

**Toute propriété Spring se surcharge aussi par une variable**, en majuscules
et avec des `_` : `app.kafka.retry.max-retries` devient
`APP_KAFKA_RETRY_MAX_RETRIES`. Les tableaux ci-dessous ne listent que les
variables effectivement utilisées.

Légende : *IDE* = défaut dans l'`application.yml` ; *Compose* = valeur
dans `compose.yaml` ; *Cluster* = valeur dans les manifests d'INFRA
(`k8s/services/`). 🔒 = secret, toujours fourni par un Secret Kubernetes
sur le cluster, jamais versionné.

### Tous les services

| Variable | IDE | Compose | Cluster | Rôle |
|---|---|---|---|---|
| `SERVER_PORT` | `server.port` de l'`application.yml` | même valeur | — | port HTTP, celui du tableau des modules |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | `kafka:9092` | `kafka:9092` | broker Kafka (services qui l'utilisent) |
| `APP_KAFKA_RETRY_*` | voir README, « Rejeu » | — | — | politique de rejeu des consommateurs |
| `APP_KAFKA_TOPICS_*` | noms du README, « Topics » | — | — | noms de topics ; un renommage se crée aussi côté broker |

### `api-gateway`

| Variable | IDE | Compose | Cluster | Rôle |
|---|---|---|---|---|
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/ecommerce` | `${KEYCLOAK_FRONTEND_URL}/realms/ecommerce` | `$(PUBLIC_URL)/auth/realms/ecommerce` | `iss` attendu dans les jetons : l'URL **publique** |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/…/certs` | `http://keycloak:8080/…/certs` | `http://keycloak:8080/auth/…/certs` | clés de signature : l'URL **interne** |
| `CART_SERVICE_URI` | `http://localhost:8083` | `http://cart-service:8083` | idem | cible de `/api/cart/**` |
| `CATALOG_SERVICE_URI` | `http://localhost:8081` | `http://catalog-service:8081` | à ajouter dans INFRA | cible de `/api/catalog/**`, `/api/admin/catalog/**` |
| `ORDER_SERVICE_URI` | `http://localhost:8084` | `http://order-service:8084` | idem | cible de `/api/orders/**`, `/api/admin/orders/**` |
| `USER_SERVICE_URI` | `http://localhost:8082` | `http://user-service:8082` | à ajouter dans INFRA | cible de `/api/admin/users/**` |
| `MCP_SERVICE_URI` | `http://localhost:8087` | `http://mcp-service:8087` | à ajouter dans INFRA | cible de `/mcp/**`, `/sse`, `/api/admin/mcp/**` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8081` | idem, via `.env` | `$(PUBLIC_URL)` | origines navigateur autorisées |

L'issuer et l'URL des clés **diffèrent volontairement** : voir README,
« L'issuer, le piège à éviter ».

### `cart-service`, `catalog-service`, `order-service` et `mcp-service`

| Variable | IDE | Compose | Cluster | Rôle |
|---|---|---|---|---|
| `DB_HOST` | `localhost` | `postgres` | `postgres` | hôte PostgreSQL |
| `DB_PORT` | `5432` | `5432` | `5432` | port PostgreSQL |
| `DB_NAME` | `ecommerce_cart` / `ecommerce_catalog` / `ecommerce_order` / `ecommerce_mcp` | via `CART_DB_NAME` / `CATALOG_DB_NAME` / `ORDER_DB_NAME` / `MCP_DB_NAME` | idem | base du service |
| `SPRING_DATASOURCE_URL` | construite avec les trois précédentes | — | — | URL JDBC complète ; si fournie, remplace les trois |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | `${POSTGRES_USER}` | `cart_service` / `catalog_service` / `order_service` / `mcp_service` | rôle PostgreSQL du service |
| `SPRING_DATASOURCE_PASSWORD` 🔒 | `postgres` | `${POSTGRES_PASSWORD}` | Secret `postgres-credentials` | mot de passe de ce rôle |

Les valeurs *Cluster* de `catalog-service` (`ecommerce_catalog`, rôle
`catalog_service`) et de `mcp-service` (`ecommerce_mcp`, rôle `mcp_service`)
s'appliqueront avec leurs manifests dans INFRA, pas encore écrits : voir
« Prérequis », point 2.

`mcp-service` lit aussi `MCP_SECURITY_PROTECTED_PATHS` (défaut
`/sse,/mcp,/mcp/*`) : les chemins du transport MCP qui exigent un jeton MCP.
À ne changer qu'avec les chemins du serveur MCP (`M01`).

### `order-service`, en plus

| Variable | IDE | Compose | Cluster | Rôle |
|---|---|---|---|---|
| `CATALOG_SERVICE_URL` | `http://localhost:8081` | `http://catalog-service:8081` | `http://catalog-service:8081` | API interne du catalogue (prix, stock) |
| `STRIPE_SECRET_KEY` 🔒 | `sk_test_placeholder` | — | Secret `stripe` | clé API Stripe |
| `STRIPE_SUCCESS_URL` | `http://localhost:3000/checkout/success?…` | — | `$(PUBLIC_URL)/checkout/success?…` | retour après paiement |
| `STRIPE_CANCEL_URL` | `http://localhost:3000/checkout/cancel` | — | `$(PUBLIC_URL)/checkout/cancel` | retour après abandon |

- **La clé Stripe par défaut est factice** : le service démarre, mais toute
  création de session de paiement échoue. Pour essayer le paiement en local,
  lancer le service depuis l'IDE ou `./mvnw` avec une clé de **test**
  (`sk_test_…`) exportée dans `STRIPE_SECRET_KEY` ; le compose ne la transmet
  pas.
- Le manifest d'INFRA fournit déjà `STRIPE_WEBHOOK_SECRET` : il sera lu par le
  traitement du webhook Stripe (`O03` #27), pas encore mergé.
- En production, la clé est une clé **live** (`sk_live_…`) : c'est le seul
  endroit où elle existe, dans le Secret `stripe` du cluster.

### `user-service`

Pas de base de données pour l'instant : les collaborateurs vivent dans
Keycloak (`U04` #91). Les variables `DB_*` arriveront avec `U01` (#58).

| Variable | IDE | Compose | Cluster | Rôle |
|---|---|---|---|---|
| `KEYCLOAK_ADMIN_SERVER_URL` | `http://localhost:8180` | `http://keycloak:8080` | `http://keycloak:8080/auth` | Keycloak joignable **depuis le service**, sans `/realms` |
| `KEYCLOAK_REALM` | `ecommerce` | — | — | realm dont les comptes sont gérés |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `user-service` | — | — | client confidentiel à compte de service |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` 🔒 | `set-me` | `${USER_SERVICE_CLIENT_SECRET}` | Secret, à créer dans INFRA | secret de ce client |

**Le secret est partagé avec Keycloak.** Le realm importé lit le sien dans
`USER_SERVICE_CLIENT_SECRET` (défaut `set-me`) : sur le cluster, le Deployment
de Keycloak et celui de `user-service` doivent lire **la même clé du même
Secret**, générée une fois (`openssl rand -hex 32`). Un défaut `set-me` laissé
sur le cluster donnerait la gestion des comptes à quiconque le connaît.

Comme pour tout changement de realm, Keycloak n'importe ce client que sur un
realm qui n'existe pas encore : sur un Keycloak déjà en place, le créer depuis
la console (voir README, « Collaborateurs »).

### La stack Docker Compose

Les variables de `compose.yaml` lui-même (identifiants PostgreSQL et Keycloak,
ports publiés, fournisseurs d'identité, partitions Kafka) sont toutes listées,
avec leur valeur par défaut, dans **`.env.example`**. Elles ne servent qu'en
local.

### Ajouter une variable

1. La lire dans l'`application.yml` du service avec un défaut qui marche
   depuis l'IDE : `${MA_VARIABLE:valeur-locale}`.
2. La surcharger dans `compose.yaml` si la valeur change dans le réseau Docker.
3. La déclarer dans le manifest du service dans INFRA ; un secret passe par un
   Secret et son modèle dans `k8s/secrets.example/`, jamais par `params.env`.
4. L'ajouter au tableau ci-dessus.
