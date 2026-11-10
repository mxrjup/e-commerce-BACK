# Serveur MCP (M01)

`mcp-service` expose Spring AI en **Streamable HTTP** sur `/api/mcp`, port
interne 8087. L'URL cliente est `http://localhost:8080/api/mcp`, via la gateway.
Les méthodes POST, GET (flux SSE optionnel) et DELETE (fermeture de session)
passent par la même route, sans réécriture du chemin. L'ancien transport
HTTP+SSE sur `/sse` et le transport STDIO sont désactivés.

Le profil par défaut déclare la capacité `tools`, avec une liste vide. Le profil
`demo-stock`, activé dans Compose local, expose `get_product_stock` et le prompt
`product_stock` avec des données JSON de démonstration. Voir le
[guide du stock de démonstration](../mcp-service/README.md#demo-product-stock).
Aucun modèle ni clé LLM, base de données ou broker Kafka n'est nécessaire.

## Versions

La [structure du module](../mcp-service/README.md) indique où définir les
prompts, outils et ressources. Les textes des prompts se trouvent dans
`mcp-service/src/main/resources/prompts/` ; le modèle fourni n'est pas publié
automatiquement comme prompt MCP.

Java 21, Spring AI 1.1.8 et Spring Boot 3.5.16. Le module utilise son propre
parent Boot pour isoler ses dépendances, tout en restant dans le réacteur
Maven racine : les autres modules conservent Boot 3.3.4 et le train Spring Cloud existant.
Voir la [documentation du transport Spring AI 1.1](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-streamable-http-server-boot-starter-docs.html).

## Démarrage

```bash
docker compose --profile services up -d --build mcp-service api-gateway
```

Compose ne publie aucun port hôte pour MCP. Dans MCP Inspector, utiliser
`http://localhost:8080/api/mcp` en Streamable HTTP avec l'en-tête
`Authorization: Bearer <jeton-admin>`.
La gateway utilise `MCP_SERVICE_URI=http://mcp-service:8087`.
Le service ne vérifie pas lui-même les JWT : en déploiement, il doit rester
sur un réseau interne, derrière la gateway qui exige
un jeton Keycloak valide (`aud=ecommerce-api`) avec le rôle realm **admin**.
`client` et `collaborator` seuls n'ont pas accès au MCP.

Pour lancer le service depuis l'IDE, ou hors Docker :

```bash
./mvnw -pl mcp-service -am install -DskipTests
./mvnw -pl mcp-service spring-boot:run
# Dans un autre terminal, avec Keycloak démarré :
./mvnw -pl api-gateway -am install -DskipTests
./mvnw -pl api-gateway spring-boot:run
```

La cible par défaut de la gateway est `http://localhost:8087` ;
`MCP_SERVICE_URI` permet de la surcharger. En lancement natif, le port 8087
reste accessible directement : ne pas l'exposer sur un réseau public.

## Vérification du protocole

Avec `TOKEN` contenant un JWT admin obtenu via Keycloak :

```bash
curl -i -N http://localhost:8080/api/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'
```

Résultat attendu : HTTP 200, en-tête `Mcp-Session-Id` et résultat JSON-RPC
contenant `serverInfo.name=ecommerce-mcp`. Conserver cet identifiant pour les
appels suivants et envoyer `MCP-Protocol-Version: 2025-03-26` :

1. POST `notifications/initialized` sans `id` : HTTP 202.
2. POST `ping` avec un `id` : résultat vide.
3. POST `tools/list` avec un `id` : `get_product_stock` avec le profil
   `demo-stock`, ou `tools: []` sans ce profil.
4. DELETE avec `Mcp-Session-Id` : HTTP 200 ; la session est invalidée.

Sans JWT : HTTP 401. Avec un JWT valide sans rôle admin : HTTP 403.
La gateway conserve les en-têtes de session et de protocole, autorise leur
usage en CORS et expose `Mcp-Session-Id` aux navigateurs. Son timeout global
de 10 s est désactivé uniquement sur la route MCP pour permettre le streaming.

```bash
./mvnw -pl mcp-service,api-gateway -am verify
```

Les tests vérifient le vrai cycle HTTP du serveur, la santé, l'absence de
`/sse`, puis les accès, le proxy SSE, les en-têtes et CORS dans la gateway.

## Déploiement

Le Dockerfile commun et les matrices CI détectent le nouveau module. Dans
`e-commerce-INFRA`, ajouter un Deployment et un Service internes pour
`mcp-service:8087`, ainsi que `MCP_SERVICE_URI` sur la gateway, avant la prochaine
release (le script de déploiement exige un Deployment pour chaque module).
Utiliser `/actuator/health/liveness` et `/actuator/health/readiness` pour les
sondes. Aucun Ingress ni port public dédié au MCP.

Les sessions sont en mémoire : avec plusieurs réplicas MCP, prévoir une
affinité par session ; un redémarrage oblige le client à se réinitialiser.
