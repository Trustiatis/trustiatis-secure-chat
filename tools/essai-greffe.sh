#!/usr/bin/env bash
# Éprouve tools/greffer-upstream.sh contre un amont synthétique, sans récupérer les
# centaines de Mo de l'amont réel.
#
# Le script fabrique, dans un dossier temporaire :
#   - un faux dépôt d'amont, avec un tag, un .gitignore, et un fichier exécutable
#   - une copie de la couche Trustiatis de ce dépôt
# puis lance la greffe et contrôle le résultat.
#
# Usage : bash tools/essai-greffe.sh

set -euo pipefail

cd "$(dirname "$0")/.."
SOURCE="$(pwd)"
TRAVAIL="$(mktemp -d)"
trap 'rm -rf "$TRAVAIL"' EXIT
TAG="v0.0.1-essai"

echo "== dossier d'essai : $TRAVAIL"

# ---- faux amont
AMONT="$TRAVAIL/amont"
mkdir -p "$AMONT/src" "$AMONT/scripts"
cd "$AMONT"
git init -q -b master .
git config user.name "Amont Synthetique"
git config user.email "amont@essai.invalid"
printf 'coeur de l amont, ne doit pas changer\n' > src/Coeur.hs
printf '#!/bin/sh\necho amont\n' > scripts/construire.sh
chmod +x scripts/construire.sh
printf 'build/\ndist/\nexclusion-propre-a-l-amont/\n' > .gitignore
printf '# Amont\n' > README.md
git add -A
git commit -q -m "Amont synthetique"
git tag "$TAG"
git branch stable

# ---- copie de la couche Trustiatis
# On recopie l'ARBRE DE TRAVAIL, pas la branche committée : l'essai doit éprouver le
# script tel qu'il est sur le disque, y compris ses modifications non committées.
ESSAI="$TRAVAIL/essai"
mkdir -p "$ESSAI"
tar -C "$SOURCE" --exclude=.git -cf - . | tar -C "$ESSAI" -xf -
cd "$ESSAI"
git init -q -b main .
git config user.name "Essai"
git config user.email "essai@essai.invalid"
git add -A
git commit -q -m "Couche Trustiatis, copie de l arbre de travail"

echo "== lancement de la greffe"
TS_UPSTREAM_URL="$AMONT" TS_ESPACE_MINIMAL_MO=1 bash tools/greffer-upstream.sh "$TAG"

# ---- contrôles
echo
echo "== contrôles"
ECHECS=0
verifier() {
  if [ "$2" = "$3" ]; then
    printf '   OK    %s\n' "$1"
  else
    printf '   ECHEC %s : attendu [%s], obtenu [%s]\n' "$1" "$3" "$2"
    ECHECS=$((ECHECS + 1))
  fi
}

verifier "branche courante" "$(git rev-parse --abbrev-ref HEAD)" "develop"
verifier "branche de référence sur l'amont pur" \
  "$(git rev-parse "upstream-$TAG")" "$(git -C "$AMONT" rev-parse "$TAG")"
verifier "le coeur de l'amont est intact" \
  "$(git diff --name-only "upstream-$TAG" main -- src/ scripts/)" ""
verifier "les 6 branches de travail existent" \
  "$(git for-each-ref --format='%(refname:short)' 'refs/heads/feature/*' | wc -l)" "6"
verifier "greffer-upstream.sh reste exécutable" \
  "$(git ls-files -s tools/greffer-upstream.sh | awk '{print $1}')" "100755"
verifier "verifier-marque.py reste exécutable" \
  "$(git ls-files -s tools/verifier-marque.py | awk '{print $1}')" "100755"
# On contrôle sur une ligne que seul l'amont possède : les lignes communes aux deux
# .gitignore (build/, dist/) apparaissent deux fois, ce qui est sans conséquence et
# préserve le fichier de l'amont tel quel pour les diffs de report.
verifier "le .gitignore de l'amont est conservé" \
  "$(grep -c '^exclusion-propre-a-l-amont/$' .gitignore)" "1"
verifier "le .gitignore Trustiatis est ajouté" \
  "$(grep -c '^\*\.jks$' .gitignore)" "1"
verifier "le README de l'amont est remplacé par le nôtre" \
  "$(head -1 README.md | grep -c 'Trustiatis')" "1"
verifier "l'audit de Phase 1 est présent" \
  "$([ -f docs/INITIAL_AUDIT.md ] && echo oui || echo non)" "oui"
verifier "deux commits sur main" "$(git rev-list --count main)" "2"

echo
if [ "$ECHECS" -eq 0 ]; then
  echo "== essai concluant, tous les contrôles passent"
else
  echo "== essai en échec : $ECHECS contrôle(s)"
  exit 1
fi
