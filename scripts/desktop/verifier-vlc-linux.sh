#!/usr/bin/env bash
#
# Refuse un arbre VLC dont les noms de bibliothèques ne se résolvent pas entre eux.
#
# Pourquoi ce script existe. Le paquet 7.1.2-ts1 pour Linux est sorti avec un arbre VLC
# où « libvlccore.so » était présent et « libvlccore.so.9 » absent. Or « libvlc.so »
# porte NEEDED libvlccore.so.9 et RUNPATH $ORIGIN : à la première tentative d'appel,
# l'éditeur de liens dynamique ne trouvait rien et la JVM remontait un
# NoClassDefFoundError. Les messages passaient, les appels non.
#
# C'est la signature d'une copie qui a suivi les liens symboliques : les trois noms
# libvlccore.so, libvlccore.so.9 et libvlccore.so.9.0.1 s'effondrent sur le seul nom du
# lien, et la version disparaît. Rien dans la chaîne ne s'en apercevait, parce que la
# construction réussit : le manque ne se voit qu'à l'exécution, et seulement sur appel.
#
# Deux pièges de la chaîne y conduisent, et aucun des deux ne fait de bruit :
#   - libs-depuis-amont.sh commence par « rm -rf » sur le répertoire de destination, donc
#     il efface aussi vlc/. Il doit passer AVANT prepare-vlc-linux.sh, jamais après.
#   - prepare-vlc-linux.sh commence par « mkdir $vlc_dir || exit 0 ». Si le répertoire
#     existe déjà, même incomplet, le script rend la main avec un code 0 sans rien faire,
#     et sans appeler patchelf.
#
# Ce script est à nous. Il ne modifie rien ; il constate, et il échoue bruyamment.
#
# Usage :
#   scripts/desktop/verifier-vlc-linux.sh [chemin du répertoire vlc]
#
set -euo pipefail

racine="$(cd "$(dirname "$0")/../.." && pwd)"
arch="$(uname -m)"
vlc="${1:-$racine/apps/multiplatform/common/src/commonMain/cpp/desktop/libs/linux-${arch}/vlc}"

if [ ! -d "$vlc" ]; then
  echo "ERREUR : arbre VLC absent : $vlc" >&2
  echo "Lancer scripts/desktop/prepare-vlc-linux.sh, et APRES libs-depuis-amont.sh." >&2
  exit 1
fi

lecteur=""
for c in readelf eu-readelf; do
  command -v "$c" >/dev/null 2>&1 && { lecteur="$c"; break; }
done
if [ -z "$lecteur" ]; then
  echo "ERREUR : ni readelf ni eu-readelf. Sans lecteur ELF, rien n'est verifiable," >&2
  echo "et un garde-fou qui passe en silence est pire que pas de garde-fou." >&2
  exit 2
fi

# Les deux noms dont l'absence a fait sortir un paquet muet. Ils sont nommés en clair :
# c'est le cas qui s'est produit, et il doit être vérifié pour lui même.
for indispensable in libvlc.so libvlccore.so.9; do
  if [ ! -e "$vlc/$indispensable" ]; then
    echo "ERREUR : $indispensable manque dans $vlc" >&2
    echo "C'est exactement le defaut du paquet 7.1.2-ts1 : les appels echouaient a" >&2
    echo "l'execution alors que la construction avait reussi." >&2
    exit 1
  fi
done

# Noms réellement présents, liens symboliques compris : c'est ce que voit l'éditeur de
# liens dynamique, qui ouvre un nom et non un contenu.
noms="$(cd "$vlc" && find . -maxdepth 1 \( -type f -o -type l \) -printf '%f\n' | sort -u)"
# Racine d'un nom de bibliothèque : « libvlccore.so.9.0.1 » donne « libvlccore ».
racines_presentes="$(printf '%s\n' "$noms" | sed 's/\.so.*$//' | sort -u)"

manques=0
while IFS= read -r elf; do
  case "$("$lecteur" -h "$elf" 2>/dev/null | head -1)" in
    *ELF*) ;;
    *) continue ;;
  esac
  while IFS= read -r besoin; do
    [ -z "$besoin" ] && continue
    printf '%s\n' "$noms" | grep -qxF "$besoin" && continue
    # Nom absent. S'il porte la racine d'une bibliothèque que l'arbre fournit par
    # ailleurs, c'est une version perdue, donc notre defaut. Sinon c'est une
    # bibliotheque du systeme, que l'hote fournit, et ce n'est pas notre affaire.
    r="$(printf '%s' "$besoin" | sed 's/\.so.*$//')"
    if printf '%s\n' "$racines_presentes" | grep -qxF "$r"; then
      echo "ERREUR : $(basename "$elf") reclame $besoin, absent de l'arbre," >&2
      echo "        alors que l'arbre fournit $r sous un autre nom. Version perdue :" >&2
      echo "        la copie a suivi les liens symboliques au lieu de les garder." >&2
      manques=$((manques + 1))
    fi
  done < <("$lecteur" -d "$elf" 2>/dev/null \
            | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p')
done < <(find "$vlc" -maxdepth 1 -type f -name '*.so*')

if [ "$manques" -gt 0 ]; then
  echo >&2
  echo "$manques dependance(s) interne(s) non resolue(s). Le paquet sortirait muet sur" >&2
  echo "les appels. Refaire l'arbre : effacer $vlc, puis prepare-vlc-linux.sh." >&2
  exit 1
fi

nb="$(find "$vlc" -type f | wc -l)"
echo "Arbre VLC coherent : $nb fichiers, libvlc.so et libvlccore.so.9 presents,"
echo "aucune dependance interne perdue."
