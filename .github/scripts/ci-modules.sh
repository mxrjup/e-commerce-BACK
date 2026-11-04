#!/usr/bin/env bash
# Liste, en tableau JSON, les modules Maven que la CI doit construire et tester
# pour les changements entre deux commits.
#
#   .github/scripts/ci-modules.sh <base> [<head>]
#   .github/scripts/ci-modules.sh origin/dev HEAD   # ["cart-service"]
#
# Regles :
# - un fichier sous <module>/ declenche ce module ;
# - common-lib et les fichiers de build communs (POM racine, wrapper Maven,
#   cette CI) declenchent tous les modules : ils en dependent tous ;
# - le reste (README, compose, docker/, Dockerfile...) ne declenche rien ;
# - sans base exploitable (premier push d'une branche, lancement manuel), on
#   construit tout plutot que de laisser passer un changement.
#
# Les modules sont lus dans les <modules> du pom.xml racine : un nouveau
# service est pris en compte sans modifier la CI.
set -euo pipefail

base="${1:-}"
head="${2:-HEAD}"

cd "$(git rev-parse --show-toplevel)"

mapfile -t modules < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)

to_json() {
    if (($# == 0)); then
        printf '[]\n'
        return
    fi
    printf '"%s"\n' "$@" | paste -sd, - | sed 's/^/[/; s/$/]/'
}

if [[ -z "$base" || "$base" =~ ^0+$ ]] || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
    echo "Pas de base exploitable : tous les modules." >&2
    to_json "${modules[@]}"
    exit 0
fi

# Trois points : les changements de la branche depuis son point de depart,
# pas ceux arrives entre-temps sur la base.
mapfile -t changed < <(git diff --name-only "${base}...${head}")

selected=()
for file in "${changed[@]}"; do
    case "$file" in
        pom.xml | mvnw | mvnw.cmd | .mvn/* | common-lib/* | \
            .github/workflows/ci.yml | .github/scripts/ci-modules.sh)
            echo "Changement commun ($file) : tous les modules." >&2
            to_json "${modules[@]}"
            exit 0
            ;;
    esac
    for module in "${modules[@]}"; do
        if [[ "$file" == "$module/"* ]]; then
            selected+=("$module")
        fi
    done
done

if ((${#selected[@]} == 0)); then
    echo "Aucun module touche." >&2
    to_json
    exit 0
fi

mapfile -t selected < <(printf '%s\n' "${selected[@]}" | sort -u)
echo "Modules touches : ${selected[*]}" >&2
to_json "${selected[@]}"
