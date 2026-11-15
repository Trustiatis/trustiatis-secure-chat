#!/usr/bin/env bash
# Installe le paquet Debian de Trustiatis Secure dans l'espace d'un utilisateur,
# sans droits root.
#
# Pourquoi ce script existe
# -------------------------
# `dpkg -i` demande root. Sur un poste où l'utilisateur ne l'a pas, et pour un
# essai avant de déployer, l'arborescence du paquet se dépose aussi bien sous
# ~/.local : le paquet de jpackage est autonome, il embarque sa machine virtuelle
# et ses bibliothèques natives, et son lanceur se moque de son emplacement.
#
# Il fait en plus deux choses que le paquet ne sait pas faire, documentées au
# chapitre 5 de docs/DISTRIBUTION.md :
#
# 1. Il écrit `MimeType=x-scheme-handler/simplex;x-scheme-handler/simplexchat;`
#    dans l'entrée de bureau. Compose Desktop n'expose aucun moyen de le faire
#    écrire par jpackage, donc le paquet livré n'enregistre pas le gestionnaire
#    des liens d'invitation. L'application le pose aussi d'elle même au
#    démarrage, par `xdg-mime`, mais une entrée correcte vaut mieux qu'un appel
#    au premier lancement.
# 2. Il force le rendu logiciel de Skiko. Sur une machine virtuelle sans pilote
#    graphique accéléré, le rendu OpenGL de l'amont plante dans son redessin,
#    avec un cadre natif trompeur qui accuse une bibliothèque de compression.
#    Le lanceur de jpackage ne transmet pas un `-D` à la machine virtuelle, il
#    le passe à l'application comme argument : il faut donc la variable
#    d'environnement.
set -euo pipefail

paquet="${1:-}"
if [ -z "$paquet" ] || [ ! -f "$paquet" ]; then
  echo "usage : $0 <chemin du .deb>" >&2
  exit 2
fi

racine="${XDG_DATA_HOME:-$HOME/.local/share}/trustiatis-secure-app"
entrees="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
rm -rf "$racine"
mkdir -p "$racine" "$entrees"

# Le paquet dépose son arborescence sous /opt/trustiatis-secure.
dpkg-deb -x "$paquet" "$racine"
app="$racine/opt/trustiatis-secure"
lanceur="$app/bin/trustiatis-secure"
[ -x "$lanceur" ] || { echo "lanceur introuvable : $lanceur" >&2; exit 1; }

icone="$app/lib/Trustiatis Secure.png"
[ -f "$icone" ] || icone="$(find "$app/lib" -maxdepth 1 -name '*.png' | head -1)"

cat > "$entrees/trustiatis-secure.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Trustiatis Secure
Comment=Canal confidentiel de Trustiatis, chiffré de bout en bout, sans compte
Exec=env SKIKO_RENDER_API=SOFTWARE "$lanceur" %u
Icon=$icone
Categories=Network;InstantMessaging;
Terminal=false
MimeType=x-scheme-handler/simplex;x-scheme-handler/simplexchat;
DESKTOP

command -v update-desktop-database >/dev/null && update-desktop-database "$entrees" || true

echo "installé    : $app"
echo "lanceur     : $lanceur"
echo "entrée      : $entrees/trustiatis-secure.desktop"
echo "désinstaller: rm -rf '$racine' '$entrees/trustiatis-secure.desktop'"
