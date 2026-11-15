#!/bin/sh
# Contrôle la règle typographique du groupe sur les documents rédigés par Trustiatis :
# ni tiret long, ni tiret demi-long. Incises en virgules ou parenthèses, plages en
# « de ... à ... ».
#
# La liste des documents concernés est tools/documents-trustiatis.txt. Les documents de
# l'amont ne sont pas contrôlés : nous ne les écrivons pas.
#
# Usage : sh tools/verifier-typographie.sh
# Sortie : 0 si conforme, 1 sinon.

set -eu
cd "$(dirname "$0")/.."

LISTE="tools/documents-trustiatis.txt"
if [ ! -f "$LISTE" ]; then
  echo "ERREUR : $LISTE est introuvable." >&2
  exit 1
fi

# Motifs construits en octal pour que ce script ne contienne lui même aucun tiret long.
em=$(printf '\342\200\224')
en=$(printf '\342\200\223')

trouve=""
manquants=""
examines=0

# shellcheck disable=SC2013
for motif in $(grep -v '^[[:space:]]*#' "$LISTE" | grep -v '^[[:space:]]*$'); do
  # Le motif peut être un glob ; on le laisse se développer.
  vu=0
  for f in $motif; do
    [ -f "$f" ] || continue
    vu=1
    examines=$((examines + 1))
    sortie=$(grep -n -e "$em" -e "$en" "$f" || true)
    if [ -n "$sortie" ]; then
      trouve="$trouve
$(echo "$sortie" | sed "s|^|$f:|")"
    fi
  done
  [ "$vu" -eq 0 ] && manquants="$manquants $motif"
done

if [ -n "$manquants" ]; then
  echo "ERREUR : documents listés mais introuvables :$manquants" >&2
  echo "Corriger $LISTE, ou le document a été supprimé sans mettre la liste à jour." >&2
  exit 1
fi

if [ -n "$trouve" ]; then
  echo "Tirets longs ou demi-longs interdits dans les documents produits :"
  echo "$trouve" | sed '/^$/d'
  exit 1
fi

echo "Typographie conforme, $examines documents examinés."
