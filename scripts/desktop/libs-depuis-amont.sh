#!/usr/bin/env bash
#
# Pose les bibliothèques du coeur, prises dans le paquet de bureau officiel de l'amont,
# là où la compilation Compose Desktop les attend.
#
# Décision D-2 : le coeur Haskell n'est jamais recompilé. Les scripts d'amont
# build-lib-{linux,mac,windows}.sh font l'inverse, ils lancent `cabal build` et réclament
# une chaîne GHC complète, plus un MinGW précis sur Windows. Ce script ci prend ce que
# l'amont a déjà compilé et publié pour le tag de tools/amont-courant.txt.
#
# Usage :
#   scripts/desktop/libs-depuis-amont.sh linux-x86_64
#   scripts/desktop/libs-depuis-amont.sh windows-x86_64
#   scripts/desktop/libs-depuis-amont.sh macos-aarch64
#   scripts/desktop/libs-depuis-amont.sh macos-x86_64
#
# La cible doit correspondre au système qui exécute le script : on n'extrait un .msi que
# sous Windows et un .dmg que sous macOS, faute d'outil portable pour les ouvrir ailleurs.
#
# Variables :
#   CACHE_AMONT   dossier où garder les paquets téléchargés (défaut : build/amont)
#   GARDER_CACHE  à 1, ne supprime pas le paquet après extraction
#
set -euo pipefail

cible="${1:-}"
if [ -z "$cible" ]; then
  echo "ERREUR : préciser la cible. Voir tools/paquets-amont.txt." >&2
  exit 2
fi

racine="$(cd "$(dirname "$0")/../.." && pwd)"
manifeste="$racine/tools/paquets-amont.txt"
fichier_tag="$racine/tools/amont-courant.txt"

for f in "$manifeste" "$fichier_tag"; do
  [ -f "$f" ] || { echo "ERREUR : fichier absent, $f" >&2; exit 2; }
done

# upstream-v7.1.2 -> v7.1.2. La base peut reculer (décision P-6), donc elle se lit ici et
# ne se déduit jamais du numéro de version le plus élevé.
tag="$(tr -d ' \t\r\n' < "$fichier_tag" | sed 's/^upstream-//')"
[ -n "$tag" ] || { echo "ERREUR : tools/amont-courant.txt est vide." >&2; exit 2; }

ligne="$(grep -v '^[[:space:]]*#' "$manifeste" | grep -v '^[[:space:]]*$' | awk -v c="$cible" '$1 == c' || true)"
if [ -z "$ligne" ]; then
  echo "ERREUR : cible inconnue, « $cible »." >&2
  echo "Cibles déclarées :" >&2
  grep -v '^[[:space:]]*#' "$manifeste" | grep -v '^[[:space:]]*$' | awk '{print "  " $1}' >&2
  exit 2
fi

read -r _ dossier lien actif attendu <<< "$ligne"

cache="${CACHE_AMONT:-$racine/apps/multiplatform/build/amont}"
mkdir -p "$cache"
paquet="$cache/$actif"
url="https://github.com/simplex-chat/simplex-chat/releases/download/$tag/$actif"

echo "amont   : $tag"
echo "actif   : $actif"

if [ ! -f "$paquet" ]; then
  echo "telech. : $url"
  curl --fail --location --silent --show-error --output "$paquet.partiel" "$url"
  mv "$paquet.partiel" "$paquet"
fi

# Une empreinte qui ne correspond pas n'est pas un incident de réseau : c'est soit un
# paquet republié sous le même nom, soit autre chose. On s'arrête, on ne « réessaie » pas.
if command -v sha256sum >/dev/null 2>&1; then
  obtenu="$(sha256sum "$paquet" | cut -d' ' -f1)"
else
  obtenu="$(shasum -a 256 "$paquet" | cut -d' ' -f1)"
fi
if [ "$obtenu" != "$attendu" ]; then
  echo "ERREUR : empreinte inattendue pour $actif." >&2
  echo "  attendue : $attendu" >&2
  echo "  obtenue  : $obtenu" >&2
  echo "Ne pas poursuivre. Relever l'empreinte publiée par l'API de GitHub et la comparer." >&2
  exit 1
fi
echo "sha256  : vérifiée"

travail="$cache/extraction-$cible"
rm -rf "$travail"
mkdir -p "$travail"

demonte=""
nettoyer() {
  [ -n "$demonte" ] && hdiutil detach "$demonte" -quiet 2>/dev/null || true
}
trap nettoyer EXIT

case "$cible" in
  linux-*)
    command -v dpkg-deb >/dev/null 2>&1 || { echo "ERREUR : dpkg-deb absent." >&2; exit 2; }
    dpkg-deb -x "$paquet" "$travail"
    arbre="$travail"
    ;;
  windows-*)
    # msiexec /a fait une « installation administrative » : il dépose l'arborescence
    # réelle au lieu des noms opaques du CAB. C'est la seule façon simple d'ouvrir un
    # .msi, et elle n'existe que sous Windows.
    command -v msiexec >/dev/null 2>&1 || { echo "ERREUR : msiexec absent, ce script doit tourner sous Windows pour cette cible." >&2; exit 2; }
    # msiexec n'ouvre un paquet que si son chemin est donné à la forme Windows, avec des
    # barres obliques inverses. Le « C:/... » que rend « pwd -W » lui arrache « This
    # installation package could not be opened » et le code 1619, vu par le shell comme 83.
    # C'est ce qui a mis en échec la première construction Windows, le 2026-11-16.
    en_windows() {
      if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
      else
        local brut
        brut="$(cd "$(dirname "$1")" && pwd -W)/$(basename "$1")"
        printf '%s' "${brut//\//\\}"
      fi
    }
    paquet_win="$(en_windows "$paquet")"
    cible_win="$(en_windows "$travail")"
    journal="$cache/msiexec-$cible.log"
    rm -f "$journal"
    # Le journal verbeux n'est pas du confort : une installation administrative en échec
    # n'affiche qu'une phrase, et la cause est dans le journal. Il est en UTF-16.
    if ! msiexec //a "$paquet_win" //qn "//l*v" "$(en_windows "$journal")" TARGETDIR="$cible_win"; then
      echo "ERREUR : msiexec n'a pas ouvert $actif." >&2
      echo "  paquet : $paquet_win" >&2
      echo "  cible  : $cible_win" >&2
      if [ -f "$journal" ]; then
        echo "--- fin du journal msiexec ---" >&2
        { iconv -f UTF-16LE -t UTF-8 "$journal" 2>/dev/null || cat "$journal"; } | tail -40 >&2
      fi
      exit 1
    fi
    arbre="$travail"
    ;;
  macos-*)
    command -v hdiutil >/dev/null 2>&1 || { echo "ERREUR : hdiutil absent, ce script doit tourner sous macOS pour cette cible." >&2; exit 2; }
    demonte="$travail/montage"
    mkdir -p "$demonte"
    hdiutil attach "$paquet" -mountpoint "$demonte" -nobrowse -readonly -quiet
    arbre="$demonte"
    ;;
  *)
    echo "ERREUR : cible non gérée, « $cible »." >&2
    exit 2
    ;;
esac

# On ne devine aucun chemin interne : on cherche le dossier « resources » qui contient la
# bibliothèque du coeur. jpackage le place sous app/resources, mais le préfixe diffère
# d'un système à l'autre et le nom de l'application peut changer.
source_res=""
while IFS= read -r d; do
  for n in libsimplex.so libsimplex.dll libsimplex.dylib; do
    if [ -f "$d/$n" ]; then source_res="$d"; break 2; fi
  done
done < <(find "$arbre" -type d -name resources 2>/dev/null)

if [ -z "$source_res" ]; then
  echo "ERREUR : aucun dossier « resources » contenant libsimplex n'a été trouvé dans $actif." >&2
  echo "L'amont a peut-être changé la disposition de ses paquets. Inspecter avant de corriger." >&2
  exit 1
fi
echo "source  : ${source_res#$arbre/}"

destination="$racine/apps/multiplatform/common/src/commonMain/cpp/desktop/libs/$dossier"
rm -rf "$destination"
mkdir -p "$destination"
# -L : sous macOS le contenu monté peut comporter des liens, on veut les fichiers.
# Deux exclusions, et elles ne sont pas de confort :
#  - vlc/ : ce sous arbre n'a pas sa source ici. prepare-vlc-{linux,mac,windows}.sh le
#    remplissent depuis la publication vlc de l'amont, après quoi le rpath des .so est
#    réécrit par patchelf. Reprendre celui du paquet ajoute 230 Mo de bibliothèques que
#    rien n'ira chercher à l'exécution.
#  - libapp-lib.* : c'est la pièce que CMake compile sur place. cmakeBuildAndCopy la dépose
#    dans ce même répertoire (desktop/build.gradle.kts). Y laisser celle de l'amont ferait
#    croire qu'on distribue un binaire qu'on n'a pas construit.
( cd "$source_res" && find . -mindepth 1 -maxdepth 1 \
    ! -name vlc ! -name 'libapp-lib.*' -print0 ) \
  | ( cd "$source_res" && xargs -0 -I{} cp -RL {} "$destination/" )
chmod -R u+w "$destination"

liens="$racine/apps/multiplatform/build/links"
mkdir -p "$liens"
rm -rf "${liens:?}/$lien"
if ln -s "../../common/src/commonMain/cpp/desktop/libs/$dossier" "$liens/$lien" 2>/dev/null; then
  :
else
  # Windows sans droit de création de lien symbolique : on recopie, c'est équivalent pour
  # la compilation, simplement plus lourd sur le disque.
  cp -R "$destination" "$liens/$lien"
fi

nettoyer
trap - EXIT
[ "${GARDER_CACHE:-0}" = "1" ] || rm -rf "$travail"

fichiers="$(find "$destination" -type f | wc -l | tr -d ' ')"
taille="$(du -sh "$destination" | cut -f1)"
echo "posé    : $fichiers fichiers, $taille, dans libs/$dossier"
echo "lien    : build/links/$lien"
