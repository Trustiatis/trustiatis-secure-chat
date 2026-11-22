#!/usr/bin/env bash
#
# Installe le service privilegie du tunnel TrustiVPN sur un poste Linux.
#
# Ce que fait ce script, et pourquoi chaque etape est la :
#
#   1. il verifie que wireguard-tools, iproute2 et nftables sont presents. Sans eux, le
#      service demarrerait et refuserait chaque commande, ce qui est le pire message
#      d'erreur possible ;
#   2. il cree le groupe trustivpn. C'est lui qui donne le droit de parler a la socket,
#      donc de monter le tunnel. Un utilisateur qui n'y est pas ne peut rien demander ;
#   3. il pose le service et son unite systemd, puis l'active.
#
# Il NE met personne dans le groupe : c'est une decision d'administration, et elle doit
# etre prise a la main, utilisateur par utilisateur.
#
# Usage :
#   sudo scripts/desktop/trustivpn-aide-installer.sh
#   sudo scripts/desktop/trustivpn-aide-installer.sh --retirer
#
set -euo pipefail

racine="$(cd "$(dirname "$0")/../.." && pwd)"
cible_service=/usr/local/libexec/trustivpn-aide.py
cible_unite=/etc/systemd/system/trustivpn-aide.service
groupe=trustivpn

[ "$(id -u)" = 0 ] || { echo "ERREUR : lancer ce script avec sudo." >&2; exit 1; }

if [ "${1:-}" = "--retirer" ]; then
  systemctl disable --now trustivpn-aide.service 2>/dev/null || true
  rm -f "$cible_unite" "$cible_service"
  systemctl daemon-reload
  echo "Service retire. Le groupe $groupe est laisse en place : des comptes y sont peut etre encore."
  exit 0
fi

manquants=""
for outil in ip wg nft sysctl; do
  command -v "$outil" >/dev/null || manquants="$manquants $outil"
done
if [ -n "$manquants" ]; then
  echo "ERREUR : outils manquants :$manquants" >&2
  echo "Sur Debian et derives : apt install wireguard-tools iproute2 nftables" >&2
  exit 1
fi

getent group "$groupe" >/dev/null || { groupadd --system "$groupe"; echo "Groupe $groupe cree."; }

install -D -m 0755 -o root -g root "$racine/scripts/desktop/trustivpn-aide.py" "$cible_service"
install -D -m 0644 -o root -g root "$racine/scripts/desktop/trustivpn-aide.service" "$cible_unite"
systemctl daemon-reload
systemctl enable --now trustivpn-aide.service

echo
systemctl --no-pager --lines=5 status trustivpn-aide.service || true
echo
echo "Installe. Il reste UNE chose a faire, a la main, pour chaque utilisateur autorise :"
echo
echo "    sudo usermod -aG $groupe <utilisateur>"
echo
echo "L'utilisateur doit se reconnecter pour que son appartenance au groupe prenne effet."
echo "Sans cela, l'application dira que le service n'est pas joignable, ce qui sera vrai."
