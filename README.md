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

**Les services applicatifs** sont déclarés derrière le profil `services` :
`docker compose --profile services up -d --build` construit leurs images et
démarre la pile complète. Pour travailler sur un service, il reste plus
pratique de lancer les dépendances seules et le service avec `./mvnw`
(ci-dessus). Voir la section « Images Docker » pour la construction.

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
| Correspondance UUID Keycloak → identifiant applicatif | `U01` #58 |
| Client confidentiel pour l'Admin API Keycloak | `U04` #91 |
| Création des topics Kafka et DTO d'événements | `K01` #85, `K02` #86 |
| Dockerfile du front web | `D01` #87, après `T06` #11 |
| CI GitHub Actions | `D02` #53 |
| Manifests Kubernetes | `D04` #88 |
