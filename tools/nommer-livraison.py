#!/usr/bin/env python3
"""Range les paquets construits par la compilation continue sous leur nom de publication.

Pourquoi cet outil existe
-------------------------
Les paquets ne sortent pas de la chaîne sous le nom qu'ils portent sur le portail.
`jpackage` les nomme d'après le produit et la version qu'il connaît, sans notre suffixe :
« Trustiatis Secure-7.1.4.dmg », « trustiatis-secure_x86_64.deb ». Le portail, lui, attend
« trustiatis-secure-7.1.4-ts1-macos-aarch64.dmg », et `tools/publier-version.py` reconnaît
la plateforme **d'après le nom**, et refuse ce qu'il ne reconnaît pas.

Entre les deux, il y avait un renommage à la main. C'est ainsi que les paquets 7.1.2-ts1
ont été préparés, et un renommage à la main est une erreur qui attend son tour : les deux
`.dmg` sortent de la chaîne avec **exactement le même nom**, et rien, dans le fichier lui
même, ne dit lequel est celui d'Apple Silicon. Seul le nom du dossier d'artefact le dit.

L'outil fait donc deux choses, et la seconde compte autant que la première :

  1. il range chaque paquet sous son nom de publication, en refusant de deviner ;
  2. il **vérifie l'empreinte** de chaque fichier contre celle que la machine de
     construction a écrite dans `empreintes-<cible>.txt`, avant de le ranger. Un paquet
     de 250 Mio qui a mal voyagé est ainsi écarté ici, et non chez l'utilisateur.

Usage
-----
    tools/nommer-livraison.py --version 7.1.4-ts1 --artefacts ~/telechargements/artefacts
    tools/nommer-livraison.py --version 7.1.4-ts1 --artefacts ... --sortie ~/livraison
    tools/nommer-livraison.py ... --simuler

`--artefacts` est le dossier où les artefacts de la compilation ont été dézippés, un
sous dossier par artefact, tels que GitHub les nomme : `paquets-linux-x86_64`,
`paquets-windows-x86_64`, `paquets-macos-aarch64`, `paquets-macos-x86_64`. Les APK, que la
compilation continue ne produit pas, se passent en dossier libre : ils portent déjà un nom
qui dit leur architecture.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import sys
from pathlib import Path

# Nom de publication, par (dossier d'artefact, extension ou motif de nom).
#
# Le dossier compte pour les deux .dmg et pour eux seuls : jpackage leur donne le même
# nom, et c'est la machine qui les a construits qui dit lequel est lequel.
REGLES = [
    ("paquets-linux-x86_64",   r"\.deb$",                   "amd64.deb"),
    ("paquets-windows-x86_64", r"\.msi$",                   "windows-x86_64.msi"),
    ("paquets-windows-x86_64", r"\.exe$",                   "windows-x86_64.exe"),
    ("paquets-macos-aarch64",  r"\.dmg$",                   "macos-aarch64.dmg"),
    ("paquets-macos-x86_64",   r"\.dmg$",                   "macos-x86_64.dmg"),
    (None,                     r"arm64-v8a.*\.apk$",        "arm64-v8a.apk"),
    (None,                     r"armeabi-v7a.*\.apk$",      "armeabi-v7a.apk"),
]

EXTENSIONS = (".deb", ".msi", ".exe", ".dmg", ".apk")


def empreinte(chemin: Path) -> str:
    h = hashlib.sha256()
    with chemin.open("rb") as f:
        for bloc in iter(lambda: f.read(1024 * 1024), b""):
            h.update(bloc)
    return h.hexdigest()


def annoncees(dossier: Path) -> dict[tuple[str | None, str], str]:
    """Les empreintes écrites par la construction, par (cible, nom de fichier).

    La cible fait partie de la clé, et ce n'est pas un ornement : les deux `.dmg`
    sortent de la chaîne sous le même nom, « Trustiatis Secure-<version>.dmg ». Une
    table indexée sur le seul nom en perd un, et compare alors le paquet d'Apple
    Silicon à l'empreinte de celui d'Intel. Le premier essai de cet outil est tombé
    exactement là dessus, et c'est le défaut qu'il est censé empêcher.

    La cible est lue dans le nom du fichier d'empreintes, `empreintes-<cible>.txt`,
    qui la porte où qu'il se trouve.

    `empreintes.txt`, sans cible, est le fichier des paquets qui n'en ont pas : les APK,
    que la compilation continue ne produit pas et qui arrivent dans un dossier libre.
    Leur nom porte déjà l'architecture, il n'y a donc rien à départager. Sans cette
    forme, un APK signé sur le poste n'était rangeable par aucun nom de fichier
    raisonnable, et c'était le même défaut d'asymétrie que celui du paquet Debian :
    le chemin hébergé produisait de quoi ranger, le chemin local non.
    """
    vues: dict[tuple[str | None, str], str] = {}
    for f in dossier.rglob("empreintes*.txt"):
        reste = f.stem[len("empreintes"):]
        cible = reste[1:] if reste.startswith("-") else None
        for ligne in f.read_text(encoding="utf-8").splitlines():
            m = re.match(r"([0-9a-f]{64})\s+\*?\.?/?(.+)", ligne.strip())
            if m:
                vues[(cible, Path(m.group(2)).name)] = m.group(1)
    return vues


def cible_de(chemin: Path, racine: Path) -> str | None:
    """La cible de construction, lue dans le dossier d'artefact `paquets-<cible>`."""
    relatif = chemin.relative_to(racine)
    if len(relatif.parts) < 2:
        return None
    tete = relatif.parts[0]
    return tete[len("paquets-"):] if tete.startswith("paquets-") else None


def nom_publie(version: str, chemin: Path, racine: Path) -> str | None:
    relatif = chemin.relative_to(racine)
    dossier = relatif.parts[0] if len(relatif.parts) > 1 else None
    for attendu, motif, fin in REGLES:
        if attendu is not None and dossier != attendu:
            continue
        if re.search(motif, chemin.name, re.IGNORECASE):
            return f"trustiatis-secure-{version}-{fin}"
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True, help="par exemple 7.1.4-ts1")
    ap.add_argument("--artefacts", required=True, type=Path)
    ap.add_argument("--sortie", type=Path, default=Path.home() / "livraison")
    ap.add_argument("--simuler", action="store_true")
    a = ap.parse_args()

    if not re.fullmatch(r"\d+\.\d+\.\d+-ts\d+", a.version):
        print(f"ERREUR : version inattendue, {a.version}. Attendu MAJEUR.MINEUR.CORRECTIF-tsN.",
              file=sys.stderr)
        return 2
    racine = a.artefacts.expanduser().resolve()
    if not racine.is_dir():
        print(f"ERREUR : dossier d'artefacts introuvable, {racine}", file=sys.stderr)
        return 2

    paquets = sorted(p for p in racine.rglob("*") if p.is_file() and p.suffix.lower() in EXTENSIONS)
    if not paquets:
        print(f"ERREUR : aucun paquet dans {racine}.", file=sys.stderr)
        return 2

    attendues = annoncees(racine)
    destination = a.sortie.expanduser() / a.version
    anomalies = 0
    plan: list[tuple[Path, Path, str]] = []

    for p in paquets:
        nom = nom_publie(a.version, p, racine)
        if nom is None:
            print(f"REFUS   {p.relative_to(racine)}\n"
                  f"        aucune règle ne reconnaît ce fichier. Il n'est pas rangé au hasard.")
            anomalies += 1
            continue
        reelle = empreinte(p)
        cible = cible_de(p, racine)
        # Les APK ne viennent pas de la compilation continue et n'ont pas de dossier de
        # cible : leur nom porte déjà l'architecture, donc le nom seul suffit.
        annoncee = attendues.get((cible, p.name)) or (
            attendues.get((None, p.name)) if cible is None else None
        )
        if annoncee is None:
            print(f"SANS    {p.relative_to(racine)}\n"
                  f"        aucune empreinte annoncée par la construction pour ce nom.")
            anomalies += 1
            continue
        if annoncee != reelle:
            print(f"ECART   {p.relative_to(racine)}\n"
                  f"        annoncée {annoncee}\n        calculée {reelle}")
            anomalies += 1
            continue
        plan.append((p, destination / nom, reelle))

    if anomalies:
        print()
        print(f"{anomalies} anomalie(s) : rien n'est rangé tant qu'elles subsistent.")
        return 1

    print(f"{len(plan)} paquet(s), empreintes conformes à celles de la construction.")
    print()
    for source, cible, somme in plan:
        print(f"{'(simulation) ' if a.simuler else ''}{source.name}")
        print(f"    -> {cible}")
        print(f"       {somme}")
        if not a.simuler:
            cible.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, cible)

    if a.simuler:
        print("\nRien n'a été écrit. Relancer sans --simuler.")
    else:
        print(f"\nRangé dans {destination}")
        print("Publier ensuite depuis le dépôt du portail :")
        print(f"  tools/publier-version.py --version {a.version} --amont <tag> "
              f"--artefacts {destination}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
