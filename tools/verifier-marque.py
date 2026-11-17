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
    ".claude-plugin/",       # manifeste d'un greffon de developpement publie par l'amont en
                             # v7.1.4 : il designe le paquet npm « @simplex-chat/mcp », qui vit
                             # sous packages/, deja exclu, et n'entre dans aucune des deux
                             # applications. Le JSON n'accepte pas de commentaire, donc la
                             # derogation « marque-ok » ne peut pas s'y poser ligne a ligne.
    "plans/",
    "eth/",
    "fastlane/",
)

# Sorties de construction : non versionnees, presentes seulement sur un poste qui a
# deja compile, et elles portent des chemins vers les sources de l'amont. Sans cette
# ligne, le total du controle depend de l'etat du poste et n'est pas reproductible.
EXCLUS_FRAGMENTS = (
    "/.cxx/",
    "/build/",
    "/.gradle/",
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

# Adresses de l'amont, avec leurs chemins. Trois raisons, toutes documentées :
# la décision D-4 garde les serveurs de la communauté, donc les conditions
# d'utilisation de l'opérateur (son PRIVACY.md) doivent rester lisibles ;
# l'historique des nouveautés décrit les versions de l'AMONT, donc renvoyer à
# ses billets est exact ; et les conditions d'utilisation du réseau, avec le commit
# qui les date, sont réellement publiées dans le dépôt de l'amont (décision D-4).
# ATTENTION : cette tolérance ne dit rien des renvois vers l'ÉDITEUR. P-7 bis les
# ferme, et comme ces URL ne sont pas signalées, le contrôle ne les trouvera pas :
# vérifier les appelants à la main, c'est ainsi que l'entrée du client en ligne de
# commande a été retrouvée, après la première mise en oeuvre de P-7 bis.
#
# Cette liste passe EN PREMIER dans l'alternance, et ce n'est pas un détail de
# présentation : une alternance Python essaie ses branches dans l'ordre, et la
# règle des clés pointées consommait « simplex.chat » en laissant le chemin,
# donc le reste de l'URL, derrière elle.
URLS_AUTORISEES = [
    r"simplex\.chat/[^\s\"')]*",
    r"github\.com/simplex-chat/[^\s\"')]*",
]

# Identifiants techniques de l'amont qu'il est NORMAL de rencontrer : noms de classes, de
# services, de types, chemins de paquets. Les renommer casserait le typage ou la
# sérialisation (voir CONTRIBUTING.md, interdit n° 3 et 4).
IDENTIFIANTS_AUTORISES = [
    r"chat\.simplex\.\w+",             # chemins de paquet : common, desktop, app, res
    # Un identifiant du code qui porte la marque : nom de classe, de fonction, de
    # variable, clé de ressource, clé de préférence, nom de fichier de base de
    # données. Les renommer casserait le typage, la sérialisation ou la lecture
    # de ce qui est déjà sur l'appareil, et rendrait conflictuel chaque report.
    #
    # La frontière tient à un détail : la marque doit être COLLÉE à d'autres
    # caractères d'identifiant. « SimplexService », « simplex_v1 » et
    # « openVerifiedSimplexUri » passent ; « SimpleX », « SimpleX Chat » et
    # « Support SimpleX », qui sont les formes vues d'un utilisateur, ne passent
    # pas. Les cas de tools/test-verifier-marque.py fixent cette frontière.
    r"[A-Za-z0-9_]+[Ss]imple[Xx][A-Za-z0-9_]*",
    r"[Ss]imple[Xx][A-Za-z0-9_]+",
    # Clé de préférence, propriété Gradle ou chemin : un jeton pointé tout en
    # minuscules n'est jamais un nom affiché. « SimpleX.exe » et « SimpleX.app »,
    # avec leur capitale, restent donc contrôlés.
    r"simplex\.[a-z][a-z0-9_.]*",
    r"data-simplex-[a-z-]+",             # attribut HTML des aperçus de canal
    r"@SerialName\(\"simplex\"\) SimpleX",  # cas d'énumération de l'opérateur
    r'name="[A-Za-z0-9_]*simplex[A-Za-z0-9_]*"',
    # Clé de ressource. Une clé n'est jamais vue par un utilisateur, et la
    # renommer obligerait à toucher tous les appels Kotlin et Swift qui la
    # lisent : chaque report de l'amont deviendrait conflictuel pour un gain
    # nul. Interdit n° 4 de CONTRIBUTING.md, même raisonnement.
    r"\w*SimpleX\w*\.(?:kt|swift)",    # renvoi vers un fichier source de l'amont
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
    # « simplex » en bas de casse et isolé. La marque s'écrit « SimpleX » : en
    # minuscules, le mot est toujours un schéma d'URI, une valeur sérialisée, un
    # segment de chemin ou une clé. Les tirets ne comptent pas comme des
    # frontières, pour que « simplex-chat » et « simplex-desktop » restent
    # contrôlés, eux.
    r"(?<![A-Za-z0-9_-])simplex(?![A-Za-z0-9_-])",
]

# Désignations du réseau et du protocole : autorisées par la politique de marque.
#
# Elles désignent un objet du protocole, pas le produit. Un lien, un nom, une
# adresse ou un relais du réseau SimpleX restent des objets du réseau SimpleX
# quand c'est Trustiatis Secure qui les affiche : les rebaptiser mentirait à
# l'utilisateur, qui doit reconnaître un lien reçu d'ailleurs et comprendre que
# n'importe quel client du réseau peut l'ouvrir.
RESEAU_AUTORISE = [
    r"SimpleX network", r"réseau SimpleX", r"SimpleX-Netzwerk",
    r"SimpleX address", r"adresses? SimpleX",
    r"SimpleX (?:invitation |group |channel )?links?",
    r"liens? (?:d\W{0,2}invitation |de groupe |de canal |pour la voie )?SimpleX",
    r"SimpleX (?:public )?names?",
    r"noms? (?:du |de )?(?:canal |groupe |publics? )?SimpleX",
    r"SimpleX domain", r"domaine SimpleX",
    r"SimpleX relay", r"relais SimpleX",
    r"SimpleX protocols?", r"protocoles? SimpleX",
    r"SimpleX contacts?", r"contacts? SimpleX",
    r"SimpleX servers?", r"serveurs? SimpleX",
    r"SimpleX one-time invitation", r"invitation (?:unique )?SimpleX(?: à usage unique)?",
    r"compatible (?:avec le réseau |with (?:the )?)SimpleX",
    r"SimpleX Chat Ltd",               # attribution, obligation de licence
    r"\(c\) \d{4}-\d{4} SimpleX Chat",  # mention de copyright, obligation de licence
    r"Copyright .{0,12}\d{4} SimpleX Chat\. All rights reserved\.",   # idem, en tête de fichier
]

# Désignations d'un opérateur du réseau, et faits historiques le concernant.
#
# La décision D-4 garde les serveurs de la communauté, dont ceux de SimpleX Chat
# Ltd et de Flux. L'application doit donc pouvoir nommer l'opérateur dont elle
# propose les serveurs : c'est une information que l'utilisateur a le droit
# d'avoir, et l'attribuer à Trustiatis serait faux. Même raison pour l'audit de
# sécurité mené sur le travail de l'amont : il a eu lieu, Trustiatis ne l'a pas
# commandé et ne peut pas s'en attribuer le mérite.
OPERATEUR_AUTORISE = [
    r"SimpleX Chat (?:servers?|and Flux|et Flux)",
    r"serveurs? (?:de )?SimpleX Chat",
    r"SimpleX Chat security was audited",
    r"(?:La s|s)écurité de SimpleX Chat a été auditée",
    r"OperatorTag\.SimpleX",             # étiquette de l'opérateur, sérialisée
    r"tradeName = \"SimpleX Chat\"",      # sa raison sociale, qui est la sienne
    r"SimpleX Chat is the first communication network",   # sa propre présentation
    r"Self-host SimpleX servers",
]

# Renvois vers l'éditeur amont dont les ÉCRANS SONT NEUTRALISÉS.
#
# **P-7 est renversée.** Le Membre l'a décidé le 2026-11-15 au soir, après avoir
# lu la réserve que j'avais portée au dossier : « Invest in SimpleX Chat » et
# « Own a part of SimpleX Chat » renvoient à un financement participatif en
# CAPITAL sur Wefunder, c'est à dire à une offre de titres, et relayer une offre
# de titres d'un tiers à ses clients n'est pas relayer un lien de don. L'appel au
# soutien de l'amont ne doit donc pas apparaître.
#
# Les points d'entrée sont fermés dans le code, pas ici : `crowdfundingAvailable()`
# rend faux, l'entrée des badges et la section « soutenir le projet » des réglages
# ne sont plus posées. Plus aucun écran ne montre ces chaînes.
#
# Pourquoi cette liste survit, alors. Les chaînes, elles, **restent dans les
# quarante-deux catalogues**, parce que les retirer voudrait dire toucher
# quarante-deux fichiers que l'amont réécrit à chaque version, pour un gain nul :
# une chaîne qu'aucun écran ne lit n'est pas vue. Elles sont donc tolérées ici, et
# le nom de la liste dit désormais la vérité : neutralisées, pas conservées.
#
# Ce que la décision coûte, et qui est consigné pour qu'on ne le redécouvre pas :
# le badge était un ACHAT qui rendait un service réel, des fichiers de 2 à 5 Gio
# conservés de 7 à 21 jours. Fermer son entrée retire cette capacité de
# l'interface.
#
# Toute ligne ajoutée ici doit correspondre à un écran effectivement neutralisé.
# Ce n'est pas une liste où l'on range ce qui résiste.
EDITEUR_NEUTRALISE = [
    r"[Ss]upport(?:s|ed)? SimpleX(?: Chat)?",
    r"[Ss]outen(?:ez|ir|u) SimpleX(?: Chat)?", r"soutient SimpleX(?: Chat)?",
    r"Invest in SimpleX Chat", r"Own a part of SimpleX Chat",
    r"[Dd]evenez copropriétaire de SimpleX Chat",
    r"invested in SimpleX Chat crowdfunding",
    r"investi dans le financement participatif de SimpleX Chat",
    r"SimpleX Team", r"[ÉéEe]quipe SimpleX",
    r"Install(?:er)? SimpleX Chat (?:for|pour) terminal",
    r"SimpleX Chat develop(?:ers|eurs)", r"développeurs de SimpleX Chat",
    # L'argumentaire du financement participatif, écrit en dur dans
    # WhatsNewView.kt et non dans un catalogue. Énuméré plutôt que couvert par
    # un motif large, pour qu'on voie exactement ce que la décision conserve.
    r"SimpleX users have been more than doubling",
    r"bet on SimpleX success",
    r"new users to SimpleX Chat",
    r"SimpleX Chat plans to earn",
    r"make SimpleX Chat and network profitable",
]

MOTIF_MARQUE = re.compile(r"simplex", re.IGNORECASE)
MOTIF_AUTORISE = re.compile(
    "|".join(
        URLS_AUTORISEES
        + IDENTIFIANTS_AUTORISES
        + RESEAU_AUTORISE
        + OPERATEUR_AUTORISE
        + EDITEUR_NEUTRALISE
    ),
    re.IGNORECASE,
)
MOTIF_AUTORISE_CASSE = re.compile("|".join(IDENTIFIANTS_AUTORISES_CASSE))

# Marqueur de dérogation explicite, à poser en commentaire sur la ligne, avec une raison.
MOTIF_DEROGATION = re.compile(r"marque-ok\s*:\s*\S+")

# Commentaire XML. Il n'atteint jamais un écran, et les catalogues de l'amont en
# sont ponctués pour dire quel fichier Kotlin lit quelle série de clés. Les
# réécrire ferait conflit à chaque report sans rien changer pour personne.
MOTIF_COMMENTAIRE_XML = re.compile(r"<!--.*?-->", re.DOTALL)


# Ligne de commentaire d'un langage à accolades. Elle n'atteint pas plus un
# écran qu'un commentaire XML, et le dépôt est un fork DÉCLARÉ de SimpleX Chat :
# un commentaire qui le dit est exact. On ne reconnaît que la ligne entièrement
# commentée, pas un commentaire en fin de ligne, pour que du code suivi d'une
# note reste contrôlé. Réserve connue : une ligne de chaîne multiligne qui
# commence par « // » serait prise pour un commentaire.
MOTIF_LIGNE_COMMENTEE = re.compile(r"^\s*(?://|/\*|\*)")


def _sans_commentaires_xml(texte: str) -> str:
    """Vide les commentaires sans déplacer une seule ligne.

    Le contrôle est ligne à ligne et rapporte des numéros de ligne : un
    commentaire qui court sur plusieurs lignes doit donc être remplacé par
    autant de lignes vides, et non supprimé.
    """
    return MOTIF_COMMENTAIRE_XML.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), texte)


def dans_perimetre(chemin: str) -> bool:
    if chemin in EXCLUS_FICHIERS:
        return False
    if os.path.basename(chemin) in EXCLUS_FICHIERS:
        return False
    for f in EXCLUS_FRAGMENTS:
        if f in "/" + chemin:
            return False
    for p in EXCLUS_PREFIXES:
        if chemin.startswith(p):
            return False
    return os.path.splitext(chemin)[1] in EXTENSIONS


def ligne_signalee(ligne: str, chemin: str = "", brute: str | None = None) -> bool:
    """La décision du contrôle, sur une ligne.

    Elle est ici, et pas dans la boucle, pour que `test-verifier-marque.py`
    l'appelle au lieu de la réécrire : la version réécrite avait divergé dès la
    première règle ajoutée.

    `brute` est la ligne telle qu'elle est écrite dans le fichier, avant que les
    commentaires XML ne soient vidés. Sans elle, la dérogation « marque-ok »
    était inutilisable dans un catalogue, c'est à dire dans les seuls fichiers
    où une chaîne peut avoir besoin de nommer la marque de l'amont : le
    commentaire qui la portait avait disparu avant l'examen de la ligne.
    """
    if not MOTIF_MARQUE.search(ligne):
        return False
    if MOTIF_DEROGATION.search(brute if brute is not None else ligne):
        return False
    if chemin.endswith((".kt", ".kts", ".swift")) and MOTIF_LIGNE_COMMENTEE.match(ligne):
        return False
    if chemin.endswith((".xml", ".html")) and MOTIF_COMMENTAIRE_XML.match(ligne.strip()):
        return False
    # L'ordre des deux substitutions compte, et il a déjà mordu : la règle du
    # « simplex » isolé en bas de casse retirait le mot de « https://simplex.chat/ »
    # et laissait « https://.chat/blog/...-simplex-chat-... », que plus aucune
    # règle ne reconnaissait. Les motifs les plus longs passent donc d'abord.
    reste = MOTIF_AUTORISE.sub("", ligne)
    reste = MOTIF_AUTORISE_CASSE.sub("", reste)
    return bool(MOTIF_MARQUE.search(reste))


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
                    contenu = f.read()
            except OSError:
                continue
            affiche = contenu.splitlines()
            if relatif.endswith((".xml", ".html")):
                contenu = _sans_commentaires_xml(contenu)
            lignes = contenu.splitlines()
            for n, ligne in enumerate(lignes, 1):
                if ligne_signalee(ligne, relatif, affiche[n - 1]):
                    yield {
                        "fichier": relatif,
                        "ligne": n,
                        "texte": affiche[n - 1].strip()[:160],
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
