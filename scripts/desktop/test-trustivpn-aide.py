#!/usr/bin/env python3
"""Essais du service privilegie, sans privilege.

Ce qui est eprouve ici : l'analyse du protocole et la validation des valeurs, c'est a dire
tout ce qui decide si une commande est executee. Ce qui ne l'est pas : l'execution elle
meme, qui exige CAP_NET_ADMIN et une interface reseau. La frontiere est nette, et c'est
pour cela que la validation est separee de l'execution dans le service.

Usage :
    python3 scripts/desktop/test-trustivpn-aide.py
"""

from __future__ import annotations

import base64
import importlib.util
import pathlib
import sys
import unittest

chemin = pathlib.Path(__file__).with_name("trustivpn-aide.py")
spec = importlib.util.spec_from_file_location("aide", chemin)
aide = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(aide)

CLE_PRIVEE = base64.b64encode(bytes(range(32))).decode()
CLE_SERVEUR = base64.b64encode(bytes(range(32, 64))).decode()


def commande(**remplace: str | None) -> str:
    champs = {
        "type": "monter",
        "adresse": "10.42.7.19/32",
        "routes": "0.0.0.0/0,::/0",
        "passerelle": "203.0.113.44:51820",
        "cle_serveur": CLE_SERVEUR,
        "cle_privee": CLE_PRIVEE,
    }
    champs.update(remplace)
    lignes = [aide.MARQUE_PROTOCOLE]
    lignes += [f"{c}={v}" for c, v in champs.items() if v is not None]
    return "\n".join(lignes)


class ProtocoleTest(unittest.TestCase):
    def test_marque_absente(self):
        with self.assertRaises(aide.Refus):
            aide.lire_champs("type=demonter")

    def test_autre_version(self):
        with self.assertRaises(aide.Refus):
            aide.lire_champs("TRUSTIVPN-AIDE/2\ntype=demonter")

    def test_ligne_sans_valeur(self):
        with self.assertRaises(aide.Refus):
            aide.lire_champs(f"{aide.MARQUE_PROTOCOLE}\ntype")

    def test_champ_repete(self):
        with self.assertRaises(aide.Refus):
            aide.lire_champs(f"{aide.MARQUE_PROTOCOLE}\ntype=monter\ntype=demonter")

    def test_nom_de_champ_invalide(self):
        with self.assertRaises(aide.Refus):
            aide.lire_champs(f"{aide.MARQUE_PROTOCOLE}\nTYPE=monter")

    def test_valeur_avec_un_egal(self):
        # Le decoupage se fait sur le PREMIER egal : une valeur en base64 peut finir par
        # un signe egal de remplissage, et la couper la en ferait une cle invalide.
        champs = aide.lire_champs(f"{aide.MARQUE_PROTOCOLE}\ncle_privee={CLE_PRIVEE}")
        self.assertEqual(CLE_PRIVEE, champs["cle_privee"])

    def test_type_inconnu(self):
        self.assertTrue(aide.traiter(commande(type="rafraichir")).startswith("erreur="))


class ValidationTest(unittest.TestCase):
    def test_cle_de_trente_deux_octets(self):
        self.assertEqual(CLE_PRIVEE, aide.cle_base64(CLE_PRIVEE, "la cle"))

    def test_cle_trop_courte(self):
        courte = base64.b64encode(bytes(31)).decode()
        with self.assertRaises(aide.Refus):
            aide.cle_base64(courte, "la cle")

    def test_cle_trop_longue(self):
        longue = base64.b64encode(bytes(33)).decode()
        with self.assertRaises(aide.Refus):
            aide.cle_base64(longue, "la cle")

    def test_cle_pas_du_base64(self):
        with self.assertRaises(aide.Refus):
            aide.cle_base64("pas du base64 !", "la cle")

    def test_adresse_sans_prefixe_est_acceptee_comme_hote_unique(self):
        # ip_interface accepte une adresse nue et lui donne un masque plein. Le contrat du
        # canal, lui, exige le prefixe : la validation stricte est dans l'application, et
        # le service ne refuse ici que ce qui n'est pas une adresse du tout.
        self.assertEqual(32, aide.reseau("10.0.0.1", "l'adresse").network.prefixlen)

    def test_adresse_absurde(self):
        with self.assertRaises(aide.Refus):
            aide.reseau("10.0.0.300/32", "l'adresse")

    def test_passerelle_sans_port(self):
        with self.assertRaises(aide.Refus):
            aide.autorite("203.0.113.44")

    def test_passerelle_port_hors_limites(self):
        with self.assertRaises(aide.Refus):
            aide.autorite("203.0.113.44:70000")

    def test_passerelle_ipv6_entre_crochets(self):
        self.assertEqual(("fd00::1", 51820), aide.autorite("[fd00::1]:51820"))

    def test_passerelle_ipv6_sans_crochets_est_refusee(self):
        # Sans crochets, « fd00::1:51820 » est ambigu : le dernier deux points peut etre un
        # port ou un groupe. On refuse plutot que de deviner.
        with self.assertRaises(aide.Refus):
            aide.autorite("fd00::1:51820")

    def test_nom_de_passerelle(self):
        self.assertEqual(("vpn.exemple.test", 51820), aide.autorite("vpn.exemple.test:51820"))

    def test_hote_invalide(self):
        with self.assertRaises(aide.Refus):
            aide.autorite("pas un hote!:51820")


class CommandeTest(unittest.TestCase):
    """Les refus qui se prononcent avant toute execution privilegiee."""

    def setUp(self):
        self.appels = []
        self.executer = aide.executer
        self.demonter = aide.demonter
        aide.executer = lambda args, entree=None: self.appels.append((args, entree)) or ""
        aide.demonter = lambda: self.appels.append((["demonter"], None))

    def tearDown(self):
        aide.executer = self.executer
        aide.demonter = self.demonter

    def test_sans_route(self):
        self.assertIn("aucune route", aide.traiter(commande(routes="")))
        self.assertEqual([], self.appels)

    def test_resolveur_qui_n_est_pas_une_adresse(self):
        self.assertIn("resolveur", aide.traiter(commande(dns="resolveur.interne")))
        self.assertEqual([], self.appels)

    def test_la_cle_privee_passe_par_l_entree_standard_et_jamais_en_argument(self):
        self.assertEqual("ok", aide.traiter(commande()))
        for args, entree in self.appels:
            self.assertNotIn(CLE_PRIVEE, " ".join(args), f"la cle privee est passee en argument a {args[0]}")
        wg = [a for a in self.appels if a[0][0] == "wg"]
        self.assertEqual(1, len(wg))
        self.assertEqual((CLE_PRIVEE + "\n").encode(), wg[0][1])
        self.assertIn("/dev/stdin", wg[0][0])

    def test_le_montage_commence_par_un_demontage(self):
        aide.traiter(commande())
        self.assertEqual(["demonter"], self.appels[0][0])

    def test_la_marque_de_pare_feu_est_posee(self):
        aide.traiter(commande())
        wg = [a for a in self.appels if a[0][0] == "wg"][0][0]
        self.assertIn("fwmark", wg)
        self.assertIn(str(aide.MARQUE), wg)

    def test_les_routes_vont_dans_la_table_dediee(self):
        aide.traiter(commande())
        tables = [a[0] for a in self.appels if a[0][0] == "ip" and "route" in a[0]]
        self.assertTrue(tables, "aucune route posee")
        for args in tables:
            self.assertIn("table", args)
            self.assertIn(str(aide.MARQUE), args)

    def test_le_coupe_circuit_est_pose(self):
        aide.traiter(commande())
        nft = [a for a in self.appels if a[0][0] == "nft"]
        self.assertEqual(1, len(nft), "le coupe circuit doit etre pose exactement une fois")
        regles = nft[0][1].decode()
        self.assertIn(f"meta mark {aide.MARQUE} accept", regles)
        self.assertIn(f'oifname "{aide.INTERFACE}" accept', regles)
        self.assertIn("reject", regles)

    def test_un_echec_en_cours_de_montage_defait_tout(self):
        appels = self.appels

        def echouer(args, entree=None):
            appels.append((args, entree))
            if args[0] == "nft":
                raise aide.Refus("pare feu indisponible")
            return ""

        aide.executer = echouer
        reponse = aide.traiter(commande())
        self.assertTrue(reponse.startswith("erreur="))
        # Le premier demontage est celui du debut ; il doit y en avoir un second, apres
        # l'echec, sinon le poste resterait avec des regles et sans tunnel.
        demontages = [a for a in appels if a[0] == ["demonter"]]
        self.assertEqual(2, len(demontages), "l'echec doit etre suivi d'un demontage")

    def test_demonter_ne_demande_rien_d_autre(self):
        self.assertEqual("ok", aide.traiter(f"{aide.MARQUE_PROTOCOLE}\ntype=demonter"))
        self.assertEqual([(["demonter"], None)], self.appels)


if __name__ == "__main__":
    unittest.main(verbosity=2)
