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

    # 3. Le monogramme utilisé dans l'application elle même : notification de
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
