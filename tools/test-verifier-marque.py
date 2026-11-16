"""Vérifie que le contrôle de marque trie correctement.

Pourquoi ce test existe
-----------------------
`verifier-marque.py` ne fait pas que chercher un mot : il porte une décision sur
chaque ligne, celle de `NOTICE-AGPL.md` chapitre 2. Un nom de produit doit sortir,
un objet du réseau doit rester. Les motifs qui tranchent cela sont des expressions
rationnelles, et une expression rationnelle trop large ne se voit pas : elle rend
simplement le contrôle silencieux, ce qui est pire que pas de contrôle du tout.

Chaque cas ci dessous est une ligne réelle, prise dans les catalogues de l'amont.

Usage
-----
    python3 tools/test-verifier-marque.py
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent


def charger():
    chemin = RACINE / "tools" / "verifier-marque.py"
    spec = importlib.util.spec_from_file_location("verifier_marque", chemin)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# (ligne, signalée ?, pourquoi)
CAS = [
    # Catégorie 1 : nom du produit. Doit sortir.
    ('<string name="x">Open SimpleX Chat to accept call</string>', True, "nom du produit"),
    ('<string name="tray_show">Show SimpleX</string>', True, "nom du produit, menu du bureau"),
    ('<string name="theme_simplex">SimpleX</string>', True, "nom du produit, étiquette de thème"),
    ('<string name="x">SimpleX Lock</string>', True, "nom du produit, verrou"),
    ('<string name="x">Logo SimpleX</string>', True, "nom du produit, description d image"),
    ("""<string name="x">Merci d\\'avoir installé SimpleX Chat !</string>""", True, "nom du produit"),
    # Catégorie 3 : renvoi vers l'éditeur amont. Doit sortir...
    ('<string name="x">Visit the SimpleX Chat website</string>', True, "renvoi non couvert par P-7"),
    ('<string name="x">Rate SimpleX Chat on the store</string>', True, "renvoi non couvert par P-7"),
    # ...SAUF l'appel au soutien, que la décision P-7 conserve. Si l'un de ces
    # cas se met à signaler, c'est que la dérogation a été retirée sans que la
    # décision soit révisée.
    ('<string name="x">Support SimpleX Chat</string>', False, "P-7, appel au soutien conservé"),
    ('<string name="x">Install SimpleX Chat for terminal</string>', False, "P-7"),
    ('<string name="x">SimpleX Team</string>', False, "P-7, équipe de l amont"),
    ('<string name="x">Own a part of SimpleX Chat!</string>', False, "P-7, financement en capital"),
    ('<string name="x">Contacter l’équipe SimpleX</string>', False, "P-7"),
    ('<string name="x">Soutenez SimpleX et obtenez un badge.</string>', False, "P-7"),
    ('<string name="x">%s a investi dans le financement participatif de SimpleX Chat.</string>', False, "P-7"),
    # Catégorie 2 : réseau et protocole. Doit rester.
    ('<string name="x">SimpleX group link</string>', False, "objet du protocole"),
    ('<string name="x">SimpleX contact address</string>', False, "objet du protocole"),
    ('<string name="x">SimpleX public names (BETA)</string>', False, "service de nommage du réseau"),
    ('<string name="x">adresse SimpleX</string>', False, "objet du protocole"),
    ('<string name="x">Invitation unique SimpleX</string>', False, "objet du protocole"),
    ('<string name="x">Nom du canal SimpleX</string>', False, "objet du protocole"),
    ('<string name="x">Les adresses SimpleX sont sûres</string>', False, "objet du protocole, pluriel"),
    ('<string name="x">Les serveurs SimpleX ne voient pas votre profil.</string>', False, "réseau"),
    # Opérateur du réseau, nécessaire à l'exactitude sous la décision D-4.
    ('<string name="x">Use SimpleX Chat servers?</string>', False, "opérateur des serveurs proposés"),
    ('<string name="x">SimpleX Chat and Flux made an agreement</string>', False, "opérateurs nommés"),
    ('<string name="x">SimpleX Chat security was audited by Trail of Bits.</string>', False, "fait historique"),
    # Identifiants du code. Invisibles d'un utilisateur, et coûteux à renommer.
    # La frontière est la marque COLLÉE à d'autres caractères d'identifiant.
    ("class SimplexService : Service() {", False, "nom de classe"),
    ('const val DATABASE = "simplex_v1_chat.db"', False, "nom de fichier de base"),
    ("uriHandler.openVerifiedSimplexUri(simplexTeamUri)", False, "noms de fonction et de variable"),
    ("val SimplexColorPalette = darkColors(", False, "nom de palette"),
    ('appPrefs.getString("simplex.theme")', False, "clé de préférence"),
    ("import chat.simplex.res.*", False, "chemin de paquet"),
    ("  // Skip when Simplex service is currently working", False, "ligne commentée"),
    ('    val titre = "SimpleX" // vu dans SimplexService', True, "note en fin de ligne : le code reste contrôlé"),
    # ...et ce que la même règle ne doit PAS laisser passer, parce qu'un espace
    # ou un guillemet sépare la marque du reste : les formes vues d'un utilisateur.
    ('val title = "SimpleX"', True, "forme vue, marque isolée"),
    ('val title = "SimpleX Chat"', True, "forme vue, deux mots"),
    ('showWindowsErrorWindow("SimpleX failed to start"', True, "forme vue, phrase"),
    ('const val CHANNEL_NAME = "SimpleX Chat service"', True, "nom de canal, vu dans les réglages"),
    # Identifiants des catalogues.
    ('<string name="simplex_name_error">Trustiatis Secure</string>', False, "clé de ressource"),
    ("  <!-- API Error Responses - SimpleXAPI.kt -->", False, "commentaire, renvoi vers une source"),
    ("  <!-- Images - chat.simplex.app.views.chat.item.CIImageView.kt -->", False, "commentaire"),
    ('<string name="x">DefaultTheme.SIMPLEX</string>', False, "énumération persistée"),
    # Dérogation posée sur la ligne. Le cas vaut d'être épinglé : dans un catalogue, les
    # commentaires sont vidés avant l'examen de la ligne, donc le contrôle rapportait la
    # chaîne alors qu'elle portait sa raison. C'est la ligne brute qui décide.
    (
        '<string name="trustiatis_non_affiliation">SimpleX is a trademark of SimpleX Chat'
        " Ltd.</string>",
        True,
        "la mention est bien signalée quand rien ne la justifie",
    ),
    (
        '<string name="trustiatis_non_affiliation">SimpleX is a trademark of SimpleX Chat'
        " Ltd.</string>  <!-- marque-ok: mention obligatoire, NOTICE-AGPL.md -->",
        False,
        "dérogation nommée sur la ligne d'un catalogue",
    ),
    (
        '<string name="x">Open SimpleX Chat to accept call</string>  <!-- rien -->',
        True,
        "un commentaire sans raison ne déroge pas",
    ),
]


def main() -> int:
    module = charger()
    rates = 0
    for ligne, attendu, pourquoi in CAS:
        # Le chemin sert à la décision : il dit si « // » ouvre un commentaire.
        chemin = "x.kt" if not ligne.lstrip().startswith("<") else "strings.xml"
        # Reproduire le chemin de production, et pas une version plus facile : sur un
        # catalogue, le contrôle vide les commentaires avant d'examiner la ligne, et ne
        # garde la ligne brute que pour y chercher une dérogation. Un test qui sauterait
        # cette étape laisserait passer le défaut qu'il est censé épingler.
        examinee = (
            module._sans_commentaires_xml(ligne) if chemin.endswith(".xml") else ligne
        )
        signale = module.ligne_signalee(examinee, chemin, ligne)
        if signale == attendu:
            continue
        rates += 1
        verbe = "signalée à tort" if signale else "laissée passer"
        print(f"RATÉ  {verbe} ({pourquoi}) : {ligne.strip()}")
    if rates:
        print(f"\n{rates} cas raté(s) sur {len(CAS)}.")
        return 1
    print(f"Les {len(CAS)} cas de tri passent.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
