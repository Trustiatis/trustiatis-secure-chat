#!/usr/bin/env bash
#
# Pousse les branches de ce dépôt vers le miroir GitHub, et rien d'autre.
#
# Pourquoi un script et non « git push --mirror » : --mirror emporte les étiquettes, dont
# celles de l'amont, et `build.yml` hérité de l'amont se déclenche sur « push » d'une
# étiquette « v* ». Une seule commande de trop lancerait la matrice complète de l'amont sur
# le compte du groupe. La liste est donc explicite.
#
# Prérequis, tous à la charge du Membre et décrits dans docs/MIROIR-GITHUB.md : le dépôt
# existe, les actions y sont désactivées, et la clé de déploiement est posée avec droit
# d'écriture.
#
set -euo pipefail

distant="${1:-github-trustiatis:Trustiatis/trustiatis-secure-chat.git}"

racine="$(cd "$(dirname "$0")/.." && pwd)"
cd "$racine"

# Les branches de référence de l'amont d'abord : les nôtres en descendent, et pousser le
# gros des objets une fois rend les poussées suivantes brèves.
branches=(
  upstream-v7.1.2
  upstream-v7.1.3
  upstream-v7.1.4
  main
  develop
  couche-trustiatis-initiale
  feature/android
  feature/branding
  feature/desktop
  feature/distribution
  feature/ios
  feature/server-configuration
)

manquantes=()
for b in "${branches[@]}"; do
  git show-ref --verify --quiet "refs/heads/$b" || manquantes+=("$b")
done
if [ ${#manquantes[@]} -gt 0 ]; then
  echo "ERREUR : branches absentes du dépôt local : ${manquantes[*]}" >&2
  exit 1
fi

echo "distant : $distant"
echo "branches: ${#branches[@]}"

# --no-tags est redondant avec une liste de refs explicite, et c'est voulu : si quelqu'un
# ajoute un jour « --follow-tags » ou une étiquette à la liste, la ligne s'y opposera.
refspecs=()
for b in "${branches[@]}"; do refspecs+=("refs/heads/$b:refs/heads/$b"); done
git push --no-tags "$distant" "${refspecs[@]}"

echo
echo "Poussé. Vérifier maintenant dans l'onglet Actions du miroir que les huit fichiers"
echo "hérités de l'amont sont désactivés, avant de réactiver les actions du dépôt."
