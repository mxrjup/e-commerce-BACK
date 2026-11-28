#!/usr/bin/env bash
# Liste, en tableau JSON, les services dont la CI construit l'image, avec
# leur port :
#
#   .github/scripts/image-modules.sh all                  # tous les services
#   .github/scripts/image-modules.sh origin/dev HEAD      # ceux touches
#   [{"module":"cart-service","port":"8083"}]
#
# Un service est un module executable : son pom.xml declare le
# spring-boot-maven-plugin (les librairies common-lib et common-test, non).
# Son port est le server.port de son application.yml ; il ne sert qu'au
# EXPOSE de l'image.
#
# « Touches » suit les regles de ci-modules.sh, plus deux fichiers qui ne
# concernent que les images : le Dockerfile et le .dockerignore.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [[ "${1:-}" == all ]]; then
    mapfile -t candidates < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)
else
    base="${1:-}"
    head="${2:-HEAD}"
    mapfile -t candidates < <(.github/scripts/ci-modules.sh "$base" "$head" | tr -d '[]"' | tr ',' '\n' | sed '/^$/d')
    if [[ -n "$base" && ! "$base" =~ ^0+$ ]] && git cat-file -e "${base}^{commit}" 2>/dev/null \
        && git diff --name-only "${base}...${head}" | grep -qxE 'Dockerfile|\.dockerignore|\.github/workflows/images\.yml|\.github/scripts/image-modules\.sh'; then
        echo "Changement des images : tous les services." >&2
        mapfile -t candidates < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)
    fi
fi

entries=()
for module in "${candidates[@]}"; do
    grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' "$module/pom.xml" 2>/dev/null || continue
    port=$(awk '/^server:/ {in_server = 1; next}
                in_server && /^[^ ]/ {exit}
                in_server && /port:/ {match($0, /[0-9]+/); print substr($0, RSTART, RLENGTH); exit}' \
        "$module/src/main/resources/application.yml")
    if [[ -z "$port" ]]; then
        echo "Pas de server.port dans $module/src/main/resources/application.yml" >&2
        exit 1
    fi
    entries+=("{\"module\":\"$module\",\"port\":\"$port\"}")
done

if ((${#entries[@]} == 0)); then
    echo "[]"
else
    printf '%s\n' "${entries[@]}" | paste -sd, - | sed 's/^/[/; s/$/]/'
fi
