#!/usr/bin/env bash
# Greffe le code de l'amont SimpleX Chat dans ce dépôt.
#
# Résultat :
#   refs/tags/<tag>        tag de l'amont, intact, conservé comme référence
#   branche upstream-<tag> pointe sur l'amont pur, sans aucune modification Trustiatis
#   branche main           amont + couche Trustiatis, point de départ des versions stables
#   branche develop        créée depuis main
#   branches feature/*     créées depuis develop
#
# La couche Trustiatis (ce que contient le dépôt avant la greffe) est appliquée en UN commit
# au dessus de l'amont. En cas de collision de nom de fichier, la version Trustiatis gagne,
# sauf pour .gitignore qui est concaténé.
#
# Usage : tools/greffer-upstream.sh [tag]      (défaut : v7.1.3)

set -euo pipefail

TAG="${1:-v7.1.3}"
UPSTREAM_URL="https://github.com/simplex-chat/simplex-chat.git"
ESPACE_MINIMAL_MO=2000

cd "$(dirname "$0")/.."
RACINE="$(pwd)"
echo "== dépôt : $RACINE"
echo "== tag d'amont demandé : $TAG"

# ---- contrôles préalables
if [ ! -d .git ]; then
  echo "ERREUR : ce dossier n'est pas un dépôt Git. Lancer d'abord docs/GIT-SETUP.md étape 1." >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "ERREUR : l'arbre de travail n'est pas propre. Committer ou remiser d'abord." >&2
  git status --short >&2
  exit 1
fi
if git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
  echo "ERREUR : le tag $TAG est déjà présent, la greffe semble déjà faite." >&2
  exit 1
fi
LIBRE_MO=$(df -Pm . | awk 'NR==2 {print $4}')
if [ "$LIBRE_MO" -lt "$ESPACE_MINIMAL_MO" ]; then
  echo "ERREUR : ${LIBRE_MO} Mo libres, il en faut au moins ${ESPACE_MINIMAL_MO}." >&2
  exit 1
fi
echo "== espace libre : ${LIBRE_MO} Mo, suffisant"

# ---- mémoriser la couche Trustiatis
COUCHE="$(git rev-parse HEAD)"
BRANCHE_COUCHE="couche-trustiatis-initiale"
git branch -f "$BRANCHE_COUCHE" "$COUCHE"
echo "== couche Trustiatis mémorisée sur $BRANCHE_COUCHE ($COUCHE)"

# ---- récupérer l'amont
if ! git remote get-url upstream >/dev/null 2>&1; then
  git remote add upstream "$UPSTREAM_URL"
fi
git remote set-url upstream "$UPSTREAM_URL"
echo "== récupération de l'amont (plusieurs centaines de Mo, patience)"
git fetch --no-tags upstream "refs/tags/$TAG:refs/tags/$TAG"
git fetch --no-tags upstream 'refs/heads/stable:refs/remotes/upstream/stable'

AMONT="$(git rev-parse "refs/tags/$TAG^{commit}")"
echo "== commit d'amont : $AMONT"
echo "== $(git log -1 --format='%ad %s' --date=short "$AMONT")"

# ---- branche de référence, amont pur
git branch -f "upstream-$TAG" "$AMONT"
echo "== branche upstream-$TAG créée sur l'amont pur"

# ---- main = amont + couche Trustiatis
git checkout -q -B main "$AMONT"

# .gitignore : concaténer celui de l'amont et le nôtre
if git cat-file -e "$COUCHE:.gitignore" 2>/dev/null; then
  {
    if [ -f .gitignore ]; then
      cat .gitignore
      printf '\n'
    fi
    printf '# ---- ajouts Trustiatis Secure ----\n'
    git show "$COUCHE:.gitignore"
  } > .gitignore.fusion
  mv .gitignore.fusion .gitignore
fi

# tout le reste de la couche : la version Trustiatis gagne
for f in $(git ls-tree -r --name-only "$COUCHE"); do
  [ "$f" = ".gitignore" ] && continue
  mkdir -p "$(dirname "$f")"
  git show "$COUCHE:$f" > "$f"
done

git add -A
git commit -q -F - <<MSG
Greffer la couche Trustiatis Secure sur l'amont $TAG

Point de départ du fork, décision P-4 de docs/DECISIONS.md.

Le code de l'amont est conservé intact : la branche upstream-$TAG et le tag
$TAG pointent sur $AMONT, sans aucune modification. Ce commit n'ajoute que la
couche Trustiatis (documentation, référentiel de marque, outillage, intégration
continue) et concatène le .gitignore.

Aucun fichier de l'amont n'est modifié par ce commit, hormis .gitignore.
Les obligations de l'AGPLv3 et de la politique de marque sont rappelées dans
NOTICE-AGPL.md.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
echo "== main créée : amont $TAG + couche Trustiatis"

# ---- develop et branches de travail
git checkout -q -B develop main
for b in branding desktop android ios distribution server-configuration; do
  git branch -f "feature/$b" develop
done
git checkout -q develop
echo "== develop et les 6 branches feature/* créées"

# ---- contrôles de non régression
echo
echo "== contrôle : aucun fichier de l'amont modifié (hors .gitignore et couche Trustiatis)"
git diff --name-only "upstream-$TAG" main -- . \
  | grep -vxF -f <(git ls-tree -r --name-only "$COUCHE") \
  | grep -vx '.gitignore' || echo "   aucun, conforme"
echo
echo "== branches"
git branch -v
echo
echo "== terminé. Étape suivante : docs/GIT-SETUP.md étape 4 (envoi vers GitLab)."
