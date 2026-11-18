"""Génère les ressources de marque des arbres de code depuis `branding/`.

Pourquoi cet outil existe
-------------------------
Les deux arbres de code, le couple Android et Desktop d'un côté, iOS de l'autre,
ne partagent aucune ressource : l'audit de Phase 1 l'a établi au chapitre 4. La
même icône doit donc exister en une vingtaine de fichiers, à des tailles et sous
des noms différents. Les tenir à la main, c'est garantir qu'ils divergeront, et
c'est rendre chaque report de l'amont pénible.

Règle : **aucune ressource de marque ne se modifie à la main dans les arbres de
code.** On modifie `branding/`, puis on propage.

Le dessin vient de `branding/logo/monogramme.png`, lui même reconstruit par
`design-system/detramer.py` depuis le fichier d'origine de la charte. Les
couleurs viennent de `branding/colors/palette.json`. Rien n'est inventé ici.

Usage
-----
    python3 tools/propager-branding.py            # diagnostic, n'écrit rien
    python3 tools/propager-branding.py --ecrire   # génère
"""

from __future__ import annotations

import hashlib
import json
import struct
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parent.parent
BRANDING = RACINE / "branding"
MULTI = RACINE / "apps" / "multiplatform"

# Une icône adaptative d'Android dessine sur 108 unités mais n'en montre que 72 :
# le reste est rogné par la forme que choisit le lanceur. Le dessin doit donc
# tenir dans le carré central.
ZONE_SURE = 72 / 108

# Densités d'Android et taille de l'icône héritée, celle d'avant les icônes
# adaptatives, qui est montrée telle quelle.
DENSITES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# L'icône de notification d'Android se dessine sur 24 unités de densité.
DENSITES_NTF = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}

# ---------------------------------------------------------------------------
# Géométrie, relevée sur les ressources de l'amont et non choisie
# ---------------------------------------------------------------------------
# Le problème que ces quatre nombres règlent : le dessin de l'amont est un carré
# (212 sur 212 dans une toile de 432), le nôtre est un rectangle large (1244 sur
# 909, rapport 1,37). Caler la LARGEUR du nôtre sur celle du sien le rend plus
# grand dans toutes les directions utiles, parce qu'un rectangle large déborde
# du cercle qui contenait le carré. Et l'application se sert de ce cercle : dans
# `addLogo`, le QR code reçoit un disque blanc de rayon `taille / 2` puis le
# dessin par dessus, à `taille x 1,5`. Avec la largeur calée sur l'amont, les
# coins de notre dessin sortaient du disque de 24 %, ce qui se voit sur le QR.
#
# La règle appliquée ici cale donc le CERCLE CIRCONSCRIT au dessin, et non sa
# largeur. Quelle que soit la forme du monogramme, il occupe exactement la place
# que l'amont avait prévue. Les valeurs viennent de la mesure des fichiers de
# `upstream-v7.1.2`, pas du jugement :
#
#   mipmap-xxxhdpi/icon_foreground.png  dessin 212x212 sur 432  -> rayon 0,347
#   mipmap-xxxhdpi/icon.png             plaque 152 sur 192      -> 0,792, coin 0,065
#   mipmap-xxxhdpi/icon_round.png       plaque ronde            -> 0,927
#   drawable-*/ntf_icon.png             dessin 88x88 sur 96     -> largeur 0,917
#
# Le rayon retenu n'est pas le 0,347 de l'amont mais 1/3, et ce tiers n'est pas
# un arrondi : c'est la valeur que `addLogo` impose. Il met le dessin à l'échelle
# 1,5 fois la taille du disque, dont le rayon vaut la moitié de cette taille ;
# pour que le cercle circonscrit tienne dans le disque il faut donc
# 1,5 x rayon <= 0,5, soit rayon <= 1/3. L'amont est 4 % au dessus et s'en tire
# parce que son dessin est une croix, dont les coins sont vides ; le nôtre est
# plein et ses angles sortaient du disque.
#
# Le tiers fait tenir le dessin dans le disque en le lui faisant toucher : ses
# quatre angles tombent exactement sur le bord, ce qui se voit. On garde donc
# une marge de 12 % à l'intérieur du disque. C'est le seul nombre de ce fichier
# qui relève du jugement, et il ne porte que sur un blanc tournant.
MARGE_DISQUE = 0.88
RAYON_CIRCONSCRIT = MARGE_DISQUE / 3
PLAQUE = 0.792
PLAQUE_RAYON_COIN = 0.065
PLAQUE_RONDE = 0.927
NTF_LARGEUR = 0.917


def palette() -> dict:
    return json.loads((BRANDING / "colors" / "palette.json").read_text())


def _hex(valeur: str) -> tuple[int, int, int, int]:
    valeur = valeur.lstrip("#")
    return (*(int(valeur[i : i + 2], 16) for i in (0, 2, 4)), 255)


def _dessin() -> Image.Image:
    """Le monogramme, rogné à son dessin, sans marge."""
    im = Image.open(BRANDING / "logo" / "monogramme.png").convert("RGBA")
    return im.crop(im.getbbox())


def _pose(dessin: Image.Image, cote: int, part: float, fond=None) -> Image.Image:
    """Centre le dessin sur un carré de `cote`, en occupant `part` de sa largeur."""
    toile = Image.new("RGBA", (cote, cote), fond or (0, 0, 0, 0))
    large = max(1, round(cote * part))
    haut = max(1, round(dessin.height * large / dessin.width))
    if haut > large:
        haut, large = large, max(1, round(dessin.width * large / dessin.height))
    reduit = dessin.resize((large, haut), Image.LANCZOS)
    toile.alpha_composite(reduit, ((cote - large) // 2, (cote - haut) // 2))
    return toile


def _pose_cercle(dessin: Image.Image, cote: int, rayon: float, fond=None) -> Image.Image:
    """Centre le dessin pour que son cercle circonscrit ait `rayon x cote`.

    C'est la mesure qui compte, et non la largeur : voir le commentaire de
    RAYON_CIRCONSCRIT. Un carré et un rectangle large posés par cette règle
    tiennent dans le même disque, donc à la même place dans le QR code.
    """
    from math import hypot

    toile = Image.new("RGBA", (cote, cote), fond or (0, 0, 0, 0))
    diagonale = 2 * rayon * cote
    facteur = diagonale / hypot(dessin.width, dessin.height)
    large = max(1, round(dessin.width * facteur))
    haut = max(1, round(dessin.height * facteur))
    reduit = dessin.resize((large, haut), Image.LANCZOS)
    toile.alpha_composite(reduit, ((cote - large) // 2, (cote - haut) // 2))
    return toile


def _pose_largeur(dessin: Image.Image, cote: int, part: float) -> Image.Image:
    """Centre le dessin sur un carré de `cote`, sa largeur occupant `part`."""
    toile = Image.new("RGBA", (cote, cote), (0, 0, 0, 0))
    large = max(1, round(cote * part))
    haut = max(1, round(dessin.height * large / dessin.width))
    reduit = dessin.resize((large, haut), Image.LANCZOS)
    toile.alpha_composite(reduit, ((cote - large) // 2, (cote - haut) // 2))
    return toile


def _plaque(cote: int, part: float, rayon_coin: float, couleur) -> Image.Image:
    """La plaque de fond : un carré arrondi centré, le reste transparent.

    L'amont ne pose pas son dessin sur un carré plein à bord perdu, il le pose
    sur une plaque arrondie entourée de transparence. Un carré plein à bord
    perdu est ce que le lanceur affiche tel quel, sans rognage ni ombre, et
    c'est ce qui donnait une vignette noire dure.
    """
    im = Image.new("RGBA", (cote, cote), (0, 0, 0, 0))
    marge = round(cote * (1 - part) / 2)
    boite = (marge, marge, cote - marge - 1, cote - marge - 1)
    rayon = max(1, round((boite[2] - boite[0]) * rayon_coin))
    ImageDraw.Draw(im).rounded_rectangle(boite, radius=rayon, fill=couleur)
    return im


def _silhouette(dessin: Image.Image) -> Image.Image:
    """Le dessin en blanc plein, ne gardant que sa transparence.

    Android n'affiche d'une icône de notification que son canal alpha, qu'il
    teinte lui même. Une icône en couleur y apparaît comme un carré plein.
    """
    sil = Image.new("RGBA", dessin.size, (255, 255, 255, 0))
    sil.putalpha(dessin.getchannel("A"))
    return sil


def _rond(im: Image.Image) -> Image.Image:
    """Masque circulaire, pour les lanceurs qui demandent une icône ronde."""
    masque = Image.new("L", im.size, 0)
    ImageDraw.Draw(masque).ellipse((0, 0, im.width - 1, im.height - 1), fill=255)
    sortie = im.copy()
    sortie.putalpha(masque)
    return sortie


def _icns(images: dict[str, Image.Image]) -> bytes:
    """Écrit un .icns dont chaque taille est un PNG.

    Pillow ne sait enregistrer ce format que sur macOS, où il délègue à
    `iconutil`. Le format est pourtant trivial : un en-tête, puis une suite de
    blocs typés. Les types en PNG sont lus depuis macOS 10.7.
    """
    blocs = b""
    for type_, im in images.items():
        from io import BytesIO

        tampon = BytesIO()
        im.save(tampon, "PNG", optimize=True)
        donnees = tampon.getvalue()
        blocs += type_.encode("ascii") + struct.pack(">I", len(donnees) + 8) + donnees
    return b"icns" + struct.pack(">I", len(blocs) + 8) + blocs


# ---------------------------------------------------------------------------
# Couleurs de thème
# ---------------------------------------------------------------------------
# Le problème : l'amont ne définit pas seulement une couleur de marque, il
# définit aussi une dizaine de teintes dérivées (bulles de message, citations,
# variantes) dont la clarté et la saturation ont été choisies pour rester
# lisibles sur chaque fond. Les remplacer par des valeurs choisies au jugé
# casserait ce travail, et inventer des couleurs est interdit par le chapitre 4
# du cahier des charges.
#
# La règle appliquée ici ne choisit donc rien : chaque teinte dérivée **garde la
# clarté et la saturation de l'amont** et ne reçoit que la **teinte** de la
# couleur de marque de Trustiatis. Le contraste dessiné par l'amont est
# conservé au chiffre près, seule la couleur change. C'est mesurable, et c'est
# pour cela que la conversion OkLCh est faite ici plutôt qu'à l'oeil.


def _vers_lineaire(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _vers_srgb(c: float) -> float:
    return c * 12.92 if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def _oklab(hexa: str) -> tuple[float, float, float]:
    r, v, b = (_vers_lineaire(int(hexa[i : i + 2], 16) / 255) for i in (0, 2, 4))
    l = (0.4122214708 * r + 0.5363325363 * v + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * v + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * v + 0.6299787005 * b) ** (1 / 3)
    return (
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )


def _canaux(L: float, a: float, b: float) -> tuple[float, float, float]:
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    canaux = (
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
    return canaux


def _vers_hexa(L: float, a: float, b: float) -> str:
    """Rend la couleur, après avoir ramené sa saturation dans le gamut sRGB.

    Précaution, et non correctif d'un défaut observé : avec la palette actuelle
    aucune des teintes produites ne sort du gamut, la clarté et la saturation de
    l'amont sont restituées à 0,001 près. Mais une marque plus saturée les en
    sortirait, et l'écrêtage canal par canal rendrait alors une couleur qui n'a
    plus ni la clarté ni la saturation demandées, sans que rien ne le signale.
    On réduit donc la saturation, et elle seule, jusqu'à ce que les trois canaux
    tiennent : la clarté, qui porte le contraste, est préservée.
    """
    facteur = 1.0
    if any(c < -1e-4 or c > 1 + 1e-4 for c in _canaux(L, a, b)):
        bas, haut = 0.0, 1.0
        for _ in range(40):
            facteur = (bas + haut) / 2
            if any(c < -1e-4 or c > 1 + 1e-4 for c in _canaux(L, a * facteur, b * facteur)):
                haut = facteur
            else:
                bas = facteur
        facteur = bas
    return "".join(
        f"{max(0, min(255, round(_vers_srgb(max(0.0, min(1.0, c))) * 255))):02X}"
        for c in _canaux(L, a * facteur, b * facteur)
    )


def teinte_de(source_hexa: str, marque_hexa: str) -> str:
    """La couleur de l'amont, avec la teinte de la marque et son propre contraste.

    Un gris reste gris : sans chroma, il n'y a pas de teinte à remplacer, et en
    donner une colorerait une couleur que l'amont a voulue neutre.
    """
    from math import atan2, cos, hypot, sin

    L, a, b = _oklab(source_hexa.lstrip("#").upper()[:6])
    chroma = hypot(a, b)
    if chroma < 0.002:
        return source_hexa.lstrip("#").upper()[:6]
    _, ma, mb = _oklab(marque_hexa.lstrip("#").upper()[:6])
    angle = atan2(mb, ma)
    return _vers_hexa(L, chroma * cos(angle), chroma * sin(angle))


# Les quatre thèmes de l'amont, et le rôle de chaque couleur de marque qu'ils
# portent. Les couleurs vives et de titre sont prises **telles quelles** dans la
# palette : ce sont les couleurs de la marque, elles ne se dérivent pas. Le
# choix de la variante par thème est celui que la palette nomme elle même, et
# celui que fait id.trustiatis.com, qui est sombre : la variante claire sur un
# fond sombre, la variante pleine sur un fond clair.
#
# Tout le reste, les bulles et les citations, passe par `teinte_de` : la clarté
# et la saturation restent celles de l'amont, seule la teinte devient la nôtre.
THEMES_AMONT = {
    "Dark": {
        "primary": ("palette", "primaire_clair"),
        "primaryVariant": ("palette", "primaire"),
        "title": ("palette", "primaire_clair"),
        "primaryVariant2": ("derive", "#18262E"),
        "sentMessage": ("derive", "#18262E"),
        "sentQuote": ("derive", "#1D3847"),
    },
    "Light": {
        "primary": ("palette", "primaire"),
        "primaryVariant": ("palette", "primaire_fonce"),
        "title": ("palette", "primaire"),
        "primaryVariant2": ("derive", "#E9F7FF"),
        "sentMessage": ("derive", "#E9F7FF"),
        "sentQuote": ("derive", "#D6F0FF"),
    },
    # Le thème que l'amont nomme « SimpleX » et qui est sa signature visuelle.
    # Son étiquette est déjà celle de Trustiatis ; son fond vient donc du thème
    # sombre de la palette, qui est celui de id.trustiatis.com.
    "Signature": {
        "primary": ("palette", "primaire_clair"),
        "primaryVariant": ("palette", "primaire"),
        "secondaryVariant": ("derive", "#2C464D"),
        "background": ("sombre", "fond"),
        "surface": ("sombre", "fond_secondaire"),
        "title": ("palette", "primaire_clair"),
        "primaryVariant2": ("derive", "#172941"),
        "sentMessage": ("derive", "#172941"),
        "sentQuote": ("derive", "#1C3A57"),
        # Seul thème de l'amont dont les bulles RECUES sont teintées : c'est son
        # thème de marque, et il les met au bleu. Dans les trois autres, elles
        # sont des gris neutres, que `teinte_de` laisse d'ailleurs intacts. Les
        # dériver ici, et nulle part ailleurs, est ce qui fait que l'écran de
        # conversation ne mélange plus deux teintes.
        "receivedMessage": ("derive", "#25283A"),
        "receivedQuote": ("derive", "#36394A"),
    },
    "Black": {
        "primary": ("palette", "primaire_clair"),
        "primaryVariant": ("palette", "primaire"),
        "title": ("palette", "primaire_clair"),
        "primaryVariant2": ("derive", "#243747"),
        "sentMessage": ("derive", "#18262E"),
        "sentQuote": ("derive", "#1D3847"),
    },
}

CHEMIN_COULEURS = (
    MULTI / "common/src/commonMain/kotlin/chat/simplex/common/ui/theme/ColorTrustiatis.kt"
)


def couleurs_kotlin() -> bytes:
    p = palette()
    marque = p["marque"]["primaire"]["hex"]
    lignes = [
        "package chat.simplex.common.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "",
        "// Fichier GÉNÉRÉ par tools/propager-branding.py depuis branding/colors/palette.json.",
        "// Ne pas modifier à la main : la prochaine propagation écraserait la correction.",
        "//",
        "// Les couleurs de marque sont reprises telles quelles dans la palette. Les teintes",
        "// dérivées gardent la clarté et la saturation de l'amont et ne reçoivent que notre",
        "// teinte, pour que le contraste qu'il a dessiné soit conservé au chiffre près.",
        "//",
        "// La paire sentinelle reste intacte : le #0088ff des catalogues n'est pas une couleur",
        "// affichée, c'est la marque que ReadableTextWithLink cherche pour la remplacer par",
        "// MaterialTheme.colors.primary. Changer l'une sans l'autre éteindrait les liens.",
        "",
    ]
    # L'encre des QR codes. Elle était juste, et écrite à la main : les trois
    # signatures de QRCode.kt la portaient en littéral. Elle est générée ici
    # pour que la règle qui la produit soit visible et rejouable.
    #
    # Ce n'est pas une couleur de la palette et ce n'est pas non plus celle de
    # l'amont : c'est le #062D56 de l'amont passé par `teinte_de`, donc sa
    # clarté et sa saturation avec notre teinte. Cela compte ici plus
    # qu'ailleurs, parce que la lecture d'un QR dépend du contraste : l'amont
    # est à 13,8 contre 1 sur blanc, la dérivée à 13,4. Le sauge profond de la
    # palette, lui, n'est qu'à 5,3, et serait un vrai recul.
    QR_AMONT = "062D56"
    lignes.append(f"// Encre des QR codes : l'amont {QR_AMONT} à notre teinte, contraste conservé")
    lignes.append(
        f"val TrustiatisQrEncre = Color(0xFF{teinte_de(QR_AMONT, marque)})  // amont #{QR_AMONT}, teinte de la marque"
    )
    lignes.append("")

    # L'incognito. Ce n'est pas une couleur de decor : c'est le signal qui
    # distingue un profil incognito partout dans l'application, et l'amont lui
    # donne une teinte franchement distincte de la sienne. Le sauge l'effacerait,
    # puisque le sauge est deja la couleur de tout le reste.
    #
    # Pourquoi l'accent TEL QUEL et non `teinte_de`. La derivation garde la
    # saturation de l'amont, et celle de son indigo est tres haute : l'appliquer
    # a la teinte du terracotta donnait #F33D50, un rouge vif que l'on prendrait
    # pour une alerte. Un signal ne se derive donc pas, il se choisit dans la
    # palette, comme les autres couleurs de marque de ce fichier.
    #
    # Contraste, et il faut le dire franchement : le terracotta est a 3,6 contre
    # 1 sur blanc, la ou l'indigo de l'amont est a 4,1. Les deux sont sous le
    # seuil de 4,5 du petit texte, et l'ecart ne change pas de categorie. Sur le
    # fond sombre de la charte, le terracotta est a 5,6.
    INCOGNITO = p["marque"]["accent"]["hex"].lstrip("#").upper()
    lignes.append("// Incognito : l'accent de la palette, pris tel quel. Un signal ne se derive pas.")
    lignes.append(
        f"val TrustiatisIncognito = Color(0xFF{INCOGNITO})  // palette, marque.accent"
    )
    lignes.append("")

    # La surbrillance de selection de texte. L'amont la pose a 0x4D0066FF, donc
    # un bleu pur a 30 % d'opacite. L'opacite est conservee telle quelle : elle
    # fait la lisibilite du texte par dessous, et n'a rien d'une couleur.
    SELECTION_AMONT = "0066FF"
    lignes.append("// Surbrillance de selection : l'amont 0066FF a notre teinte, opacite 30 % conservee")
    lignes.append(
        f"val TrustiatisSelectionHighlight = Color(0x4D{teinte_de(SELECTION_AMONT, marque)})"
        f"  // amont #{SELECTION_AMONT}, teinte de la marque"
    )
    lignes.append("")

    # Teinte rose de la marque : le terracotta, que la charte nomme « accent
    # principal ». Sert au canevas ci dessous et au signal de l'incognito.
    accent = p["marque"]["accent"]["hex"]

    # Le canevas de la conversation, et ce n'est pas un detail de decor.
    #
    # L'amont pose un motif par defaut, SCHOOL, sur les quatre themes. Or dans
    # `Theme.kt`, `presetWallpaperTheme` PRIME sur la palette de base : tant qu'un
    # motif est actif, et il l'est par defaut, ce sont les couleurs du MOTIF qui
    # donnent les bulles, pas celles des quatre themes. Teinter les themes sans
    # teinter le motif ne se voyait donc nulle part dans la configuration par
    # defaut, et le canevas restait franchement bleu : #E7F5FF en clair.
    #
    # Pourquoi la teinte de l'ACCENT et non celle du sauge. Le Membre l'a demande
    # en rose, et la charte ne nomme aucun rose : sa couleur rose est le
    # terracotta #BE7C7B, qu'elle appelle « accent principal ». La meme charte
    # situe d'ailleurs la marque « en opposition au bleu tech generique ».
    #
    # Les gris restent gris : `teinte_de` ne touche pas une couleur sans chroma,
    # donc les bulles RECUES de l'amont, qui sont des gris neutres dans trois
    # themes sur quatre, traversent cette derivation intactes. C'est voulu : une
    # conversation ou les deux cotes sont teintes ne se lit plus.
    #
    # Seul le motif par defaut est repris. Les autres, chats, fleurs, voyage, sont
    # des choix explicites de l'utilisateur et gardent l'identite de leur image.
    CANEVAS_AMONT = {
        "FondClair": "E7F5FF",
        "TeinteClair": "CEEBFF",
        "TeinteSombre": "0F293B",
        "TeinteSignature": "112F43",
        "TeinteNoir": "0F293B",
        "ClairEnvoye": "EEF9FF",
        "ClairEnvoyeCitation": "D6EDFA",
        "ClairRecu": "F3F5F9",
        "ClairRecuCitation": "E4E8EE",
        "SombreEnvoye": "172833",
        "SombreEnvoyeCitation": "1C3E4F",
        "SombreRecu": "26282C",
        "SombreRecuCitation": "393C40",
        "SignatureEnvoye": "1A3C5D",
        "SignatureEnvoyeCitation": "235B80",
        "SignatureRecu": "26282C",
        "SignatureRecuCitation": "393C40",
        "NoirEnvoye": "1A3C5D",
        "NoirEnvoyeCitation": "235B80",
        "NoirRecu": "1D1E22",
        "NoirRecuCitation": "292B2F",
        # Fond du canevas en theme Signature, partage par tous les motifs.
        "FondSignature": "111528",
    }
    lignes.append("// Canevas de la conversation : le motif par defaut de l'amont a la teinte de")
    lignes.append("// l'accent. Il PRIME sur les quatre themes tant qu'un motif est actif.")
    for nom, source in CANEVAS_AMONT.items():
        derive = teinte_de(source, accent)
        inchange = " (gris de l'amont, laisse intact)" if derive == source else ""
        # Toutes en chaines « #ffxxxxxx » : c'est la forme que l'amont ecrit dans
        # ChatWallpaper.kt, pour les fonds, les teintes et les ThemeColors. La
        # conversion reste la sienne, `colorFromReadableHex()`, donc le remplacement
        # ne porte que sur le litteral.
        lignes.append(
            f'val TrustiatisCanevas{nom} = "#ff{derive.lower()}"  // amont #{source}{inchange}'
        )
    lignes.append("")

    for theme, roles in THEMES_AMONT.items():
        lignes.append(f"// Thème {theme}")
        for role, (origine, cle) in roles.items():
            if origine == "palette":
                hexa = p["marque"][cle]["hex"].lstrip("#").upper()
                venue = f"palette, marque.{cle}"
            elif origine == "sombre":
                hexa = p["theme_sombre"][cle].lstrip("#").upper()
                venue = f"palette, theme_sombre.{cle}"
            else:
                hexa = teinte_de(cle, marque)
                venue = f"amont {cle.upper()}, teinte de la marque"
            nom = f"Trustiatis{theme}{role[0].upper()}{role[1:]}"
            lignes.append(f"val {nom} = Color(0xFF{hexa})  // {venue}")
        lignes.append("")
    return ("\n".join(lignes)).encode()


def cibles() -> dict[Path, bytes]:
    """Le contenu attendu de chaque fichier généré, sans rien écrire."""
    p = palette()
    fond_clair = _hex(p["fond_icone"]["clair"])
    fond_sombre = _hex(p["fond_icone"]["sombre"])
    dessin = _dessin()
    res = MULTI / "android" / "src" / "main" / "res"
    sortie: dict[Path, bytes] = {}

    def png(chemin: Path, im: Image.Image) -> None:
        from io import BytesIO

        tampon = BytesIO()
        im.save(tampon, "PNG", optimize=True)
        sortie[chemin] = tampon.getvalue()

    # 1. Icônes d'application Android, les deux variantes que l'amont propose
    #    par `activity-alias`. On garde les deux : elles ne coûtent rien et le
    #    manifeste n'a pas à bouger. Elles diffèrent par le fond, pas par le
    #    dessin. Le second fond est la plaque blanche du favicon d'origine, et
    #    non le sauge : le I du monogramme est sauge et s'y effacerait.
    # L'ordre des deux fonds est celui de l'amont, que la première propagation
    # avait inversé : l'icône par défaut est sur plaque CLAIRE (l'amont pose
    # #FFFFFF), la variante nommée « dark_blue » est celle sur fond sombre.
    # C'est ce que le Membre a relevé : une vignette noire dure dans le lanceur.
    for suffixe, fond in (("", fond_clair), ("_dark_blue", fond_sombre)):
        for densite, cote in DENSITES.items():
            d = res / f"mipmap-{densite}"
            # L'avant plan adaptatif se dessine sur 108 unités de densité.
            png(
                d / f"icon{suffixe}_foreground.png",
                _pose_cercle(dessin, round(cote * 108 / 48), RAYON_CIRCONSCRIT),
            )
            glyphe = _pose_cercle(dessin, cote, RAYON_CIRCONSCRIT)
            carre = _plaque(cote, PLAQUE, PLAQUE_RAYON_COIN, fond)
            carre.alpha_composite(glyphe)
            png(d / f"icon{suffixe}.png", carre)
            # Un rayon d'un demi côté fait un disque : c'est l'icône ronde.
            rond = _plaque(cote, PLAQUE_RONDE, 0.5, fond)
            rond.alpha_composite(glyphe)
            png(d / f"icon{suffixe}_round.png", rond)

    # 1 bis. Icônes de notification. Elles manquaient, et c'est pour cela que
    #        les notifications d'Android montraient encore le dessin de l'amont.
    #        Android n'en garde que le canal alpha et le teinte lui même, donc
    #        une silhouette blanche, et non le monogramme en couleur.
    silhouette = _silhouette(dessin)
    for densite, cote in DENSITES_NTF.items():
        d = res / f"drawable-{densite}"
        marque = _pose_largeur(silhouette, cote, NTF_LARGEUR)
        png(d / "ntf_icon.png", marque)
        png(d / "ntf_service_icon.png", marque)

    # 2. Couleurs de fond des icônes adaptatives, lues par mipmap-anydpi-v26/
    for nom, couleur in (
        ("icon_background", p["fond_icone"]["clair"]),
        ("icon_dark_blue_background", p["fond_icone"]["sombre"]),
    ):
        sortie[res / "values" / f"{nom}.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            f'    <color name="{nom}">{couleur.upper()}</color>\n'
            "</resources>\n"
        ).encode()

    # 2 bis. Le fond de fenêtre d'Android, que le système peint avant que Compose
    #        n'ait dessiné quoi que ce soit, et pendant les transitions. L'amont y
    #        pose son gris #121212 ; depuis que le thème sombre par défaut est celui
    #        de la marque, ce gris apparaissait un instant à chaque lancement, avant
    #        de laisser place au #070D18 de la charte. Il vient donc de la palette.
    #
    #        Ce fichier est repris en entier parce qu'Android refuse deux ressources
    #        de même nom : on ne peut pas poser la couleur à côté sans retirer celle
    #        de l'amont. `black` et `white` sont celles de l'amont, inchangées.
    sortie[res / "values" / "colors.xml"] = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        '  <color name="black">#FF000000</color>\n'
        '  <color name="white">#FFFFFFFF</color>\n'
        f'  <color name="window_background_dark">{p["theme_sombre"]["fond"].upper()}</color>\n'
        "</resources>\n"
    ).encode()

    # 3. Couleurs de thème, le seul fichier Kotlin que cet outil produit. Il est
    #    à nous, donc il ne conflicte avec aucun report de l'amont : seules les
    #    affectations de Theme.kt le référencent.
    sortie[CHEMIN_COULEURS] = couleurs_kotlin()

    # 4. Le monogramme utilisé dans l'application elle même : notification de
    #    bureau, fenêtre, écran d'accueil.
    commun = _pose_cercle(dessin, 432, RAYON_CIRCONSCRIT)
    png(MULTI / "common/src/commonMain/moko-resources/images/icon_foreground_common@4x.png", commun)
    png(MULTI / "common/src/androidMain/res/drawable/icon_foreground_android_common.png", commun)

    # 4. Le logo complet des écrans d'accueil. `logo` est posé sur fond clair et
    #    `logo_light` sur fond sombre : c'est ce que fait SimpleXInfo.kt, qui
    #    choisit `logo_light` quand le thème est sombre. Les deux encres de la
    #    charte, sauge et terracotta, tiennent sur les deux fonds, donc le même
    #    dessin sert aux deux. Aucune encre n'est inventée pour l'occasion.
    mot = Image.open(BRANDING / "logo" / "logo-mot.png").convert("RGBA")
    mot = mot.crop(mot.getbbox())
    for nom, hauteur in (("logo@4x.png", 189), ("logo_light@4x.png", 190)):
        large = round(mot.width * hauteur / mot.height)
        toile = Image.new("RGBA", (768, hauteur), (0, 0, 0, 0))
        reduit = mot.resize((min(large, 768), hauteur), Image.LANCZOS)
        toile.alpha_composite(reduit, ((768 - reduit.width) // 2, 0))
        png(MULTI / "common/src/commonMain/moko-resources/images" / nom, toile)

    # 5. Desktop. Le .icns est écrit à la main, voir _icns.
    dist = MULTI / "desktop" / "src" / "jvmMain" / "resources" / "distribute"

    def plaquee(cote: int) -> Image.Image:
        """La plaque claire et le monogramme, comme l'icône d'Android."""
        im = _plaque(cote, PLAQUE, PLAQUE_RAYON_COIN, fond_clair)
        im.alpha_composite(_pose_cercle(dessin, cote, RAYON_CIRCONSCRIT))
        return im

    png(dist / "simplex.png", plaquee(512))
    from io import BytesIO

    tampon = BytesIO()
    plaquee(256).save(
        tampon, "ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    )
    sortie[dist / "simplex.ico"] = tampon.getvalue()
    sortie[dist / "simplex.icns"] = _icns(
        {
            "ic11": plaquee(32),
            "ic12": plaquee(64),
            "ic07": plaquee(128),
            "ic08": plaquee(256),
            "ic09": plaquee(512),
        }
    )
    return sortie


def main(ecrire: bool) -> int:
    attendu = cibles()
    ecarts = 0
    for chemin in sorted(attendu):
        contenu = attendu[chemin]
        actuel = chemin.read_bytes() if chemin.exists() else b""
        relatif = chemin.relative_to(RACINE)
        if actuel == contenu:
            continue
        ecarts += 1
        empreinte = hashlib.sha256(contenu).hexdigest()[:12]
        if ecrire:
            chemin.parent.mkdir(parents=True, exist_ok=True)
            chemin.write_bytes(contenu)
            print(f"écrit   {relatif}  {len(contenu):>8} o  {empreinte}")
        else:
            etat = "absent" if not actuel else "différent"
            print(f"{etat:9} {relatif}  {len(contenu):>8} o  {empreinte}")
    if not ecarts:
        print(f"Les {len(attendu)} ressources générées sont à jour.")
    elif not ecrire:
        print(f"\n{ecarts} ressource(s) à écrire : relancer avec --ecrire")
    return 0


if __name__ == "__main__":
    raise SystemExit(main("--ecrire" in sys.argv))
