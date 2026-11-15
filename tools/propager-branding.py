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
    fond_principal = _hex(p["fond_icone"]["sombre"])
    fond_second = _hex(p["fond_icone"]["clair"])
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
    for suffixe, fond in (("", fond_principal), ("_dark_blue", fond_second)):
        for densite, cote in DENSITES.items():
            d = res / f"mipmap-{densite}"
            # L'avant plan adaptatif se dessine sur 108 unités de densité.
            avant = _pose(dessin, round(cote * 108 / 48), ZONE_SURE)
            png(d / f"icon{suffixe}_foreground.png", avant)
            pleine = _pose(dessin, cote, 0.72, fond)
            png(d / f"icon{suffixe}.png", pleine)
            png(d / f"icon{suffixe}_round.png", _rond(pleine))

    # 2. Couleurs de fond des icônes adaptatives, lues par mipmap-anydpi-v26/
    for nom, couleur in (
        ("icon_background", p["fond_icone"]["sombre"]),
        ("icon_dark_blue_background", p["fond_icone"]["clair"]),
    ):
        sortie[res / "values" / f"{nom}.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<resources>\n"
            f'    <color name="{nom}">{couleur.upper()}</color>\n'
            "</resources>\n"
        ).encode()

    # 3. Couleurs de thème, le seul fichier Kotlin que cet outil produit. Il est
    #    à nous, donc il ne conflicte avec aucun report de l'amont : seules les
    #    affectations de Theme.kt le référencent.
    sortie[CHEMIN_COULEURS] = couleurs_kotlin()

    # 4. Le monogramme utilisé dans l'application elle même : notification de
    #    bureau, fenêtre, écran d'accueil.
    commun = _pose(dessin, 432, ZONE_SURE)
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
    png(dist / "simplex.png", _pose(dessin, 512, 0.78, fond_principal))
    from io import BytesIO

    tampon = BytesIO()
    _pose(dessin, 256, 0.78, fond_principal).save(
        tampon, "ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    )
    sortie[dist / "simplex.ico"] = tampon.getvalue()
    sortie[dist / "simplex.icns"] = _icns(
        {
            "ic11": _pose(dessin, 32, 0.78, fond_principal),
            "ic12": _pose(dessin, 64, 0.78, fond_principal),
            "ic07": _pose(dessin, 128, 0.78, fond_principal),
            "ic08": _pose(dessin, 256, 0.78, fond_principal),
            "ic09": _pose(dessin, 512, 0.78, fond_principal),
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
