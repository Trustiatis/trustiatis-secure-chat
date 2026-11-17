#!/usr/bin/env bash
#
# Pose les bibliothèques du coeur pour Android, extraites des APK publiés par l'amont.
#
# Pourquoi ce script existe. Le pendant pour le bureau, scripts/desktop/libs-depuis-amont.sh,
# existait depuis le début ; pour Android, l'extraction se faisait À LA MAIN. Rien ne
# rafraîchissait donc ces fichiers au report d'une version, et rien ne le signalait : le
# 17 novembre 2026, le poste portait encore les .so de la v7.1.2 alors que la base était
# passée à la v7.1.4. Une compilation signée aurait produit des APK « 7.1.4-ts1 » avec le
# coeur de la version précédente, sans un mot.
#
# La décision D-2 interdit de recompiler le coeur. download-libs.sh de l'amont veut une URL
# Hydra qui n'est pas publique, et les .so ne sont pas des actifs de version. Ils sont dans
# les APK officiels, qui le sont : les extraire de l'APK de la version greffée garantit
# qu'ils viennent de la compilation de l'amont pour ce commit précis.
#
# libapp-lib.so n'est PAS repris de l'APK : c'est la pièce que CMake compile sur place, et
# c'est précisément ce que la compilation doit éprouver.
#
# Usage :
#   scripts/android/libs-depuis-amont.sh              # les deux architectures
#   scripts/android/libs-depuis-amont.sh arm64-v8a    # une seule
#
# Variables :
#   CACHE_AMONT   dossier où garder les APK téléchargés (défaut : build/amont).
#                 Ils y sont rangés dans un sous dossier par tag, pour la même raison que
#                 côté bureau : l'amont ne versionne pas ses noms de fichiers.
#
set -euo pipefail

racine="$(cd "$(dirname "$0")/../.." && pwd)"
manifeste="$racine/tools/apk-amont.txt"
fichier_tag="$racine/tools/amont-courant.txt"

for f in "$manifeste" "$fichier_tag"; do
  [ -r "$f" ] || { echo "ERREUR : fichier introuvable, $f" >&2; exit 2; }
done

tag="$(tr -d ' \t\r\n' < "$fichier_tag" | sed 's/^upstream-//')"
[ -n "$tag" ] || { echo "ERREUR : tools/amont-courant.txt est vide." >&2; exit 2; }

cibles=()
if [ $# -gt 0 ]; then
  cibles=("$@")
else
  while read -r c _; do cibles+=("$c"); done < <(
    grep -v '^[[:space:]]*#' "$manifeste" | grep -v '^[[:space:]]*$'
  )
fi

cache="${CACHE_AMONT:-$racine/apps/multiplatform/build/amont}/$tag"
mkdir -p "$cache"

echo "amont   : $tag"

for cible in "${cibles[@]}"; do
  ligne="$(grep -v '^[[:space:]]*#' "$manifeste" | grep -v '^[[:space:]]*$' \
    | awk -v c="$cible" '$1 == c' || true)"
  if [ -z "$ligne" ]; then
    echo "ERREUR : cible inconnue, « $cible ». Voir tools/apk-amont.txt." >&2
    exit 2
  fi
  read -r _ abi actif attendu <<< "$ligne"

  paquet="$cache/$actif"
  url="https://github.com/simplex-chat/simplex-chat/releases/download/$tag/$actif"

  echo
  echo "cible   : $cible ($abi)"
  echo "actif   : $actif"
  if [ ! -f "$paquet" ]; then
    echo "telech. : $url"
    curl --fail --location --silent --show-error --output "$paquet.partiel" "$url"
    mv "$paquet.partiel" "$paquet"
  fi

  # Une empreinte qui ne correspond pas n'est pas un incident de réseau. On s'arrête.
  obtenu="$(sha256sum "$paquet" | cut -d' ' -f1)"
  if [ "$obtenu" != "$attendu" ]; then
    echo "ERREUR : empreinte inattendue pour $actif." >&2
    echo "  attendue : $attendu" >&2
    echo "  obtenue  : $obtenu" >&2
    echo "Si le tag vient de changer, ce sont les empreintes de tools/apk-amont.txt qu'il" >&2
    echo "faut relever à nouveau, la commande est en tête de ce fichier." >&2
    exit 1
  fi
  echo "sha256  : vérifiée"

  destination="$racine/apps/multiplatform/common/src/commonMain/cpp/android/libs/$abi"
  travail="$cache/extraction-$abi"
  rm -rf "$travail"
  mkdir -p "$travail"
  # Les deux seules bibliothèques reprises, nommées en clair : un « lib/$abi/* » emporterait
  # aussi libapp-lib.so, celle que CMake doit compiler ici.
  unzip -q -o "$paquet" "lib/$abi/libsimplex.so" "lib/$abi/libsupport.so" -d "$travail"
  for n in libsimplex.so libsupport.so; do
    [ -f "$travail/lib/$abi/$n" ] || {
      echo "ERREUR : $n absente de $actif. L'amont a peut-être changé la disposition de" >&2
      echo "ses APK. Inspecter avant de corriger." >&2
      exit 1; }
  done

  mkdir -p "$destination"
  for n in libsimplex.so libsupport.so; do
    mv -f "$travail/lib/$abi/$n" "$destination/$n"
  done
  rm -rf "$travail"

  echo "posé    : $(cd "$destination" && ls -l libsimplex.so libsupport.so | awk '{printf "%s (%s o) ", $9, $5}')"
  echo "          dans cpp/android/libs/$abi"
done

echo
echo "Fait. libapp-lib.so n'est pas reprise : CMake la compile pendant la construction."
