#!/usr/bin/env python3
"""Service privilegie du tunnel TrustiVPN, pour les postes de bureau Linux.

Pourquoi ce service existe
--------------------------
Creer une interface reseau et reecrire les routes exige CAP_NET_ADMIN. Une messagerie qui
tournerait avec cette capacite serait un defaut de securite, pas une fonction : toute
faille d'analyse d'un message recu deviendrait une prise de controle du reseau de la
machine. Le privilege est donc ici, dans un service qui n'accepte qu'un jeu de commandes
ferme, et l'application n'en a aucun.

Ce que ce service garantit, et ce qu'il ne garantit pas
------------------------------------------------------
Il garantit que, tunnel monte, **tout** le trafic passe dedans : table de routage dediee,
marque de pare feu, et un coupe circuit qui jette ce qui tenterait de sortir ailleurs.
C'est le montage de wg-quick pour une route par defaut, et il n'y a pas de raison d'en
inventer un autre.

Il ne garantit rien contre un compte utilisateur deja compromis : qui tient le compte peut
parler a la socket, donc monter ou demonter le tunnel. Il pourrait de toute facon lire les
conversations de cet utilisateur. La socket evite que l'application ait le privilege, elle
ne remplace pas la securite du poste.

La cle privee ne touche jamais le disque
---------------------------------------
Elle arrive par la socket, en memoire, et elle est passee a `wg` par son entree standard.
Aucun fichier de configuration n'est ecrit, a aucun moment. C'est l'exigence du Membre, et
c'est aussi ce qui evite qu'une sauvegarde du poste emporte une cle de session.

Protocole
---------
Une commande par connexion, en texte, premiere ligne `TRUSTIVPN-AIDE/1`, puis des couples
`cle=valeur`. Reponse d'une ligne : `ok`, ou `erreur=<texte destine a l'utilisateur>`.

    TRUSTIVPN-AIDE/1
    type=monter
    adresse=10.42.7.19/32
    routes=0.0.0.0/0,::/0
    passerelle=203.0.113.44:51820
    cle_serveur=<base64>
    dns=10.42.0.1
    cle_privee=<base64>

    TRUSTIVPN-AIDE/1
    type=demonter

Usage :
    trustivpn-aide.py                  # service, sur /run/trustivpn/aide.sock
    trustivpn-aide.py --verifier       # controle les outils requis et sort
    trustivpn-aide.py --socket CHEMIN  # pour les essais
"""

from __future__ import annotations

import argparse
import base64
import grp
import ipaddress
import os
import re
import shutil
import socket
import stat
import subprocess
import sys
import threading

# Deux montages simultanes se marcheraient dessus : l'un defait les regles que l'autre
# vient de poser. Les commandes sont donc serialisees.
_verrou = threading.Lock()

INTERFACE = "wg-trusti"
MARQUE = 51820  # marque de pare feu et numero de table de routage, comme wg-quick
MARQUE_PROTOCOLE = "TRUSTIVPN-AIDE/1"
SOCKET_DEFAUT = "/run/trustivpn/aide.sock"
GROUPE = "trustivpn"
TABLE_NFT = "trustivpn"
TAILLE_MAX = 8192  # une commande legitime fait moins de 1 Kio
OUTILS = ("ip", "wg", "nft", "sysctl")

NOM_DNS = re.compile(r"^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)*[A-Za-z]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")


class Refus(Exception):
    """Une commande que ce service refuse. Le texte est destine a l'utilisateur."""


def executer(args: list[str], entree: bytes | None = None) -> str:
    """Lance une commande. Jamais de shell : les valeurs viennent d'une socket."""
    r = subprocess.run(args, input=entree, capture_output=True, check=False)
    if r.returncode != 0:
        # Le stderr de ip et de wg est court et parlant. La cle privee n'y figure pas,
        # puisqu'elle n'est jamais passee en argument.
        raise Refus(f"{args[0]} a echoue : {r.stderr.decode(errors='replace').strip()}")
    return r.stdout.decode(errors="replace")


def lire_champs(texte: str) -> dict[str, str]:
    lignes = texte.strip().splitlines()
    if not lignes or lignes[0].strip() != MARQUE_PROTOCOLE:
        raise Refus("commande d'une autre version du protocole")
    champs: dict[str, str] = {}
    for ligne in lignes[1:]:
        if not ligne.strip():
            continue
        if "=" not in ligne:
            raise Refus("ligne sans valeur")
        cle, _, valeur = ligne.partition("=")
        cle = cle.strip()
        if not cle or not re.fullmatch(r"[a-z_]+", cle):
            raise Refus("nom de champ invalide")
        if cle in champs:
            raise Refus("champ repete")
        champs[cle] = valeur.strip()
    return champs


def cle_base64(valeur: str, quoi: str) -> str:
    """Valide une cle X25519 encodee, et rend l'encodage canonique.

    On revalide ce que l'application a deja valide, et ce n'est pas une redondance : le
    service ne sait pas qui lui parle, et une cle de la mauvaise longueur serait completee
    par wg. Le pair ne serait alors pas celui qu'on croit.
    """
    try:
        octets = base64.b64decode(valeur, validate=True)
    except Exception as e:
        raise Refus(f"{quoi} n'est pas du base64") from e
    if len(octets) != 32:
        raise Refus(f"{quoi} fait {len(octets)} octets, 32 attendus")
    return base64.b64encode(octets).decode()


def reseau(valeur: str, quoi: str) -> ipaddress.IPv4Interface | ipaddress.IPv6Interface:
    try:
        return ipaddress.ip_interface(valeur)
    except ValueError as e:
        raise Refus(f"{quoi} n'est pas une adresse en notation CIDR") from e


def autorite(valeur: str) -> tuple[str, int]:
    """Decoupe hote:port, crochets d'IPv6 compris."""
    if valeur.startswith("["):
        fin = valeur.find("]")
        if fin < 0 or not valeur[fin + 1:].startswith(":"):
            raise Refus("adresse de passerelle incomplete")
        hote, port_texte = valeur[1:fin], valeur[fin + 2:]
    else:
        hote, _, port_texte = valeur.rpartition(":")
        if not hote:
            raise Refus("la passerelle doit porter un port")
        # Sans crochets, « fd00::1:51820 » est ambigu : le dernier groupe peut etre un port
        # ou une partie de l'adresse. On refuse plutot que de deviner, et c'est aussi ce
        # que wg exige.
        if ":" in hote:
            raise Refus("une adresse IPv6 s'ecrit entre crochets : [adresse]:port")
    if not port_texte.isdigit() or not 1 <= int(port_texte) <= 65535:
        raise Refus("port de passerelle hors limites")
    try:
        ipaddress.ip_address(hote)
    except ValueError:
        if not NOM_DNS.match(hote):
            raise Refus("hote de passerelle invalide") from None
    return hote, int(port_texte)


def existe_interface() -> bool:
    r = subprocess.run(["ip", "link", "show", "dev", INTERFACE], capture_output=True, check=False)
    return r.returncode == 0


def demonter() -> None:
    """Defait tout, dans l'ordre inverse, et sans s'arreter au premier echec.

    Un demontage qui abandonne en route laisserait une regle de pare feu sans interface,
    c'est a dire un poste sans reseau que seul un redemarrage sauverait. Chaque etape est
    donc tentee, et les echecs sont ignores : il n'y a rien a reparer si une etape avait
    deja ete defaite.
    """
    for args in (
        ["nft", "delete", "table", "inet", TABLE_NFT],
        ["ip", "-4", "rule", "del", "not", "fwmark", str(MARQUE), "table", str(MARQUE)],
        ["ip", "-4", "rule", "del", "table", "main", "suppress_prefixlength", "0"],
        ["ip", "-6", "rule", "del", "not", "fwmark", str(MARQUE), "table", str(MARQUE)],
        ["ip", "-6", "rule", "del", "table", "main", "suppress_prefixlength", "0"],
        ["ip", "link", "del", "dev", INTERFACE],
    ):
        subprocess.run(args, capture_output=True, check=False)


def coupe_circuit(familles: set[int]) -> None:
    """Le coupe circuit : tunnel monte, rien ne sort ailleurs.

    Ce que ces regles font, ligne par ligne, parce qu'une regle de pare feu qu'on ne sait
    pas relire est une regle qu'on n'osera pas corriger :

      - le trafic deja marque est celui de WireGuard lui meme, vers la passerelle. Il doit
        sortir par l'interface physique, sinon le tunnel ne peut pas exister ;
      - la boucle locale reste ouverte, sans quoi la machine ne se parle plus a elle meme ;
      - tout le reste qui tenterait de sortir par une autre interface que le tunnel est
        **rejete**, et rejete plutot que jete en silence : une application qui recoit un
        refus le dit, alors qu'un paquet perdu se traduit par une attente de trente
        secondes que l'utilisateur prendra pour une panne.

    C'est l'exigence du Membre : interrupteur allume, tout passe dans le tunnel.
    """
    regles = [
        f"table inet {TABLE_NFT} {{",
        "  chain sortie {",
        "    type filter hook output priority filter; policy accept;",
        f"    meta mark {MARQUE} accept",
        "    oifname \"lo\" accept",
        f"    oifname \"{INTERFACE}\" accept",
    ]
    if 4 in familles:
        regles.append("    meta nfproto ipv4 reject with icmp type admin-prohibited")
    if 6 in familles:
        regles.append("    meta nfproto ipv6 reject with icmpv6 type admin-prohibited")
    regles += ["  }", "}"]
    executer(["nft", "-f", "-"], entree=("\n".join(regles) + "\n").encode())


def monter(champs: dict[str, str]) -> None:
    adresse = reseau(champs.get("adresse", ""), "l'adresse")
    routes_texte = [r.strip() for r in champs.get("routes", "").split(",") if r.strip()]
    if not routes_texte:
        raise Refus("aucune route : un tunnel sans route ne porte rien")
    routes = [reseau(r, "une route") for r in routes_texte]
    hote, port = autorite(champs.get("passerelle", ""))
    cle_serveur = cle_base64(champs.get("cle_serveur", ""), "la cle du serveur")
    cle_privee = cle_base64(champs.get("cle_privee", ""), "la cle privee")
    dns = champs.get("dns") or None
    if dns:
        try:
            ipaddress.ip_address(dns)
        except ValueError:
            raise Refus("le resolveur n'est pas une adresse") from None

    # Un montage par dessus un montage laisserait deux jeux de regles. On defait d'abord.
    demonter()

    monte = False
    try:
        executer(["ip", "link", "add", "dev", INTERFACE, "type", "wireguard"])
        monte = True
        executer(
            [
                "wg", "set", INTERFACE,
                "private-key", "/dev/stdin",
                "fwmark", str(MARQUE),
                "peer", cle_serveur,
                "endpoint", f"[{hote}]:{port}" if ":" in hote else f"{hote}:{port}",
                "persistent-keepalive", "25",
                "allowed-ips", ",".join(str(r) for r in routes),
            ],
            entree=(cle_privee + "\n").encode(),
        )
        famille = "-6" if adresse.version == 6 else "-4"
        executer(["ip", famille, "address", "add", str(adresse), "dev", INTERFACE])
        executer(["ip", "link", "set", "up", "dev", INTERFACE])

        # src_valid_mark : sans lui, le noyau jette les paquets de WireGuard lui meme,
        # dont l'adresse source appartient a une interface qui n'a pas encore de route.
        # C'est le meme reglage que pose wg-quick, et pour la meme raison.
        executer(["sysctl", "-q", "-w", "net.ipv4.conf.all.src_valid_mark=1"])

        familles = set()
        for route in routes:
            # Une route par defaut ne peut pas remplacer la route par defaut : le trafic de
            # WireGuard vers la passerelle passerait alors par le tunnel, c'est a dire par
            # lui meme. On emploie donc une table dediee et une regle de marque, comme
            # wg-quick, ce qui laisse la table principale intacte.
            f = "-6" if route.version == 6 else "-4"
            familles.add(route.version)
            executer(["ip", f, "route", "add", str(route), "dev", INTERFACE, "table", str(MARQUE)])
        for version in sorted(familles):
            f = "-6" if version == 6 else "-4"
            executer(["ip", f, "rule", "add", "not", "fwmark", str(MARQUE), "table", str(MARQUE)])
            # suppress_prefixlength 0 : la table principale continue de servir pour les
            # routes precises, mais sa route par defaut est ignoree. Sans cela, une route
            # par defaut locale reprendrait la main et le tunnel fuirait.
            executer(["ip", f, "rule", "add", "table", "main", "suppress_prefixlength", "0"])

        coupe_circuit(familles)

        if dns:
            # Pas de resolv.conf reecrit : ce fichier appartient au systeme, et le
            # reecrire casse les postes ou il est gere par systemd-resolved ou NetworkManager.
            # On pose le resolveur sur l'interface, ce que resolvectl sait faire, et on
            # n'echoue pas si l'outil est absent : le tunnel vaut mieux sans resolveur
            # dedie que pas de tunnel.
            if shutil.which("resolvectl"):
                subprocess.run(["resolvectl", "dns", INTERFACE, dns], capture_output=True, check=False)
                subprocess.run(["resolvectl", "domain", INTERFACE, "~."], capture_output=True, check=False)
    except Exception:
        # Un montage a moitie fait est le pire etat possible : des regles sans interface,
        # donc un poste sans reseau. On defait tout avant de rendre l'erreur.
        if monte:
            demonter()
        raise


def traiter_commande(texte: str) -> str:
    """Execute une commande. Appele sous le verrou de `traiter`."""
    try:
        champs = lire_champs(texte)
        type_ = champs.get("type")
        if type_ == "monter":
            monter(champs)
        elif type_ == "demonter":
            demonter()
        else:
            raise Refus("type de commande inconnu")
        return "ok"
    except Refus as e:
        return f"erreur={e}"
    except Exception as e:  # noqa: BLE001
        # On ne laisse pas fuir une trace par la socket : elle dirait au client des choses
        # sur le poste qu'il n'a pas a savoir.
        print(f"trustivpn-aide : erreur inattendue : {e!r}", file=sys.stderr, flush=True)
        return "erreur=erreur interne du service trustivpn-aide"


def traiter(texte: str) -> str:
    """Point d'entree du protocole, serialise."""
    with _verrou:
        return traiter_commande(texte)


def servir(chemin: str) -> None:
    repertoire = os.path.dirname(chemin)
    os.makedirs(repertoire, exist_ok=True)
    if os.path.exists(chemin):
        os.unlink(chemin)
    serveur = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    serveur.bind(chemin)
    try:
        gid = grp.getgrnam(GROUPE).gr_gid
        os.chown(chemin, 0, gid)
    except KeyError:
        print(
            f"trustivpn-aide : le groupe {GROUPE} n'existe pas, la socket reste reservee a root",
            file=sys.stderr, flush=True,
        )
    # 0660 : seul un membre du groupe parle au service. Le mode est pose apres le bind,
    # parce que bind cree le fichier avec l'umask du processus.
    os.chmod(chemin, stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IWGRP)
    serveur.listen(4)
    print(f"trustivpn-aide : a l'ecoute sur {chemin}", flush=True)

    def servir_un(connexion: socket.socket) -> None:
        with connexion:
            connexion.settimeout(10)
            morceaux = []
            recu = 0
            try:
                while True:
                    bloc = connexion.recv(4096)
                    if not bloc:
                        break
                    recu += len(bloc)
                    if recu > TAILLE_MAX:
                        connexion.sendall(b"erreur=commande trop longue")
                        return
                    morceaux.append(bloc)
                reponse = traiter(b"".join(morceaux).decode("utf-8", errors="replace"))
            except socket.timeout:
                reponse = "erreur=commande incomplete"
            try:
                connexion.sendall(reponse.encode())
            except OSError:
                pass

    while True:
        connexion, _ = serveur.accept()
        # Un fil par connexion, et un seul en pratique : les commandes sont serialisees
        # par le verrou, parce que deux montages simultanes se marcheraient dessus.
        threading.Thread(target=servir_un, args=(connexion,), daemon=True).start()


def main() -> int:
    parseur = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parseur.add_argument("--socket", default=SOCKET_DEFAUT)
    parseur.add_argument("--verifier", action="store_true", help="controle les outils requis et sort")
    args = parseur.parse_args()

    manquants = [o for o in OUTILS if shutil.which(o) is None]
    if manquants:
        print(f"trustivpn-aide : outils manquants : {', '.join(manquants)}", file=sys.stderr)
        print("Installer wireguard-tools, iproute2 et nftables.", file=sys.stderr)
        return 1
    if args.verifier:
        print("trustivpn-aide : les outils requis sont presents.")
        return 0
    if os.geteuid() != 0:
        print("trustivpn-aide : ce service doit tourner en root.", file=sys.stderr)
        return 1
    servir(args.socket)
    return 0


if __name__ == "__main__":
    sys.exit(main())
