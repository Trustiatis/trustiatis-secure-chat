# Identité Trustiatis Secure

**Document de travail de la Phase 2. Il se remplit au fur et à mesure et sert de preuve de
l'intention derrière chaque modification.**

## 0. La charge réelle, mesurée après la greffe

Relevé le 2026-11-15, une fois l'amont greffé, par `python3 tools/verifier-marque.py`. Ce ne
sont plus des estimations d'audit : ce sont les lignes que le contrôle signale aujourd'hui.

| Où | Signalements | Nature du travail |
|---|---|---|
| Catalogues de traduction (`.xml`) | **3 355** | chaînes traduites. Décision D-6 : fichier de référence et français d'abord, les autres langues par vagues |
| Kotlin et Kotlin Script | **816** | code commun Android et Desktop : thèmes, identifiants, libellés en dur |
| Swift | **606** | couche iOS, au périmètre depuis la décision P-1 |
| `SimpleX.xcodeproj/project.pbxproj` | **168** | noms de cibles et de produits Xcode |
| Divers (scripts, manifestes, droits) | **65** | |
| **Total** | **5 010** sur **567 fichiers** | |

Relevé sur l'amont **v7.1.2**, base de la greffe depuis le 2026-11-15 au soir. Sur v7.1.3,
le même contrôle donnait 4 990 sur 555 fichiers : l'écart de vingt signalements n'est pas une
régression, c'est la différence entre deux versions de l'amont.

> **Le relevé du matin, 2 957 sur 154 fichiers, était faux et le contrôle était en cause.**
> La liste des dérogations contenait `\bSIMPLEX\b`, destinée à la valeur d'énumération de
> thème, écrite en capitales. Elle était compilée avec `re.IGNORECASE`, donc elle autorisait
> aussi **« SimpleX » isolé**, c'est à dire la marque elle même partout où elle est un mot
> et non un identifiant collé. Autrement dit, le contrôle laissait passer la quasi totalité
> des phrases vues par un utilisateur, qui sont précisément ce que la politique de marque
> interdit de conserver. Corrigé le 2026-11-15 : les dérogations sensibles à la casse sont
> appliquées séparément des autres. Deux dérogations ont été ajoutées dans le même temps,
> parce qu'elles sont obligatoires ou intouchables : la mention de droit d'auteur de l'amont
> en tête de fichier, exigée par l'AGPL, et les fonctions du pont FFI
> (`sendSimpleXCmd`, `recvSimpleXMsg`).
>
> Reste à trancher en Phase 2, et volontairement laissé signalé : les **213 en-têtes de
> fichier** de la forme `//  SimpleX`, qui ne sont vus par aucun utilisateur mais portent le
> nom du projet amont. Les garder relève de la provenance, les réécrire relève du conflit à
> chaque report.

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

Première tâche de la Phase 2, **faite le 2026-11-15** :

- [x] couleurs extraites de `id-trustiatis/src/brand.ts` vers `branding/colors/palette.json`,
      complétées du thème clair relevé sur `trustiatis.com`. Chaque valeur cite sa source
- [x] typographie : **rien n'est embarqué**. `brand.ts` demande Roboto, qui est la police
      système d'Android et une police courante ailleurs. Embarquer des fichiers de police
      alourdirait chaque report sans rien apporter
- [x] monogramme détramé récupéré dans `branding/logo/monogramme.png`. Il n'existait pas :
      `design-system/trustiatis/logo-icon.png` est aplati sur une plaque blanche. Une
      recette a été ajoutée à `design-system/detramer.py` pour le reconstruire
- [x] deux thèmes définis dans `palette.json`, `theme_sombre` et `theme_clair`

Deux défauts de `detramer.py` ont été corrigés au passage, et ils valaient d'être trouvés :
son diagnostic plantait sur une cible absente au lieu de dire qu'elle était à construire, et
surtout **il moyennait la couleur des pixels de bord avant de la comparer aux aplats**. Sur un
logo fait de deux aplats, comme le monogramme Trustiatis, la moyenne tombe entre les deux et
accuse un liseré qui n'existe pas. Mesurer chaque pixel contre l'aplat le plus proche donne
un écart de 0 sur les six logos du groupe.

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

- [x] `tools/propager-branding.py` écrit le 2026-11-15. Il génère **39 ressources** : les
      icônes Android aux cinq densités et dans les deux variantes que propose l'amont, les
      deux couleurs de fond des icônes adaptatives, le monogramme employé dans
      l'application, les deux logos des écrans d'accueil, et les trois icônes du Desktop.
      `python3 tools/propager-branding.py` compare sans écrire, `--ecrire` écrit

**Aucune ressource ne doit être modifiée à la main dans les arbres de code.** Toute
modification se fait dans `branding/`, puis se propage. C'est ce qui rend les reports de
l'amont tenables.

Trois choses apprises en l'écrivant, qui ne se devinent pas :

| Piège | Ce qu'il faut savoir |
|---|---|
| `vertical_logo@{1,2,3}x.png` | malgré son nom, **ce n'est pas un logo** : 8, 16 et 24 pixels de côté, utilisé par `TextItemView.kt` comme motif de remplissage pour masquer un texte secret. Ne pas y toucher |
| `logo` et `logo_light` | `SimpleXInfo.kt` choisit `logo_light` quand le thème est **sombre**. Le suffixe dit la couleur de l'encre, pas celle du fond. Le même dessin sert aux deux : les deux encres de la charte tiennent sur blanc comme sur le fond sombre |
| `.icns` | Pillow ne sait écrire ce format que sur macOS, où il délègue à `iconutil`. Le format est pourtant trivial, un en-tête et des blocs typés contenant des PNG : `propager-branding.py` l'écrit lui même, sans Mac |

## 3. Registre des modifications

À remplir. Chaque ligne cite le cercle (voir `CONTRIBUTING.md`) et la justification. Les
entrées du **cercle 3** doivent être argumentées, ce sont elles qui coûtent à chaque report.

| Cercle | Fichier | Modification | Justification | Commit |
|---|---|---|---|---|
| **3** | `apps/ios/Shared/Model/AppAPITypes.swift` | `NotificationsMode.values` réduit à `[.off]` | P-5. Les cas `instant` et `periodic` de l'énumération sont **conservés**, ils sont échangés avec le coeur sous forme de chaînes (interdit n° 3 de `CONTRIBUTING.md`). Seule la liste offerte à l'interface change, ce qui neutralise d'un coup tous les sélecteurs, présents et futurs | `feature/ios` |
| **3** | `apps/ios/Shared/Views/UserSettings/NotificationsView.swift` | sélecteur de mode remplacé par un texte, et retrait de `setNotificationsMode` | P-5. `setNotificationsMode` était le chemin qui appelait `apiRegisterToken` depuis les réglages | `feature/ios` |
| **3** | `apps/ios/Shared/Views/Onboarding/YourNetwork.swift` | défaut `.instant` passé à `.off`, feuille de sélection remplacée par l'écran d'information, `applyNotificationMode` réduit | P-5, et c'est **le point de fuite principal** : l'amont arme `instant` par défaut et enregistre le jeton dès que l'utilisateur touche « Continue », sans l'avoir demandé | `feature/ios` |
| **3** | `apps/ios/Shared/Views/Onboarding/SetNotificationsMode.swift` | `SetNotificationsMode` et `NtfModeSelector` retirés, `NotificationsInfoView` conservée et complétée | P-5. L'écran d'information reste, c'est lui qui porte l'explication | `feature/ios` |
| **3** | `apps/ios/Shared/Model/SimpleXAPI.swift` | `registerToken` ne pose plus de jeton et ramène le mode à `.off`, garde ajoutée dans `reRegisterToken` | P-5. Verrou de dernier recours : une base importée d'une autre application peut annoncer `instant`, et c'est ce chemin qui enverrait alors le jeton | `feature/ios` |
| **3** | `apps/ios/Shared/Views/UserSettings/AppSettings.swift` | un mode importé autre que `.off` est ignoré | P-5. Un fichier de réglages ne doit pas pouvoir rallumer ce que l'interface a retiré | `feature/ios` |
| 1 | 30 fichiers `mipmap-*/icon*.png`, et les deux `values/icon*_background.xml` | icônes d'application régénérées | générées par `tools/propager-branding.py`, jamais à la main. Les deux variantes que l'amont offre par `activity-alias` sont conservées : même dessin, deux fonds, le manifeste n'a pas à bouger | `feature/branding` |
| 1 | `moko-resources/images/{logo,logo_light,icon_foreground_common}@4x.png`, `androidMain/res/drawable/icon_foreground_android_common.png` | logos et monogramme de l'application | idem, générés | `feature/branding` |
| 1 | `desktop/src/jvmMain/resources/distribute/simplex.{png,ico,icns}` | icônes du Desktop | idem. Le nom des fichiers reste celui de l'amont : il n'est vu de personne et le changer n'apporterait qu'un conflit de report | `feature/branding` |
| 1 | `moko-resources/{base,fr}/strings.xml`, 45 clés | nom de l'application et textes d'accueil réécrits | posés par `tools/textes-trustiatis.py`, qui refuse une clé qu'il ne trouve pas. Décision D-6 : référence et français d'abord. Contenu demandé par le Membre : pourquoi l'application, comment elle fonctionne, l'usage interne, la présentation de la carte d'identité, le canal à privilégier, les seconds facteurs. S'y ajoutent les onze chaînes du verrou et du service d'arrière plan, qui sont les mentions de marque **les plus vues** : le service affiche une notification permanente sur l'appareil, et « Verrouillage SimpleX » apparaît dans les réglages. « SimpleX Lock » devient « Trustiatis Lock » | `feature/branding` |
| 2 | `android/build.gradle.kts` | `applicationId` passé à `com.trustiatis.secure`, autorités du fournisseur de contenu alignées | une version dérivée ne peut pas porter l'identifiant de l'amont : ni sur un magasin, ni sur un appareil où les deux doivent cohabiter. Le `namespace`, lui, **reste** celui de l'amont : il ne nomme que `R` et `BuildConfig`, et le changer obligerait à déplacer les sources Kotlin | `feature/branding` |
| 2 | `values/themes.xml`, `values-night/themes.xml`, `AndroidManifest.xml` | `Theme.SimpleX` renommé `Theme.TrustiatisSecure` | nom de style, interne, non persisté. À ne pas confondre avec `DefaultTheme.SIMPLEX`, qui est persisté par son nom et **ne doit pas** être renommé | `feature/branding` |
| 2 | `desktop/build.gradle.kts` | `packageName`, `bundleID` et `upgradeUuid` | le `upgradeUuid` est le point à ne pas manquer : gardé tel quel, l'installateur Windows **remplacerait** une installation de l'application officielle | `feature/branding` |
| 2 | `distribute/TrustiatisSecure.desktop`, `distribute/com.trustiatis.secure.appdata.xml` | renommés et réécrits | les chemins d'installation suivent `packageName`, donc `/opt/trustiatis-secure/`. La fiche AppStream de l'amont était une page de publicité renvoyant à son site : réécrite | `feature/branding` |
| 2 | `scripts/desktop/make-appimage-linux.sh` | motifs `*imple*` remplacés par le nom exact | le script devinait le nom du répertoire, du binaire et de l'icône par un motif qui ne correspond plus. Nommer vaut mieux que deviner | `feature/branding` |
| 2 | `apps/ios/SimpleX Snapshots/SnapshotTests.swift` | scène `NotificationsModeView_Previews` remplacée par `NotificationsInfoView_Previews` | conséquence mécanique du retrait ci dessus, sans quoi la cible de test ne compile pas | `feature/ios` |

| 1 | `moko-resources/{base,fr}/strings.xml`, 19 clés de plus | le nom du produit là où il désigne l'application | deuxième vague du même outil. Ce sont les mentions que l'utilisateur rencontre sans chercher : le menu de la zone de notification du bureau (`tray_*`), les canaux de notification d'Android, l'objet du courriel d'invitation, l'étiquette du thème livré par défaut, « À propos ». Les objets du réseau, eux, gardent leur nom : un lien, un nom ou une adresse SimpleX reste un lien, un nom ou une adresse SimpleX quand c'est Trustiatis Secure qui l'affiche | `feature/branding` |
| outil | `tools/verifier-marque.py` | le contrôle ne lit plus que ce qu'un utilisateur peut voir | il signalait une clé de ressource (`name="simplex_link_group"`) et un commentaire XML (`<!-- SimpleXAPI.kt -->`) au même titre qu'une phrase affichée. Ni l'une ni l'autre n'atteint un écran, et renommer une clé obligerait à toucher tous les appels Kotlin et Swift qui la lisent, donc à rendre conflictuel chaque report de l'amont. S'y ajoutent les désignations du réseau et celles d'un opérateur, que la décision D-4 rend nécessaires. 4 967 signalements avant, 4 053 après, sans qu'une seule phrase vue par un utilisateur sorte du champ | `feature/branding` |

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
| Liens web du manifeste | `AndroidManifest.xml` revendique `https://simplex.chat` avec `autoVerify="true"`. Pour Trustiatis Secure, cette revendication **ne peut pas aboutir** : la vérification lit le `assetlinks.json` du domaine, qui ne listera jamais notre signature. Elle reste donc inerte, mais elle revendique le domaine d'autrui. La retirer casserait en revanche l'ouverture des liens d'invitation reçus par navigateur, qui sont des liens `simplex.chat`. Les schémas `simplex:` et `simplexchat:`, eux, sont le protocole et se gardent. C'est la **décision ouverte P-2**, non bloquante, déjà instruite dans `DECISIONS.md` : ne rien changer ici tant qu'elle n'est pas rendue |
| Chaînes | le fichier de référence et le français sont traités : il n'y reste que 15 et 14 lignes, toutes de la **troisième** catégorie de `NOTICE-AGPL.md`, les renvois vers l'éditeur amont. Elles ne se réécrivent pas, elles disparaissent avec les fonctions qui les affichent : voir la ligne suivante. Les 39 autres langues gardent le texte de l'amont, décision D-6, et ne se remplacent **jamais** en bloc |
| Financement de l'amont | ce qui reste dans le fichier de référence est l'appel aux dons et au financement participatif de SimpleX Chat Ltd : badges de soutien, bannière d'investissement, section « Support SimpleX Chat » des réglages, contact des développeurs, lien vers le client en ligne de commande, pictogramme de l'équipe. Sept fichiers Kotlin les affichent, dont `views/badges/BadgesSupportSimplexView.kt`, `views/chatlist/GetStakeBanner.kt` et `views/usersettings/SettingsView.kt`. Les laisser serait **solliciter de l'argent pour l'éditeur amont auprès des clients de Trustiatis**, et suggérer une affiliation que la politique de marque interdit expressément. Les retirer fait perdre une capacité réelle : le badge de soutien débloque des fichiers plus volumineux (2 à 5 Gio) et plus durables (7 à 21 jours) sur les serveurs de la communauté. C'est la **décision P-7**, instruite dans `DECISIONS.md` |

## 5. Contrôle

```bash
python3 tools/verifier-marque.py
```

Doit être vert avant la première version publiée. Le pipeline l'exécute à chaque demande de
fusion, en avertissement tant que la Phase 2 n'est pas finie, puis en blocage.
