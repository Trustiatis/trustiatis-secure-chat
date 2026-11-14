# Trustiatis Secure, audit technique initial du dépôt SimpleX Chat

**Phase 1 du projet. Document de décision, aucune modification de code n'a été faite.**

| | |
|---|---|
| Entité porteuse | Trustiatis |
| Produit visé | Trustiatis Secure |
| Amont audité | `https://github.com/simplex-chat/simplex-chat` |
| Version auditée | **tag `v7.1.3`**, commit `19889b1a0ee11b6071c41146b6549f46e2b5b6af`, daté du 2026-11-11 |
| Branche par défaut de l'amont | `stable` (le `HEAD` distant pointe sur `stable`, pas sur `master`) |
| Version en préparation chez l'amont | `7.1.4` (sur `master`, commit `3282dfc`, « ios: 7.1.4 build 372 ») |
| Date de l'audit | 2026-11-14 |
| Statut | PROJET, soumis à validation avant toute modification |

**Méthode.** Clone superficiel (`--depth 1 --filter=blob:none`) dans un répertoire isolé hors
du dépôt de travail, puis extraction du tag `v7.1.3`. Toutes les affirmations de ce document
sont issues de la lecture des fichiers du dépôt. Chaque constat porte une mention de statut :

- **[V]** vérifié par lecture directe du fichier cité,
- **[D]** déduit d'un fichier lu, à confirmer par un essai ou par la lecture d'un autre dépôt,
- **[NV]** non vérifiable depuis ce dépôt (dépôt tiers, compte éditeur, matériel).

Aucune compilation n'a été tentée : le poste d'audit ne dispose ni de JDK, ni de Gradle,
ni de GHC, ni de Nix, ni du SDK Android, et il ne reste que 6 Gio d'espace disque libre.
Aucune affirmation de ce document ne prétend donc qu'un binaire a été produit.

---

## 1. Synthèse pour la décision

**Le projet est faisable, mais il ne se résume pas à un changement de logo.** Sept points
déterminent le coût et le calendrier, et six d'entre eux doivent être arbitrés avant la Phase 2.

1. **La politique de marque de SimpleX interdit explicitement de conserver le nom et le logo
   SimpleX dans la marque d'un logiciel dérivé, les forks étant nommément visés** [V].
   Retirer la marque SimpleX n'est donc pas une option esthétique : c'est une obligation.
   Cela élargit le périmètre bien au delà des icônes, car la marque est présente dans
   **3 407 occurrences réparties sur 41 fichiers de chaînes** (le fichier de référence et 40 langues) côté multiplateforme [V].
2. **Le coeur de l'application est écrit en Haskell et se compile via Nix.** Les clients
   Android, Desktop et iOS ne sont que des interfaces qui appellent une bibliothèque native
   (`libsimplex`) par FFI [V]. Deux stratégies s'opposent, détaillées au chapitre 13 :
   réutiliser les bibliothèques compilées par l'amont (léger, mais interdit de modifier les
   serveurs préconfigurés), ou reconstruire le coeur soi-même (lourd, mais autonome).
3. **Aucune dépendance à Firebase ni aux Google Play Services pour les notifications sur
   Android** [V]. La compatibilité GrapheneOS est donc, sur le papier, acquise par
   construction : il existe déjà une variante de build `foss` sans dépendance Google, qui est
   la variante **par défaut**. Il reste à la valider sur un appareil réel.
4. **Sur iOS, à l'inverse, les notifications passent obligatoirement par un serveur de
   notification tiers qui relaie vers APNs** [V]. Les serveurs par défaut sont ceux de
   SimpleX (`ntf3.simplex.im`, `ntf4.simplex.im`). Un fork portant son propre identifiant
   d'application ne peut pas recevoir de notification par ces serveurs [D]. Trustiatis devra
   héberger un **troisième composant serveur non prévu dans votre cahier des charges**, en
   plus de SMP et XFTP.
5. **La publication sur l'App Store d'une application sous AGPLv3 dont Trustiatis n'est pas
   titulaire des droits est juridiquement contestée.** C'est le risque le plus sérieux du
   projet. Voir chapitre 12, risque **J-02**.
6. **Le mécanisme de mise à jour du client Desktop est codé en dur sur le dépôt GitHub de
   SimpleX** [V]. S'il n'est pas modifié, Trustiatis Secure proposera à ses utilisateurs
   d'installer le paquet SimpleX officiel par dessus lui-même. Correction obligatoire.
7. **La signature Authenticode n'existe pas chez l'amont** [V] : seul macOS est signé et
   notarisé. La chaîne de signature Windows est à construire entièrement.

---

## 2. Arborescence réelle du dépôt

Racine du dépôt au tag `v7.1.3` [V] :

```text
simplex-chat/
├── apps/                      applications et bots
│   ├── ios/                   application iOS et macOS Catalyst (Swift, SwiftUI)
│   ├── multiplatform/         Android + Desktop (Kotlin Multiplatform, Compose)
│   ├── simplex-chat/          client en ligne de commande (Haskell)
│   ├── simplex-bot/           bot minimal (Haskell)
│   ├── simplex-bot-advanced/  bot avec gestion d'événements (Haskell)
│   ├── simplex-broadcast-bot/ bot de diffusion
│   ├── simplex-calculator-bot/
│   ├── simplex-directory-service/
│   ├── simplex-badge-service/
│   ├── simplex-support-bot/
│   └── simplex-support-bot-light/
├── src/                       coeur applicatif Haskell (317 fichiers, 68 866 lignes)
├── packages/                  bibliothèques clientes
│   ├── simplex-chat-client/   types TypeScript
│   ├── simplex-chat-nodejs/   liaison Node.js
│   ├── simplex-chat-python/   liaison Python
│   ├── simplex-chat-mcp/      serveur MCP
│   └── simplex-chat-webrtc/   couche WebRTC pour les appels
├── scripts/                   outillage de build, de CI et de déploiement
├── docs/                      documentation publique (licence, marque, serveurs, thèmes)
├── tests/                     tests Haskell
├── website/, blog/, media-logos/, assets/, images/
├── flake.nix, flake.lock      construction du coeur par Nix (haskell.nix)
├── cabal.project, simplex-chat.cabal
├── Dockerfile, Dockerfile.build
├── LICENSE                    GNU AGPL version 3
└── .github/workflows/         intégration continue (3 446 lignes de YAML)
```

Un seul sous-module Git, sans rapport avec le coeur [V] (`.gitmodules`) :
`apps/multiplatform/external/nanohttpd/upstream` vers `https://github.com/NanoHttpd/nanohttpd`,
utilisé par le client Desktop uniquement.

### 2.1 Branche multiplateforme (Android et Desktop)

```text
apps/multiplatform/
├── settings.gradle.kts        modules : :android, :desktop, :common, :external:nanohttpd
├── build.gradle.kts           versions, signature macOS, propriétés de build
├── gradle.properties          android.version_name=7.1.3, android.version_code=395
│                              desktop.version_name=7.1.3, desktop.version_code=174
│                              kotlin 2.1.20, AGP 8.10.1, Compose 1.8.2, cible JVM 11
├── local.properties.example   surcharges locales (nom de l'app de debug, suffixe d'ID)
├── common/                    383 fichiers Kotlin, 102 942 lignes
│   └── src/
│       ├── commonMain/        interface Compose, modèle, contrôleur, thèmes
│       │   ├── kotlin/chat/simplex/common/
│       │   ├── moko-resources/   chaînes et images (41 langues + base)
│       │   ├── resources/assets/ interface d'appel WebRTC (call.html, call.js)
│       │   └── cpp/              ponts natifs et chaînes de compilation croisée
│       ├── androidMain/       implémentations Android (notifications, biométrie, Keystore)
│       ├── desktopMain/       implémentations Desktop (notifications, mise à jour)
│       ├── commonTest/, desktopTest/
├── android/                   12 fichiers Kotlin, 2 839 lignes (conteneur d'application)
│   └── src/
│       ├── main/              AndroidManifest.xml, res/, services, activités
│       ├── foss/              variante sans dépendance Google (par défaut)
│       ├── google/            variante Play, avec Play Billing
│       ├── test/, androidTest/
├── desktop/                   1 fichier Kotlin, 120 lignes (point d'entrée JVM)
│   └── src/jvmMain/resources/distribute/   icônes et métadonnées de paquet
├── product/                   documentation fonctionnelle (flux, vues, règles, lacunes)
└── spec/                      documentation d'architecture (api, état, base, services)
```

Les dossiers `product/` et `spec/` de l'amont constituent une documentation d'architecture
remarquablement détaillée, avec références de lignes. Elle doit être exploitée en Phase 2 et
non refaite.

### 2.2 Branche iOS

```text
apps/ios/                      235 fichiers Swift, 83 982 lignes
├── SimpleX.xcodeproj/         projet Xcode, 6 cibles
├── Shared/                    interface et modèle de l'application
│   ├── Assets.xcassets/       30 jeux de ressources dont AppIcon et DarkAppIcon
│   ├── SharedAssets.xcassets/, SimpleXAssets.xcassets/
│   ├── Theme/                 Theme.swift, ThemeManager.swift
│   ├── Model/, Views/
│   └── AppDelegate.swift, ContentView.swift
├── SimpleXChat/               framework partagé (types, API, thème, groupe d'app)
├── SimpleX NSE/               extension de service de notification
├── SimpleX SE/                extension de partage
├── SimpleX Snapshots/         tests de capture d'écran
├── Tests iOS/
├── SimpleX (iOS).entitlements, SimpleX--iOS--Info.plist
├── Debug.xcconfig, Release.xcconfig
├── product/, spec/            documentation fonctionnelle et d'architecture
├── 17 dossiers *.lproj        localisations (Localizable.strings)
└── SimpleX Localizations/     28 catalogues *.xcloc
```

---

## 3. Technologies par plateforme

| Couche | Technologie | Détail vérifié |
|---|---|---|
| Coeur commun à toutes les plateformes | **Haskell**, GHC 9.6.3 (et GHC 8.10.7 pour la cible Android armv7a, épinglée par révision) | `.github/workflows/build.yml` (`GHC_VER: 9.6.3`), `flake.nix` [V] |
| Base de données | SQLCipher (SQLite chiffré), PostgreSQL en option | `gradle.properties` : `database.backend=sqlite` [V] |
| Protocoles | SMP (messagerie), XFTP (fichiers), bibliothèque `simplexmq` | `cabal.project`, `src/` [V] |
| Pont coeur vers interface | FFI, JNI sur Android, chargement natif sur Desktop, bibliothèque statique sur iOS | `spec/architecture.md`, `common/src/commonMain/cpp/` [V] |
| Android | **Kotlin 2.1.20**, Compose Multiplatform 1.8.2, AGP 8.10.1, NDK 23.1.7779620, `compileSdk`/`targetSdk` 36, `minSdk` 26 | `android/build.gradle.kts` [V] |
| Desktop | **JVM** (cible 11), Compose Desktop, empaquetage par `jpackage`, CMake pour les bibliothèques natives | `desktop/build.gradle.kts` [V] |
| iOS | **Swift**, SwiftUI, Xcode, 7 paquets Swift externes | `Package.resolved` [V] |
| Ressources partagées Android et Desktop | **Moko Resources** 0.27.1 | `build.gradle.kts` racine [V] |
| Appels audio et vidéo | **WebRTC**, interface portée par une page web embarquée | `assets/www/call.html`, `call.js` [V] |
| Lecture vidéo Desktop | **VLCJ** (bibliothèque VLC, LGPLv2) | `docs/dependencies/README.md`, `scripts/desktop/prepare-vlc-*.sh` [V] |

### 3.1 Points d'entrée

| Plateforme | Point d'entrée | Fichier |
|---|---|---|
| Android | classe d'application `SimplexApp`, activité `MainActivity` | `android/src/main/AndroidManifest.xml` [V] |
| Desktop | `chat.simplex.desktop.MainKt` | `desktop/build.gradle.kts`, `desktop/src/jvmMain/kotlin/chat/simplex/desktop/Main.kt` [V] |
| iOS | `AppDelegate.swift`, `ContentView.swift`, schéma Xcode « SimpleX (iOS) » | `.github/workflows/build.yml` [V] |
| Ligne de commande | `apps/simplex-chat/` | [V] |

### 3.2 Architecture en trois couches

Résumé de `apps/multiplatform/spec/architecture.md` [V] : l'interface Compose appelle des
fonctions suspendues de `ChatController`, qui sérialise une commande en JSON et la transmet
au coeur Haskell par JNI (`chatSendCmdRetry`). Les événements asynchrones remontent par une
boucle de réception (`chatRecvMsgWait`). Le coeur détient l'état protocolaire, les clés et la
base chiffrée.

**Conséquence directe pour le projet :** la personnalisation graphique n'a aucun contact avec
la cryptographie. L'exigence « ne pas modifier le moteur cryptographique pour du branding »
est satisfaite par la structure même du logiciel, à condition de ne modifier que les couches
interface et ressources.

---

## 4. Modules communs et modules spécifiques

| Nature | Emplacement | Partagé entre |
|---|---|---|
| Coeur protocolaire et base | `src/` (Haskell) | Android, Desktop, iOS, CLI, bots |
| Modèle, contrôleur, interface | `apps/multiplatform/common/src/commonMain/` | Android et Desktop uniquement |
| Chaînes et images | `common/src/commonMain/moko-resources/` | Android et Desktop uniquement |
| Spécifique Android | `common/src/androidMain/` + `android/src/` | Android |
| Spécifique Desktop | `common/src/desktopMain/` + `desktop/src/` | Desktop |
| Interface iOS | `apps/ios/Shared/`, `apps/ios/SimpleXChat/` | iOS seul |
| Chaînes et images iOS | `apps/ios/*.lproj/`, `Shared/Assets.xcassets/` | iOS seul |

**Point structurant pour le design system :** il n'existe **aucun** partage de ressources
graphiques ni de chaînes entre iOS et le couple Android/Desktop. Les deux arbres sont
indépendants. L'homogénéité visuelle entre iOS et les autres plateformes est obtenue chez
l'amont par duplication disciplinée, pas par un mécanisme technique. Le design system
Trustiatis devra donc être un référentiel source unique (`branding/`) dont les ressources sont
**générées et propagées** vers les deux arbres par script, exactement comme le fait déjà
`design-system/detramer.py` pour les logos du groupe.

---

## 5. Mécanismes de compilation existants

La construction se fait en **trois étages distincts**, et c'est le point le plus coûteux du
projet.

### Étage 1, le coeur Haskell

Construit par **Nix avec flakes**, via `haskell.nix`. `flake.nix` déclare des `hydraJobs`
par cible [V] :

| Cible | Nom du job Nix | Hôte de compilation requis |
|---|---|---|
| Android arm64 | `x86_64-linux.aarch64-android:lib:simplex-chat` et `:lib:support` | Linux x86_64 |
| Android armv7a | `x86_64-linux.armv7a-android:lib:simplex-chat` et `:lib:support` | Linux x86_64, **GHC 8.10.7 épinglé** |
| iOS arm64 | `aarch64-darwin.aarch64-darwin-ios:lib:simplex-chat` | **macOS Apple Silicon** |
| iOS x86_64 (simulateur) | `x86_64-darwin.x86_64-darwin-ios:lib:simplex-chat` | macOS Intel |
| Desktop Linux | `scripts/desktop/build-lib-linux.sh` | Linux (dans un conteneur Docker en CI) |
| Desktop macOS | `scripts/desktop/build-lib-mac.sh` | macOS |
| Desktop Windows | `scripts/desktop/build-lib-windows.sh` | Windows |

Pour iOS, le post-traitement utilise `mac2ios` de `github:zw3rk/mobile-core-tools` [V].

Deux scripts permettent de **récupérer les bibliothèques déjà compilées** au lieu de les
construire [V] : `scripts/android/download-libs.sh` et `scripts/ios/download-libs.sh`.
Tous deux prennent en premier argument l'URL d'un dépôt de jobs Hydra, c'est à dire
l'infrastructure de build de SimpleX. C'est le pivot de la décision du chapitre 13.

`scripts/android/build-android.sh` réalise une construction reproductible complète, en
installant Nix si nécessaire, et produit un APK `foss` aligné et horodaté de façon
déterministe [V]. Ce script est la référence pour la reproductibilité des builds Trustiatis.

### Étage 2, Android et Desktop par Gradle

Commandes officielles, extraites de `apps/multiplatform/README.md` [V] :

```bash
./gradlew assembleFossDebug                      # APK de debug, sans dépendance Google
./gradlew assembleFossRelease                    # APK de production, hors Play
./gradlew bundleGoogleRelease                    # AAB pour Google Play, avec Play Billing
./gradlew :desktop:packageDistributionForCurrentOS   # paquet Desktop du système courant
./gradlew desktopTest                            # tests JVM
./gradlew connectedFossDebugAndroidTest          # tests instrumentés Android
./gradlew common:cmakeBuild -PcrossCompile       # bibliothèques natives, toutes cibles
```

**Garde-fou important de l'amont** [V] : les tâches agrégées (`build`, `assemble`,
`assembleRelease`, `bundle`) **échouent volontairement**, par une vérification du graphe de
tâches dans `android/build.gradle.kts`, pour empêcher de produire un APK de production
contenant Play Billing ou un bundle sans Play Billing. Il faut toujours nommer la variante.
Ce garde-fou doit être conservé tel quel.

### Étage 3, iOS par Xcode

Compilation par `xcodebuild` sur le projet `apps/ios/SimpleX.xcodeproj`, schéma
« SimpleX (iOS) » [V]. En intégration continue, l'amont construit avec
`scripts/ci/ios-stub-libs.sh` et `CODE_SIGNING_ALLOWED=NO`, c'est à dire **sans** le vrai
coeur et sans signature : il ne s'agit que d'une vérification de compilation. Les
constructions signées et publiables se font ailleurs.

### 5.1 Formats de paquets réellement produits

| Plateforme | Format | Source | Statut |
|---|---|---|---|
| Linux | **DEB** | `scripts/desktop/make-deb-linux.sh`, `TargetFormat.Deb` | [V] produit en CI |
| Linux | **AppImage** | `scripts/desktop/make-appimage-linux.sh` | [V] produit en CI, **hors** `targetFormats` Gradle (la cible `AppImage` de Compose est désactivée en commentaire car elle casse la synchronisation Gradle sur Mac) |
| Linux | **Flatpak** | `scripts/flatpak/chat.simplex.simplex.desktop` et `.metainfo.xml` | [V] fichiers présents, recette de build **non** présente dans ce dépôt |
| Linux | CLI en DEB | `scripts/desktop/build-cli-deb.sh` | [V] |
| macOS | **DMG** | `TargetFormat.Dmg` | [V] arm64 et Intel (`macos-latest` et `macos-15-intel`) |
| Windows | **MSI** et **EXE** | `TargetFormat.Msi`, `TargetFormat.Exe` | [V] MSI publié en CI |
| Android | **APK** par ABI, **AAB** | Gradle, `scripts/ci/android/release-apk.sh` | [V] |
| iOS | IPA | non construit dans ce dépôt | [NV] |

**Architectures Linux** : l'amont compile nativement sur `ubuntu-22.04`, `ubuntu-24.04`,
`ubuntu-22.04-arm` et `ubuntu-24.04-arm` [V]. L'ARM64 Linux est donc **réellement pris en
charge**, par compilation native et non par compilation croisée (les chaînes croisées
`linux-aarch64` sont en commentaire dans `desktop/build.gradle.kts`).

**Architectures Android** : `arm64-v8a` et `armeabi-v7a` seulement. Pas de x86 ni x86_64, et
`isUniversalApk = false` [V]. **L'« APK universel » demandé au chapitre 6 de votre cahier des
charges n'existe pas en l'état** et nécessiterait un changement de configuration, au prix du
doublement de la taille du paquet.

---

## 6. Dépendances natives

| Dépendance | Rôle | Licence | Plateformes |
|---|---|---|---|
| SQLCipher | base chiffrée | BSD modifiée | toutes |
| OpenSSL | TLS, cryptographie | Apache 2.0 | toutes, préparée par `scripts/desktop/prepare-openssl-windows.sh` |
| WebRTC | appels audio et vidéo | BSD 3 clauses | toutes |
| VLC via VLCJ | lecture vidéo Desktop | **LGPLv2** | Desktop, `prepare-vlc-{linux,mac,windows}.sh` |
| libspng, miniz | PNG | BSD 2 clauses, libpng 2.0, MIT | toutes |
| mozjpeg | JPEG | IJG et BSD 3 clauses | toutes |
| stb_image_resize | redimensionnement | MIT ou domaine public | toutes |
| Compact ICC Profiles | profil Display P3 | CC0 1.0 | toutes |
| libffi, libiconv | runtime Haskell sur Android | voir `flake.nix` | Android |
| NanoHTTPD | serveur local Desktop | sous-module Git | Desktop |

Source : `docs/dependencies/README.md` et `docs/dependencies/HASKELL.md` [V]. Le rapport
Haskell est produit automatiquement et liste les licences de l'intégralité de l'arbre de
dépendances, avec deux entrées `*MISSING*` à documenter (`direct-sqlcipher` 2.3.28).

Paquets Swift externes, depuis `Package.resolved` [V] : `Elegant-Emoji-Picker`,
`lzstring-swift`, `ink`, `Yams`, `SwiftyGif`, `CodeScanner`, et
`github.com/simplex-chat/WebRTC.git` (fork SimpleX de WebRTC, dépendance de build **sur un
dépôt contrôlé par l'amont**, à mirrorer côté Trustiatis).

Dépendances Android notables [V] : `androidx.core`, `appcompat`, `lifecycle`, `activity-compose`,
`work-runtime-ktx` et `work-multiprocess`, `com.jakewharton:process-phoenix`,
`com.google.accompanist:accompanist-permissions`. **La seule dépendance Google Play est
`com.android.billingclient:billing:9.1.0`, et elle n'est présente que dans la variante
`google`** (`"googleImplementation"`). `accompanist-permissions` porte le préfixe `com.google`
mais c'est une bibliothèque Compose sans lien avec les services Google.

---

## 7. Surface de personnalisation, ce qu'il faut modifier

### 7.1 Fichiers graphiques à remplacer

**Android**, `apps/multiplatform/android/src/main/res/` [V] :

| Chemin | Contenu | Action |
|---|---|---|
| `mipmap-{m,h,xh,xxh,xxxh}dpi/icon.png`, `icon_round.png`, `icon_foreground.png` | icône de lanceur, jeu par défaut | à remplacer, 5 densités |
| `mipmap-*/icon_dark_blue*.png` | second jeu d'icônes sélectionnable dans l'app | à remplacer ou à retirer |
| `mipmap-anydpi-v26/icon.xml`, `icon_round.xml`, `icon_dark_blue*.xml` | icônes adaptatives | à adapter |
| `values/icon_background.xml` (`#FFFFFF`), `values/icon_dark_blue_background.xml` (`#030749`) | fonds d'icônes adaptatives | à passer aux couleurs Trustiatis |
| `drawable-v24/ic_launcher_foreground.xml` | avant-plan vectoriel | à remplacer |
| `drawable/logo.png`, `drawable/logo_light.png` | logos | à remplacer |
| `drawable-*/ntf_icon.png`, `ntf_service_icon.png` | icônes de notification, 5 densités chacune | à remplacer |
| `raw/ring_once.mp3` | sonnerie d'appel | optionnel |
| `values/themes.xml`, `values-night/themes.xml` | style `Theme.SimpleX` | à renommer en `Theme.TrustiatisSecure` |

**Commun Android et Desktop**, `common/src/commonMain/moko-resources/images/` [V], 301
fichiers dont **260 pictogrammes `ic_*.svg` à conserver tels quels** (ce sont des symboles
Material, pas de la marque). À traiter :

| Fichier | Action |
|---|---|
| `logo@4x.png`, `logo_light@4x.png` | logo horizontal, clair et sombre |
| `vertical_logo@{1,2,3}x.png` | logo vertical, écran d'accueil |
| `icon_foreground_common@4x.png` | avant-plan d'icône partagé |
| `decentralized*.png`, `privacy*.png`, `shield*.png` (5 facteurs d'échelle chacun) | illustrations d'onboarding, à refaire aux couleurs Trustiatis |
| `flux_logo*.png`, `badge_*.svg` | marques de tiers (Flux, badges SimpleX), **à retirer** avec les fonctions associées |
| `wallpaper_*.png` (6 fichiers) | fonds de conversation, à conserver ou remplacer |
| `fonts/Inter-*.ttf` (6 graisses) | police Inter, à conserver ou remplacer selon la charte |

**Desktop**, `apps/multiplatform/desktop/src/jvmMain/resources/distribute/` [V] :
`simplex.png` (Linux), `simplex.ico` (Windows), `simplex.icns` (macOS),
`SimpleX.desktop` (entrée de menu Linux), `chat.simplex.app.appdata.xml` (métadonnées
AppStream). Les quatre premiers sont référencés par chemin dans `desktop/build.gradle.kts`.

**iOS**, `apps/ios/Shared/Assets.xcassets/` [V] : `AppIcon.appiconset`,
`DarkAppIcon.appiconset`, `AccentColor.colorset`, `logo.imageset`, `logo-light.imageset`,
`vertical_logo.imageset`, `icon-light.imageset`, `icon-dark.imageset`,
`icon-transparent.imageset`, `decentralized*.imageset`, `privacy.imageset`,
`shield.imageset`, `wallpaper_*.imageset`, et `flux_logo*` plus `badge-*` à retirer.

**Base de référence Trustiatis.** Votre consigne est d'utiliser la charte de
`id.trustiatis.com` comme future charte. Cette application est en ligne depuis le 2026-11-04
et son code est dans `id-trustiatis/` (hors dépôt de l'espace de travail). Première action de
la Phase 2 : **extraire la charte de ce code vers `branding/colors/` et
`branding/typography/`**, puis générer les déclinaisons. Aucun logo ni aucune couleur ne sera
inventé.

### 7.2 Couleurs et thèmes

Tout tient dans deux fichiers [V] :

- `common/src/commonMain/kotlin/chat/simplex/common/ui/theme/Color.kt`, 36 lignes : les
  couleurs nommées, dont `SimplexBlue = Color(0, 136, 255)`. **Le fichier porte un
  avertissement explicite** : « If this value changes also need to update #0088ff in string
  resource files ». La valeur est donc dupliquée dans les fichiers de chaînes, il faudra la
  chercher partout.
- `common/src/commonMain/kotlin/chat/simplex/common/ui/theme/Theme.kt`, 884 lignes, dont les
  **quatre palettes aux lignes 670 à 770** : `LightColorPalette`, `DarkColorPalette`,
  `SimplexColorPalette`, `BlackColorPalette`, chacune doublée d'un bloc `AppColors`.

Côté iOS, l'équivalent est `apps/ios/SimpleXChat/Theme/Color.swift` et
`apps/ios/Shared/Theme/Theme.swift` [V].

**Recommandation forte** : l'énumération `DefaultTheme { LIGHT, DARK, SIMPLEX, BLACK }`
(`Theme.kt` ligne 27) est **persistée par son nom** dans les préférences utilisateur [V].
Le coeur Haskell, lui, ne connaît que `light` et `dark` plus des surcharges par entité
(`src/Simplex/Chat/Types/UITheme.hs`) [V]. Il faut donc **changer les valeurs de couleurs
sans renommer l'énumération**. Renommer `SIMPLEX` en `TRUSTIATIS` est techniquement possible
mais réinitialiserait le thème de tous les utilisateurs à chaque mise à jour et créerait une
divergence inutile avec l'amont. Le nom affiché du thème vient des chaînes de traduction,
pas de l'énumération : il peut donc être « Trustiatis » dans l'interface tout en restant
`SIMPLEX` dans le code.

### 7.3 Nom de l'application et chaînes

| Élément | Emplacement | Valeur actuelle |
|---|---|---|
| Nom de l'application, Android et Desktop | `moko-resources/base/strings.xml` ligne 2 | `<string name="app_name">SimpleX</string>` [V] |
| Idem, 40 autres langues | `moko-resources/<langue>/strings.xml` | `SimpleX`, marqué `translatable="false"` [V] |
| Substitution dans le manifeste | `android/src/main/AndroidManifest.xml` | `android:label="${app_name}"` [V] |
| Nom du paquet Desktop | `desktop/build.gradle.kts` | `SimpleX` sur Windows et macOS, `simplex` sur Linux [V] |
| Copyright Desktop | `desktop/build.gradle.kts` | `(c) 2020-2026 SimpleX Chat` [V] |
| Nom du produit iOS | `project.pbxproj` | `PRODUCT_NAME = SimpleX` [V] |

**Volumétrie de la marque dans les chaînes** [V] :

| Périmètre | Mesure |
|---|---|
| Chaînes totales, `moko-resources/base/strings.xml` | 3 071 |
| Lignes contenant « simplex » dans `base/strings.xml` | 141 |
| Occurrences de « simplex » dans les 41 fichiers de chaînes (référence et 40 langues) | **3 407** |
| Littéraux Kotlin contenant « SimpleX » hors identifiants techniques | 52 |
| Littéraux Swift contenant « SimpleX » hors identifiants techniques | 153 |

Les occurrences Kotlin et Swift se répartissent entre de véritables identifiants techniques,
à conserver (`SimplexService`, `SimplexLink`, `SimplexApp`, `simplexWindowState`,
`SimplexLinkMode`), et des textes visibles. **Il ne faut surtout pas faire de
rechercher-remplacer global** : cela casserait le typage, la sérialisation JSON avec le coeur
et le schéma d'URI.

**Distinction juridique à tenir dans les chaînes.** La politique de marque autorise
« SimpleX » pour désigner le réseau ou une adresse (« Compatible with SimpleX network »,
« adresse SimpleX ») et l'interdit dans la marque du logiciel [V]. Les chaînes doivent donc
être triées en trois catégories, et non traitées en bloc :

1. nom du produit, à remplacer par « Trustiatis Secure »,
2. désignation du réseau et des objets du protocole, à **conserver** pour rester exact
   (une adresse du réseau SimpleX reste une adresse du réseau SimpleX),
3. renvois vers SimpleX Chat Ltd, son site, ses conditions, ses dons, ses badges, à retirer
   ou à réécrire vers Trustiatis.

### 7.4 Identifiants techniques à changer

| Élément | Valeur amont | Contrainte |
|---|---|---|
| `applicationId` et `namespace` Android | `chat.simplex.app` | à changer, sinon cohabitation impossible avec SimpleX officiel |
| Autorité du `FileProvider` | `chat.simplex.app.provider` | à changer avec l'`applicationId` |
| Canaux de notification Android | `chat.simplex.app.MESSAGE_NOTIFICATION`, `chat.simplex.app.CALL_NOTIFICATION_2` | à changer |
| `bundleID` macOS Desktop | `chat.simplex.app` | à changer |
| `upgradeUuid` MSI Windows | `CC9EFBC8-AFFF-40D8-BB69-FCD7CE99EFB9` | **à régénérer impérativement**, sinon le MSI Trustiatis se comporte comme une mise à niveau du MSI SimpleX |
| Identifiants iOS | `chat.simplex.app`, `chat.simplex.app.SimpleX-NSE`, `chat.simplex.app.SimpleX-SE`, `chat.simplex.SimpleXChat`, `chat.simplex.app.snapshots`, `chat.simplex.Tests-iOS` | les 6 à changer |
| Groupe d'application iOS | `group.chat.simplex.app`, déclaré dans `SimpleXChat/AppGroup.swift` ligne 69 et dans 3 fichiers d'entitlements | à changer de façon cohérente |
| Groupe de trousseau iOS | `$(AppIdentifierPrefix)chat.simplex.app` | à changer |
| Schémas d'URI | `simplex:`, `simplexchat:` | **décision produit, voir chapitre 11.4** |
| Domaines associés iOS | `simplex.chat`, `*.simplex.im`, `*.simplexonflux.com` | à remplacer par les domaines Trustiatis |
| Liens applicatifs Android | 1 hôte `simplex.chat` et 23 hôtes `smp*.simplex.im` ou `simplexonflux.com` | à remplacer, voir 11.4 |

Tous ces constats sont **[V]**, lus dans `android/build.gradle.kts`,
`AndroidManifest.xml`, `desktop/build.gradle.kts`, `project.pbxproj`, les trois fichiers
`*.entitlements` et `AppGroup.swift`.

### 7.5 Ce qu'il ne faut pas toucher

- `src/` et toute la cryptographie, les protocoles, la base.
- Les 260 pictogrammes `ic_*.svg` : symboles Material, pas de la marque.
- Les noms des énumérations sérialisées, échangées avec le coeur ou persistées.
- Le garde-fou de variantes de `android/build.gradle.kts`.
- La taille des blocs de fichiers (`fileChunkSize = 15780`, commenté « do not change »).
- Les catalogues de traduction (40 langues côté multiplateforme, 17 `*.lproj` et 28 `*.xcloc` côté iOS), sauf pour la marque : les rouvrir au delà serait créer une
  divergence massive et ingérable à chaque synchronisation.

---

## 8. Notifications, et ce que cela implique pour GrapheneOS

### 8.1 Android, le point fort du dossier

`apps/multiplatform/spec/services/notifications.md` l'écrit noir sur blanc [V] :
« SimpleX does not use push notifications (no Firebase/APNs dependency for privacy) ».

Recherche exhaustive sur `firebase`, `com.google.gms`, `google-services` et
`FirebaseMessaging` dans l'ensemble du dépôt [V] : **trois occurrences seulement, toutes dans
des commentaires** signalant un mode `INSTANT` non implémenté. Aucun fichier
`google-services.json`, aucun greffon Gradle Google, aucune dépendance Firebase.

Les modes de notification Android sont `OFF`, `PERIODIC`, `SERVICE`
(`SimpleXAPI.kt` ligne 8740) [V] :

- `SERVICE` : service de premier plan `SimplexService`, type `remoteMessaging`, relancé au
  démarrage (`BOOT_COMPLETED`), après mise à jour (`MY_PACKAGE_REPLACED`) et après
  destruction, et demandant l'exemption d'optimisation de batterie.
- `PERIODIC` : tâches périodiques par `WorkManager`.

**Conséquence : la compatibilité GrapheneOS ne demande aucun développement spécifique.** La
variante `foss` est déjà celle par défaut (`isDefault = true`) et c'est celle que l'amont
distribue en APK et sur F-Droid. La règle 14 de votre cahier des charges (ne pas créer de code
spécifique à GrapheneOS) est donc respectée sans effort.

Ce qu'il reste à **vérifier sur appareil réel**, et qui n'est pas vérifiable depuis ce dépôt
[NV] : le comportement du service de premier plan sous les restrictions de GrapheneOS,
l'exemption d'optimisation de batterie, le fonctionnement dans les profils utilisateurs
secondaires, l'écran de verrouillage pour les appels entrants (`USE_FULL_SCREEN_INTENT`),
et la biométrie sous GrapheneOS.

### 8.2 iOS, le point dur du dossier

Sur iOS, le modèle est inverse [V] :

1. `AppDelegate.swift` appelle `registerForRemoteNotifications()` et récupère le jeton APNs.
2. `NotificationsView.swift` appelle `apiRegisterToken(token:notificationMode:)`, qui
   enregistre ce jeton auprès d'un **serveur de notification** du réseau.
3. Les serveurs par défaut sont codés dans le coeur, `src/Simplex/Chat/Library/Commands.hs`
   lignes 165 à 169 : `ntf3.simplex.im` et `ntf4.simplex.im`.
4. Le serveur de notification envoie une poussée APNs qui réveille l'extension
   `SimpleX NSE`, laquelle va chercher le message sur le relais SMP.

**Déduction à confirmer [D] :** une poussée APNs est émise avec une clé d'authentification
Apple liée à l'identifiant de l'application. Trustiatis Secure portant son propre identifiant,
les serveurs de notification de SimpleX ne peuvent pas lui pousser de notification. Trustiatis
devra donc héberger son **propre serveur de notification** (`ntf-server` du dépôt
`simplexmq`), configuré avec sa propre clé de poussée Apple.

Ce composant **n'apparaît pas dans le chapitre 9 de votre cahier des charges**, qui ne prévoit
que SMP et XFTP. Il faut l'ajouter au périmètre de la Phase 5. La confirmation exige l'audit
du dépôt `simplexmq`, qui n'est pas inclus ici.

### 8.3 Desktop

Bibliothèque TwoSlices avec replis natifs par système [V]. Aucune contrainte particulière.

---

## 9. Serveurs Trustiatis

Ce dépôt **ne contient pas** le code des serveurs. SMP, XFTP et le serveur de notification
proviennent du dépôt `simplexmq`, qui devra faire l'objet d'un audit distinct. Ce dépôt
fournit en revanche la documentation d'exploitation [V] :

- `docs/SERVER.md` : hébergement d'un relais SMP, service systemd ou conteneur, certificats,
  Tor et adresse onion, port de contrôle, statistiques quotidiennes, rotation de certificats
  en ligne, sécurité d'initialisation et des clés privées, vérification des binaires,
  reproductibilité des builds, mise à jour.
- `docs/XFTP-SERVER.md` : serveur de fichiers.
- `scripts/relay/` : conteneur de relais de conversation, avec `sql_exporter.yml`, donc
  métriques exportables vers le SOC existant.

Les domaines que vous envisagez (`smp1.trustiatis.com`, `smp2.trustiatis.com`,
`xftp.trustiatis.com`) sont cohérents. **Il faut y ajouter `ntf.trustiatis.com`.**

### 9.1 Comment imposer les serveurs Trustiatis, et le piège associé

Les serveurs préconfigurés sont décrits dans le coeur, par **opérateur** [V]
(`src/Simplex/Chat.hs` lignes 83 à 105, `src/Simplex/Chat/Operators/Presets.hs`). Deux
opérateurs existent : `SimpleX Chat` (SimpleX Chat Ltd, domaine `simplex.im`) et `Flux`
(InFlux Technologies Limited, domaine `simplexonflux.com`), chacun avec acceptation de
conditions d'utilisation obligatoire (`conditionsAcceptance = CARequired`).

Trois voies, par coût croissant :

| Voie | Description | Coût | Limite |
|---|---|---|---|
| A | L'utilisateur ajoute les serveurs Trustiatis à la main dans les réglages | nul | inacceptable pour un produit |
| B | L'application appelle l'API de configuration des serveurs à la fin de l'onboarding | **faible, pas de recompilation du coeur** | les serveurs SimpleX restent préconfigurés et visibles |
| C | Ajout d'un opérateur `Trustiatis` dans `Operators/Presets.hs` | **recompilation du coeur par Nix obligatoire** | voir l'avertissement ci-dessous |

**Avertissement de l'amont, à prendre au sérieux** [V]. `Operators/Presets.hs` porte ce
commentaire au-dessus de `allPresetServers` : si des serveurs sont ajoutés,
`shortLinkPresetServers` devra être scindé en deux listes, l'une pour la restauration des
liens et l'autre pour l'émission, afin de préserver la compatibilité ascendante et
descendante des liens courts. Autrement dit, **l'ajout naïf d'un opérateur Trustiatis peut
casser les liens d'invitation courts**. La voie C exige une étude dédiée, à mener en Phase 5
et pas avant.

**Recommandation : voie B pour la version 1**, voie C seulement si le produit l'exige, et
après étude des liens courts.

---

## 10. Mises à jour

### Desktop, un défaut bloquant à corriger

`common/src/desktopMain/kotlin/chat/simplex/common/views/helpers/AppUpdater.kt`, 502 lignes
[V] :

- ligne 188, l'URL interrogée est **codée en dur** :
  `https://api.github.com/repos/simplex-chat/simplex-chat/releases` ;
- deux canaux, `STABLE` et `BETA`, comparaison par version sémantique ;
- `chooseGitHubReleaseAssets` sélectionne l'artefact selon le système, par le **nom du
  fichier** (préfixe `simplex-desktop-`) ;
- le téléchargement se fait en HTTPS, avec respect d'un éventuel proxy SOCKS, puis
  déplacement atomique et proposition d'installation ;
- les paquets `.deb` ne sont volontairement pas installés automatiquement ;
- **aucune vérification cryptographique du fichier téléchargé n'est faite dans
  l'application** : ni somme de contrôle, ni signature. La confiance repose sur TLS vers
  GitHub, puis sur la vérification de signature effectuée par l'installeur du système.

**Trois conséquences :**

1. Si cette URL n'est pas changée, **Trustiatis Secure proposera d'installer le paquet SimpleX
   officiel par dessus lui-même**. Correction obligatoire en Phase 2, pas en Phase 6.
2. Le nom des artefacts Trustiatis doit rester cohérent avec `chooseGitHubReleaseAssets`, ou
   cette fonction doit être adaptée.
3. Conformément à votre chapitre 13 (ne pas développer un système propriétaire avant
   d'évaluer l'existant) : l'existant est à conserver, en changeant la source. Comme
   Trustiatis publie sur GitLab et non sur GitHub, il faudra soit adapter le client à l'API
   GitLab Releases, soit exposer un index JSON statique sur `downloads.trustiatis.com`. La
   seconde voie est plus simple et plus sûre, et permet d'ajouter la vérification SHA-256 qui
   manque aujourd'hui.

### Android

**Aucun mécanisme de mise à jour dans l'application** [V] : `AppUpdater` n'existe que dans
`desktopMain`, et aucune référence n'apparaît dans `androidMain`. Les mises à jour passent
donc par le magasin, ou par réinstallation manuelle de l'APK. Pour la distribution directe et
GrapheneOS, cela signifie qu'il faut soit accepter la mise à jour manuelle, soit publier un
**dépôt de type F-Droid** signé, ce qui dépasse le cahier des charges actuel et doit être
arbitré.

### iOS

TestFlight puis App Store, mécanisme Apple, rien à développer [NV].

---

## 11. Limites par plateforme

### 11.1 iOS

| Limite | Nature |
|---|---|
| Compilation, signature et publication impossibles sans Mac avec Xcode | [V] confirmé par la structure du projet et par `flake.nix` (cible iOS en `aarch64-darwin`) |
| Le coeur iOS se compile uniquement sur macOS Apple Silicon, avec Nix et `mac2ios` | [V] `flake.nix` lignes 556 et 593 |
| La CI de l'amont ne construit l'application iOS que sans signature et avec des bibliothèques factices | [V] `scripts/ci/ios-stub-libs.sh` |
| Notifications impossibles sans serveur de notification propre | [D] voir 8.2 |
| Compte Apple Developer, certificats, profils de provisionnement, clé de poussée : à ouvrir au nom de Trustiatis | [NV] |
| Publication App Store sous AGPLv3, risque juridique majeur | voir risque J-02 |

### 11.2 Android

| Limite | Nature |
|---|---|
| Deux ABI seulement, `arm64-v8a` et `armeabi-v7a`. Pas de x86, pas de x86_64 | [V] |
| Pas d'APK universel en configuration standard | [V] `isUniversalApk = false` |
| `minSdk 26`, Android 8 minimum | [V] |
| NDK 23.1.7779620, version ancienne et figée | [V] |
| L'armv7a impose de conserver GHC 8.10.7, épinglé par révision Nix | [V] |
| Les liens applicatifs ne se vérifieront pas sans `assetlinks.json` hébergé sur les domaines Trustiatis, contenant l'empreinte du certificat de signature Trustiatis | [V] `apps/multiplatform/README.md` |
| Pas de mise à jour dans l'application | [V] |
| Clé de signature de publication à créer, et **à ne jamais mettre dans Git** | consigne du cahier des charges, à outiller |

### 11.3 GrapheneOS

Aucune limite technique identifiée dans le code. Les points à **tester sur appareil réel**
sont listés en 8.1. **Tant que ce test n'a pas eu lieu, aucune compatibilité ne sera
déclarée**, conformément à votre règle.

### 11.4 Décision produit à prendre, les schémas d'URI et les liens

Le manifeste Android déclare les schémas `simplex:` et `simplexchat:`, et vérifie les liens
sur `simplex.chat` plus 23 hôtes `smp*.simplex.im` et `simplexonflux.com` [V].

- **Conserver ces schémas** préserve l'interopérabilité : un lien d'invitation reçu d'un
  utilisateur SimpleX s'ouvrira dans Trustiatis Secure. Mais si les deux applications sont
  installées sur le même appareil, Android présentera un sélecteur, et les liens applicatifs
  des domaines SimpleX ne se vérifieront pas pour Trustiatis.
- **Les remplacer** par un schéma propre casse l'ouverture directe des liens du réseau
  SimpleX, alors que le protocole reste compatible.

**Recommandation : conserver `simplex:` et `simplexchat:`** (ce sont des identifiants de
protocole, pas de la marque commerciale, et la politique de marque autorise la mention du
réseau), **retirer les hôtes de liens applicatifs de SimpleX** et les remplacer par les hôtes
Trustiatis, avec un `assetlinks.json` publié sur `smp1.trustiatis.com`,
`smp2.trustiatis.com` et le domaine de liens retenu. À faire valider.

### 11.5 Desktop

| Limite | Nature |
|---|---|
| Aucune signature Authenticode chez l'amont, tout est à construire | [V] seule la signature macOS existe (`scripts/ci/sign-libs-mac.sh`, identité « Developer ID Application: SimpleX Chat Ltd ») |
| AppImage produit par script, hors Gradle | [V] |
| Flatpak : seuls les fichiers de métadonnées sont présents, pas la recette | [V] |
| MSIX : aucune trace dans le dépôt, à développer entièrement | [V] |
| Chaque paquet se construit sur son propre système d'exploitation | [V] commentaire de `desktop/build.gradle.kts` |
| Un correctif PowerShell est appliqué au MSI après `jpackage` | [V] `disable-msi-install-dir-wipe-windows.ps1` |

---

## 12. Obligations juridiques

### Licence

Le dépôt est sous **GNU AGPL version 3** [V] (`LICENSE`). Un fork distribué entraîne :

- mise à disposition du **code source complet correspondant** de chaque binaire distribué,
  modifications comprises ;
- conservation des mentions de copyright et de licence, et du texte de la licence ;
- indication claire des modifications apportées et de leur date ;
- distribution des dérivés sous AGPLv3, sans restriction supplémentaire ;
- article 13 de l'AGPL : si une version modifiée est mise à disposition par le réseau, les
  utilisateurs doivent pouvoir obtenir la source. Cela concerne directement les serveurs SMP,
  XFTP et de notification de Trustiatis ;
- respect des licences tierces : LGPLv2 pour VLC (obligation de permettre le remplacement de
  la bibliothèque), BSD pour WebRTC, SQLCipher, et l'arbre Haskell complet documenté dans
  `docs/dependencies/HASKELL.md`.

### Marque

`docs/TRADEMARK.md` [V]. Contextes **interdits**, cités : « **Software Branding**: As part of
any software's name, branding, or logo, **including forks**. » Également interdits : toute
suggestion d'affiliation ou d'aval, et l'usage commercial sans autorisation.

Contextes **autorisés** : indiquer une adresse ou une entité du réseau, publications à propos
de SimpleX, et déclaration de compatibilité en documentation, par exemple « Compatible with
SimpleX network », **mais pas dans le nom ni la marque du logiciel**.

**Lecture opérationnelle :**

1. Le retrait complet de la marque SimpleX de l'identité du produit est une **obligation**.
2. La mention de la compatibilité avec le réseau SimpleX dans la documentation et les
   descriptions de magasins est **autorisée**, et même nécessaire à l'exactitude.
3. Le logo SimpleX, les illustrations de marque, le logo Flux et les badges doivent
   disparaître des ressources.
4. Il ne doit subsister aucune suggestion d'affiliation ou d'aval de SimpleX Chat Ltd.
5. Le dossier `media-logos/` ne doit pas être réutilisé.

### Risques

| Réf. | Risque | Gravité | Traitement proposé |
|---|---|---|---|
| **J-01** | Violation de la politique de marque par des résidus de marque SimpleX dans les 3 407 occurrences de chaînes, les ressources ou les descriptions de magasins | **Élevée** | contrôle automatisé en CI, liste blanche explicite des usages autorisés (réseau, adresse, compatibilité), revue juridique avant publication |
| **J-02** | **Publication App Store d'une œuvre AGPLv3 dont Trustiatis n'est pas titulaire.** Les conditions de l'App Store imposent des restrictions d'usage et une gestion des droits numériques que la FSF considère incompatibles avec la GPL et l'AGPL. SimpleX Chat Ltd peut publier car elle détient les droits et peut concéder une autre licence. Trustiatis ne peut pas. | **Élevée, potentiellement bloquante** | à soumettre à un conseil juridique **avant** d'engager la Phase 3 iOS. Options : obtenir une autorisation écrite de SimpleX Chat Ltd, ou renoncer à l'App Store et se limiter à TestFlight interne et à la distribution d'entreprise, ou renoncer à iOS |
| **J-03** | Obligation AGPL article 13 sur les serveurs Trustiatis | Moyenne | publier les sources des serveurs et un avis d'offre de source sur le portail |
| **J-04** | LGPLv2 de VLC, obligation de remplaçabilité de la bibliothèque | Faible | VLC reste une bibliothèque dynamique distincte, documenter |
| **J-05** | Non respect de l'obligation d'indication des modifications | Faible | journal `UPSTREAM_SYNC.md` et en-têtes datés |
| **T-01** | Rupture des liens d'invitation courts en cas d'ajout d'un opérateur Trustiatis aux serveurs préconfigurés | **Élevée** | voie B du chapitre 9.1, voie C seulement après étude |
| **T-02** | Mise à jour Desktop pointant vers le dépôt SimpleX | **Élevée, triviale à corriger** | correction en Phase 2 |
| **T-03** | Dépendance de build à l'infrastructure Hydra de SimpleX pour les bibliothèques natives | Moyenne | voir chapitre 13 |
| **T-04** | Dépendance au fork SimpleX de WebRTC pour iOS | Moyenne | mirroir Trustiatis du dépôt, version épinglée |
| **T-05** | Divergence croissante avec l'amont rendant les mises à jour de sécurité coûteuses | **Élevée sur la durée** | principe de personnalisation minimale, chapitre 13 |
| **T-06** | Clé de signature Android ou Authenticode compromise ou perdue | **Élevée** | stockage dans KeePassXC puis Vault, jamais dans GitLab, procédure de rotation documentée |
| **T-07** | Notifications iOS inopérantes faute de serveur de notification | **Élevée** | ajouter `ntf` au périmètre de la Phase 5 |
| **T-08** | Liens applicatifs non vérifiés sur Android et iOS | Moyenne | `assetlinks.json` et `apple-app-site-association` sur les domaines Trustiatis |
| **T-09** | `upgradeUuid` MSI non régénéré, conflit d'installation avec SimpleX | Moyenne | régénérer en Phase 2 |
| **T-10** | GHC 8.10.7 figé pour armv7a, fin de support à terme | Faible | suivre l'amont, abandonner armv7a si l'amont l'abandonne |

---

## 13. Stratégie de personnalisation recommandée

Votre exigence est explicite : « La personnalisation doit rester aussi légère que possible
pour faciliter les futures mises à jour de sécurité. » Cette exigence et l'exigence de
serveurs Trustiatis préconfigurés sont partiellement en tension. Voici l'arbitrage proposé.

### 13.1 Principe, trois cercles de modification

| Cercle | Contenu | Effet sur la synchronisation amont |
|---|---|---|
| **1. Ressources remplacées** | icônes, logos, illustrations, valeurs de couleurs, nom de l'app, fichiers de distribution | **nul ou presque nul**, les chemins ne changent pas, seuls les octets changent |
| **2. Identifiants et configuration** | `applicationId`, `bundleID`, UUID MSI, URL de mise à jour, domaines, groupes d'app | **faible**, modifications ponctuelles et localisées, conflits de fusion rares et triviaux |
| **3. Code** | tri des chaînes de marque, retrait des fonctions Flux et badges, configuration des serveurs à l'onboarding | **réel**, à maintenir au minimum strict |

**Règle de travail proposée : tout ce qui peut être fait dans le cercle 1 doit l'être, rien ne
doit entrer dans le cercle 3 sans justification écrite dans `BRANDING.md`.**

### 13.2 Décision structurante, qui compile le coeur Haskell

| | Option **Légère** | Option **Autonome** |
|---|---|---|
| Coeur | bibliothèques compilées par l'amont, récupérées par `download-libs.sh` | reconstruction par Nix sur l'infrastructure Trustiatis |
| Serveurs préconfigurés | voie B, configurés par l'application à l'onboarding | voie C possible, opérateur Trustiatis dans le coeur |
| Infrastructure de build | un runner Linux, un runner Windows, un Mac | **en plus** : Nix, cache binaire, runner Linux puissant, Mac Apple Silicon pour iOS |
| Reproductibilité | dépend de l'amont | maîtrisée de bout en bout |
| Conformité AGPL | respectée, le coeur n'est pas modifié | respectée |
| Souveraineté | **dépendance à l'infrastructure de SimpleX** | complète |
| Délai de mise à jour de sécurité | immédiat, dès publication amont | à reconstruire |
| Coût | faible | **élevé**, plusieurs semaines d'ingénierie et du matériel |
| Cohérence avec votre positionnement | discutable pour un produit vendu comme souverain | alignée |

**Recommandation : commencer en option Légère pour les Phases 2 et 3**, afin de produire
rapidement un prototype démontrable, **puis basculer en option Autonome avant toute
distribution à des clients**. Un produit de sécurité vendu par Trustiatis ne peut pas
durablement dépendre des binaires d'un tiers qu'il ne contrôle pas, et la règle 3 de votre
cahier des charges (ne jamais prétendre qu'une compilation a réussi sans preuve) suppose de
maîtriser la chaîne.

Cette bascule doit être **planifiée dès maintenant**, pas découverte en Phase 6.

---

## 14. Exigences des runners et de l'infrastructure

### Runners GitLab nécessaires

| Runner | Rôle | Exigences |
|---|---|---|
| **Linux x86_64** | coeur Android et Desktop Linux, APK, AAB, DEB, AppImage, analyse statique, tests | JDK 17, SDK Android, NDK 23.1.7779620, Gradle aligné sur le wrapper, CMake, `zipalign`, et **Nix avec flakes** en option Autonome. Prévoir large : l'amont libère du disque et monte un disque RAM pour les tests, et utilise `ZSTD_CLEVEL: 9` et un cache de dépendances Haskell. Compter **au moins 8 cœurs, 16 Gio de RAM et 150 Gio de disque** pour une construction du coeur |
| **Linux ARM64** | Desktop Linux ARM64, par compilation native | même outillage |
| **Windows x86_64** | MSI et EXE, signature Authenticode | JDK 17, `jpackage`, WiX, PowerShell, certificat de signature de code |
| **macOS Apple Silicon** | **obligatoire** : coeur iOS, application iOS, DMG arm64, signature et notarisation | Xcode, Nix, `mac2ios`, trousseau dédié (`scripts/ci/prepare-keychain-mac.sh`), compte Apple Developer |
| **macOS Intel** | DMG x86_64, si cette architecture est maintenue | Xcode |

**Un Mac est un prérequis matériel non contournable.** Il n'existe aucune voie de compilation
iOS depuis Linux.

### Secrets et matériel à acquérir

| Élément | Usage | Stockage |
|---|---|---|
| Magasin de clés Android | signature APK et AAB | KeePassXC puis Vault, **jamais dans GitLab** |
| Certificat de signature de code Windows | Authenticode | idem, support matériel recommandé |
| Identité Apple Developer ID Application | signature macOS | trousseau du runner, identifiants en variables protégées |
| Identifiants de notarisation Apple | notarisation macOS | variables protégées |
| Clé de poussée Apple (APNs) | serveur de notification Trustiatis | Vault, côté serveur |
| Compte Apple Developer Program | TestFlight et App Store | 99 USD par an |
| Compte Google Play Console | publication Android | 25 USD une fois |
| Appareil **GrapheneOS réel** | validation Phase 4 | matériel compatible, Pixel |
| Appareil iOS réel | validation Phase 4 | |

---

## 15. Plan de travail détaillé

Les phases reprennent votre découpage. Les durées sont indicatives et supposent une
intervention à temps partiel.

### Phase 1, audit. Terminée avec ce document

Reste à faire avant de passer à la suite, et qui **requiert vos décisions** (chapitre 16).

### Phase 2, prototype de branding

| Lot | Contenu | Livrable |
|---|---|---|
| 2.1 | Création du dépôt `trustiatis/trustiatis-secure` sur GitLab, `upstream` vers GitHub SimpleX, protection de `main`, arborescence de branches de votre chapitre 3 | dépôt opérationnel, `docs/UPSTREAM_SYNC.md` |
| 2.2 | Extraction de la charte depuis `id-trustiatis/` vers `branding/colors/` et `branding/typography/` | `branding/`, `docs/BRANDING.md` |
| 2.3 | Script de génération et de propagation des ressources depuis `branding/` vers les deux arbres, sur le modèle de `design-system/detramer.py` | `tools/propager-branding.py` |
| 2.4 | Icônes, logos, illustrations, splash, cercle 1 | ressources en place |
| 2.5 | Palettes dans `Theme.kt`, `Color.kt`, `Color.swift`, `Theme.swift`, sans renommer les énumérations | thèmes clair et sombre Trustiatis |
| 2.6 | Nom de l'application et tri des 3 407 occurrences de marque en trois catégories, puis contrôle automatisé | `tools/verifier-marque.py`, exécuté en CI |
| 2.7 | Identifiants techniques du chapitre 7.4, dont la régénération de `upgradeUuid` | commits atomiques |
| 2.8 | **Correction de l'URL de mise à jour Desktop** et retrait des fonctions Flux et badges | correctif documenté |
| 2.9 | Prototype compilé : APK `foss` de debug et paquet Desktop du système courant, en option Légère | **preuves de compilation jointes**, journaux et sommes SHA-256 |

Sortie de phase : prototype Desktop et Android démontrable, sans aucune marque SimpleX dans
l'identité du produit.

### Phase 3, déploiement multiplateforme

Windows (MSI, EXE, Authenticode), macOS (DMG, Developer ID, notarisation), Linux (DEB,
AppImage, et Flatpak si la recette peut être reconstituée), Android (APK par ABI, AAB),
iOS (projet Xcode renommé, 6 identifiants, groupe d'application, trousseau).
Livrables : `docs/DESKTOP_BUILD.md`, `ANDROID_BUILD.md`, `IOS_BUILD.md`.

**Prérequis bloquant pour le lot iOS : la décision sur le risque J-02.**

### Phase 4, GrapheneOS

Tests sur appareil réel selon la matrice du chapitre 15 de votre cahier des charges :
installation par APK, premier démarrage, absence de Google Play Services, notifications en
mode `SERVICE` et `PERIODIC`, connexions SMP, transferts XFTP, appels, profils utilisateurs,
biométrie, permissions, sauvegardes, mises à jour.
Livrable : `docs/GRAPHENEOS_COMPATIBILITY.md`, avec les résultats réels, aucune compatibilité
déclarée sans test.

### Phase 5, infrastructure

Audit du dépôt `simplexmq`, puis déploiement de `smp1`, `smp2`, `xftp` **et `ntf`**, sur les
trois environnements, avec certificats, pare-feu, supervision vers le SOC existant,
sauvegardes de configuration. Étude de la voie C et des liens courts.
Livrable : rôles Ansible, documentation d'exploitation, et décision sur l'opérateur
préconfiguré.

### Phase 6, industrialisation

Pipelines GitLab CI selon votre chapitre 11, runners du chapitre 14, signatures, sommes de
contrôle, portail `downloads.trustiatis.com` sur Cloudflare Pages et R2, index de mise à jour
JSON avec SHA-256.
Livrables : `docs/CI_CD.md`, `DISTRIBUTION.md`, `RELEASE.md`.

### Phase 7, validation finale

Matrice de tests croisés entre les cinq plateformes, tests de sécurité, vérification des
signatures et des sommes, revue de conformité AGPL et marque.
Livrables : `docs/SECURITY.md`, `LICENSING.md`, rapport de tests.

### Phase 8, publication

Distribution Desktop, Android, GrapheneOS, puis iOS selon la décision J-02. Aucune publication
sans validation humaine explicite.

---

## 16. Décisions qui vous appartiennent, à arbitrer avant la Phase 2

| N° | Décision | Recommandation |
|---|---|---|
| **D-1** | **Risque J-02 : publier ou non sur l'App Store une œuvre AGPLv3 dont Trustiatis n'est pas titulaire.** Soumettre à un conseil juridique avant d'engager le lot iOS | faire examiner avant la Phase 3, et prévoir le repli TestFlight ou distribution d'entreprise |
| **D-2** | Option Légère ou Autonome pour la compilation du coeur, et date de bascule | Légère pour les Phases 2 et 3, Autonome avant toute diffusion client |
| **D-3** | Conserver ou remplacer les schémas d'URI `simplex:` et `simplexchat:` | conserver, et remplacer uniquement les hôtes de liens applicatifs |
| **D-4** | Serveurs préconfigurés par voie B (onboarding) ou voie C (opérateur dans le coeur) | voie B pour la version 1 |
| **D-5** | Conserver ou non `armeabi-v7a`, et produire ou non un APK universel | conserver armv7a, renoncer à l'APK universel |
| **D-6** | Portée de la traduction de la marque : les 41 langues, ou seulement le français et l'anglais au départ | base et français d'abord, les autres par vagues |
| **D-7** | Publier ou non un dépôt de mise à jour de type F-Droid pour la distribution directe Android et GrapheneOS | hors périmètre de la version 1, à reconsidérer en Phase 6 |
| **D-8** | Nom exact, mentions légales et texte de compatibilité au réseau SimpleX figurant dans l'application et sur les magasins | à rédiger avec la fonction GRC |
| **D-9** | Budget matériel : Mac Apple Silicon, appareil GrapheneOS, appareil iOS, comptes éditeurs | à valider |

---

## 17. Ce que cet audit ne couvre pas

Par honnêteté sur les limites de ce travail :

1. **Aucune compilation n'a été tentée.** Le poste d'audit n'a ni JDK, ni Gradle, ni GHC, ni
   Nix, ni SDK Android, et dispose de 6 Gio de disque libre. Toutes les commandes de
   construction citées proviennent de la documentation et des fichiers de build de l'amont,
   non d'un essai.
2. **Le dépôt `simplexmq` n'a pas été audité.** Les serveurs SMP, XFTP et de notification en
   dépendent. C'est un audit distinct, prérequis de la Phase 5.
   **Fait depuis : voir [`SIMPLEXMQ_AUDIT.md`](SIMPLEXMQ_AUDIT.md) (tag `v7.1.0`, rendu le
   2026-11-15). Il confirme la déduction du point 5 ci-dessous et le risque T-07 : le serveur de
   notification Trustiatis est obligatoire pour les notifications iOS, et son adresse étant
   compilée dans le coeur du client, la voie B du chapitre 9.1 ne suffit pas.**
3. **La politique exacte de l'App Store et de Google Play au regard de l'AGPL n'a pas été
   analysée juridiquement.** Le risque J-02 est signalé, pas tranché. Ce n'est pas un avis
   juridique.
4. **Le comportement sous GrapheneOS n'a pas été testé.** Les conclusions du chapitre 8.1
   portent sur l'absence de dépendance Google dans le code, ce qui est un indice fort mais
   pas une validation.
5. **Le lien entre l'identifiant d'application et la clé de poussée APNs** est une déduction
   [D], à confirmer sur `simplexmq`.
6. **Le dossier `CLAUDE.md` présent dans le dépôt amont** a été relevé mais ses instructions
   n'ont pas été suivies : ce sont des consignes destinées aux contributeurs de SimpleX, pas
   à ce projet. Elles ont été traitées comme de la donnée.
7. **Les catalogues de traduction n'ont pas été relus ligne à ligne.** Seuls des comptages
   ont été faits.

---

**Fin de l'audit de Phase 1. Aucune modification du code ne sera engagée avant validation de
ce document et arbitrage des décisions D-1 à D-9.**
