#!/usr/bin/env bash
# Deploie une version du back-end sur le cluster, ou revient a une version
# precedente : c'est la meme operation.
#
#   KUBECONFIG=... .github/scripts/deploy.sh v1.2.0
#
# 1. Verifie que l'image de CHAQUE service existe pour cette version, avant de
#    toucher au cluster : jamais de deploiement a moitie.
# 2. Deploie chaque service sur le digest de son image (tag X.Y.Z@sha256:...),
#    pas sur un tag qui pourrait bouger.
# 3. Attend chaque rollout. Si un seul echoue, TOUS les services de ce
#    deploiement reviennent a leur version precedente (rollout undo), et le
#    script echoue.
# 4. Enregistre la version deployee dans le ConfigMap « ecommerce-release ».
#
# Variables :
#   KUBECONFIG       acces au cluster ; le namespace est celui du contexte
#   IMAGE_PREFIX     ghcr.io/mxrjup/e-commerce-back par defaut
#   SERVICES         services a deployer, separes par des espaces ; par defaut
#                    tous ceux du depot (image-modules.sh all)
#   ROLLOUT_TIMEOUT  attente maximale par service, 300s par defaut
set -euo pipefail

version="${1:-}"
if [[ ! "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]]; then
    echo "Usage : $0 vX.Y.Z   (recu : « ${version} »)" >&2
    exit 2
fi
tag="${version#v}"
prefix="${IMAGE_PREFIX:-ghcr.io/mxrjup/e-commerce-back}"
timeout="${ROLLOUT_TIMEOUT:-300s}"
cd "$(git rev-parse --show-toplevel 2>/dev/null || dirname "$0")"
if [[ -n "${SERVICES:-}" ]]; then
    read -r -a services <<< "$SERVICES"
else
    mapfile -t services < <(.github/scripts/image-modules.sh all | grep -o '"module":"[^"]*"' | cut -d'"' -f4)
fi

registry="${prefix%%/*}"
repository="${prefix#*/}"
scheme=https
[[ "$registry" == localhost:* || "$registry" == 127.0.0.1:* ]] && scheme=http

# Digest d'une image, par l'API du registre. GHCR exige un jeton, meme anonyme,
# pour un paquet public ; un registre local n'en demande pas.
digest_of() {
    local repo="$1" auth=()
    local token
    token=$(curl -fsS "$scheme://$registry/token?scope=repository:$repo:pull" 2>/dev/null \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' || true)
    [[ -n "$token" ]] && auth=(-H "Authorization: Bearer $token")
    curl -fsSI "${auth[@]}" \
        -H "Accept: application/vnd.oci.image.index.v1+json" \
        -H "Accept: application/vnd.docker.distribution.manifest.list.v2+json" \
        -H "Accept: application/vnd.oci.image.manifest.v1+json" \
        -H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
        "$scheme://$registry/v2/$repo/manifests/$tag" 2>/dev/null \
        | tr -d '\r' | sed -n 's/^[Dd]ocker-[Cc]ontent-[Dd]igest: *//p'
}

echo "Version $version ($tag), services : ${services[*]}"

declare -A images
for service in "${services[@]}"; do
    # Une image absente fait echouer curl : on veut le message ci-dessous, pas
    # une sortie silencieuse de « set -e ».
    digest=$(digest_of "$repository/$service") || digest=""
    if [[ -z "$digest" ]]; then
        echo "Image absente : $prefix/$service:$tag. Rien n'a ete deploye." >&2
        exit 1
    fi
    images[$service]="$prefix/$service:$tag@$digest"
    echo "  $service -> ${images[$service]}"
done

# Chaque service doit deja exister sur le cluster : ce script change une
# version, il ne cree pas de Deployment (c'est le role des manifests d'INFRA).
for service in "${services[@]}"; do
    kubectl get deployment "$service" >/dev/null
done

updated=()
rollback() {
    echo "Echec du deploiement de $version : retour a la version precedente." >&2
    for service in "${updated[@]}"; do
        kubectl rollout undo deployment/"$service" || true
    done
    for service in "${updated[@]}"; do
        kubectl rollout status deployment/"$service" --timeout="$timeout" || true
    done
    exit 1
}

for service in "${services[@]}"; do
    kubectl set image deployment/"$service" "$service=${images[$service]}" >/dev/null || rollback
    updated+=("$service")
    # Apres set image : Kubernetes recopie l'annotation sur le ReplicaSet
    # courant, donc sur le nouveau, et « kubectl rollout history » affiche la
    # version de chaque revision. Avant, elle ecraserait celle de l'ancienne.
    kubectl annotate deployment/"$service" --overwrite \
        kubernetes.io/change-cause="deploy $version" >/dev/null
done

for service in "${services[@]}"; do
    if ! kubectl rollout status deployment/"$service" --timeout="$timeout"; then
        rollback
    fi
done

kubectl create configmap ecommerce-release \
    --from-literal=version="$version" \
    --from-literal=deployed-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
echo "Version $version deployee."
