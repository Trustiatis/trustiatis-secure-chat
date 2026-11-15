"""Pose les textes de Trustiatis Secure dans les catalogues de chaînes.

Pourquoi cet outil existe
-------------------------
Les mêmes clés existent dans 42 catalogues. Les éditer à la main, c'est en
oublier, et c'est laisser une version anglaise d'origine sous une clé qu'on
croit traduite. Ce script tient la liste des clés que Trustiatis réécrit, avec
leur texte par langue, et il refuse d'écrire une clé qu'il ne trouve pas.

Décision D-6 : le fichier de référence (anglais) et le français d'abord, les
autres langues par vagues. Une langue non traitée garde le texte de l'amont,
ce qui est visible et assumé, plutôt qu'un texte à moitié réécrit.

Usage
-----
    python3 tools/textes-trustiatis.py            # diagnostic, n'écrit rien
    python3 tools/textes-trustiatis.py --ecrire   # écrit
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
CATALOGUES = RACINE / "apps/multiplatform/common/src/commonMain/moko-resources"

# Les textes. Une clé par ligne, une langue par dictionnaire. L'apostrophe
# s'échappe en \\' comme partout dans ces fichiers, c'est la convention
# d'Android et l'amont la suit.
TEXTES: dict[str, dict[str, str]] = {
    "base": {
        "app_name": "Trustiatis Secure",
        "onboarding_be_free": "Your confidential\\nchannel",
        "onboarding_private_and_secure": "Encrypted end to end, with no account.",
        "onboarding_first_network": "The channel Trustiatis uses for anything sensitive,\\nand the one you can use inside your own company.",
        "why_simplex_is_built": "Why this application, and how it works.",
        "badges_why_simplex_is_built": "Why this application, and how it works.",
        "why_built_heading": "A channel that cannot be read in transit.",
        "why_built_p1": "Trustiatis works on your infrastructure, your servers and your accounts. That work produces things that must never travel through an ordinary mailbox: access credentials, audit findings, network diagrams, recovery keys. Email was not built to carry them, and the usual messengers ask you to hand over a phone number before they will carry anything at all.",
        "why_built_p2": "This application is a Trustiatis build of an open source messenger that runs on the SimpleX network. The name, the colours and these explanations are ours. The cryptography and the protocol are untouched, and the whole source code is published, so that nobody has to take our word for any of it.",
        "why_built_p3": "There are no accounts, no usernames and no phone numbers. You connect to someone once, through a link or a QR code used a single time, and that connection then has delivery queues of its own. Messages are encrypted on your device and decrypted on your correspondent\\'s. The relay servers pass them on without knowing who is talking to whom, and Trustiatis runs none of those servers: we could not read your messages even if we were asked to.",
        "why_built_p4": "You are not limited to talking with the Trustiatis departments. The same application carries your own internal conversations, between your colleagues, with the same guarantees and at no extra cost. There is no licence to buy, no account to create, and no administrator reading along.",
        "why_built_p5": "Because the network holds no identity of its own, identity is proved outside it. Every Trustiatis contact presents their identity card from id.trustiatis.com when a conversation opens, and we ask you to do the same with the people you talk to. Ask for the card, check that the name and the role match the person you expect, and discuss nothing sensitive before you have it.",
        "why_built_p6": "This is the channel to prefer with Trustiatis, and for sensitive information and data it is the only one we use. Nobody at Trustiatis will ever ask you for a password, a key or an access code by email, by telephone, or through another messenger. A request that reaches you by another route did not come from us.",
        "why_built_p7": "Some second factors will be delivered here as well. A one time code that arrives in this application has passed neither through your mailbox nor through the mobile network, which are the two places such codes are usually stolen.",
        "why_built_tagline": "Open the application, ask for the card, then speak freely.",
    },
    "fr": {
        "app_name": "Trustiatis Secure",
        "onboarding_be_free": "Votre canal\\nconfidentiel",
        "onboarding_private_and_secure": "Chiffré de bout en bout, sans compte.",
        "onboarding_first_network": "Le canal que Trustiatis emploie pour tout ce qui est sensible,\\net celui dont vous pouvez vous servir en interne.",
        "why_simplex_is_built": "Pourquoi cette application, et comment elle fonctionne.",
        "badges_why_simplex_is_built": "Pourquoi cette application, et comment elle fonctionne.",
        "why_built_heading": "Un canal qu\\'on ne peut pas lire en route.",
        "why_built_p1": "Trustiatis travaille sur votre infrastructure, vos serveurs et vos comptes. Ce travail produit des choses qui n\\'ont rien à faire dans une boîte aux lettres ordinaire : des identifiants d\\'accès, des constats d\\'audit, des schémas de réseau, des clés de récupération. Le courriel n\\'a pas été conçu pour les transporter, et les messageries habituelles réclament un numéro de téléphone avant de transporter quoi que ce soit.",
        "why_built_p2": "Cette application est une version Trustiatis d\\'une messagerie libre qui fonctionne sur le réseau SimpleX. Le nom, les couleurs et ces explications sont les nôtres. La cryptographie et le protocole ne sont pas touchés, et l\\'intégralité du code source est publiée, pour que personne n\\'ait à nous croire sur parole.",
        "why_built_p3": "Il n\\'y a ni compte, ni identifiant, ni numéro de téléphone. Vous vous connectez une fois à quelqu\\'un, par un lien ou un code QR à usage unique, et cette liaison dispose ensuite de ses propres files de remise. Les messages sont chiffrés sur votre appareil et déchiffrés sur celui de votre correspondant. Les serveurs relais les transmettent sans savoir qui parle à qui, et Trustiatis n\\'en exploite aucun : nous ne pourrions pas lire vos messages, même si on nous le demandait.",
        "why_built_p4": "Vous n\\'êtes pas limité à vos échanges avec les départements de Trustiatis. La même application porte vos conversations internes, entre vos collègues, avec les mêmes garanties et sans coût supplémentaire. Aucune licence à acheter, aucun compte à créer, aucun administrateur qui lise par dessus votre épaule.",
        "why_built_p5": "Comme le réseau ne détient aucune identité, l\\'identité se prouve en dehors de lui. Chaque interlocuteur de Trustiatis présente sa carte d\\'identité issue de id.trustiatis.com à l\\'ouverture d\\'une conversation, et nous vous demandons d\\'en faire autant avec vos propres interlocuteurs. Réclamez la carte, vérifiez que le nom et la fonction correspondent à la personne attendue, et n\\'abordez rien de sensible avant de l\\'avoir.",
        "why_built_p6": "C\\'est le canal à privilégier avec Trustiatis, et pour les informations et les données sensibles c\\'est le seul que nous employons. Personne chez Trustiatis ne vous demandera jamais un mot de passe, une clé ou un code d\\'accès par courriel, par téléphone ou par une autre messagerie. Une demande qui vous parvient par une autre voie ne vient pas de nous.",
        "why_built_p7": "Certains seconds facteurs seront également remis ici. Un code à usage unique qui arrive dans cette application n\\'est passé ni par votre boîte aux lettres, ni par le réseau mobile, qui sont les deux endroits où ces codes se volent d\\'habitude.",
        "why_built_tagline": "Ouvrez l\\'application, réclamez la carte, puis parlez librement.",
    },
}


def fichier(langue: str) -> Path:
    return CATALOGUES / langue / "strings.xml"


def appliquer(langue: str, ecrire: bool) -> tuple[int, list[str]]:
    chemin = fichier(langue)
    texte = chemin.read_text(encoding="utf-8")
    changes, absents = 0, []
    for cle, valeur in TEXTES[langue].items():
        motif = re.compile(
            r'(<string name="' + re.escape(cle) + r'"[^>]*>)(.*?)(</string>)', re.DOTALL
        )
        trouve = motif.search(texte)
        if not trouve:
            absents.append(cle)
            continue
        if trouve.group(2) == valeur:
            continue
        changes += 1
        texte = texte[: trouve.start(2)] + valeur + texte[trouve.end(2) :]
    if ecrire and changes:
        chemin.write_text(texte, encoding="utf-8")
    return changes, absents


def main(ecrire: bool) -> int:
    souci = 0
    for langue in TEXTES:
        changes, absents = appliquer(langue, ecrire)
        verbe = "écrites" if ecrire else "à écrire"
        print(f"{langue:6} {changes:3} chaîne(s) {verbe}, sur {len(TEXTES[langue])}")
        for cle in absents:
            souci += 1
            print(f"       ABSENTE du catalogue : {cle}")
    if souci:
        print(f"\n{souci} clé(s) introuvable(s) : l’amont les a renommées, à reprendre.")
    return 1 if souci else 0


if __name__ == "__main__":
    raise SystemExit(main("--ecrire" in sys.argv))
