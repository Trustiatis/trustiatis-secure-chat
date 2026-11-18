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
# Longueur des trois textes du premier écran, et ce n'est pas du zèle. Le Membre a vu,
# dans la version 7.1.2-ts1 sur Android, les textes et les icônes de l'écran d'accueil se
# chevaucher. La cause n'est pas la mise en page, qui est celle de l'amont et n'a pas été
# touchée : ce sont nos textes. `SimpleXInfoLayout` pose des interlignes FIXES, 42, 25 et
# 20 sp, alors que le corps des caractères suit le réglage de taille du système ; et
# `OnboardingShrinkingLayout` rétrécit l'image pour faire tenir le reste, jusqu'à un
# point. Notre « onboarding_first_network » faisait 101 caractères contre 58 à l'amont,
# soit deux lignes de plus sur un téléphone.
#
# Budget de l'amont en v7.1.4, à ne pas dépasser : onboarding_be_free 24, base 29 et
# fr 31 pour onboarding_private_and_secure, base 58 et fr 64 pour onboarding_first_network.
# Toute reformulation de ces trois clés se vérifie contre ces nombres.
TEXTES: dict[str, dict[str, str]] = {
    "base": {
        "app_name": "Trustiatis Secure",
        "onboarding_be_free": "Your confidential\\nchannel",
        "onboarding_private_and_secure": "Encrypted end to end, no account.",
        "onboarding_first_network": "The channel Trustiatis uses\\nfor anything sensitive.",
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
        # Le verrou de l'application et le service d'arrière plan. Ce sont les
        # mentions de marque les plus vues : le service d'arrière plan affiche
        # une notification permanente sur l'appareil.
        "simplex_service_notification_title": "Trustiatis Secure",
        "to_preserve_privacy_simplex_has_background_service_instead_of_push_notifications_it_uses_a_few_pc_battery": "<![CDATA[To improve privacy, <b>this application runs in background</b> instead of using push notifications.]]>",
        "la_notice_title_simplex_lock": "Trustiatis Lock",
        "la_lock_mode": "Trustiatis Lock mode",
        "auth_simplex_lock_turned_on": "Trustiatis Lock turned on",
        "auth_enable_simplex_lock": "Enable Trustiatis Lock",
        "auth_disable_simplex_lock": "Disable Trustiatis Lock",
        "la_notice_to_protect_your_information_turn_on_simplex_lock_you_will_be_prompted_to_complete_authentication_before_this_feature_is_enabled": "To protect your information, turn on Trustiatis Lock.\\nYou will be prompted to complete authentication before this feature is enabled.",
        "auth_device_authentication_is_not_enabled_you_can_turn_on_in_settings_once_enabled": "Device authentication is not enabled. You can turn on Trustiatis Lock via Settings, once you enable device authentication.",
        "lock_not_enabled": "Trustiatis Lock not enabled!",
        "you_can_turn_on_lock": "You can turn on Trustiatis Lock via Settings.",
        # Le nom du produit, partout où il désigne l'application et non le
        # réseau. Un lien, un nom ou une adresse du réseau SimpleX garde son
        # nom : voir RESEAU_AUTORISE dans tools/verifier-marque.py.
        "system_restricted_background_desc": "Trustiatis Secure can\\'t run in background. You will receive the notifications only when the app is running.",
        "ntf_channel_messages": "Trustiatis Secure messages",
        "ntf_channel_calls": "Trustiatis Secure calls",
        "auth_device_authentication_is_disabled_turning_off": "Device authentication is disabled. Turning off Trustiatis Lock.",
        "chat_lock": "Trustiatis Lock",
        "thank_you_for_installing_simplex": "Thank you for installing Trustiatis Secure!",
        "if_you_received_simplex_invitation_link_you_can_open_in_browser": "If you received a SimpleX invitation link, you can open it in your browser:",
        "image_descr_simplex_logo": "Trustiatis Secure logo",
        "about_simplex_chat": "About Trustiatis Secure",
        "about_simplex": "About Trustiatis Secure",
        "open_simplex_chat_to_accept_call": "Open Trustiatis Secure to accept call",
        "email_invite_subject": "Let\\'s talk in Trustiatis Secure",
        "email_invite_body": "Hi!\\nConnect to me via Trustiatis Secure: %s",
        "onboarding_send_1_time_link": "Send the link via any messenger - it\\'s secure. Ask to paste into Trustiatis Secure.",
        # Le thème livré par défaut. Seule l'étiquette change : la valeur
        # d'énumération SIMPLEX est persistée dans les préférences et ne se
        # renomme pas (interdit n° 4 de CONTRIBUTING.md).
        "theme_simplex": "Trustiatis",
        # Menu de la zone de notification, sur le bureau.
        "tray_show": "Show Trustiatis Secure",
        "tray_quit": "Quit Trustiatis Secure",
        "tray_tooltip": "Trustiatis Secure",
        "tray_tooltip_unread": "Trustiatis Secure: %d unread",
    },
    "fr": {
        "app_name": "Trustiatis Secure",
        "onboarding_be_free": "Votre canal\\nconfidentiel",
        "onboarding_private_and_secure": "Chiffré de bout en bout, sans compte.",
        "onboarding_first_network": "Le canal que Trustiatis emploie\\npour tout ce qui est sensible.",
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
        "simplex_service_notification_title": "Trustiatis Secure",
        "to_preserve_privacy_simplex_has_background_service_instead_of_push_notifications_it_uses_a_few_pc_battery": "<![CDATA[Pour protéger votre vie privée, <b>cette application fonctionne en arrière-plan</b> au lieu d\\'utiliser les notifications push.]]>",
        "la_notice_title_simplex_lock": "Verrouillage Trustiatis",
        "la_lock_mode": "Mode de Verrouillage Trustiatis",
        "auth_simplex_lock_turned_on": "Verrouillage Trustiatis activé",
        "auth_enable_simplex_lock": "Activer le Verrouillage Trustiatis",
        "auth_disable_simplex_lock": "Désactiver le Verrouillage Trustiatis",
        "la_notice_to_protect_your_information_turn_on_simplex_lock_you_will_be_prompted_to_complete_authentication_before_this_feature_is_enabled": "Pour protéger vos informations, activez le Verrouillage Trustiatis.\\nUne authentification vous sera demandée avant que cette fonctionnalité ne soit activée.",
        "auth_device_authentication_is_not_enabled_you_can_turn_on_in_settings_once_enabled": "L\\'authentification de l\\'appareil n\\'est pas activée. Vous pouvez activer le Verrouillage Trustiatis dans les Paramètres une fois l\\'authentification de l\\'appareil activée.",
        "lock_not_enabled": "Le Verrouillage Trustiatis n\\'est pas activé !",
        "you_can_turn_on_lock": "Vous pouvez activer le Verrouillage Trustiatis dans les Paramètres.",
        "system_restricted_background_desc": "Trustiatis Secure ne peut pas fonctionner en arrière-plan. Vous ne recevrez les notifications que lorsque l\\'application sera en cours d\\'exécution.",
        "ntf_channel_messages": "Messages Trustiatis Secure",
        "ntf_channel_calls": "Appels Trustiatis Secure",
        "auth_device_authentication_is_disabled_turning_off": "L\\'authentification de l\\'appareil est désactivée. Désactivation du Verrouillage Trustiatis.",
        "chat_lock": "Verrouillage Trustiatis",
        "thank_you_for_installing_simplex": "Merci d\\'avoir installé Trustiatis Secure !",
        "if_you_received_simplex_invitation_link_you_can_open_in_browser": "Si vous avez reçu un lien d\\'invitation SimpleX, vous pouvez l\\'ouvrir dans votre navigateur :",
        "image_descr_simplex_logo": "Logo Trustiatis Secure",
        "about_simplex_chat": "À propos de Trustiatis Secure",
        "about_simplex": "À propos de Trustiatis Secure",
        "open_simplex_chat_to_accept_call": "Ouvrez Trustiatis Secure pour accepter l\\'appel",
        "email_invite_subject": "Discutons sur Trustiatis Secure",
        "email_invite_body": "Salut !\\nConnectez-vous avec moi via Trustiatis Secure : %s",
        "onboarding_send_1_time_link": "Envoyez le lien via n\\'importe quelle messagerie : c\\'est sécurisé. Demandez de le coller dans Trustiatis Secure.",
        "theme_simplex": "Trustiatis",
        "tray_show": "Afficher Trustiatis Secure",
        "tray_quit": "Quitter Trustiatis Secure",
        "tray_tooltip": "Trustiatis Secure",
        "tray_tooltip_unread": "Trustiatis Secure : %d non lus",
    },
}

# Les clés que Trustiatis AJOUTE, parce qu'elles nomment une chose qui n'existe
# pas chez l'amont. Elles portent toutes le préfixe « trustiatis_ », pour qu'une
# recherche les distingue d'un coup des clés de l'amont, et elles s'écrivent à la
# fin du catalogue, dans un bloc à elles : un report de l'amont ne touche donc
# jamais la même région du fichier.
#
# Une langue non traitée n'en a pas besoin : Moko Resources retombe sur le
# catalogue de référence pour une clé absente.
AJOUTS: dict[str, dict[str, str]] = {
    "base": {
        "trustiatis_updates_from_portal": "Updates are published on the Trustiatis portal, downloads.trustiatis.com/trustiatis-secure. The application does not check for them by itself.",
        "trustiatis_non_affiliation": "Trustiatis Secure is an independent product published by Trustiatis, derived from the free software SimpleX Chat and compatible with the SimpleX network. It is not published, supported or endorsed by SimpleX Chat Ltd. SimpleX is a trademark of SimpleX Chat Ltd.",
    },
    "fr": {
        "trustiatis_updates_from_portal": "Les mises à jour sont publiées sur le portail Trustiatis, downloads.trustiatis.com/trustiatis-secure. L\\'application ne les cherche pas d\\'elle même.",
        "trustiatis_non_affiliation": "Trustiatis Secure est un produit indépendant édité par Trustiatis, dérivé du logiciel libre SimpleX Chat et compatible avec le réseau SimpleX. Il n\\'est ni édité, ni soutenu, ni approuvé par SimpleX Chat Ltd. « SimpleX » est une marque de SimpleX Chat Ltd.",
    },
}

MARQUEUR_AJOUTS = "  <!-- Chaînes propres à Trustiatis Secure, absentes de l'amont -->"

# Clés dont la valeur nomme légitimement la marque de l'amont, et pourquoi. La raison est
# posée sur la ligne elle même, à la forme que « verifier-marque.py » reconnaît, pour qu'elle
# reste lisible dans le catalogue et survive à une regénération.
DEROGATIONS: dict[str, str] = {
    "trustiatis_non_affiliation": "mention obligatoire de non affiliation, NOTICE-AGPL.md",
}


def fichier(langue: str) -> Path:
    return CATALOGUES / langue / "strings.xml"


def ajouter(langue: str, ecrire: bool) -> int:
    """Pose les clés propres à Trustiatis, sans doublon et sans rien déplacer."""
    chemin = fichier(langue)
    texte = chemin.read_text(encoding="utf-8")
    manquantes = {
        cle: valeur
        for cle, valeur in AJOUTS.get(langue, {}).items()
        if f'<string name="{cle}"' not in texte
    }
    # Celles qui sont déjà là suivent la table, comme toutes les autres chaînes.
    deja = {
        cle: valeur
        for cle, valeur in AJOUTS.get(langue, {}).items()
        if cle not in manquantes
    }
    corrigees = appliquer(langue, ecrire, deja)[0] if deja else 0
    if not manquantes:
        return corrigees
    texte = fichier(langue).read_text(encoding="utf-8")
    bloc = "".join(
        f'  <string name="{cle}">{valeur}</string>'
        + (f"  <!-- marque-ok: {DEROGATIONS[cle]} -->" if cle in DEROGATIONS else "")
        + "\n"
        for cle, valeur in manquantes.items()
    )
    if MARQUEUR_AJOUTS in texte:
        texte = texte.replace(MARQUEUR_AJOUTS + "\n", MARQUEUR_AJOUTS + "\n" + bloc, 1)
    else:
        texte = texte.replace("</resources>", MARQUEUR_AJOUTS + "\n" + bloc + "</resources>", 1)
    if ecrire:
        chemin.write_text(texte, encoding="utf-8")
    return corrigees + len(manquantes)


def appliquer(langue: str, ecrire: bool, table: dict[str, str] | None = None) -> tuple[int, list[str]]:
    """Met le catalogue en accord avec la table. `table` vaut TEXTES par défaut.

    Le paramètre sert à `ajouter` : une chaîne propre à Trustiatis déjà posée doit suivre
    la table comme n'importe quelle autre. Sans cela, corriger une de ces chaînes dans
    l'outil ne corrigeait rien, puisque `ajouter` ne regardait que sa présence.
    """
    chemin = fichier(langue)
    texte = chemin.read_text(encoding="utf-8")
    changes, absents = 0, []
    for cle, valeur in (TEXTES[langue] if table is None else table).items():
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
        poses = ajouter(langue, ecrire)
        verbe = "écrites" if ecrire else "à écrire"
        ligne = f"{langue:6} {changes:3} chaîne(s) {verbe}, sur {len(TEXTES[langue])}"
        if poses or AJOUTS.get(langue):
            attendus = len(AJOUTS.get(langue, {}))
            ligne += f", et {poses} ajout(s) {verbe} sur {attendus}"
        print(ligne)
        for cle in absents:
            souci += 1
            print(f"       ABSENTE du catalogue : {cle}")
    if souci:
        print(f"\n{souci} clé(s) introuvable(s) : l’amont les a renommées, à reprendre.")
    return 1 if souci else 0


if __name__ == "__main__":
    raise SystemExit(main("--ecrire" in sys.argv))
