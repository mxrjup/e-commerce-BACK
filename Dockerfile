# syntax=docker/dockerfile:1

# Image d'un service du monorepo. Le module se choisit au build :
#
#   docker build --build-arg MODULE=cart-service -t cart-service .
#
# Le contexte est la racine du depot, pas le dossier du module : la
# construction a besoin du POM parent et de common-lib. Les neuf services
# produisant le meme genre d'artefact (un jar Spring Boot executable), ils
# partagent ce fichier plutot que d'en dupliquer neuf copies qui divergeraient.
# Un service qui aurait besoin d'un runtime particulier pourra toujours avoir
# son propre Dockerfile.

# -----------------------------------------------------------------------------
# Etape 1 : construction
# -----------------------------------------------------------------------------
# Maven est fourni par l'image, epinglee sur la meme version que le wrapper du
# depot : une construction locale et une construction d'image utilisent donc le
# meme Maven, sans que l'image ait a le telecharger.
FROM maven:3.9.11-eclipse-temurin-21 AS build

ARG MODULE
WORKDIR /build

# Les POM seuls d'abord : tant qu'aucune dependance ne change, cette couche est
# reutilisee et le depot Maven n'est pas retelecharge.
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY api-gateway/pom.xml api-gateway/
COPY cart-service/pom.xml cart-service/
COPY order-service/pom.xml order-service/
COPY user-service/pom.xml user-service/
RUN test -n "$MODULE" || (echo "MODULE est obligatoire : --build-arg MODULE=<module>" >&2; exit 1)
RUN mvn -B -q -pl "$MODULE" -am dependency:go-offline -DskipTests

# Puis les sources : une modification de code ne refait que ce qui suit.
COPY common-lib/src common-lib/src
COPY ${MODULE}/src ${MODULE}/src
RUN mvn -B -q -pl "$MODULE" -am package -DskipTests \
    && cp "$MODULE"/target/"$MODULE"-*.jar app.jar

# Le jar est decoupe en couches par Spring Boot : les dependances, qui bougent
# rarement, sont separees du code applicatif. Les images des differents services
# partagent donc leurs couches, ce qui allege les envois vers GHCR (D03) et les
# telechargements dans le cluster (D04).
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# -----------------------------------------------------------------------------
# Etape 2 : execution
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG MODULE
ARG SERVER_PORT=8080

# Jamais root dans le conteneur.
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build --chown=spring:spring /build/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/application/ ./

USER spring
EXPOSE ${SERVER_PORT}

LABEL org.opencontainers.image.source="https://github.com/mxrjup/e-commerce-BACK"
LABEL org.opencontainers.image.title="${MODULE}"

# MaxRAMPercentage : la JVM se dimensionne sur la limite memoire du conteneur,
# pas sur la memoire de la machine hote.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
