# Référentiel de marque de Trustiatis Secure

Ce dossier est la **source unique** des ressources de marque. Aucune ressource n'est
modifiée directement dans l'arborescence de l'amont : on dépose ici, puis on propage avec
`tools/propager-branding.py` (à écrire en Phase 2, sur le modèle de
`design-system/detramer.py` de l'espace de travail).

Raison : à chaque report d'une version de l'amont, les ressources de l'amont reviennent.
Repropager depuis ce dossier est une commande ; refaire la recherche des 200 et quelques
fichiers touchés n'en est pas une.

## Provenance

La charte vient de **id.trustiatis.com**, décision du chapitre 4 du cahier des charges.
Rien n'est inventé ici : ni logo, ni couleur, ni police. Tant qu'une ressource n'a pas été
extraite de la charte, son dossier reste vide et la ressource de l'amont reste en place.

## Contenu attendu

| Dossier | Contenu | État |
|---|---|---|
| `colors/` | `palette.json`, source unique ; les constantes Kotlin des quatre thèmes sont générées | **fait** |
| `typography/` | fichiers de police et leur licence de redistribution | **sans objet** : la décision de `palette.json` est Roboto, police de `id.trustiatis.com` et police système d'Android, donc demandée au système. Rien à embarquer, rien à redistribuer, et un report de l'amont n'a aucun fichier de police à retrouver |
| `logo/` | monogramme et logo au mot, en PNG détramés | **fait**, en PNG et non en SVG : la charte ne fournit pas de source vectorielle, et `design-system/detramer.py` reconstruit un fond propre au lieu de découper |
| `icons/` | icônes d'application par plateforme (voir le tableau ci dessous) | **fait** pour Android, Windows, macOS et Linux ; iOS hors périmètre tant que D-1 n'est pas prise |
| `splash/` | écran de lancement Android | **sans objet** : l'amont n'en a pas. Le thème de lancement (`values/themes.xml`) ne porte qu'une couleur de barre d'état, aucun dessin, et le premier écran dessiné est déjà celui que nous avons repris |
| `screenshots/` | captures pour les fiches de distribution | **à faire**, et pas avant que l'application tourne avec sa palette : une capture faite avant ne servirait qu'une fois |
| `store-assets/` | bannières, descriptions, visuels de boutique | **à faire**, et suspendu à la décision D-1 pour l'App Store |

## Formats d'icône par plateforme

Relevé au chapitre 7 de `docs/INITIAL_AUDIT.md`. À produire depuis le SVG source.

| Plateforme | Format | Remarque |
|---|---|---|
| Android | `ic_launcher` et son adaptatif (premier plan, arrière plan) | deux jeux existent dans l'amont, les deux sont à traiter |
| Windows | `.ico` | consommé par jpackage |
| macOS | `.icns` | consommé par jpackage |
| Linux | `.png` | consommé par jpackage, AppImage et Flatpak |
| **Dans l'application** (toutes plateformes) | `ic_simplex.svg`, `ic_simplex_{light,dark}@4x.png`, `ic_simplex_tray_light.svg`, `ic_simplex_tray_dot{,_light}.svg` | **manquait à ce tableau, et donc n'a jamais été propagé.** Ce ne sont pas des icônes de lanceur : ce sont l'icône de la fenêtre, celle de la zone de notification du Desktop, et l'image de profil par défaut. Relevé par le Membre le 2026-11-22 |
| iOS | jeu de tailles du catalogue d'actifs | hors périmètre tant que la décision D-1 n'est pas prise |

## Ce qui ne va pas ici

Les clés de signature. Jamais. Elles vivent dans KeePassXC puis Vault, comme le reste des
secrets du groupe.
