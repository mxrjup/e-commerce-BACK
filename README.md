# e-commerce — back-end

Monorepo Maven multi-modules : un module par microservice, plus une librairie
commune. Un seul POM parent fixe les versions, un seul `./mvnw clean verify`
construit et teste l'ensemble.

## Prérequis

- **JDK 21** (version imposée par le POM parent)
- **Docker** pour les dépendances locales (PostgreSQL, Keycloak, Kafka)
- Maven n'est pas à installer : le wrapper `./mvnw` s'en charge

## Construire et lancer

```bash
./mvnw clean verify                              # tous les modules + tests
./mvnw -pl cart-service -am clean verify         # un seul module et ses dépendances
./mvnw -pl cart-service spring-boot:run          # lancer un service
```

Pour démarrer un service sans PostgreSQL, le profil `local-h2` bascule sur une
base en mémoire :

```bash
./mvnw -pl cart-service spring-boot:run -Dspring-boot.run.profiles=local-h2
```

## Stack locale (Docker Compose)

`compose.yaml` démarre les dépendances dont les services ont besoin :
PostgreSQL, Keycloak et Kafka.

```bash
docker compose up -d              # les dépendances
docker compose ps                 # état et santé des conteneurs
docker compose logs -f keycloak   # les logs d'un service
docker compose down               # arrêt, données conservées
docker compose down -v            # arrêt + remise à zéro des données
```

| Service | Depuis l'hôte | Depuis le réseau Docker | Identifiants |
|---|---|---|---|
| PostgreSQL | `localhost:5432` | `postgres:5432` | `postgres` / `postgres` |
| Keycloak | <http://localhost:8180> | `keycloak:8080` | `admin` / `admin` |
| Kafka | `localhost:29092` | `kafka:9092` | — |

Les valeurs par défaut suffisent : la stack démarre sans configuration. Pour en
changer une, `cp .env.example .env` puis ajuster — le `.env` n'est jamais
commité.

**Une base de données par service.** `docker/postgres/init/01-databases.sql`
les crée au premier démarrage (`keycloak`, `ecommerce_cart`, `ecommerce_catalog`
et `ecommerce_order` aujourd'hui). Ce script ne s'exécute que sur un volume vide : après y avoir ajouté une base,
`docker compose down -v && docker compose up -d`.

**Les services applicatifs** sont déclarés derrière le profil `services` :
`docker compose --profile services up -d --build` construit leurs images et
démarre la pile complète. Pour travailler sur un service, il reste plus
pratique de lancer les dépendances seules et le service avec `./mvnw`
(ci-dessus). Voir la section « Images Docker » pour la construction.

**Kafka** expose deux listeners : `kafka:9092` pour les conteneurs,
`localhost:29092` pour un service lancé depuis l'IDE. Le service `kafka-init`
crée les topics dès que le broker est prêt, puis s'arrête — voir la section
« Topics Kafka ».

**Keycloak** tourne en `start-dev` (HTTP en clair, pas de cache distribué),
persiste dans la base `keycloak` et importe au démarrage le realm décrit dans
`docker/keycloak/import/` — voir la section suivante.

## Authentification (Keycloak)

Le realm **`ecommerce`** est décrit par
`docker/keycloak/import/ecommerce-realm.json` et importé au démarrage de la
stack. Il se recrée entièrement avec
`docker compose down -v && docker compose up -d`.

> **Attention au piège.** Keycloak n'importe le realm que s'il n'existe pas
> déjà : un simple `restart` ou `up --force-recreate` après modification du
> fichier affiche `Realm 'ecommerce' already exists. Import skipped` et garde
> l'ancienne configuration. Pour voir un changement, il faut repartir d'une
> base vide avec `down -v`.

### Clients

| Client | Pour | Type | Flux | Redirections |
|---|---|---|---|---|
| `web-bo` | back-office (`T06` #11) | public | Authorization Code + **PKCE S256** | `http://localhost:5173/*` |
| `mobile-app` | app Expo (`T05` #41) | public | Authorization Code + **PKCE S256** | `ecommerce://auth-callback`, `exp://*`, `http://localhost:8081/*` |

Aucun des deux n'autorise le *Direct Access Grant* : aucun mot de passe ne
transite en dehors du navigateur. Les deux ajoutent l'audience
`ecommerce-api` dans le jeton d'accès, que la gateway (`T04` #84) pourra
vérifier.

Le client confidentiel à compte de service, pour l'Admin API de Keycloak,
viendra avec `U04` (#91) : il porte un secret, il n'a donc rien à faire dans un
fichier versionné.

### Rôles

| Rôle | Attribution | Donne accès à |
|---|---|---|
| `client` | **automatique** à la création du compte | API client : panier, commandes, profil |
| `collaborator` | manuelle | back-office |
| `admin` | manuelle, **inclut** `collaborator` | paramètres boutique, collaborateurs, panneau MCP |

`client` est automatique parce qu'il fait partie du composite
`default-roles-ecommerce`. C'est ce sur quoi `U01` s'appuie pour créer le
compte applicatif au premier login.

### Comptes de démonstration

| Compte | Mot de passe | Rôles dans le jeton |
|---|---|---|
| `client.demo@ecommerce.test` | `demo1234` | `client` |
| `admin.demo@ecommerce.test` | `demo1234` | `admin`, `collaborator`, `client` |

Ils sont dans `ecommerce-users-0.json`, **séparé du realm exprès** : c'est un
jeu de données local, et `D04` (#88) ne doit déployer que
`ecommerce-realm.json`.

### L'issuer, le piège à éviter

`KEYCLOAK_FRONTEND_URL` (par défaut `http://localhost:8180`) fixe le `iss` des
jetons. Un service qui tourne dans Docker ne peut pas joindre
`localhost:8180` : il garde donc cette URL comme `issuer-uri` et va chercher
les clés sur l'URL interne.

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/ecommerce/protocol/openid-connect/certs
```

Configurer les deux sur la même URL interne produit des jetons dont le `iss` ne
correspond à rien de ce que voit le navigateur, et la validation échoue.

### Google et Apple

Les deux fournisseurs sont importés **désactivés et sans identifiants**. Pour
en essayer un : renseigner le couple dans `.env`, relancer la stack, puis
activer le fournisseur dans la console d'administration.

- **Google** utilise le fournisseur intégré de Keycloak.
- **Apple** n'a pas de fournisseur intégré : il est déclaré comme fournisseur
  OIDC générique pointant sur `appleid.apple.com`. Deux conséquences à prévoir.
  Son `clientSecret` n'est pas un secret statique mais **un JWT signé avec une
  clé `.p8`, valable six mois au maximum**, donc à régénérer périodiquement —
  il faudra une tâche dédiée avant la production. Et Apple ne transmet le nom
  et l'e-mail qu'au tout premier consentement de l'utilisateur.

Le parcours de première connexion par un fournisseur reste le flux Keycloak par
défaut, qui **demande une confirmation de liaison** quand l'adresse existe
déjà. Lier automatiquement sur un e-mail vérifié demande un flux
d'authentification personnalisé : décision à prendre avec `U06` (#70).

### Réexporter le realm après une modification dans la console

```bash
mkdir -p /tmp/kc-export && chmod 777 /tmp/kc-export
docker compose run --rm --no-deps -v /tmp/kc-export:/tmp/export keycloak \
  export --dir /tmp/export --realm ecommerce --users different_files
```

Un conteneur jetable, pas `docker compose exec` : dans le conteneur déjà
démarré, l'export échoue sur le port d'administration déjà pris.

Relire le résultat avant de remplacer le fichier versionné. L'export complet
fait une quinzaine de fois la taille du fichier écrit à la main, **il contient
les `clientSecret` des fournisseurs d'identité en clair**, et il remplace les
mots de passe lisibles des comptes de démo par leurs empreintes.

## Topics Kafka

| Topic | Publié par | Consommé par |
|---|---|---|
| `order.paid` | Commande, à la validation du paiement (`O03` #27) | Panier (`P06` #24), Notifications (`N02` #63) |
| `catalogue.evenements` | Catalogue : baisse de prix, retour en stock (`C09` #8) | Alertes (`N03` #64) |
| `sav.reponse-admin` | SAV : réponse d'un collaborateur (`S03` #69) | Notifications (`N02` #63) |

Chaque topic a son **topic de rebut** (DLT), même nom suivi de `.dlt` :
`order.paid.dlt`, `catalogue.evenements.dlt`, `sav.reponse-admin.dlt`. Le
script les crée avec les autres ; voir « Événements Kafka » plus bas.

La liste de référence est **`docker/kafka/create-topics.sh`**, rejoué par le
service `kafka-init` à chaque `docker compose up` : il est idempotent, un
second passage ne touche à rien. Les manifests Kubernetes du dépôt
`e-commerce-INFRA` portent la même liste, et les deux doivent rester
synchronisées.

Pour ajouter un topic : une ligne dans ce script, une ligne dans le manifest
d'INFRA (son `.dlt` suit automatiquement), et le nom repris dans la propriété que lit le service — tous les
services lisent leur topic depuis une propriété surchargeable, jamais une
chaîne en dur.

**Le nom est le contrat** entre un producteur et ses consommateurs : point
comme séparateur, jamais d'underscore — mélanger les deux expose à des
collisions de noms de métriques côté broker.

**L'auto-création est désactivée.** Un nom mal orthographié dans un service
échoue (`Topic ... not present in metadata`) au lieu de créer un topic fantôme
où personne ne lira jamais rien.

## Modules

| Module | Domaine | Issues | Port | État |
|---|---|---|---|---|
| `common-lib` | Librairie partagée (contrat d'erreur HTTP, événements Kafka) | `T01`, `K02` | — | en place |
| `cart-service` | Panier | `P*` | 8083 | en place |
| `catalog-service` | Catalogue, stock, avis | `C*` | 8081 | référentiels en place (`C05`) |
| `user-service` | Utilisateurs, adresses, collaborateurs | `U*` | 8082 | à créer |
| `order-service` | Commandes, paiement, livraison | `O*` | 8084 | en place |
| `notification-service` | Notifications, alertes, emails | `N*` | 8085 | à créer |
| `support-service` | SAV, tickets | `S*` | 8086 | à créer |
| `mcp-service` | Serveur MCP | `M*` | 8087 | à créer |
| `api-gateway` | Routage, validation JWT, CORS | `T04` | 8080 | en place |

Les ports sont réservés ici pour éviter les collisions en local et dans le
Docker Compose.

## Catalogue : les référentiels

`catalog-service` (`C05` #90) porte les référentiels transverses que les
produits (`C01` #3) référenceront : **marques**, **catégories** (arborescentes),
**univers** et **taux de TVA**.

| Chemin | Pour | Contenu |
|---|---|---|
| `GET /api/catalog/brands`, `/brands/{slug}` | vitrine | marques, par nom |
| `GET /api/catalog/categories` | vitrine | **l'arbre** des catégories, en une requête |
| `GET /api/catalog/categories/{slug}` | vitrine | une catégorie et l'id de son parent |
| `GET /api/catalog/universes`, `/universes/{slug}` | vitrine | univers, par `position` puis nom |
| `/api/admin/catalog/{brands,categories,universes,vat-rates}` | back-office | `GET` liste, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |

La vitrine adresse une entrée par son **slug**, le back-office par son **id**.
Sans slug dans la requête, il est dérivé du nom : `Écrans & Moniteurs` donne
`ecrans-moniteurs`.

Règles, toutes en **409** avec un message lisible :

- un nom de marque ou d'univers, et un slug, sont uniques (le nom sans tenir
  compte de la casse) ; deux catégories de branches différentes peuvent porter
  le même nom, pas le même slug ;
- une catégorie ne se déplace pas sous elle-même ni sous l'une de ses
  sous-catégories, et ne se supprime pas tant qu'elle en a ;
- **il y a toujours exactement un taux de TVA par défaut** : en marquer un
  autre retire la marque du précédent ; le taux par défaut ne peut être ni
  démarqué ni supprimé ;
- une entrée encore référencée par une autre table (un produit, demain) ne se
  supprime pas : la contrainte de la base répond 409, pas 500.

**Le schéma est géré par Flyway**, une première dans le dépôt :
`catalog-service/src/main/resources/db/migration`. Hibernate ne fait que
vérifier (`ddl-auto: validate`) que les entités correspondent aux tables, et les
tests passent par les mêmes migrations : un écart entre les deux fait échouer
le build. Une évolution du schéma est donc **une nouvelle migration** (`V2__…`),
jamais une modification de `V1`, déjà appliquée. Le SQL doit passer sur
PostgreSQL et sur H2 en mode PostgreSQL (profil `local-h2` et tests).

`V1` insère les quatre taux français (20 % par défaut, 10 %, 5,5 %, 2,1 %) :
une donnée de référence, pas un jeu de démonstration.

```bash
./mvnw -pl catalog-service -am install -DskipTests
./mvnw -pl catalog-service spring-boot:run -Dspring-boot.run.profiles=local-h2
curl -s localhost:8081/api/admin/catalog/vat-rates
curl -s -X POST localhost:8081/api/admin/catalog/categories \
  -H 'Content-Type: application/json' -d '{"name":"Périphériques"}'
curl -s localhost:8081/api/catalog/categories
```

> **Port 8081.** C'est aussi celui du serveur de développement Expo. Si les
> deux tournent sur la même machine, `CATALOG_PORT` (voir `.env.example`)
> publie le catalogue ailleurs dans le Docker Compose ; en dehors de Docker,
> `-Dspring-boot.run.arguments=--server.port=8091`.

## Gateway : le point d'entrée de l'API

Tout le trafic entre par `api-gateway`, sur le port 8080. Les services ne sont
pas joignables depuis l'extérieur : les règles ci-dessous sont donc le seul
endroit où une route devient publique, authentifiée ou réservée au back-office.

### Routes et accès

| Chemin | Service | Accès |
|---|---|---|
| `/api/cart/**` | `cart-service` | **public** — le panier accepte les invités (`P01`) |
| `/api/catalog/**` | `catalog-service` | **public en lecture** (`GET`) ; le reste exige un jeton |
| `/api/admin/catalog/**` | `catalog-service` | rôle `collaborator` ou `admin` |
| `/api/orders/**` | `order-service` | jeton valide |
| `/api/admin/orders/**` | `order-service` | rôle `collaborator` ou `admin` |
| `/actuator/health`, `/actuator/info` | gateway | public |
| tout le reste | — | jeton valide : on refuse par défaut |

Convention : chaque service expose son sous-arbre d'administration sous
`/api/admin/<domaine>/**`, et la gateway exige `collaborator` sur tout
`/api/admin/**`. Un nouveau service s'ajoute donc avec deux routes et aucune
règle de sécurité supplémentaire.

### L'identité devient fiable

La gateway supprime `X-User-Id` et `X-Auth-Subject` de **toute** requête
entrante, puis repose `X-Auth-Subject` à partir du `sub` du jeton validé. Un
service derrière la gateway peut donc faire confiance à `X-Auth-Subject` : il ne
peut venir que d'une signature émise par Keycloak. Sans ce filtre, n'importe
quel client pouvait réclamer le panier d'un autre en envoyant son `X-User-Id`.

`X-Session-Token` n'est pas touché : il identifie un panier invité, pas un
compte, et le client en est le propriétaire légitime. Le jeton lui-même est
transmis tel quel, pour un service qui voudrait lire des claims plus fins.

> **À faire côté services.** `cart-service` lit encore `X-User-Id`, un `Long`,
> alors que `X-Auth-Subject` porte l'UUID Keycloak. La correspondance
> UUID → identifiant applicatif arrive avec `U01` (#58). En attendant, une
> requête authentifiée est traitée comme un panier invité plutôt que comme
> celui de quelqu'un d'autre : c'est dégradé, mais sûr.

### Lancer la gateway en local

```bash
docker compose up -d                        # Keycloak doit tourner
./mvnw -pl api-gateway spring-boot:run
curl -i http://localhost:8080/api/orders    # 401 sans jeton
```

Par défaut elle valide les jetons contre
`http://localhost:8180/realms/ecommerce` et route vers `localhost:8081`,
`localhost:8083` et `localhost:8084`. Dans Docker, les variables de `compose.yaml` remplacent ces
valeurs — en particulier les clés de signature, qui se récupèrent sur l'URL
interne de Keycloak alors que l'issuer reste l'URL frontale.

### CORS

Configuré une seule fois dans la gateway, jamais dans un service : les origines
viennent de `CORS_ALLOWED_ORIGINS` (back-office Vite et serveur Expo par
défaut), avec `Authorization`, `Content-Type` et `X-Session-Token` autorisés en
requête, `X-Session-Token` exposé en lecture au navigateur, et les
identifiants autorisés.

## Images Docker

Un seul `Dockerfile`, à la racine, construit n'importe quel service : le module
est un paramètre.

```bash
docker build --build-arg MODULE=cart-service --build-arg SERVER_PORT=8083 \
  -t cart-service .
```

`compose.yaml` passe ces arguments pour chaque service : containeriser un
nouveau module ne demande donc pas d'écrire un Dockerfile, seulement de
l'ajouter à la liste de POM décrite plus bas.

**Le contexte de build est la racine du dépôt**, pas le dossier du module : la
construction a besoin du POM parent et de `common-lib`. C'est aussi la raison
d'être du `.dockerignore` — sans lui le contexte embarquerait tous les
`target/` et le `.git`.

**Deux étapes.** La première compile avec `maven:3.9.11-eclipse-temurin-21`,
épinglée sur la même version que le wrapper du dépôt, pour qu'une construction
locale et une construction d'image utilisent le même Maven. Les POM sont copiés
avant les sources et `dependency:go-offline` remplit le dépôt Maven dans une
couche à part : une modification de code ne retélécharge donc rien.
**Un nouveau module doit être ajouté aux `COPY <module>/pom.xml` du
Dockerfile**, sinon sa construction échoue sur un POM manquant.

**Le jar est découpé en couches** par Spring Boot (`jarmode=tools`, option
`extract --layers`) : dépendances, chargeur, dépendances SNAPSHOT, puis code
applicatif. Les services partageant l'essentiel de leurs dépendances, leurs
images partagent ces couches — ce qui allège les envois vers GHCR (`D03` #54)
et les téléchargements dans le cluster (`D04` #88).

**L'étape d'exécution** part d'un JRE Alpine, tourne sous l'utilisateur
`spring` et jamais root, et laisse la JVM se dimensionner sur la limite mémoire
du conteneur (`-XX:MaxRAMPercentage=75`) plutôt que sur la mémoire de la
machine hôte. Les images pèsent environ 380 Mo.

Si un service finit par exiger un runtime particulier, rien n'empêche de lui
donner son propre Dockerfile : il suffira de repointer son `dockerfile:` dans
le compose.

### Publication sur GHCR

Le workflow `.github/workflows/images.yml` (`D03` #54) construit les images et
les pousse sur le registre de conteneurs de GitHub :

```
ghcr.io/mxrjup/e-commerce-back/<service>:<tag>
```

| Événement | Images construites | Poussées | Tags |
|---|---|---|---|
| Pull Request | services touchés | **non** | — |
| push sur `dev` / `main` | **tous** les services | oui | `sha-<commit>`, `dev` ou `main` |
| tag `vX.Y.Z` | **tous** les services | oui | `sha-<commit>`, `X.Y.Z`, `X.Y`, `latest` |

**Sur une PR, l'image est construite sans être poussée** : un Dockerfile cassé
se voit avant le merge, sans publier d'image d'un code pas encore relu. Un
changement du `Dockerfile`, du `.dockerignore` ou du workflow reconstruit tous
les services ; sinon les mêmes règles que la CI s'appliquent (voir plus bas).

**Sur `dev` et `main`, tous les services sont poussés à chaque commit**, même
ceux qui n'ont pas changé : un `sha-<commit>` désigne ainsi toujours un jeu
d'images complet, et un déploiement (`D04` #88, `D05` #38) n'a qu'un tag à
connaître. Le cache de couches de GitHub Actions rend la reconstruction d'un
service inchangé rapide.

**Le tag `vX.Y.Z` posé sur `main`** à la release ajoute les tags de version,
ceux qu'un déploiement de production doit utiliser. `latest` ne suit que ces
tags, jamais `dev`.

Le workflow pousse avec le `GITHUB_TOKEN` (permission `packages: write`) :
aucun secret à créer. **Visibilité** : les paquets héritent de celle du dépôt,
public ; le cluster les tire donc sans identifiants (vérifié au premier push).

Un service est reconnu à son `spring-boot-maven-plugin` : un nouveau service
déclaré dans les `<modules>` du POM racine a donc son image sans toucher au
workflow. Son port vient du `server.port` de son `application.yml`. Pour voir
ce qu'une branche construirait :

```bash
.github/scripts/image-modules.sh origin/dev HEAD
```

### Déploiement en production

Le workflow `.github/workflows/deploy.yml` (`D05` #38) déploie une version sur
le cluster :

| Déclencheur | Ce qui est déployé |
|---|---|
| tag `vX.Y.Z` poussé sur `main` | cette version, dès que le workflow `Images` a fini de la publier |
| *Actions* → *Deploy* → *Run workflow* | la version saisie : **revenir à une version précédente**, ou redéployer la courante |

Le rollback n'est donc pas une opération à part : c'est le déploiement d'un tag
plus ancien, toujours présent sur GHCR puisqu'un tag ne se déplace ni ne se
supprime jamais.

**Ce que fait un déploiement**, dans `.github/scripts/deploy.sh` :

1. il refuse un tag absent, ou posé ailleurs que sur `main` ;
2. il vérifie que l'image de **chaque** service existe pour cette version,
   avant de toucher au cluster ;
3. il passe chaque service sur le **digest** de son image
   (`X.Y.Z@sha256:…`), pas sur un tag ;
4. il attend chaque rollout. **Si un seul échoue, tous les services de ce
   déploiement reviennent à leur version précédente**, et le workflow échoue ;
5. il enregistre la version déployée dans le ConfigMap `ecommerce-release`.

Testé sur un cluster k3s aux contraintes de l'école, avec une version dont
`cart-service` plante au démarrage : les trois services sont revenus à la
version précédente. Pendant tout le déploiement raté et son retour arrière,
196 requêtes sur 197 vers le panier ont abouti ; l'ancienne version a servi
tant que la nouvelle n'était pas prête.

```bash
kubectl get configmap ecommerce-release -o jsonpath='{.data.version}'   # version en production
kubectl rollout history deployment/cart-service                         # « deploy vX.Y.Z » par révision
```

**Le workflow ne s'active qu'une fois sur `main`** : GitHub ne lance un
workflow `workflow_run` ou `workflow_dispatch` que depuis la branche par
défaut. Il arrive donc avec la première release, et c'est elle qui fera le
premier déploiement.

**À configurer une fois**, dans *Settings* → *Environments* : un environnement
**`production`**, avec le secret **`KUBECONFIG`** (le contenu du kubeconfig du
namespace). Y ajouter des *Required reviewers* fait attendre chaque
déploiement d'une approbation. Le secret n'est lisible que par ce job.

**Si le cluster n'est pas joignable depuis GitHub** (API réservée au réseau de
l'école), le même script se lance depuis un poste, avec le même résultat :

```bash
KUBECONFIG=~/.kube/ecole.yaml .github/scripts/deploy.sh v1.2.0
```

**Avec les manifests d'INFRA.** Le script change la version des services
existants ; il ne crée ni ne modifie rien d'autre. Un `kubectl apply -k`
d'INFRA remet les images au tag de son `kustomization.yaml` (`dev` pour
l'instant) : redéployer ensuite la version en production,

```bash
.github/scripts/deploy.sh "$(kubectl get configmap ecommerce-release -o jsonpath='{.data.version}')"
```

ou faire lire ce ConfigMap par le déploiement d'INFRA.

## Intégration continue

GitHub Actions (`.github/workflows/ci.yml`) construit et teste le back-end à
chaque Pull Request vers `dev` ou `main`, et à chaque merge sur ces branches.

**Seuls les modules touchés sont reconstruits**, un job par module :

| Changement | Modules construits |
|---|---|
| `<module>/**` | ce module |
| `common-lib/**`, `pom.xml` racine, `mvnw`, `.mvn/**`, la CI elle-même | tous |
| le reste : README, `compose.yaml`, `docker/`, `Dockerfile`… | aucun |

Chaque job construit d'abord les modules dont il dépend (POM parent,
`common-lib`) sans leurs tests, puis lance `./mvnw -pl <module> verify`. Sans
base de comparaison (lancement manuel, premier push d'une branche), tout est
construit. Pour voir ce qu'une branche déclencherait :

```bash
.github/scripts/ci-modules.sh origin/dev HEAD
```

**Le job `CI` résume le run** : vert si tous les modules construits sont verts,
ou si aucun module n'était concerné. C'est **le seul check à rendre
obligatoire** dans la protection des branches `dev` et `main` : les jobs par
module n'existent que quand leur module change, un check obligatoire sur l'un
d'eux bloquerait toute PR qui ne le touche pas.

En cas d'échec, les rapports Surefire du module sont joints au run
(`surefire-<module>`, gardés 7 jours).

## Ajouter un service

1. Créer le dossier `<domaine>-service`, en anglais et en kebab-case.
2. Son `pom.xml` hérite de `com.ecommerce:ecommerce-parent` avec
   `<relativePath>../pom.xml</relativePath>`. Ne pas redéclarer `groupId`,
   `version`, `java.version`, ni la configuration du `spring-boot-maven-plugin`
   ni les versions de dépendances : tout vient du parent.
3. Déclarer le module dans la section `<modules>` du `pom.xml` racine.
4. Ajouter la dépendance `common-lib` (version `${project.version}`).
5. Package racine `com.ecommerce.<domaine>`, classe
   `<Domaine>ServiceApplication`.
6. Déclarer le plugin `spring-boot-maven-plugin` dans `<build><plugins>`, sans
   configuration.
7. Ajouter `COPY <module>/pom.xml <module>/` au `Dockerfile` de la racine, sa
   base de données à `docker/postgres/init/01-databases.sql` et son entrée dans
   le profil `services` du `compose.yaml`, avec le port réservé au tableau des
   modules.
8. Lui donner son Deployment dans les manifests de `e-commerce-INFRA`
   (`k8s/services/`) **avant la release suivante** : `deploy.sh` déploie tous
   les modules du POM et s'arrête si l'un d'eux n'a pas de Deployment sur le
   cluster.

La CI n'a pas à être modifiée : elle lit la liste des modules dans le
`pom.xml` racine.

Une version de dépendance absente du BOM Spring Boot se déclare dans le
`dependencyManagement` du POM racine, **jamais** dans le module.

## Contrat d'erreur partagé

`common-lib` fournit la réponse d'erreur unique de la plateforme : les fronts
web et mobile n'ont qu'une seule forme de payload à traiter, quel que soit le
service interrogé.

```json
{
  "timestamp": "2026-10-07T08:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Cart 42 not found",
  "details": null
}
```

Il suffit de dépendre de `common-lib` : l'advice est enregistré par
auto-configuration, sans annotation ni scan de composants côté service.

- Lever `ResourceNotFoundException` pour un 404 métier : son message est repris
  tel quel dans `message`.
- Lever `ConflictException` pour un 409 : requête valide, mais en conflit avec
  l'état des données (nom déjà pris, ressource encore utilisée).
- Les erreurs de validation (`@Valid`) remplissent `details`.
- Les statuts décidés par Spring sont conservés (404 sur une URL inconnue, 405
  sur une mauvaise méthode, 400 sur un corps illisible).
- Une exception imprévue est **journalisée** et renvoyée en 500 avec un message
  générique : aucun détail interne ne sort du service.
- Ne pas redéclarer de `@RestControllerAdvice` global dans un service, sauf pour
  remplacer volontairement celui de `common-lib`.
- Un service qui ajoute Spring Security doit mapper explicitement
  `AccessDeniedException`, sinon elle serait traitée comme une erreur imprévue
  au lieu d'un 403.

## Événements Kafka

`common-lib` porte tout ce qui est commun aux producteurs et aux
consommateurs : les **classes d'événements**, la **configuration Spring
Kafka** et la **politique de rejeu**. Un service déclare `spring-kafka` dans
son `pom.xml`, et c'est tout : rien à écrire dans son `application.yml`.

### Les événements

Dans `com.ecommerce.common.event`, un `record` par événement. Producteur et
consommateur utilisent **la même classe** : ne jamais en recopier une dans un
service.

| Classe | Topic | Propriété du nom de topic |
|---|---|---|
| `OrderPaidEvent` | `order.paid` | `app.kafka.topics.order-paid` |
| `PriceDroppedEvent`, `BackInStockEvent` (via `CatalogEvent`) | `catalogue.evenements` | `app.kafka.topics.catalog-events` |
| `SupportAdminReplyEvent` | `sav.reponse-admin` | `app.kafka.topics.support-admin-reply` |

Tous portent un `eventId` (UUID) et un `occurredAt`, remplis automatiquement
s'ils ne sont pas fournis. Un consommateur dont l'effet n'est pas idempotent
par nature, comme l'envoi d'un e-mail, retient les `eventId` traités : Kafka
garantit *au moins une* livraison, pas *exactement une*.

`eventId` ne protège que de la relivraison **du même message**. Deux
publications du même fait métier (deux webhooks Stripe pour un même paiement,
traités en parallèle) portent deux `eventId` différents : là, dédoublonner sur
une **clé métier**, comme `orderId` (c'est ce que fait le panier, #100).

```java
// Producteur
@Value("${app.kafka.topics.order-paid}")
private String orderPaidTopic;

OrderPaidEvent event = OrderPaidEvent.builder()
        .orderId(order.getId())
        .orderNumber(order.getOrderNumber())
        .userId(order.getUserId())
        .totalInclTax(order.getTotalInclTax())
        .build();
kafkaTemplate.send(orderPaidTopic, order.getOrderNumber(), event);

// Consommateur
@KafkaListener(topics = "${app.kafka.topics.order-paid}")
void onOrderPaid(OrderPaidEvent event) { ... }
```

La **clé** du message est l'identifiant métier (numéro de commande, id de
variante, id de ticket) : tous les événements d'un même objet tombent dans la
même partition et sont lus dans l'ordre.

`catalogue.evenements` transporte deux sortes d'événements. `CatalogEvent` est
une interface scellée : un seul listener les reçoit toutes, et un `switch`
exhaustif ne compile plus le jour où une troisième sorte est ajoutée.

```java
@KafkaListener(topics = "${app.kafka.topics.catalog-events}")
void onCatalogEvent(CatalogEvent event) {
    switch (event) {
        case PriceDroppedEvent drop -> alerts.priceDropped(drop);
        case BackInStockEvent restock -> alerts.backInStock(restock);
    }
}
```

**Faire évoluer un événement.** Ajouter un champ est sans risque : un
consommateur plus ancien l'ignore. Renommer ou supprimer un champ casse les
consommateurs déjà déployés : ajouter le nouveau champ, migrer les
consommateurs, puis seulement retirer l'ancien.

### La configuration commune

Fournie par `common-lib` avec la priorité la plus basse : une valeur posée
dans l'`application.yml` d'un service, une variable d'environnement ou une
propriété de test la remplace.

| Réglage | Valeur | Pourquoi |
|---|---|---|
| `bootstrap-servers` | `localhost:29092` | l'IDE ; en conteneur, `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` |
| sérialisation | JSON, classe dans les en-têtes | le consommateur retrouve le type exact, y compris derrière `CatalogEvent` |
| `acks` | `all` | un événement confirmé n'est pas perdu |
| `group-id` | `spring.application.name` | chaque service reçoit tous les événements d'un topic |
| `auto-offset-reset` | `earliest` | un nouveau service lit ce qui a été publié avant son premier démarrage |
| paquets autorisés | `com.ecommerce.common.event` | jamais `*` : sinon un message choisit la classe que le consommateur instancie |

### Rejeu et topic de rebut

Un listener qui lève une exception est **rejoué sur place**, avec une attente
qui double à chaque fois. Une fois les tentatives épuisées, le message part
dans `<topic>.dlt`, avec l'exception dans ses en-têtes (`kafka_dlt-exception-message`,
`kafka_dlt-original-topic`, ...), et le consommateur passe au suivant : un
message en échec ne bloque pas la partition.

> **À condition que le DLT existe.** L'auto-création étant coupée, publier
> dans un `.dlt` absent échoue : le message n'est jamais écarté, il est relivré
> sans fin et **sa partition est bloquée** (mesuré par la revue : 26 tentatives
> en 20 s, et le message suivant jamais reçu). Donc :
>
> - le Job de création des topics d'INFRA (mxrjup/e-commerce-INFRA#3) doit être
>   déployé **avant ou en même temps** que les services qui consomment ;
> - un topic renommé par `app.kafka.topics.*` doit être créé, **lui et son
>   `.dlt`**, dans `create-topics.sh` et dans INFRA : les scripts ne créent que
>   les noms par défaut.

| Propriété | Défaut |
|---|---|
| `app.kafka.retry.max-retries` | `3` (soit 4 tentatives) ; `0` : rebut dès le premier échec |
| `app.kafka.retry.initial-interval` | `1s` |
| `app.kafka.retry.multiplier` | `2` |
| `app.kafka.retry.max-interval` | `10s` |

Soit 1 s, 2 s puis 4 s d'attente : environ 7 secondes avant le rebut.

Un message **illisible** (JSON invalide, classe inconnue ou hors des paquets
autorisés) ne sera jamais lu correctement : il part dans le DLT **sans
rejeu**, avec ses octets d'origine intacts.

Pour qu'une exception métier parte directement au rebut sans attendre, le
service l'ajoute au gestionnaire commun :

```java
@Autowired
void doNotRetry(DefaultErrorHandler kafkaErrorHandler) {
    kafkaErrorHandler.addNotRetryableExceptions(InvalidOrderException.class);
}
```

Le rejeu est **bloquant** : pendant les quelques secondes d'attente, la
partition n'avance pas. C'est voulu : l'ordre des événements d'une même clé
est préservé, et aucun topic de rejeu supplémentaire n'est à créer et
surveiller. Si un traitement doit un jour attendre plusieurs minutes (un
service tiers indisponible), il faudra passer à `@RetryableTopic` pour ce
listener-là.

**Lire un DLT** pour comprendre un échec :

```bash
docker exec ecommerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order.paid.dlt --from-beginning \
  --property print.key=true --property print.headers=true
```

## Conventions

- **Git, commits, branches, PR, tags** : `CLAUDE.md` est la source unique. Rien
  n'est dupliqué ici.
- **Langue** : code, Javadoc et messages de commit en anglais ; documentation
  d'équipe (README, description de PR) en français.
- **Indentation et encodage** : imposés par `.editorconfig` (4 espaces en Java,
  2 en YAML/JSON/Markdown, UTF-8, LF).

## Pas encore en place

| Sujet | Issue |
|---|---|
| Correspondance UUID Keycloak → identifiant applicatif | `U01` #58 |
| Client confidentiel pour l'Admin API Keycloak | `U04` #91 |
| Dockerfile du front web | `D01` #87, après `T06` #11 |
| Manifests Kubernetes | `D04` #88 |
