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
les crée au premier démarrage (`keycloak` et `ecommerce_cart` aujourd'hui). Ce
script ne s'exécute que sur un volume vide : après y avoir ajouté une base,
`docker compose down -v && docker compose up -d`.

**Les services applicatifs** sont déclarés derrière le profil `services` et
démarrent avec `docker compose --profile services up -d`. Ils ont besoin des
Dockerfiles de `D01` (#87), pas encore écrits : en attendant, lancer les
services avec `./mvnw` (ci-dessus) en gardant les dépendances dans Docker.

**Kafka** expose deux listeners : `kafka:9092` pour les conteneurs,
`localhost:29092` pour un service lancé depuis l'IDE. Les topics se nomment
avec des points comme séparateur (`catalogue.evenements`), jamais des
underscores : mélanger les deux expose à des collisions de noms de métriques
côté broker. La création explicite des topics arrive avec `K01` (#85) ; en
attendant le broker les crée à la demande.

**Keycloak** tourne en `start-dev` (HTTP en clair, pas de cache distribué),
persiste dans la base `keycloak` et importe au démarrage le realm décrit dans
`docker/keycloak/import/` — voir la section suivante.

## Authentification (Keycloak)

Le realm **`ecommerce`** est décrit par
`docker/keycloak/import/ecommerce-realm.json` et importé au démarrage de la
stack. Il se recrée entièrement avec
`docker compose down -v && docker compose up -d`.

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

## Modules

| Module | Domaine | Issues | Port | État |
|---|---|---|---|---|
| `common-lib` | Librairie partagée (contrat d'erreur HTTP) | `T01` | — | en place |
| `cart-service` | Panier | `P*` | 8083 | en place |
| `catalog-service` | Catalogue, stock, avis | `C*` | 8081 | à créer |
| `user-service` | Utilisateurs, adresses, collaborateurs | `U*` | 8082 | à créer |
| `order-service` | Commandes, paiement, livraison | `O*` | 8084 | à créer |
| `notification-service` | Notifications, alertes, emails | `N*` | 8085 | à créer |
| `support-service` | SAV, tickets | `S*` | 8086 | à créer |
| `mcp-service` | Serveur MCP | `M*` | 8087 | à créer |
| `api-gateway` | Routage, validation JWT, CORS | `T04` | 8080 | à créer |

Les ports sont réservés ici pour éviter les collisions en local et dans le
Docker Compose.

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
| Gateway (routes, JWT, CORS) | `T04` #84 |
| Client confidentiel pour l'Admin API Keycloak | `U04` #91 |
| Création des topics Kafka et DTO d'événements | `K01` #85, `K02` #86 |
| Dockerfiles | `D01` #87 |
| CI GitHub Actions | `D02` #53 |
| Manifests Kubernetes | `D04` #88 |
