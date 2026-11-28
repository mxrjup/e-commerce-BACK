
#!/usr/bin/env bash
# Tests automatises du filtre de modules GitHub Actions
# Chaque scenario cree un commit dans un depot Git temporaire.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/.github/scripts"
cp "$script_dir/ci-modules.sh" "$fixture/.github/scripts/ci-modules.sh"

cd "$fixture"
git init -q
git config core.autocrlf false
git config user.name "CI Test"
git config user.email "ci-test@example.invalid"

cat > pom.xml <<'POM'
<project>
  <modules>
    <module>common-lib</module>
    <module>api-gateway</module>
    <module>catalog-service</module>
    <module>cart-service</module>
    <module>order-service</module>
  </modules>
</project>
POM

git add .
git commit -qm "Fixture initiale"
base="$(git rev-parse HEAD)"
all='["common-lib","api-gateway","catalog-service","cart-service","order-service"]'
count=0

assert_modules() {
  local description="$1"
  local expected="$2"
  local compare_base="$3"
  local actual

  actual="$(bash .github/scripts/ci-modules.sh "$compare_base" HEAD 2>/dev/null)"

  if [[ "$actual" != "$expected" ]]; then
    printf 'ECHEC : %s\n  attendu : %s\n  obtenu  : %s\n' \
      "$description" "$expected" "$actual" >&2
    exit 1
  fi

  count=$((count + 1))
  printf 'OK %02d : %s\n' "$count" "$description"
}

check_changes() {
  local description="$1"
  local expected="$2"
  shift 2

  git reset --hard -q "$base"
  git clean -fdq

  local file
  for file in "$@"; do
    mkdir -p "$(dirname "$file")"
    printf '\n# fixture change\n' >> "$file"
  done

  git add -A
  git commit -qm "Scenario : $description"
  assert_modules "$description" "$expected" "$base"
}

assert_modules "aucun changement" '[]' "$base"

check_changes "README seul" '[]' README.md

check_changes "Compose et Dockerfile" '[]' \
  compose.yaml Dockerfile

check_changes "dossier docker/" '[]' docker/setup.txt

check_changes "cart-service seul" '["cart-service"]' \
  cart-service/src/App.java

check_changes "catalog-service seul" '["catalog-service"]' \
  catalog-service/pom.xml

check_changes "deux modules" '["cart-service","order-service"]' \
  cart-service/src/App.java order-service/src/App.java

check_changes "prefixe non identique" '[]' \
  cart-service-extra/src/App.java

check_changes "common-lib : tous" "$all" \
  common-lib/src/App.java

check_changes "POM racine : tous" "$all" pom.xml

check_changes "wrapper Maven : tous" "$all" mvnw

check_changes "configuration Maven : tous" "$all" \
  .mvn/wrapper/maven-wrapper.properties

check_changes "workflow CI : tous" "$all" \
  .github/workflows/ci.yml

check_changes "script du filtre : tous" "$all" \
  .github/scripts/ci-modules.sh

# Si la base est invalide, tous les modules doivent etre construits.
git reset --hard -q "$base"
git clean -fdq

assert_modules "base absente : tous" "$all" ""
assert_modules "base inexistante : tous" "$all" "commit-inconnu"
assert_modules "SHA nul : tous" "$all" \
  "0000000000000000000000000000000000000000"

printf '\nSucces : %d scenarios valides.\n' "$count"
