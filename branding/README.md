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
| `colors/` | palette, au format source et en fichiers de thème générés | vide |
| `typography/` | fichiers de police et leur licence de redistribution | vide |
| `logo/` | logo, en SVG source et en déclinaisons | vide |
| `icons/` | icônes d'application par plateforme (voir le tableau ci dessous) | vide |
| `splash/` | écran de lancement Android | vide |
| `screenshots/` | captures pour les fiches de distribution | vide |
| `store-assets/` | bannières, descriptions, visuels de boutique | vide |

## Formats d'icône par plateforme

Relevé au chapitre 7 de `docs/INITIAL_AUDIT.md`. À produire depuis le SVG source.

| Plateforme | Format | Remarque |
|---|---|---|
| Android | `ic_launcher` et son adaptatif (premier plan, arrière plan) | deux jeux existent dans l'amont, les deux sont à traiter |
| Windows | `.ico` | consommé par jpackage |
| macOS | `.icns` | consommé par jpackage |
| Linux | `.png` | consommé par jpackage, AppImage et Flatpak |
| iOS | jeu de tailles du catalogue d'actifs | hors périmètre tant que la décision D-1 n'est pas prise |

## Ce qui ne va pas ici

Les clés de signature. Jamais. Elles vivent dans KeePassXC puis Vault, comme le reste des
secrets du groupe.
