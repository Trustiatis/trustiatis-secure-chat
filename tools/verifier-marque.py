#!/usr/bin/env python3
"""Contrôle de marque pour Trustiatis Secure.

Vérifie qu'aucune occurrence de la marque SimpleX ne subsiste là où la politique de marque de
l'amont l'interdit, tout en conservant les occurrences qu'elle autorise et qui sont
nécessaires à l'exactitude (le réseau, le protocole, une adresse).

Trois catégories, voir NOTICE-AGPL.md :
  1. nom du produit            -> interdit, doit devenir « Trustiatis Secure »
  2. réseau et protocole       -> autorisé, conservé
  3. renvois vers l'éditeur    -> interdit, retiré ou réécrit

Le contrôle est volontairement conservateur : il signale, il ne corrige jamais.
Code de sortie 0 si aucun signalement, 1 sinon. Utilisé par .gitlab-ci.yml.

Usage :
    tools/verifier-marque.py [--racine .] [--json | --resume]
"""

import argparse
import json
import os
import re
import sys

# Fichiers et dossiers hors périmètre du contrôle : code de l'amont dont la marque est
# légitime (le coeur, les tests, la documentation de l'amont, son site, ses bots).
EXCLUS_PREFIXES = (
    ".git/",
    "src/",                  # coeur Haskell, non modifié (décision D-2)
    "tests/",
    "docs/",                 # documentation de l'amont ET nos audits, qui citent la marque
    "blog/",
    "website/",
    "media-logos/",          # ressources de marque de l'amont, à ne pas réutiliser mais pas à réécrire
    "packages/",
    "bots/",
    "apps/simplex-bot",
    "apps/simplex-broadcast-bot/",
    "apps/simplex-directory-service/",
    "apps/simplex-badge-service/",
    "apps/simplex-calculator-bot/",
    "apps/simplex-support-bot",
    "apps/simplex-chat/",    # client en ligne de commande, hors produit
    "apps/ios/SimpleX Localizations/",
    "scripts/",
    ".github/",              # intégration continue de l'amont, remplacée par la nôtre : n'y figurent que
                             # des identifiants de construction et des noms de secrets GitHub, jamais
                             # de texte vu par un utilisateur
    "plans/",
    "eth/",
    "fastlane/",
)

EXCLUS_FICHIERS = {
    "LICENSE",               # à conserver intact, obligation AGPL
    "CHANGELOG.md",          # historique de l'amont
    "NOTICE-AGPL.md",        # explique précisément la marque
    "README.md",             # porte la mention de compatibilité
    "CONTRIBUTING.md",
    "CLAUDE.md",
    "PRIVACY.md",
    "flake.lock",
}

EXTENSIONS = {
    ".kt", ".kts", ".swift", ".xml", ".plist", ".entitlements", ".json", ".jsonc",
    ".gradle", ".properties", ".pbxproj", ".xcconfig", ".html", ".js", ".ts",
    ".desktop", ".yaml", ".yml", ".sh", ".cfg", ".ini",
}

# Identifiants techniques de l'amont qu'il est NORMAL de rencontrer : noms de classes, de
# services, de types, chemins de paquets. Les renommer casserait le typage ou la
# sérialisation (voir CONTRIBUTING.md, interdit n° 3 et 4).
IDENTIFIANTS_AUTORISES = [
    r"chat\.simplex\.common",          # chemin de paquet du module commun
    r"chat\.simplex\.desktop",
    r"Simplex(App|Service|Link|Name|Theme|Info|Links|LinkMode|Chrome)\w*",
    r"simplexWindowState",
    r"simplexChatImage",
    r"SimpleXChat",                    # nom du framework iOS et du module
    r"SimpleXTheme",
    r"simplex(chat)?:",                # schémas d'URI, conservés (décision D-3)
    r"SimplexBlue|SimplexGreen",       # couleurs nommées, valeurs changées mais nom conservé
    r"simplex\.im|simplex\.chat|simplexonflux\.com",   # serveurs communautaires (décision D-4)
    r"simplexmq",
    r"(?:send|recv)SimpleX(?:Cmd|Msg)\w*",   # fonctions du pont FFI vers le coeur
]

# Identifiants dont la CASSE fait tout le sens : « SIMPLEX » en capitales est une valeur
# d'énumération persistée, « SimpleX » est la marque. Les confondre, comme le faisait la
# version précédente de ce fichier, revenait à autoriser la marque partout où elle est un
# mot isolé, c'est à dire dans presque toutes les phrases vues par un utilisateur.
IDENTIFIANTS_AUTORISES_CASSE = [
    r"\bDefaultTheme\.SIMPLEX\b",      # énumération persistée, conservée
    r"\bSIMPLEX\b",                    # valeur de l'énumération de thème, en capitales
]

# Désignations du réseau et du protocole : autorisées par la politique de marque.
RESEAU_AUTORISE = [
    r"SimpleX network", r"réseau SimpleX", r"SimpleX-Netzwerk",
    r"SimpleX address", r"adresse SimpleX",
    r"compatible (?:avec le réseau |with (?:the )?)SimpleX",
    r"SimpleX Chat Ltd",               # attribution, obligation de licence
    r"Copyright .{0,12}\d{4} SimpleX Chat\. All rights reserved\.",   # idem, en tête de fichier
]

MOTIF_MARQUE = re.compile(r"simplex", re.IGNORECASE)
MOTIF_AUTORISE = re.compile("|".join(IDENTIFIANTS_AUTORISES + RESEAU_AUTORISE), re.IGNORECASE)
MOTIF_AUTORISE_CASSE = re.compile("|".join(IDENTIFIANTS_AUTORISES_CASSE))

# Marqueur de dérogation explicite, à poser en commentaire sur la ligne, avec une raison.
MOTIF_DEROGATION = re.compile(r"marque-ok\s*:\s*\S+")


def dans_perimetre(chemin: str) -> bool:
    if chemin in EXCLUS_FICHIERS:
        return False
    if os.path.basename(chemin) in EXCLUS_FICHIERS:
        return False
    for p in EXCLUS_PREFIXES:
        if chemin.startswith(p):
            return False
    return os.path.splitext(chemin)[1] in EXTENSIONS


def signalements(racine: str):
    for dossier, sousdossiers, fichiers in os.walk(racine):
        sousdossiers[:] = [d for d in sousdossiers if d not in (".git", "build", "node_modules", "__pycache__")]
        for nom in fichiers:
            absolu = os.path.join(dossier, nom)
            relatif = os.path.relpath(absolu, racine).replace(os.sep, "/")
            if not dans_perimetre(relatif):
                continue
            try:
                with open(absolu, encoding="utf-8", errors="replace") as f:
                    lignes = f.readlines()
            except OSError:
                continue
            for n, ligne in enumerate(lignes, 1):
                if not MOTIF_MARQUE.search(ligne):
                    continue
                if MOTIF_DEROGATION.search(ligne):
                    continue
                reste = MOTIF_AUTORISE_CASSE.sub("", ligne)
                reste = MOTIF_AUTORISE.sub("", reste)
                if MOTIF_MARQUE.search(reste):
                    yield {
                        "fichier": relatif,
                        "ligne": n,
                        "texte": ligne.strip()[:160],
                    }


def afficher_resume(trouves) -> None:
    """Sortie bornée, pour un journal d'intégration continue.

    Le rapport complet fait plusieurs milliers de lignes, et GitLab tronque les journaux :
    un journal tronqué de 500 Kio n'apprend rien à personne. On affiche donc la répartition
    et un échantillon, le détail partant en artefact JSON.
    """
    if not trouves:
        print("Contrôle de marque : aucun signalement.")
        return

    import collections

    def categorie(f: str) -> str:
        if f.endswith("strings.xml"):
            return "catalogues de traduction"
        if f.endswith((".kt", ".kts")):
            return "Kotlin, commun Android et Desktop"
        if f.endswith(".swift"):
            return "Swift, iOS"
        if f.endswith(".pbxproj"):
            return "projet Xcode"
        return "autres"

    cat = collections.Counter(categorie(s["fichier"]) for s in trouves)
    fic = collections.Counter(s["fichier"] for s in trouves)

    print(f"Contrôle de marque : {len(trouves)} signalement(s) sur {len(fic)} fichier(s).\n")
    print("Répartition :")
    for nom, n in cat.most_common():
        print(f"  {n:6d}  {nom}")
    print("\nFichiers les plus touchés :")
    for nom, n in fic.most_common(10):
        print(f"  {n:6d}  {nom}")

    echantillon = 20
    print(f"\nÉchantillon, {echantillon} premières lignes sur {len(trouves)} :")
    for s in trouves[:echantillon]:
        print(f"  {s['fichier']}:{s['ligne']}  {s['texte']}")

    print(
        "\nLe détail complet est dans l'artefact marque-complet.json de ce job.\n"
        "Chaque ligne doit être traitée selon NOTICE-AGPL.md :\n"
        "  - nom du produit        -> remplacer par « Trustiatis Secure »\n"
        "  - réseau ou protocole   -> conserver, et ajouter le commentaire « marque-ok: reseau »\n"
        "  - renvoi vers l'éditeur -> retirer ou réécrire vers Trustiatis\n"
        "Une dérogation se pose en commentaire sur la ligne, sous la forme\n"
        "« marque-ok: <raison> », et doit être justifiable."
    )


def main() -> int:
    ap = argparse.ArgumentParser(description="Contrôle de marque Trustiatis Secure")
    ap.add_argument("--racine", default=".")
    ap.add_argument("--json", action="store_true")
    ap.add_argument(
        "--resume",
        action="store_true",
        help="sortie bornée : répartition et échantillon, au lieu des milliers de lignes",
    )
    args = ap.parse_args()

    trouves = list(signalements(args.racine))

    if args.json:
        print(json.dumps(trouves, ensure_ascii=False, indent=2))
    elif args.resume:
        afficher_resume(trouves)
    else:
        if not trouves:
            print("Contrôle de marque : aucun signalement.")
        else:
            print(f"Contrôle de marque : {len(trouves)} signalement(s).\n")
            for s in trouves:
                print(f"  {s['fichier']}:{s['ligne']}  {s['texte']}")
            print(
                "\nChaque ligne doit être traitée selon NOTICE-AGPL.md :\n"
                "  - nom du produit        -> remplacer par « Trustiatis Secure »\n"
                "  - réseau ou protocole   -> conserver, et ajouter le commentaire « marque-ok: reseau »\n"
                "  - renvoi vers l'éditeur -> retirer ou réécrire vers Trustiatis\n"
                "Une dérogation se pose en commentaire sur la ligne, sous la forme\n"
                "« marque-ok: <raison> », et doit être justifiable."
            )
    return 1 if trouves else 0


if __name__ == "__main__":
    sys.exit(main())
