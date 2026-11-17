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
- [x] **2026-11-17**, section `surfaces` ajoutée à `palette.json` : les panneaux et
      les filets translucides, plus l'opacité des voiles, repris de la charte fournie
      par le Membre (page « Entretien Collaborateur », variables `--panel`, `--panel2`,
      `--line`, `--line2`, `--orange-soft`, `--green-soft`). Ils servent le portail de
      téléchargement et **ne sont pas propagés dans les applications** : Compose n'a
      pas de notion de surface translucide au dessus d'un arrière plan animé

### 2026-11-17 : l'application s'ouvre enfin sur la charte

La charte était dans l'application depuis le 2026-11-15, mais **elle ne se voyait pas**.
Deux réglages la tenaient cachée, et aucun n'était une couleur :

| Ce qui clochait | Ce que voyait l'utilisateur | Correction |
|---|---|---|
| `systemDarkTheme` valait `DARK` | en mode sombre, l'application s'ouvrait sur le gris de l'amont, sous notre logo. Le thème de la marque existait, nommé « Trustiatis » dans les réglages, mais il fallait aller le chercher | la valeur par défaut est `SIMPLEX`, le thème de la marque, fond `#070D18` et surface `#0A1220` |
| `values/colors.xml` posait `window_background_dark` à `#121212` | un gris apparaissait le temps du lancement et des transitions, avant que Compose ne dessine | le fichier est généré depuis `theme_sombre.fond` de la palette |

Le mode clair n'est pas touché. `LIGHT` porte déjà la couleur primaire de la marque sur
fond blanc, ce qui est le thème clair de la palette, relevé sur `trustiatis.com`. Forcer
le sombre contre le réglage du système se ferait contre l'utilisateur, et la décision lui
appartient : les quatre thèmes restent dans les réglages.

### Ce qui n'a pas été aligné, et pourquoi

**La police.** La charte demande Roboto ; l'application utilise Inter, embarquée par
l'amont en six graisses. Elle n'a pas été changée, pour trois raisons qui se cumulent.
La palette dit elle même « rien n'est embarqué dans l'application », ce qui exclut
d'ajouter Roboto au dépôt. « Demandée au système » donne bien Roboto sur Android, mais sur
ordinateur donne Segoe UI, SF Pro ou DejaVu Sans selon la machine : trois polices, dont
aucune n'est la charte, à la place d'une qui est au moins dessinée pour l'interface. Et
changer de police change la largeur de **tous** les textes, c'est à dire rouvre le défaut
de chevauchement de l'écran d'accueil d'Android que le Membre avait relevé. Le gain est
sur Android seul, le risque est partout. À reprendre si le Membre tranche autrement : la
bascule tient en deux lignes, dans `Type.android.kt` et `Type.desktop.kt`.

**Le rayon de 22 px.** C'est le rayon des panneaux du web. Dans l'application, `Shapes`
vaut 4 dp, et c'est une valeur de structure de l'amont : elle dessine les boutons, les
champs, les menus et les fenêtres modales. L'imposer à 22 dp redessinerait toute
l'interface de l'amont, bien au delà d'une question de marque, et sans pouvoir l'éprouver
écran par écran.

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
| 1 | 10 fichiers `drawable-*/ntf_{icon,service_icon}.png` | icônes de notification d'Android | **elles avaient été oubliées**, et c'est la marque de l'amont que les notifications d'Android affichaient, relevé par le Membre le 2026-11-17. Android n'affiche d'une petite icône que son canal alpha, qu'il teinte lui même : ce sont donc des silhouettes blanches et non le monogramme en couleur, sans quoi la notification montrerait un rectangle plein | `feature/branding` |
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

| **3** | `views/usersettings/SettingsView.desktop.kt`, `views/helpers/AppUpdater.kt` | le sélecteur de canal de mise à jour est remplacé par un texte, et `setupUpdateChecker()` ramène la préférence au cas désactivé | risque T-02. Le vérificateur de l'amont interroge en dur l'API GitHub de SimpleX Chat : choisir un canal faisait **télécharger et installer l'application officielle par dessus Trustiatis Secure**, et sur macOS écarter `/Applications/SimpleX.app` pour la remplacer. Même méthode que P-5 : les trois cas de `AppUpdatesChannel` sont conservés, ils sont persistés par leur nom, et le verrou est posé sur le seul chemin qui démarre la vérification, parce que la préférence peut venir d'une archive importée. Détail dans `DISTRIBUTION.md` chapitre 5 | `feature/branding` |
| **3** | `platform/AppLinkScheme.desktop.kt` | six constantes d'enregistrement rebaptisées | elles décident de vraies associations sur le poste. `WINDOWS_PROG_ID_PREFIX` et `WINDOWS_CAPABILITIES_KEY` faisaient écrire à notre installation **les mêmes clés de registre** que l'officielle, donc se voler l'association de protocole. `DEB_ENTRY_NAME` nommait un fichier que notre paquet n'installe plus, donc le contrôle d'enregistrement répondait toujours non. Les schémas `simplex:` et `simplexchat:` ne bougent pas, décision D-3 | `feature/branding` |
| **3** | `platform/StartupError.kt` | titre, texte, adresse de signalement et courriel de support | la fenêtre d'erreur au démarrage envoyait la **trace d'exécution du poste d'un client de Trustiatis** à l'éditeur amont, à qui elle n'était pas destinée | `feature/branding` |
| 2 | `desktopTest/.../AppLinkSchemeTest.kt`, 16 lignes | affirmations alignées sur les constantes | conséquence mécanique, et c'est la preuve : 7 tests sur 254 échouaient, les 247 autres passaient, ce qui borne la modification à la couche d'enregistrement. Les décors d'une fausse installation (`SimpleX.exe`, `SimpleX.AppImage`) restent tels quels, la production ne les lit jamais par leur nom | `feature/branding` |
| 2 | `DesktopApp.kt`, `model/NtfManager.desktop.kt`, `android/.../CallService.kt`, `android/.../SimplexService.kt` | titre de la fenêtre, titres des sélecteurs de fichiers, infobulle de la zone de notification, noms des deux canaux de notification d'Android | des chaînes en dur, hors catalogue, donc invisibles pour `textes-trustiatis.py`. Le titre de la fenêtre est la mention la plus vue du client Desktop. Les **identifiants** de canal ne bougent pas : les changer orphelinerait les réglages que l'utilisateur a posés dessus | `feature/branding` |
| 1 | `moko-resources/{base,fr}/strings.xml` | `trustiatis_updates_from_portal`, clé **ajoutée** | première chaîne propre à Trustiatis. Posée par la section `AJOUTS` de `tools/textes-trustiatis.py`, dans un bloc en fin de fichier, pour qu'un report de l'amont ne touche jamais la même région. Préfixe `trustiatis_` pour qu'une recherche la distingue des clés de l'amont | `feature/branding` |

| **3** | `platform/Platform.desktop.kt` | répertoires de configuration et de données séparés de ceux de l'amont | **le défaut le plus lourd trouvé sur le Desktop.** Gardés tels quels, Trustiatis Secure et l'application officielle liraient et écriraient les **mêmes bases SQLCipher** sous `~/.config/simplex` et `~/.local/share/simplex` (et `AppData\\SimpleX` sur Windows). Et comme le verrou d'instance unique est un fichier de ce répertoire (`dataDir/simplex.started`, `SingleInstance.kt:36`), chacune des deux refuserait de démarrer en voyant l'autre. Les valeurs de `githubAssetName` restent celles de l'amont : elles ne sont lues que par le vérificateur neutralisé, donc inertes | `feature/branding` |
| 2 | `views/database/DatabaseView.kt`, `views/migration/MigrateToDevice.kt` | nom de l'archive exportée, `trustiatis-secure.<horodatage>.zip` | c'est un fichier que l'utilisateur voit, nomme et retrouve. Le **format** de l'archive ne change pas, l'import de l'amont et le nôtre restent interchangeables | `feature/branding` |
| 2 | `android/build.gradle.kts` | nom des fichiers APK produits | ce qui est publié sur le portail et téléchargé par l'utilisateur | `feature/branding` |
| 2 | `SingleInstance.kt`, `AppLinkScheme.desktop.kt`, `desktopTest/.../TempDir.kt` | noms de fils d'exécution, préfixe des répertoires temporaires de test | internes, vus seulement dans une trace d'exécution ou un profileur, là où savoir quelle application on inspecte a un prix | `feature/branding` |
| outil | `tools/verifier-marque.py`, `tools/test-verifier-marque.py` | liste blanche étendue aux identifiants, aux URL de l'amont et au financement conservé ; décision ligne à ligne extraite dans `ligne_signalee()` | le compteur mesure autant le contrôle que le code, donc le contrôle se prouve : 42 cas de tri épinglent la frontière des trois catégories de `NOTICE-AGPL.md` et la dérogation P-7. Deux pièges d'ordre ont déjà mordu, l'alternance de Python retient le motif le plus à gauche et les deux substitutions ne commutent pas ; Troisième piège, trouvé en posant la première dérogation réelle : **l'échappatoire documentée du contrôle, le commentaire `marque-ok`, était inutilisable dans un catalogue**, c'est à dire dans les seuls fichiers où une chaîne peut avoir besoin de nommer la marque de l'amont, parce que les commentaires XML y sont vidés avant l'examen de la ligne ; c'est donc la ligne brute qui porte désormais la dérogation. Le test appelle la fonction de décision au lieu de la réécrire, et suit le même chemin qu'elle, commentaires vidés compris : un premier cas écrit sans cette précaution passait pour la mauvaise raison, et un second parce que la phrase choisie était déjà permise. 45 cas. 4 967 signalements au départ, **2 782** ; Kotlin 717, puis **12**, et ces douze sont le risque T-02 et l'index du portail, laissés visibles exprès | `feature/branding` |

| **3** | `views/onboarding/SimpleXInfo.kt`, `moko-resources/{base,fr}/strings.xml` | mention de non affiliation affichée dans l'écran « À propos », clé `trustiatis_non_affiliation` **ajoutée** | contrepartie technique de la décision P-7, et non une formalité : garder l'appel au soutien de l'amont dans une application nommée autrement peut se lire comme une affiliation, que sa politique de marque interdit expressément. Le texte est celui de `NOTICE-AGPL.md`, mot pour mot. Posée dans la branche où `onboardingStage` est nul, qui est exactement l'écran ouvert depuis les réglages : la mention n'encombre pas le premier démarrage, et une seule modification sert Android et le Desktop, qui partagent cette disposition | `feature/branding` |

| 1 | `ui/theme/ColorTrustiatis.kt`, **fichier généré** | les couleurs des quatre thèmes de l'application | **c'était le manque le plus visible du projet** : `SimplexBlue` (#0088ff) alimentait les quatre thèmes, donc l'application s'ouvrait en bleu de l'amont sous notre logo. Les couleurs de marque sont reprises telles quelles dans `branding/colors/palette.json` (sauge sur fond clair, sauge clair sur fond sombre, et le fond de `id.trustiatis.com` pour le thème de signature). Les teintes dérivées, bulles et citations, ne sont **pas choisies** : chacune garde la clarté et la saturation de l'amont et ne reçoit que notre teinte, par une conversion OkLCh ajoutée au propagateur. Le contraste dessiné par l'amont est donc conservé au chiffre près, c'est vérifiable, et aucune valeur n'est inventée, ce qu'interdit le chapitre 4 | `feature/branding` |
| **3** | `ui/theme/Theme.kt`, 9 blocs | les quatre palettes pointent vers les constantes générées | seule modification faite à la main de cette série, et elle est bornée aux affectations, un bloc par thème, pour qu'un report de l'amont conflicte au bon endroit. **La paire sentinelle n'est pas touchée** : le `#0088ff` des catalogues n'est pas une couleur affichée, c'est le repère que `ReadableTextWithLink` remplace par `MaterialTheme.colors.primary`, et l'avertissement de l'amont (« si cette valeur change, changez aussi #0088ff ») demande de garder la paire synchronisée, pas de la rebaptiser. Changer la palette suffit, les liens suivent. `DefaultTheme.SIMPLEX` garde son nom, qui est persisté | `feature/branding` |

| **3** | `views/newchat/OnboardingCards.kt` | le dégradé de l'écran d'accueil | **trouvé en regardant la première capture, pas en lisant le code** : un pavé bleu cyan occupait la moitié de l'écran de bienvenue, dessiné en dur et invisible pour tous les contrôles. L'amont l'écrit déjà en OkLCh avec l'angle de teinte en clair, donc seule cette troisième valeur change, dix nombres : les arrêts froids prennent l'angle du sauge, les deux arrêts chauds celui du terracotta. La leçon vaut d'être notée : **une capture d'écran trouve ce qu'un `grep` ne trouve pas** | `feature/branding` |
| 2 | `views/newchat/QRCode.kt`, `views/newchat/AddChannelView.kt` | teinte du QR code (lue depuis `ColorTrustiatis.kt` depuis le 2026-11-17, sa valeur est inchangée), couleur de l'arc de progression | le QR passe du bleu marine de l'amont au vert très sombre de même clarté, ce qui préserve le contraste dont la lecture dépend. L'arc, lui, était un bleu Material écrit en dur et commenté « accent blue » : il lit désormais `MaterialTheme.colors.primary`, donc il **suit le thème choisi par l'utilisateur**, ce que la valeur littérale ne faisait pas | `feature/branding` |
| outil | `scripts/desktop/installer-sans-root.sh` | installation du paquet dans l'espace d'un utilisateur | `dpkg -i` demande root, que ce poste n'a pas. Le paquet de jpackage est autonome, donc son arborescence se dépose aussi bien sous `~/.local`. Le script fait en plus les deux choses que le paquet ne sait pas faire : il écrit le `MimeType` des liens d'invitation dans l'entrée de bureau, que Compose Desktop ne sait pas transmettre à jpackage, et il force le rendu logiciel de Skiko, sans quoi l'application plante au redessin sur une machine virtuelle sans accélération | `feature/branding` |

| **3** | `views/onboarding/WhatsNewView.kt`, `views/usersettings/SettingsView.kt` | l'appel au soutien de l'amont ne paraît plus | **P-7 bis**, qui annule P-7. `crowdfundingAvailable()` rend faux, ce qui ferme d'un coup la bannière de la liste des conversations, la carte des nouveautés et la section « Invest » des réglages, sans toucher leurs trois appelants ni l'écran lui même. S'y ajoutent l'entrée des badges, dont l'intitulé est « Support SimpleX » tant qu'on n'en a pas, et la section « soutenir le projet », qui menait à la page de contribution de l'amont, à sa fiche Google Play et à son dépôt. Le courriel de contact est réécrit vers `support@trustiatis.com`, les deux canaux de l'amont retirés. **Ce que cela coûte est consigné** : le badge était un achat qui donnait des fichiers de 2 à 5 Gio conservés de 7 à 21 jours, et son entrée se rouvre d'une ligne si le Membre le décide | `feature/branding` |

| **3** | `views/chatlist/ChatListView.kt`, `views/onboarding/WhatsNewView.kt` | la bannière « Support SimpleX » de la liste des conversations ne paraît plus | **P-7 ter**, le 2026-11-17, et c'est une porte que P-7 bis avait manquée. Retirer l'entrée des réglages ne suffisait pas : la liste des conversations pose cette bannière d'elle même dès la quatrième conversation, et elle mène au même achat. Le Membre l'a vue dans la 7.1.2-ts1. Même méthode que P-5 et P-7 bis, on neutralise la porte et non ses appelants : `badgePitchAvailable()` rend faux, posée dans le même fichier que `crowdfundingAvailable()` pour que les deux décisions se lisent ensemble. **Ce qui n'est pas neutralisé, et c'est voulu** : les alertes qui informent un utilisateur d'un badge qu'il a réellement payé, par exemple après l'import d'une base venue de l'application officielle ; elles renseignent, elles ne sollicitent pas | `feature/branding` |
| **3** | `views/onboarding/WhatsNewView.kt` | l'écran des nouveautés de la v7.1.4 ne présente plus les badges | **P-7 quater**, le 2026-11-17 : le report de la v7.1.4 a rouvert la porte. Sa description de version appelle `SupporterBadgeWhatsNew`, qui n'existait pas quand P-7 bis a fermé les six premières, et qui affiche « Supporter badges » puis « Support SimpleX and get a badge », menant à l'achat. **Trouvé en lançant le paquet construit, pas en lisant le code** : aucun compteur ne le voyait, et `InvestInSimpleXChatView`, juste à côté dans la même description, était déjà muette parce qu'elle consulte `crowdfundingAvailable()`. Le même verrou, `badgePitchAvailable()`, est posé en tête du composable. Le bloc disparaît en entier et non seulement son lien : annoncer une capacité que notre version ne permet pas d'obtenir tromperait l'utilisateur | `feature/branding` |
| **3** | `views/usersettings/SetSimplexNameView.kt`, `views/chat/ChatItemInfoView.kt` | l'offre Wefunder et la dernière sollicitation au badge ne paraissent plus | **P-7 quinquies**, le 2026-11-17. Trouvées en remontant depuis la porte précédente, et non par un contrôle. Deux points, aucun des deux neuf, aucun des deux visible : l'écran qui règle le nom SimpleX portait en pied de section « Crowdfunding investors can reserve names before the sale starts », c'est à dire l'offre de titres elle même, dans les réglages ; et l'information d'un message proposait « Larger files available for longer » au moment précis où un fichier expire, à quelqu'un qui n'a pas de badge. Ce second point était **le seul appelant de `noShownBadge()` sans verrou**, les quatre autres l'ayant reçu par P-7 bis et P-7 ter. Les deux verrous existants suffisent, `crowdfundingAvailable()` et `badgePitchAvailable()`. **Ce qui reste, et c'est voulu** : le nom SimpleX, le compte à rebours de la vente et la date d'expiration du fichier, qui informent sans solliciter | `feature/branding` |
| 2 | `ui/theme/Color.kt`, `views/chat/TextSelection.kt` | la couleur de l'incognito et la surbrillance de sélection de texte | deux couleurs que le Membre a vues hors charte, et que ni le contrôle de marque ni les captures précédentes n'avaient relevées, parce qu'aucune ne porte le nom de l'amont. L'`Indigo` #9966FF marque les profils incognito dans une quinzaine d'écrans ; la surbrillance était un bleu pur #0066FF à 30 %. Les deux valeurs viennent maintenant de `ColorTrustiatis.kt`, et les noms de l'amont sont conservés pour que leurs appelants restent intacts. **L'incognito ne se dérive pas** : c'est un signal, pas un décor, et `teinte_de` appliqué à la teinte du terracotta donnait #F33D50, un rouge vif que l'on prendrait pour une alerte, parce que la dérivation garde la saturation très haute de l'indigo. Il prend donc l'accent de la palette tel quel. Le contraste est dit franchement : 3,6 contre 1 sur blanc, là où l'indigo de l'amont est à 4,1, les deux sous le seuil du petit texte, et 5,6 sur le fond sombre de la charte | `feature/branding` |
| 1 | `ui/theme/ColorTrustiatis.kt`, **fichier généré** | les bulles **reçues** du thème Signature | elles étaient restées bleues quand les bulles envoyées étaient passées au sauge, donc l'écran de conversation mélangeait deux teintes. Le thème Signature est le seul des quatre que l'amont teinte : dans les trois autres, les bulles reçues sont des gris neutres, que `teinte_de` laisse d'ailleurs intacts par construction. La correction porte donc sur ce thème et sur lui seul | `feature/branding` |
| 1 | `moko-resources/{base,fr}/strings.xml`, trois clés du premier écran | textes de l'accueil ramenés au budget de l'amont | le Membre a vu les textes et les icônes se chevaucher sur Android. **La mise en page n'est pas en cause, nos textes le sont** : `SimpleXInfoLayout` pose des interlignes fixes, 42, 25 et 20 sp, alors que le corps suit le réglage de taille du système, et `OnboardingShrinkingLayout` ne rétrécit l'image que jusqu'à un point. Notre `onboarding_first_network` faisait 101 caractères contre 58 à l'amont, soit deux lignes de plus sur un téléphone. Les budgets de l'amont sont inscrits dans `tools/textes-trustiatis.py`, au dessus de la table, et toute reformulation se vérifie contre eux | `feature/branding` |
| outil | `scripts/desktop/verifier-vlc-linux.sh` | refuse un arbre VLC dont les noms ne se résolvent pas | écrit après le défaut le plus coûteux de la série : la 7.1.2-ts1 est sortie sans `libvlccore.so.9`, que `libvlc.so` réclame par `NEEDED` avec un `RUNPATH $ORIGIN`. Les messages passaient, les appels faisaient remonter un `NoClassDefFoundError`. **La construction, elle, avait réussi** : le manque ne se voit qu'à l'exécution, et seulement sur appel. Le script lit les `NEEDED` de chaque ELF du premier niveau et signale tout nom absent dont l'arbre fournit pourtant la racine sous un autre nom, ce qui est la signature d'une copie ayant suivi les liens symboliques. Éprouvé dans les deux sens : il passe sur l'arbre courant, il refuse l'arbre réellement livré | `feature/branding` |
| outil | `scripts/desktop/paquet-deb-trustiatis.sh` | empaquette le `.deb` et vérifie ce qu'il contient | `make-deb-linux.sh` de l'amont fait la bonne chose mais ne peut pas servir : il commence par recompiler le coeur, ce que D-2 interdit, et **toutes ses recherches portent le nom de l'amont** (`simplex*.deb`, `/opt/*imple*/`, `simplex.cfg`), qu'aucun fichier de notre paquet ne rencontre. C'est pourquoi la 7.1.2-ts1 est sortie d'un `packageDeb` nu, donc sans aucune correction d'après empaquetage : son entrée de bureau avait `MimeType=` vide et un `Exec=` sans `%u`, **donc les liens `simplex:` n'étaient pas enregistrés sous Linux, et la décision D-3 n'était pas tenue**. Ce script transpose les étapes de l'amont à notre nom, et refuse de rendre la main si l'une d'elles n'a pas pris | `feature/branding` |

| **3** | `views/helpers/ChatWallpaper.kt`, motif `SCHOOL` et fond partagé | le canevas de la conversation et les bulles qu'il impose | **constat du Membre, et il allait plus loin qu'il n'y paraissait**. Le canevas sortait bleu, `#E7F5FF` en clair. En cherchant d'où, on trouve que `Theme.kt` lit `presetWallpaperTheme` **avant** la palette de base : tant qu'un motif est actif, et il l'est par défaut, ce sont les couleurs du MOTIF qui donnent les bulles. Autrement dit **le travail fait sur les quatre thèmes ne se voyait pas dans la configuration par défaut**, et il ne pouvait pas se voir. Les 22 valeurs du motif par défaut passent donc par `teinte_de` avec la teinte de l'ACCENT : le Membre a demandé du rose, la charte ne nomme aucun rose, sa couleur rose est le terracotta `#BE7C7B` qu'elle appelle « accent principal », et la même charte situe la marque « en opposition au bleu tech générique ». Saturation vérifiée et non supposée : chaque valeur dérivée est à ou sous la chroma du terracotta lui même, 0,0828, sauf la citation Signature à 0,0843, soit 1,8 % au dessus. Les bulles **reçues** de l'amont sont des gris neutres et traversent la dérivation intactes, ce que `teinte_de` fait par construction : une conversation dont les deux côtés sont teintés ne se lit plus. Seul le motif par défaut est repris ; chats, fleurs et voyage sont des choix explicites de l'utilisateur et gardent l'identité de leur image | `feature/branding` |

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
| Chaînes | le fichier de référence et le français sont **à zéro signalement**. Les 39 autres langues gardent le texte de l'amont, décision D-6, et ne se remplacent **jamais** en bloc |
| Financement de l'amont | **décision P-7 rendue : conservé.** Badges, bannière de financement, section « Support SimpleX Chat », contact des développeurs, pictogramme de l'équipe : rien n'est retiré. La dérogation vit dans `EDITEUR_CONSERVE_P7` de `tools/verifier-marque.py`, nommée et testée, et non en commentaires dispersés. Deux réserves portées au dossier dans `DECISIONS.md` : la partie « Invest » est un financement **en capital** sur Wefunder, donc une offre de titres, à soumettre à la GRC ; et la mention de non affiliation de `NOTICE-AGPL.md` devient la contrepartie technique de cette décision, à placer dans l'écran « À propos » |

## 5. Contrôle

```bash
python3 tools/verifier-marque.py
```

Doit être vert avant la première version publiée. Le pipeline l'exécute à chaque demande de
fusion, en avertissement tant que la Phase 2 n'est pas finie, puis en blocage.
