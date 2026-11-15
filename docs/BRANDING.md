# Identité Trustiatis Secure

**Document de travail de la Phase 2. Il se remplit au fur et à mesure et sert de preuve de
l'intention derrière chaque modification.**

## 0. La charge réelle, mesurée après la greffe

Relevé le 2026-11-15, une fois l'amont greffé, par `python3 tools/verifier-marque.py`. Ce ne
sont plus des estimations d'audit : ce sont les lignes que le contrôle signale aujourd'hui.

| Où | Signalements | Nature du travail |
|---|---|---|
| `strings.xml` des 41 catalogues Moko | **2 423** | chaînes traduites. Décision D-6 : fichier de référence et français d'abord, les autres langues par vagues |
| Kotlin et Kotlin Script | **303** | code commun Android et Desktop : thèmes, identifiants, libellés en dur |
| Swift | **203** | couche iOS, au périmètre depuis la décision P-1 |
| `SimpleX.xcodeproj/project.pbxproj` | **28** | noms de cibles et de produits Xcode |
| **Total** | **2 957** sur **154 fichiers** | |

Ce total n'est pas la quantité de travail : un même mot répété dans 41 traductions se traite
une fois, par un script. Il est la **mesure de départ**, et il doit tomber à zéro avant la
première version publiée. Le contrôle est en avertissement dans la CI jusque là, puis
bloquant (`allow_failure: false` dans `.gitlab-ci.yml`).

Ce que le contrôle **ne** signale pas, volontairement : le coeur Haskell (`src/`), les tests,
la documentation et le site de l'amont, ses robots, son client en ligne de commande, et son
intégration continue GitHub. Et il laisse passer les identifiants techniques et les mentions
du **réseau**, qui restent permis par la politique de marque et nécessaires à l'exactitude.
La liste exacte des exclusions et des dérogations est en tête de `tools/verifier-marque.py`,
qui est la seule source sur ce point.

## 1. Source de l'identité

Décision du Membre : la charte de référence est celle de **`id.trustiatis.com`**, l'application
de cartes d'identité en ligne depuis le 2026-11-04. Son code vit dans `id-trustiatis/` de
l'espace de travail, hors de ce dépôt.

**Rien n'est inventé.** Aucune couleur, aucun logo, aucune police n'est créé pour ce projet :
tout est extrait de cette charte, puis décliné aux formats exigés par chaque plateforme.

Première tâche de la Phase 2, non encore faite :

- [ ] extraire les couleurs de `id-trustiatis/` vers `branding/colors/palette.json`
- [ ] extraire la ou les polices vers `branding/typography/`
- [ ] récupérer le logo détramé depuis `design-system/` (voir `design-system/detramer.py`)
- [ ] définir les deux thèmes, clair et sombre

## 2. Référentiel et propagation

`branding/` est le **référentiel unique**. Les deux arbres de code (le couple Android et
Desktop d'un côté, iOS de l'autre) **ne partagent aucune ressource** : l'audit de Phase 1,
chapitre 4, l'a établi. L'homogénéité ne peut donc venir que d'une génération automatisée.

```text
branding/
├── colors/          palette source, et son export par plateforme
├── typography/      polices et échelle typographique
├── logo/            logos sources, vectoriels
├── icons/           icônes d'application, sources
├── splash/          écrans d'accueil
├── screenshots/     captures pour les magasins
└── store-assets/    textes et visuels de fiche de magasin
```

- [ ] écrire `tools/propager-branding.py`, sur le modèle de `design-system/detramer.py`, qui
      génère et copie vers les chemins exacts listés au chapitre 7.1 de l'audit de Phase 1

**Aucune ressource ne doit être modifiée à la main dans les arbres de code.** Toute
modification se fait dans `branding/`, puis se propage. C'est ce qui rend les reports de
l'amont tenables.

## 3. Registre des modifications

À remplir. Chaque ligne cite le cercle (voir `CONTRIBUTING.md`) et la justification. Les
entrées du **cercle 3** doivent être argumentées, ce sont elles qui coûtent à chaque report.

| Cercle | Fichier | Modification | Justification | Commit |
|---|---|---|---|---|
| | | | | |

## 4. Points de vigilance relevés à l'audit

| Point | Détail |
|---|---|
| Couleur dupliquée | `Color.kt` porte l'avertissement « If this value changes also need to update #0088ff in string resource files ». Chercher `0088ff` dans **tous** les fichiers de chaînes |
| Énumération de thème | `DefaultTheme { LIGHT, DARK, SIMPLEX, BLACK }` est persistée **par son nom**. Changer les valeurs de couleurs, **pas** les noms, sinon le thème de chaque utilisateur est réinitialisé à la mise à jour |
| Pictogrammes | les 260 fichiers `ic_*.svg` sont des symboles Material, **pas** de la marque. Ne pas y toucher |
| Marques de tiers | `flux_logo*`, `badge_*`, `decentralized*` et les écrans associés renvoient à des tiers ou à l'éditeur amont. À retirer avec les fonctions correspondantes |
| Style Android | `Theme.SimpleX` dans `values/themes.xml` et `values-night/themes.xml`, référencé par le manifeste |
| Icônes adaptatives | deux jeux (`icon` et `icon_dark_blue`), sélectionnables dans l'application par deux `activity-alias`. Décider si Trustiatis en conserve deux |
| Interface d'appel | `common/src/commonMain/resources/assets/www/call.html` et `style.css` portent leurs propres couleurs, faciles à oublier |
| Chaînes | 141 lignes du fichier de référence et 3 407 occurrences sur les 41 fichiers de chaînes. Trier en trois catégories, voir `NOTICE-AGPL.md`, et ne jamais remplacer en bloc |

## 5. Contrôle

```bash
python3 tools/verifier-marque.py
```

Doit être vert avant la première version publiée. Le pipeline l'exécute à chaque demande de
fusion, en avertissement tant que la Phase 2 n'est pas finie, puis en blocage.
