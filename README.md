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

**Keycloak** tourne en `start-dev` (HTTP en clair, pas de cache distribué) et
persiste dans la base `keycloak`. Il importe au démarrage tout realm déposé
dans `docker/keycloak/import/` : c'est là que `T03` (#83) mettra son export
JSON.

## Modules

| Module | Domaine | Issues | Port | État |
|---|---|---|---|---|
| `common-lib` | Librairie partagée (contrat d'erreur HTTP) | `T01` | — | en place |
| `cart-service` | Panier | `P*` | 8083 | en place |
| `catalog-service` | Catalogue, stock, avis | `C*` | 8081 | à créer |
| `user-service` | Utilisateurs, adresses, collaborateurs | `U*` | 8082 | à créer |
| `order-service` | Commandes, paiement, livraison | `O*` | 8084 | en place |
| `notification-service` | Notifications, alertes, emails | `N*` | 8085 | à créer |
| `support-service` | SAV, tickets | `S*` | 8086 | à créer |
| `mcp-service` | Serveur MCP | `M*` | 8087 | à créer |
| `api-gateway` | Routage, validation JWT, CORS | `T04` | 8080 | en place |

Les ports sont réservés ici pour éviter les collisions en local et dans le
Docker Compose.

## Gateway : le point d'entrée de l'API

Tout le trafic entre par `api-gateway`, sur le port 8080. Les services ne sont
pas joignables depuis l'extérieur : les règles ci-dessous sont donc le seul
endroit où une route devient publique, authentifiée ou réservée au back-office.

### Routes et accès

| Chemin | Service | Accès |
|---|---|---|
| `/api/cart/**` | `cart-service` | **public** — le panier accepte les invités (`P01`) |
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
`http://localhost:8180/realms/ecommerce` et route vers `localhost:8083` et
`localhost:8084`. Dans Docker, les variables de `compose.yaml` remplacent ces
valeurs — en particulier les clés de signature, qui se récupèrent sur l'URL
interne de Keycloak alors que l'issuer reste l'URL frontale.

### CORS

Configuré une seule fois dans la gateway, jamais dans un service : les origines
viennent de `CORS_ALLOWED_ORIGINS` (back-office Vite et serveur Expo par
défaut), avec `Authorization`, `Content-Type` et `X-Session-Token` autorisés en
requête, `X-Session-Token` exposé en lecture au navigateur, et les
identifiants autorisés.

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
| Keycloak (realm, clients, rôles) | `T03` #83 |
| Correspondance UUID Keycloak → identifiant applicatif | `U01` #58 |
| Création des topics Kafka et DTO d'événements | `K01` #85, `K02` #86 |
| Dockerfiles | `D01` #87 |
| CI GitHub Actions | `D02` #53 |
| Manifests Kubernetes | `D04` #88 |
