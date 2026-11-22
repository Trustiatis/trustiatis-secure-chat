#!/usr/bin/env bash
#
# Fabrique le paquet Debian de Trustiatis Secure, et verifie ce qu'il contient.
#
# Pourquoi ce script existe, et n'est pas celui de l'amont. L'amont a
# scripts/desktop/make-deb-linux.sh, qui fait la bonne chose en deux temps : il empaquette,
# puis il repasse derriere jpackage pour corriger l'entree de bureau et retirer une
# bibliotheque en double. On ne peut pas l'employer tel quel, pour deux raisons :
#
#  - il commence par « scripts/desktop/build-lib-linux.sh », qui recompile le coeur
#    Haskell. La decision D-2 l'interdit : les bibliotheques viennent du paquet publie par
#    l'amont, par scripts/desktop/libs-depuis-amont.sh ;
#  - toutes ses recherches de fichiers portent le nom de l'amont : « simplex*.deb »,
#    « /opt/*imple*/ », « simplex.cfg », et il renomme en « simplex_<arch>.deb ». Aucun de
#    ces motifs ne rencontre « trustiatis-secure ». Lance tel quel, il ne trouve rien et
#    ne corrige rien.
#
# C'est ce qui est arrive au paquet 7.1.2-ts1 : il est sorti d'un « gradlew packageDeb »
# nu, donc sans aucune des corrections d'apres empaquetage. Trois defauts en ont decoule,
# et le premier a ete rapporte par le Membre :
#
#  1. l'arbre VLC livre n'avait pas « libvlccore.so.9 », que « libvlc.so » reclame : les
#     messages passaient, les appels faisaient remonter un NoClassDefFoundError ;
#  2. l'entree de bureau sortait avec « MimeType= » vide et un « Exec= » sans « %u », donc
#     les liens « simplex: » et « simplexchat: » n'etaient pas enregistres. La decision
#     D-3 dit de garder ces schemas : sans cette ligne, elle n'est pas tenue sous Linux ;
#  3. la bibliotheque skiko restait en double, une fois dans un .jar et une fois extraite.
#
# Les etapes 2 et 3 sont celles de make-deb-linux.sh, transposees a notre nom de paquet.
# A chaque report d'une version de l'amont, relire make-deb-linux.sh et reporter ici ce
# qui a change : ce fichier le double volontairement, il ne le remplace pas.
#
# Usage :
#   scripts/desktop/paquet-deb-trustiatis.sh
#
# Variables :
#   ASSETS_DIR   passe a Gradle comme -Psimplex.assets.dir, comme chez l'amont
#
set -euo pipefail

racine="$(cd "$(dirname "$0")/../.." && pwd)"
arch="$(uname -m)"
libs="$racine/apps/multiplatform/common/src/commonMain/cpp/desktop/libs/linux-${arch}"

# --- Avant d'empaqueter : l'arbre doit etre complet. -------------------------------------
# L'ordre compte, et il ne se devine pas : libs-depuis-amont.sh commence par un « rm -rf »
# sur ce repertoire, donc il efface vlc/ ; et prepare-vlc-linux.sh rend la main en silence,
# avec un code 0, si le repertoire vlc existe deja. Lances dans le mauvais ordre, les deux
# scripts reussissent et le paquet sort muet sur les appels.
if [ ! -e "$libs/libsimplex.so" ]; then
  echo "ERREUR : $libs/libsimplex.so absent." >&2
  echo "Lancer d'abord : scripts/desktop/libs-depuis-amont.sh linux-${arch}" >&2
  echo "puis seulement  : scripts/desktop/prepare-vlc-linux.sh" >&2
  exit 1
fi
"$racine/scripts/desktop/verifier-vlc-linux.sh" "$libs/vlc"

# --- Empaquetage. -----------------------------------------------------------------------
cd "$racine/apps/multiplatform"
if [ -n "${ASSETS_DIR:-}" ]; then
  set -- -Psimplex.assets.dir="$ASSETS_DIR"
else
  set --
fi
./gradlew --no-daemon --console=plain "$@" packageDeb

brut="$(find release/main/deb -maxdepth 1 -name '*.deb' | head -1)"
[ -n "$brut" ] || { echo "ERREUR : packageDeb n'a produit aucun .deb." >&2; exit 1; }
echo "Paquet brut : $brut"

# --- Apres empaquetage : les corrections de l'amont, a notre nom. -----------------------
# Date fixee, comme chez l'amont : sans elle, l'extraction du .jar skiko horodate au
# moment de la construction et le paquet n'est pas reproductible.
export SOURCE_DATE_EPOCH=1704067200

rm -rf extracted
dpkg-deb -R "$brut" ./extracted

racine_app="$(find ./extracted/opt -maxdepth 1 -mindepth 1 -type d | head -1)"
[ -n "$racine_app" ] || { echo "ERREUR : /opt introuvable dans le paquet." >&2; exit 1; }

# 3. skiko en double : une fois dans le .jar d'execution, une fois extraite a cote.
rm -f "$racine_app"/lib/app/*skiko-awt-runtime-linux*
cfg="$(find "$racine_app"/lib/app -maxdepth 1 -name '*.cfg' | head -1)"
[ -n "$cfg" ] || { echo "ERREUR : fichier .cfg de l'application introuvable." >&2; exit 1; }
sed -i -e '/skiko-awt-runtime-linux/d' "$cfg"

# Suffixe de distribution sur la version, comme chez l'amont : deux paquets construits sur
# deux Ubuntu differentes n'ont pas la meme glibc et ne doivent pas porter la meme version.
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  [ -n "${VERSION_CODENAME:-}" ] && sed -i "/^Version/ s/\$/~$VERSION_CODENAME/" ./extracted/DEBIAN/control
fi

# 2. L'entree de bureau. jpackage n'y met ni URL ni type MIME : sans ces deux
# substitutions, un lien d'invitation cliqué n'ouvre rien.
ligne_mime='MimeType=x-scheme-handler/simplexchat;x-scheme-handler/simplex;'  # marque-ok: reseau, decision D-3

# La classe X de la fenetre. Elle vient du nom de la classe principale, telle que la JVM
# la donne a X11 : « chat.simplex.desktop.MainKt » devient « chat-simplex-desktop-MainKt ».
# Elle est verifiee plus bas contre le .cfg du paquet, pour qu'un report d'amont qui
# deplacerait la classe principale fasse echouer la construction au lieu de livrer une
# entree de bureau qui ne designe plus aucune fenetre.
classe_fenetre='chat-simplex-desktop-MainKt'
classe_attendue="$(sed -n 's|^app.mainclass=\(.*\)|\1|p' "$cfg" | tr '.' '-')"
if [ -n "$classe_attendue" ] && [ "$classe_attendue" != "$classe_fenetre" ]; then
  echo "ERREUR : la classe principale du paquet est « $classe_attendue »," >&2
  echo "         l'entree de bureau annonce « $classe_fenetre »." >&2
  echo "         Reporter la nouvelle valeur dans ce script." >&2
  exit 1
fi

entree="$(find "$racine_app"/lib -maxdepth 1 -name '*.desktop' | head -1)"
[ -n "$entree" ] || { echo "ERREUR : entree de bureau introuvable." >&2; exit 1; }
sed -i -e '/%[uU]/!s|^Exec=.*|& %u|' -e "s|^MimeType=.*|$ligne_mime|" "$entree"

# Le nom affiche. jpackage y met le nom du paquet, « trustiatis-secure », et c'est ce que
# le lanceur montre sous l'icone.
sed -i -e 's|^Name=.*|Name=Trustiatis Secure|' "$entree"

# StartupWMClass : sans elle, le bureau ne rattache pas la fenetre ouverte a l'icone du
# lanceur. Il croit l'application fermee, lance un second processus, qui trouve le verrou
# mono-instance, signale le premier et se termine aussitot. L'utilisateur voit l'icone
# clignoter et rien d'autre, la fenetre restant derriere les autres. C'est le defaut que
# le Membre a rapporte sur 7.1.2-ts1 puis sur 7.1.4-ts1 : il n'est pas dans le code de
# l'application, qui demine, remonte et demande l'activation X11 comme il faut, mais dans
# cette entree de bureau, que jpackage ecrit sans cette ligne.
grep -q '^StartupWMClass=' "$entree" || printf 'StartupWMClass=%s\n' "$classe_fenetre" >> "$entree"
grep -q '^SingleMainWindow=' "$entree" || printf 'SingleMainWindow=true\n' >> "$entree"
grep -q '^StartupNotify=' "$entree" || printf 'StartupNotify=true\n' >> "$entree"

grep -qxF "$ligne_mime" "$entree" || {
  echo "ERREUR : l'entree de bureau n'enregistre pas simplex: ni simplexchat:." >&2; exit 1; }
grep -q '^Exec=.*%u' "$entree" || {
  echo "ERREUR : l'entree de bureau ne recoit aucune URL." >&2; exit 1; }
grep -qxF "StartupWMClass=$classe_fenetre" "$entree" || {
  echo "ERREUR : l'entree de bureau ne designe pas la classe de la fenetre." >&2; exit 1; }

find ./extracted/ -exec touch -d "@$SOURCE_DATE_EPOCH" {} +

final="release/main/deb/trustiatis-secure_${arch}.deb"
rm -f "$final"
dpkg-deb --build --root-owner-group --uniform-compression ./extracted "$final"
command -v strip-nondeterminism >/dev/null 2>&1 && strip-nondeterminism "$final"

# Le paquet brut de jpackage s'en va. Il n'a aucune des corrections ci dessus, en
# particulier pas l'enregistrement des liens « simplex: », et le laisser a cote du
# paquet fini, dans le meme repertoire que l'artefact ramasse, reviendrait a livrer
# deux paquets dont un seul fonctionne.
rm -f "$brut"

# --- Verification du paquet fini, sans l'installer. -------------------------------------
echo
echo "Verification du paquet fini :"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
dpkg-deb --fsys-tarfile "$final" > "$tmp/fs.tar"
tar -xf "$tmp/fs.tar" -C "$tmp" 2>/dev/null || true
vlc_livre="$(find "$tmp/opt" -type d -name vlc -path '*resources*' | head -1)"
[ -n "$vlc_livre" ] || { echo "ERREUR : aucun arbre VLC dans le paquet fini." >&2; exit 1; }
"$racine/scripts/desktop/verifier-vlc-linux.sh" "$vlc_livre"
if find "$tmp/opt" -name '*skiko-awt-runtime-linux*' | grep -q .; then
  echo "ERREUR : skiko est encore en double dans le paquet fini." >&2; exit 1
fi
# -type f, et ce n'est pas une precaution de style : le JDK embarque un repertoire
# « lib/runtime/legal/java.desktop », que « -name '*.desktop' » attrape. grep s'en
# plaignait a chaque construction (« Is a directory »), et surtout, ce controle ne
# verifiait rien : il affichait, sans jamais echouer. Si l'entree de bureau avait
# disparu, il n'aurait affiche que le message d'erreur de grep, et le paquet serait
# sorti sans gestionnaire de liens d'invitation, ce qui est exactement le defaut
# numero 2 de 7.1.2-ts1 que ce script existe pour fermer.
entree_livree="$(find "$tmp/opt" -type f -name '*.desktop' | head -1)"
[ -n "$entree_livree" ] || { echo "ERREUR : aucune entree de bureau dans le paquet fini." >&2; exit 1; }
echo "Entree de bureau du paquet fini :"
grep -E '^(Name|Exec|MimeType|StartupWMClass)=' "$entree_livree"
grep -q '^Exec=.*%u' "$entree_livree" || {
  echo "ERREUR : l'entree de bureau du paquet fini ne recoit aucune URL." >&2; exit 1; }
grep -q '^MimeType=.*x-scheme-handler/simplex;' "$entree_livree" || {  # marque-ok: reseau, decision D-3
  echo "ERREUR : l'entree de bureau du paquet fini n'enregistre pas les liens." >&2; exit 1; }
grep -qxF 'StartupWMClass=chat-simplex-desktop-MainKt' "$entree_livree" || {
  echo "ERREUR : l'entree de bureau du paquet fini ne designe pas la classe de la fenetre." >&2; exit 1; }
grep -qxF 'Name=Trustiatis Secure' "$entree_livree" || {
  echo "ERREUR : l'entree de bureau du paquet fini n'affiche pas le nom du produit." >&2; exit 1; }

# Le fichier d'empreintes, sous le nom que la chaine hebergee lui donne. Sans lui, le
# paquet construit ici ne peut pas etre range par tools/nommer-livraison.py, qui exige
# l'empreinte ecrite par la machine de construction : le chemin local et le chemin
# heberge produisaient deux choses differentes, et seul le second etait livrable.
# C'est la meme commande que l'etape « Empreintes » de .github/workflows/paquets-bureau.yml.
# L'arbre decompresse a servi, il pese 864 Mo, et le script le recreait a chaque fois
# sans jamais le reprendre. Laisse en place, il remplit le disque du poste et encombre
# « git status » d'un repertoire non suivi.
rm -rf extracted

empreintes="$racine/empreintes-linux-${arch}.txt"
( cd "$(dirname "$final")" && sha256sum "$(basename "$final")" ) > "$empreintes"

echo
ls -l "$final"
cat "$empreintes"
echo "empreintes : ${empreintes#$racine/}"
