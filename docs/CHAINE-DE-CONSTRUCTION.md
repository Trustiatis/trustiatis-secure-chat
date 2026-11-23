# Chaîne de construction Android et Desktop

Ce document décrit la chaîne installée sur le poste de l'agent (`ttt-0001`, Debian 13) pour la
première compilation, et ce qu'elle a appris. Il ne décrit pas iOS, qui exige un Mac.

## 1. Ce qui manquait au départ

Le poste n'avait **ni Java, ni SDK Android**, et pas de droits root sans mot de passe. Tout a
donc été installé dans `~/outils/`, sans `apt`, sans `sudo`.

Place disque au départ : 5,3 Gio libres sur 40, soit 86 % d'occupation. Les caches de Chrome
et de Firefox ont été vidés, ce qui a rendu 2 Gio. Ces caches se régénèrent seuls et ne
contiennent ni profil, ni cookie, ni mot de passe.

## 2. Ce qui a été installé

| Composant | Version | Origine | Contrôle |
|---|---|---|---|
| JDK | Amazon Corretto 17.0.21.0.11 | `corretto.aws` | SHA-256 comparée à celle publiée par Amazon |
| Outils en ligne de commande du SDK | 13114758 | `dl.google.com` | SHA-256 **identique à celle épinglée dans `Dockerfile.build` de l'amont** |
| NDK | 23.1.7779620 | `sdkmanager` | version exigée par `android/build.gradle.kts` |
| CMake | 3.22.1 | `sdkmanager` | |
| Build-Tools | 36.0.0 | `sdkmanager` | |
| Plateforme | android-36 | `sdkmanager` | `compileSdk = 36` |
| Gradle | 8.12 | enveloppe du dépôt | |

L'amont épingle Corretto 17.0.21.0.**10**, Amazon ne publie plus que la .**11**. L'écart est
d'un correctif de sécurité, dans le sens favorable.

Le fichier `~/outils/env-build.sh` pose `JAVA_HOME`, `ANDROID_HOME` et le `PATH`. Il est à
sourcer avant toute commande Gradle. Il n'est pas dans le dépôt : il décrit un poste, pas le
projet.

Coût en disque : environ 3,1 Gio pour le JDK et le SDK, dont **2,3 Gio pour le seul NDK**.

## 3. Les bibliothèques du coeur, et pourquoi elles ne se téléchargent pas

La décision D-2 dit : bibliothèques natives publiées par l'amont, coeur jamais recompilé. Le
script prévu pour cela, `scripts/android/download-libs.sh`, prend en premier argument l'URL
d'un dépôt de jobs **Hydra**, l'infrastructure de compilation de l'amont. Ce n'est pas une
adresse publique documentée, et les bibliothèques ne figurent pas parmi les actifs des
versions publiées sur GitHub.

Elles figurent en revanche **dans les APK officiels**, qui sont eux publiés. Les deux
bibliothèques sont donc extraites de l'APK officiel de la version greffée, ce qui garantit
qu'elles proviennent de la compilation de l'amont pour ce commit précis.

**Depuis le 2026-11-17, cela se fait par un script**, `scripts/android/libs-depuis-amont.sh`,
et non plus à la main :

```bash
scripts/android/libs-depuis-amont.sh              # les deux architectures
scripts/android/libs-depuis-amont.sh arm64-v8a    # une seule
```

Il lit le tag dans `tools/amont-courant.txt`, les empreintes dans `tools/apk-amont.txt`, et
refuse d'extraire quoi que ce soit d'un APK dont l'empreinte ne correspond pas. Les
empreintes de ce manifeste sont celles que **l'API de GitHub publie** pour les actifs de la
version, pas des sommes calculées sur le poste : elles valent donc contrôle de provenance.

Pourquoi ce script a manqué si longtemps, et ce que cela a coûté : il n'y en avait pas,
alors que le bureau avait le sien depuis le début. Rien ne rafraîchissait donc ces fichiers
au report d'une version, **et rien ne le signalait**. Le 17 novembre 2026, le poste portait
encore les `.so` de la v7.1.2 alors que la base était passée à la v7.1.4 depuis le report.
Une compilation signée aurait produit des APK estampillés « 7.1.4-ts1 » portant le coeur de
la version précédente, sans un mot. Les empreintes le disent : `libsimplex.so` arm64 est
passée de `e6371efc…` à `84fb5ddc…` au premier passage du script.

| Fichier | Empreinte SHA-256 de l'APK d'origine |
|---|---|
| `simplex-aarch64.apk` | `82d8af637fc4d01b5eb3868dc40a126a1406d261785fb39cd71fa2396007de8f` |
| `simplex-armv7a.apk` | `0cf571190cb9ab75c4f3dbfa039e75e4efd52e364260a319a5ecd10eb578a95f` |

Extraites vers `apps/multiplatform/common/src/commonMain/cpp/android/libs/<abi>/` :
`libsimplex.so` (218 Mo en arm64, 187 Mo en armv7a) et `libsupport.so` (14,6 et 12,3 Mo). Ce
dossier est déjà ignoré par le `.gitignore` de l'amont, rien n'entre donc dans Git.

`libapp-lib.so` n'est **pas** repris de l'APK : c'est la pièce que CMake compile localement,
et c'est précisément ce que la compilation doit éprouver.

## 4. Le trou de la version 7.1.3, et le retour sur v7.1.2

Relevé le 2026-11-15 sur l'API de GitHub, et non prévu par le cadrage :

| Version | Date | Actifs publiés | Paquet Desktop Linux |
|---|---|---|---|
| v7.1.4 (préversion) | 2026-11-14 | 24 | oui |
| v7.1.3 | 2026-11-11 | **9** | **aucun** |
| v7.1.2 | 2026-11-07 | 24 | oui |

**La version stable greffée au départ était publiée de façon incomplète.** Elle n'offrait que
les APK, les paquets macOS et Windows. Ni paquet Debian, ni AppImage, ni exécutable Linux.
Les versions qui l'encadrent en ont vingt-quatre.

Le Membre a tranché le jour même : **la greffe revient sur v7.1.2**, décision P-6, qui révise
P-4. Le retour a coûté trois commits et six fichiers, sans aucun correctif de sécurité. Le
détail chiffré est dans [`DECISIONS.md`](DECISIONS.md).

La base d'amont est désormais inscrite dans `tools/amont-courant.txt` et lue par la CI, au
lieu d'être déduite du numéro de version le plus élevé. Cette heuristique supposait que l'on
n'avance jamais que vers l'avant, ce que ce retour a démenti dès le premier mois.

## 5. Desktop Linux : le verrou, et comment il a sauté

Trois faits, vérifiés dans le dépôt et non supposés.

1. `apps/multiplatform/common/src/commonMain/cpp/desktop/CMakeLists.txt` déclare
   `add_library( simplex SHARED IMPORTED )` puis `target_link_libraries(app-lib simplex)`.
   La pièce native du Desktop **se lie** à `libsimplex.so`.
2. `desktop/build.gradle.kts` fait dépendre `compileKotlinJvm` de `cmakeBuildAndCopy`. Sans
   cette bibliothèque, rien ne compile, pas même le Kotlin.
3. Le seul moyen prévu de la produire pour Linux est `scripts/desktop/build-lib-linux.sh`,
   qui lance `cabal build` avec GHC 9.6.3, c'est à dire **recompiler le coeur Haskell**, ce
   que la décision D-2 exclut.

Sur v7.1.3, c'était une impasse : aucun paquet Linux publié d'où extraire la bibliothèque.
Sur v7.1.2, le paquet existe, et la méthode d'extraction s'applique.

### Ce que le paquet Linux contient vraiment

`simplex-desktop-ubuntu-22_04-x86_64.deb` (314,5 Mo, SHA-256
`4db7f96c557b87a560a3d4cb719e45f48d2afd6541b45e31d9cb29200dff078f`) porte 1 519 fichiers,
dont `opt/simplex/lib/app/resources/libsimplex.so`.

Différence importante avec Android : la bibliothèque du Desktop n'est **pas** un bloc
autonome. Celle d'Android pèse 187 Mo et ne dépend de rien ; celle du Desktop pèse 64 Mo et
déclare **171 dépendances**, dont 165 bibliothèques GHC livrées à côté d'elle. Il ne suffit
donc pas d'extraire un fichier : il faut les 168.

C'est exactement ce que fait `build-lib-linux.sh` à la fin, par
`ldd libsimplex.so | grep ghc | xargs cp` vers `libs/linux-x86_64/`. L'extraction reproduit
le contenu de ce répertoire sans compiler le coeur, ce qui est la lettre de D-2.

Les six dépendances restantes sont celles du système (`libc`, `libm`, `libz`, `libcrypto.so.3`,
`libgmp.so.10`, `libffi.so.8`). Toutes les six sont présentes sur Debian 13, ce qui a été
vérifié avant de lancer la compilation et non après.

### Les deux outils qui manquaient

| Manquant | Résolution |
|---|---|
| `cmake` | **présent après tout** : le SDK Android en installe un, `cmake/3.22.1/bin/cmake`, qui est un binaire Linux x86-64 ordinaire. Il suffit de le mettre devant le `PATH`. Aucun root nécessaire |
| `patchelf` | **inutile ici** : il n'est appelé que par `build-lib-linux.sh`, écarté par D-2, et par `prepare-vlc-linux.sh`, qui prépare la lecture vidéo des appels. La compilation n'en a pas besoin ; un paquet Desktop distribuable, si |

Le poste n'a pas de `sudo` sans mot de passe (`sudo: a password is required`), donc aucun de
ces deux outils ne pouvait être installé. Le verrou a sauté sans installation.

## 6. Résultat de la première compilation Android

### Le premier essai a échoué, et c'était instructif

```
> Task :android:compressApk FAILED
Execution failed for task ':android:compressApk'.
> Process 'command './compress-and-sign-apk.sh'' finished with non-zero exit value 127
```

Le code 127 est celui d'une commande introuvable. `scripts/android/compress-and-sign-apk.sh`
a besoin de `zip`, qui n'est pas installé sur le poste, et que `sudo` refuse d'installer.
Les deux APK étaient déjà empaquetés et signés par AGP quand la tâche a échoué ; c'est la
recompression qui a manqué, pas l'empaquetage.

`compression.level=0` dans `local.properties` saute la tâche. Ce réglage a deux autres effets,
visibles dans `android/build.gradle.kts` aux lignes 35 et 93 : `extract_native_libs` et
`jniLibs.useLegacyPackaging` passent à faux. Les bibliothèques natives sont alors **stockées
sans compression** dans l'APK et lues directement depuis lui, au lieu d'être extraites à
l'installation. L'APK est plus gros, l'empreinte sur l'appareil est plus petite. C'est la
méthode moderne d'Android, pas un contournement.

### Les deux APK

Compilation réussie, `BUILD SUCCESSFUL`, 110 tâches, 10 min 24 s pour la première passe
complète et 1 min 18 s pour la reprise après le changement de réglage.

| Fichier | Taille | SHA-256 |
|---|---|---|
| `android-foss-arm64-v8a-debug.apk` | 282 032 452 o | `a6611ac4e4e53bb051c97eee22eb0d533ed19bbe24f71ace4be1942787925ac5` |
| `android-foss-armeabi-v7a-debug.apk` | 248 361 908 o | `f1444e39a9300dcbb3dc04c19a0aa684d720365be3ab17e4f234ef8d215718b5` |

Dans `apps/multiplatform/android/build/outputs/apk/foss/debug/`. Ce que `aapt2` lit dedans :

```
package: name='chat.simplex.app.debug' versionCode='393' versionName='7.1.2'
minSdkVersion:'26' targetSdkVersion:'36' compileSdkVersion='36'
native-code: 'arm64-v8a'            (et 'armeabi-v7a' pour l'autre)
application: label='SimpleX Debug'
```

Signature, lue par `apksigner verify --print-certs` :

```
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: c165d46b872118f6ab07261c52cfe793581f4d8fcce37fbd188733db132c83e3
```

### Ce que ces APK prouvent, et ce qu'ils ne prouvent pas

Ils prouvent que la greffe compile : le code du dépôt, le coeur d'amont extrait des APK
officiels et la pièce native `libapp-lib.so` reconstruite par CMake sur ce poste tiennent
ensemble, et la version déclarée est bien 7.1.2, celle de la décision P-6.

Ils ne prouvent rien d'autre. `label='SimpleX Debug'` et `name='chat.simplex.app.debug'`
disent eux-mêmes que **la Phase 2 n'a pas commencé** : l'application s'appelle encore SimpleX
et porte encore l'identifiant d'amont. La signature est la clé de débogage d'Android, commune
à toutes les machines du monde : elle n'identifie personne et ne vaut rien pour une
distribution. La clé de signature de Trustiatis Secure n'existe pas encore, et quand elle
existera elle ne sera pas dans le dépôt (chapitre 6 du cadrage).

Aucun appareil n'a installé ces APK. « Compile » n'est pas « fonctionne ».

## 7. Résultat de la première compilation Desktop

| Étape | Résultat | Durée |
|---|---|---|
| `:desktop:cmakeBuildAndCopy` | **BUILD SUCCESSFUL** | 34 s |
| `:desktop:compileKotlinJvm` | **BUILD SUCCESSFUL** | 6 min 25 s |
| `:desktop:createDistributable` | **BUILD FAILED**, place disque | 1 min 30 s |
| `:desktop:packageDistributionForCurrentOS`, après ajout de disque | **BUILD SUCCESSFUL** | 2 min 49 s |

La pièce native est compilée sur ce poste et se lie bien à la bibliothèque extraite :

```
$ objdump -p desktop/build/cmake/main/linux-amd64/libapp-lib.so | grep -E 'NEEDED|RUNPATH'
  NEEDED               libsimplex.so
  NEEDED               libc.so.6
  RUNPATH              $ORIGIN:.../cpp/desktop/libs/linux-x86_64
```

26 752 octets, horodatée du jour, là où les bibliothèques extraites portent la date de leur
publication par l'amont. C'est la différence entre ce qui est compilé ici et ce qui est repris.

Le Kotlin suit : 8 319 classes dans `common/build/classes`, et
`common/build/libs/common-desktop-7.1.2.jar` à 35 534 481 octets. Le numéro de version du
nom du fichier vient de `gradle.properties` et confirme la base v7.1.2.

### Le paquet

| Fichier | Taille | SHA-256 |
|---|---|---|
| `release/main/deb/simplex_7.1.2-1_amd64.deb` | 221 473 624 o | `f6fbd49cd26bdb87ec6d2f132d607ffbe482ed9e7931e6917412e71814b5e66f` |

Il contient `opt/simplex/lib/app/resources/libapp-lib.so` à 26 752 octets, c'est à dire la
pièce compilée ici, à côté du coeur d'amont extrait. Son champ `Maintainer` dit
`Unknown <Unknown>` et le paquet s'appelle `simplex` : la Phase 2 n'a pas commencé, le paquet
le déclare lui-même.

Le premier essai d'empaquetage avait échoué, et le message valait d'être gardé :

```
java.nio.file.FileSystemException: .../libskiko-linux-x64.so
  -> .../release/main/app/simplex/lib/app/libskiko-linux-x64.so: No space left on device
```

`jpackage` ne compile rien, il recopie : les 392 Mo de bibliothèques du coeur, le jar commun,
Skiko et une image réduite du JDK. Le disque de la VM est passé de 40 à 70 Gio et l'étape est
passée du premier coup.

### L'application démarre, et son coeur fonctionne

C'est le seul endroit de ce document qui dépasse « compile ».

Premier lancement : **plantage**, `SIGSEGV`, dans
`org.jetbrains.skiko.redrawer.LinuxOpenGLRedrawerKt.createContext`. Le cadre natif accusait
`libHSzstd`, ce qui est trompeur : la pile Java, elle, est sans ambiguïté. C'est l'accélération
OpenGL qui manque, sur une VM dont le serveur X tourne sur `modesetting`.

Avec le rendu logiciel, l'application tient :

```
$ DISPLAY=:0 SKIKO_RENDER_API=SOFTWARE ./opt/simplex/bin/simplex
$ xwininfo -id 0x60001e
  xwininfo: Window id: 0x60001e "SimpleX"
  Width: 1498   Height: 890   Map State: IsViewable
```

Une fenêtre réelle, affichée, et aucun rapport de plantage. Attention au passage : le
lanceur de `jpackage` **ne transmet pas** un `-D` à la machine virtuelle, il le passe à
l'application comme argument. Il faut la variable d'environnement, ou `-J-D`.

Et le coeur a travaillé. L'application a créé `~/.local/share/simplex/` avec
`simplex_v1_chat.db` (1 388 544 o) et `simplex_v1_agent.db` (479 232 o). Leurs premiers
octets ne sont pas `SQLite format 3` mais du bruit : les bases sont **chiffrées par
SQLCipher**, comme prévu. Autrement dit, `libsimplex.so` extraite du paquet d'amont ne fait
pas que se lier, elle s'initialise et applique ses migrations.

Le répertoire de données et l'arborescence d'essai ont été supprimés après le test : aucun
profil n'a été créé, aucune connexion n'a été établie, et ces bases n'avaient pas à rester.

### Ce que le Desktop n'a pas encore

`prepare-vlc-linux.sh` n'a pas été exécuté, faute de `patchelf` : la lecture vidéo des
appels manquera. Les bibliothèques VLC du paquet d'amont ont été extraites avec les autres,
mais sans le `patchelf` qui réécrit leur `rpath` elles ne seront pas trouvées à l'exécution.
À traiter avec la distribution, pas avant.

Le paquet n'est pas signé, et sur ce poste le rendu matériel ne fonctionne pas : un
utilisateur sur une machine normale n'aura pas besoin de `SKIKO_RENDER_API`.

## 8. Reproduire

```bash
. ~/outils/env-build.sh
export PATH="$ANDROID_HOME/cmake/3.22.1/bin:$PATH"   # pour le Desktop seulement
cd apps/multiplatform
./gradlew assembleFossDebug --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2200m -Dfile.encoding=UTF-8" \
  -Pkotlin.compiler.execution.strategy=in-process
./gradlew :desktop:compileKotlinJvm --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2200m -Dfile.encoding=UTF-8" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Les deux derniers arguments ne sont pas décoratifs : le poste a 6,6 Gio de mémoire, dont
environ 2 disponibles. Les réglages par défaut du dépôt demandent 2 Gio pour Gradle **plus**
4 Gio pour le démon Kotlin, soit davantage que la machine. La compilation en processus unique
évite la seconde machine virtuelle Java.

## 9. Construire la version signée d'Android

Le chemin de l'amont, `scripts/android/compress-and-sign-apk.sh`, ne signe pas sur ce poste :
il exige la commande `zip`, absente, et ne s'exécute que si `compression.level` n'est pas nul.
La signature est donc faite par le greffon Android, configuré dans
`apps/multiplatform/android/build.gradle.kts`.

**Aucun secret n'est dans le dépôt.** Quatre variables d'environnement, et rien d'autre :

| Variable | Rôle | Défaut |
|---|---|---|
| `TRUSTIATIS_KEYSTORE` | chemin du magasin de clés | aucun : sans elle, pas de signature |
| `TRUSTIATIS_KEYSTORE_PASSWORD` | mot de passe du magasin | aucun |
| `TRUSTIATIS_KEY_ALIAS` | alias de la clé | `trustiatis` |
| `TRUSTIATIS_KEY_PASSWORD` | mot de passe de la clé | celui du magasin |

**Seul le Membre peut lancer cette construction**, parce que lui seul connaît le mot de passe
du magasin. L'agent ne le demande pas et ne le lit pas, voir le chapitre 6 du cadrage. La
commande ne l'écrit nulle part, `read -rsp` ne l'affiche pas :

```bash
cd ~/Documents/claude/trustiatis-secure/apps/multiplatform
. ~/outils/env-build.sh
export PATH="$ANDROID_HOME/cmake/3.22.1/bin:$PATH"
export TRUSTIATIS_KEYSTORE="$HOME/trustiatis-secure.jks"
export TRUSTIATIS_KEY_ALIAS=trustiatis
read -rsp "Mot de passe du magasin : " TRUSTIATIS_KEYSTORE_PASSWORD && echo
export TRUSTIATIS_KEYSTORE_PASSWORD
./gradlew assembleFossRelease --no-daemon --console=plain
```

**Ne plus passer `-Dorg.gradle.jvmargs` ici.** Depuis le 2026-11-17 le réglage de mémoire du
poste vit dans `~/.gradle/gradle.properties`, hors du dépôt, et il donne 3,8 Gio avec la
compilation en processus unique. Une valeur passée en ligne de commande **prime** sur ce
fichier : reprendre l'ancien `-Xmx2200m` rognerait le tas au lieu de l'assurer. Voir le
chapitre 15.

Contrôle d'après construction, qui ne demande aucun secret :

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify -v --print-certs \
  android/build/outputs/apk/foss/release/android-foss-arm64-v8a-release.apk
```

Attendu : `Signer #1 certificate SHA-256 digest:`
`502cbc06654a134d536bf496ac44f1013a50c3dcfcd5beba5d61dd70f7c9c82f`, v2 et v3 vraies.
**v1 fausse est normal** à `minSdk 26`, ce n'est pas un défaut à corriger.

`apksigner` est un script shell qui appelle `java` par son nom, sans chemin : lancé dans un
shell où `env-build.sh` n'a pas été chargé, il échoue sur « exec: java: not found », ce qui
n'a rien à voir avec l'APK. Et sans `ANDROID_HOME`, la ligne ci dessus pointe sur
`/build-tools/...`, qui n'existe pas. Charger l'environnement d'abord, dans ce shell là.

Ranger ensuite les deux APK sous leur nom de publication. La compilation d'Android n'écrit
pas de fichier d'empreintes, contrairement à celle du bureau : il se fait ici, et il
s'appelle `empreintes.txt`, **sans cible**, parce que ces paquets n'arrivent pas dans un
dossier `paquets-<cible>` et que leur nom porte déjà l'architecture.

```bash
cd ~/Documents/claude/trustiatis-secure
D=apps/multiplatform/android/build/outputs/apk/foss/release
( cd "$D" && sha256sum ./*.apk ) > "$D/empreintes.txt"
tools/nommer-livraison.py --version <version> --artefacts "$D" --simuler
tools/nommer-livraison.py --version <version> --artefacts "$D"
```

**Piège de `/tmp` sur cette VM.** `jpackage` assemble l'image du paquet dans
`/tmp/jdk.jpackage*`, or `/tmp` est un **tmpfs de 3,4 Gio** pris sur la mémoire. Un dossier
de travail volumineux laissé là, par exemple un `.deb` de 600 Mo décompressé pour vérifier
son contenu, fait échouer `packageDeb` sur `dpkg-deb ... exited with 2 code`, sans message
sur l'espace. Vider `/tmp` avant d'empaqueter, et décompresser ailleurs.

**Reconstruire après chaque modification de l'interface.** Un APK signé ne vaut que pour
l'état des sources au moment où il a été empaqueté, et les deux plateformes ne se
construisent pas ensemble sur cette VM : comparer la date du paquet à celle des sources avant
de livrer. C'est ainsi qu'un paquet Debian antérieur aux retraits de P-7 bis a été écarté.

## 10. Windows et macOS, sans machine Windows ni Mac

Le poste de construction est une machine Linux, et il n'y en a pas d'autre. Or un `.msi`, un
`.exe` et un `.dmg` ne se fabriquent que sur leur propre système : `jpackage` appelle
`candle`/`light` sur Windows et `hdiutil` sur macOS. La solution retenue est un miroir
public du dépôt sur GitHub, et un seul fichier de compilation continue qui nous appartient,
`.github/workflows/paquets-bureau.yml`, qui construit sur les machines hébergées. Elles sont
gratuites et sans plafond pour un dépôt public, ce qui n'est vrai ni des machines Windows de
GitLab (bêta de l'offre gratuite) ni de ses machines macOS (offre Premium).

### Le coeur ne se recompile pas, il se prend où il est déjà compilé

`scripts/desktop/build-lib-windows.sh` et `build-lib-mac.sh` lancent `cabal build
lib:simplex-chat`. Les suivre reviendrait à recompiler le coeur Haskell, que D-2 interdit, et
supposerait une chaîne GHC complète plus un MinGW très précis sur Windows.

À la place, `scripts/desktop/libs-depuis-amont.sh <cible>` télécharge le paquet de bureau que
l'amont a publié pour la base de `tools/amont-courant.txt`, vérifie son empreinte SHA-256
contre `tools/paquets-amont.txt`, l'ouvre selon le système (`dpkg-deb -x`, `msiexec /a`,
`hdiutil attach`), **cherche** le répertoire `resources` qui contient `libsimplex` au lieu de
supposer un chemin, et remplit `cpp/desktop/libs/<dossier>/` puis
`apps/multiplatform/build/links/<lien>`. C'est la méthode déjà employée pour Linux et pour
Android, étendue aux deux autres systèmes.

| Cible | Dossier | Lien | Ce que le paquet d'amont apporte |
|---|---|---|---|
| `linux-x86_64` | `linux-x86_64` | `linux-x64` | 166 fichiers, dont `libsimplex.so` et 165 bibliothèques GHC |
| `windows-x86_64` | `windows-x86_64` | `windows-x64` | `libsimplex.dll` et `libcrypto-3-x64.dll`, les deux seuls que `build-lib-windows.sh` dépose |
| `macos-aarch64` | `mac-aarch64` | `macos-arm64` | `libsimplex.dylib` et ses dépendances, **avec les `install_name` déjà corrigés** par l'amont |
| `macos-x86_64` | `mac-x86_64` | `macos-x64` | idem pour Intel |

Le gain sur macOS n'est pas mince : tout le travail d'`install_name_tool` de
`build-lib-mac.sh`, une centaine de lignes, est déjà fait dans le paquet publié.

### Deux exclusions à la copie, et pourquoi

`libapp-lib.*` n'est pas repris du paquet : c'est la pièce que CMake compile sur place, et
`cmakeBuildAndCopy` la dépose dans ce même répertoire. Y laisser celle de l'amont ferait
croire qu'on distribue un binaire qu'on n'a pas construit.

`vlc/` n'est pas repris non plus. Sa source n'est pas le paquet mais
`scripts/desktop/prepare-vlc-{linux,mac,windows}.sh`, qui le remplissent depuis la
publication `vlc` de l'amont et réécrivent les chemins de chargement. Le reprendre du paquet
ajoute 230 Mo dont rien n'aurait l'usage.

### Ce que la vérification de ce script a révélé sur la chaîne Linux

Le script a été éprouvé de bout en bout sur sa cible Linux, en comparant son résultat au
contenu qui avait servi à la compilation déjà livrée. **Les 166 bibliothèques du coeur sont
identiques octet pour octet.** Trois écarts sont apparus, et aucun n'était connu :

| Écart | Ce que c'était |
|---|---|
| Le paquet d'amont pris était celui d'**Ubuntu 24.04** | La compilation livrée s'appuyait sur celui de **22.04**. Mêmes versions de paquets Haskell, mais **aucun fichier commun**. `tools/paquets-amont.txt` épingle désormais le 22.04, empreinte `4db7f96c…`, qui vise aussi la plus ancienne glibc des deux |
| `libffi.so.8` | Présent dans le paquet d'amont, absent du répertoire précédent. L'extraction d'alors ne gardait que les bibliothèques GHC et comptait sur celle du système. Celle de l'amont est maintenant livrée, ce qui vaut mieux pour un paquet distribuable |
| L'arborescence `vlc/` | En comptait 389 fichiers réels, d'origine non consignée, dont 4 au premier niveau portant des noms résolus (`libvlc.so` et non `libvlc.so.5`) et deux bibliothèques du système hôte (`libdb-5.3.so`, `libpulsecommon-16.1.so`). Elle vient d'être refaite par `prepare-vlc-linux.sh`, la voie prévue, qui en pose 1 035 |

Conséquence à ne pas taire : **un paquet Debian reconstruit maintenant ne sera pas identique
à `trustiatis-secure-7.1.2-ts1-amd64.deb` déjà empaqueté**, puisqu'il emportera `libffi.so.8`
et l'arborescence VLC complète. Le coeur, lui, est le même. Le contrôle `patchelf` que la
section 7 annonçait nécessaire pour VLC ne l'est pas sur Linux : dans
`prepare-vlc-linux.sh`, les appels à `patchelf` sont tous situés après un `exit 0` et ne
s'exécutent jamais.

### Le fichier de compilation continue

`.github/workflows/paquets-bureau.yml` ne se déclenche **que sur demande**, avec trois cases
à cocher (Windows, macOS Apple Silicon, macOS Intel). Chaque travail pose le JDK 17, appelle
`libs-depuis-amont.sh`, puis `prepare-vlc-*.sh`, puis Gradle :

| Système | Machine | Préparation | Cible Gradle |
|---|---|---|---|
| Windows | `windows-latest` | MSYS2 **UCRT64** avec `make`, `perl`, `cmake` et `gcc` de MSYS2 | `packageMsi packageExe` |
| macOS Apple Silicon | `macos-latest` | rien de plus | `packageDmg` |
| macOS Intel | `macos-15-intel` | rien de plus | `packageDmg` |

UCRT64 et non MINGW64 : `build-lib-windows.sh` prévient que l'autre produit des threads non
posix et que l'application se termine au démarrage. CMake doit venir de MSYS2 et non d'une
installation Windows séparée, pour la même raison.

Le paramètre `-Psimplex.assets.dir` que l'amont passe à Gradle n'est **pas** repris : il
recopie les images de marque de l'amont, ce que le chapitre 4 écarte, et la compilation Linux
livrée ne l'utilisait pas davantage.

Rien n'est signé. La signature de code Windows suppose depuis juin 2023 une clé sur support
matériel, et Microsoft a confirmé qu'un certificat EV ne contourne plus SmartScreen ; côté
Apple, il faut un identifiant de développeur et la notarisation. Les deux sont une décision
du Membre, pas une difficulté technique. Sans identité dans `local.properties`, le bloc
`signing` de `desktop/build.gradle.kts` reste inactif et le `.dmg` sort non signé, sans
échec.

### Avant d'ouvrir le miroir : neutraliser les huit fichiers de l'amont

Le dépôt porte huit fichiers de compilation continue hérités de l'amont. Sur un dépôt public,
trois se déclenchent tout seuls :

| Fichier | Déclencheur | Risque sur le miroir |
|---|---|---|
| `reproduce-schedule.yml` | `schedule`, tous les jours à 2 h | **S'exécute depuis la branche par défaut**, donc tous les jours |
| `cla.yml` | `issue_comment`, `pull_request_target` | S'exécute au premier commentaire venu |
| `build.yml` | `push` sur `master`/`stable` et **étiquettes `v*`**, `pull_request` | Pousser les étiquettes de l'amont le déclenche |
| `packages.yml` | `pull_request` | S'exécute à la première proposition de modification |
| `web.yml` | `push` sur `master` | Sans branche `master`, inerte |
| `android-emu.yml`, `desktop-linux.yml`, `ios-sim.yml` | `workflow_dispatch` seul | Inertes tant que personne ne les lance |

La neutralisation ne passe **pas** par une modification de ces fichiers : les éditer créerait
un conflit à chaque report depuis l'amont. Elle passe par le réglage du dépôt, qui n'est pas
dans Git. Ordre à respecter, faute de quoi la première poussée les déclenche :

1. créer le dépôt avec les actions désactivées
   (`PATCH /repos/<compte>/<dépôt>/actions/permissions`, `{"enabled": false}`) ;
2. pousser les branches, **sans les étiquettes de l'amont** ;
3. désactiver les huit fichiers un par un
   (`PUT /repos/<compte>/<dépôt>/actions/workflows/<id>/disable`) ;
4. réactiver les actions du dépôt, puis vérifier que seul « Paquets de bureau » est actif.

À refaire pour tout fichier que l'amont ajouterait lors d'un report.

### Ce que les trois premières constructions ont appris, le 2026-11-16

Les paquets Windows et macOS de 7.1.2-ts1 sont sortis ce jour là, et il a fallu trois
constructions. Ce qu'elles ont révélé est consigné ici parce que rien n'en était connu.

| Construction | Branche | Résultat |
|---|---|---|
| #1 | `main` | macOS Apple Silicon et macOS Intel **réussies** en 16 min ; Windows en échec au bout de 1 min 56 |
| #2 | `feature/branding` | Windows **bloquée** 27 min sans une ligne de sortie, interrompue |
| #3 | `feature/branding` | Windows **réussie** en 11 min 14 |

**Les deux systèmes macOS n'ont rien demandé.** `libs-depuis-amont.sh` a ouvert les `.dmg`
de l'amont, posé les bibliothèques et rendu la main ; `prepare-vlc-mac.sh` a suivi ; Gradle
a produit le `.dmg`. Aucun `install_name_tool` n'a été nécessaire, comme prévu : les
bibliothèques du paquet d'amont portent déjà leurs chemins corrigés.

**Windows a coûté deux échecs, de deux natures différentes.**

Le premier : `msiexec` répond « This installation package could not be opened » et sort en
1619, que le shell voit comme 83. L'empreinte du paquet venait d'être vérifiée, donc le
fichier était intact : seul son chemin était en cause. `pwd -W` rend `C:/a/...` et `msiexec`
n'ouvre un paquet qu'à la forme `C:\a\...`. Les deux chemins passent désormais par
`cygpath -w`.

Le second, et c'est le plus coûteux : l'option de journalisation `//l*v` ajoutée pour
diagnostiquer le premier a fait **rester `msiexec` en vie sans fin**, sans une ligne de
sortie. Devant une ligne de commande qu'il n'analyse pas, `msiexec` ouvre une fenêtre
d'aide, que `/qn` ne supprime pas et que personne ne fermera sur une machine sans écran.
L'option est retirée et l'appel est borné par `timeout 900`, sans quoi un `msiexec` qui
attend occupe la machine jusqu'au plafond de six heures de GitHub.

**Le lien vers les bibliothèques n'en est pas un sous Windows.** `ln -s` sous MSYS2 produit
un lien que seuls les outils de MSYS2 savent suivre, et c'est la JVM qui lit ce chemin. Le
script pose donc une **jonction de répertoire** (`cmd /c mklink /J`), qui en est un pour
tout le monde et ne demande aucun droit particulier. Une copie ne conviendrait pas :
`prepare-vlc-windows.sh` remplit le répertoire d'origine **après** ce script, et une copie
ne verrait pas cet ajout, donc le paquet sortirait sans VLC.

### Ce que contiennent les paquets, vérifié sans les installer

| Paquet | Taille | SHA-256 |
|---|---|---|
| `trustiatis-secure-7.1.2-ts1-windows-x86_64.msi` | 257 089 536 o | `56857e6a138b84b560358312e3ebfe2bf8516c3c7e2dbf2c372697112d6746fb` |
| `trustiatis-secure-7.1.2-ts1-windows-x86_64.exe` | 257 662 976 o | `b16775f8ff6e3c0ab844e996b4709319b7798b25c19056cb862aa15e6dae6ea0` |
| `trustiatis-secure-7.1.2-ts1-macos-aarch64.dmg` | 269 843 829 o | `7eb852261f6454bbfeb687453f9354de38592d7dbb8817de26d31a55e5a39ece` |
| `trustiatis-secure-7.1.2-ts1-macos-x86_64.dmg` | 283 114 289 o | `c5e969bc8a5533d4fb4bce10179574916bf5fb85184d33fdc6a8cc14d50be014` |

Chaque empreinte a été recalculée sur le fichier reçu et comparée à celle que la machine de
construction avait inscrite dans son artefact : les quatre correspondent.

Le `.msi` a été inspecté depuis le poste Linux, sans l'installer : il porte `libsimplex.dll`,
`libapp-lib.dll` et **363 greffons VLC** nommés, ce qui prouve que la jonction a tenu et que
VLC est bien embarqué. Le nom de l'amont n'y apparaît **pas une fois**, le nôtre deux. Aucune
bibliothèque `libHS*.dll` n'y figure, et c'est normal : sous Windows le runtime Haskell est
lié dans `libsimplex.dll`, là où la chaîne Linux pose 166 bibliothèques séparées.

Le contenu des deux `.dmg` n'a **pas** été inspecté : il n'y a pas de machine macOS ici, et
une image disque HFS+ ne s'ouvre pas honnêtement autrement. Le nom que jpackage leur a donné,
`Trustiatis Secure-7.1.2.dmg`, atteste au moins que la configuration de marque s'est
appliquée.

**Aucun de ces quatre paquets n'est signé.** Le groupe n'a de certificat de signature de code
ni chez Apple ni pour Windows, et la décision appartient au Membre. Conséquence pour qui
télécharge : macOS refuse le premier lancement, SmartScreen avertit. Le portail de
téléchargement l'explique et donne la manoeuvre pour les deux systèmes.

## 13. Ce que le paquet Linux 7.1.2-ts1 avait de faux, et pourquoi rien ne l'a vu

Le Membre a rapporté, le 2026-11-17, que l'application plante sous Linux au moment d'un
appel, les messages continuant de passer. La trace qu'il a jointe nomme la cause sans
détour :

```
libvlccore.so.9: cannot open shared object file: No such file or directory
```

### Le défaut, et sa preuve

L'arbre VLC livré dans `trustiatis-secure-7.1.2-ts1-amd64.deb` porte `libvlccore.so`, et
**pas** `libvlccore.so.9`. Or `libvlc.so` déclare :

```
RUNPATH              $ORIGIN
NEEDED               libvlccore.so.9
```

L'éditeur de liens dynamique cherche donc un nom qui n'existe pas, dans un répertoire qui
contient pourtant la bibliothèque, sous un autre nom. C'est la signature d'une copie qui a
suivi les liens symboliques : les trois noms `libvlccore.so`, `libvlccore.so.9` et
`libvlccore.so.9.0.1` s'effondrent sur celui du lien, et la version disparaît. Le paquet
livré compte 424 entrées VLC ; l'arbre que `prepare-vlc-linux.sh` dépose en compte 1 035,
dont les six liens symboliques qui portent les noms versionnés.

### Pourquoi la chaîne ne s'en est pas aperçue

Trois raisons, et aucune ne fait de bruit.

1. **L'ordre des deux scripts ne se devine pas.** `libs-depuis-amont.sh` commence par un
   `rm -rf` sur le répertoire de destination, donc il efface aussi `vlc/`. Il doit passer
   avant `prepare-vlc-linux.sh`, jamais après.
2. **`prepare-vlc-linux.sh` échoue en silence.** Sa première ligne utile est
   `mkdir $vlc_dir || exit 0` : si le répertoire existe déjà, même incomplet, le script
   rend la main avec un code 0 sans rien faire.
3. **La construction réussit quand même.** Le manque ne se voit qu'à l'exécution, et
   seulement si l'on passe un appel. Un contrôle de compilation ne pouvait pas le trouver.

L'horodatage le confirme : le `.deb` est daté du 2026-11-16 à 07:05, l'arbre VLC correct
du même jour à 08:52. Le paquet a été fabriqué avant que l'arbre ne soit posé.

### Deux autres défauts, trouvés en ouvrant le même paquet

L'entrée de bureau livrée portait :

```
Exec=/opt/trustiatis-secure/bin/trustiatis-secure
MimeType=
```

Sans `%u` et sans type MIME, **les liens `simplex:` et `simplexchat:` ne sont enregistrés
nulle part** : un lien d'invitation cliqué sous Linux n'ouvre rien. La décision D-3 dit de
garder ces schémas ; elle n'était pas tenue. La bibliothèque `skiko` était en outre
présente deux fois, dans son `.jar` et extraite à côté.

Ces deux défauts ont la même origine que le premier : le paquet est sorti d'un
`./gradlew packageDeb` nu. L'amont, lui, repasse derrière jpackage avec
`scripts/desktop/make-deb-linux.sh`. Ce script ne pouvait pas servir ici, pour deux
raisons dont aucune n'est un oubli de notre part :

- il commence par `scripts/desktop/build-lib-linux.sh`, qui recompile le coeur Haskell,
  ce que la décision D-2 interdit ;
- toutes ses recherches de fichiers portent le nom de l'amont, `simplex*.deb`,
  `/opt/*imple*/`, `simplex.cfg`, et il renomme en `simplex_<arch>.deb`. Aucun de ces
  motifs ne rencontre `trustiatis-secure`.

### Ce qui a été mis en place

| Pièce | Rôle |
|---|---|
| `scripts/desktop/verifier-vlc-linux.sh` | lit les `NEEDED` de chaque ELF du premier niveau de l'arbre et refuse tout nom absent dont l'arbre fournit pourtant la racine sous un autre nom. Vérifie en plus, nommément, `libvlc.so` et `libvlccore.so.9` |
| `scripts/desktop/paquet-deb-trustiatis.sh` | transpose à notre nom les étapes d'après empaquetage de l'amont, et refuse de rendre la main si l'une d'elles n'a pas pris. Vérifie le paquet fini sans l'installer |
| travail `linux` de `.github/workflows/paquets-bureau.yml` | part d'un arbre vide à chaque fois, sur `ubuntu-22.04`, et fixe l'ordre des étapes une bonne fois |

Le garde-fou a été éprouvé **dans les deux sens** : il passe sur l'arbre courant (1 035
fichiers, les deux noms présents, aucune dépendance interne perdue) et il refuse l'arbre
extrait du paquet réellement livré, en nommant `libvlccore.so.9`.

### La leçon

Elle vaut d'être écrite, parce qu'elle s'est produite deux fois dans ce projet. Une étape
de préparation qui se saute elle même en silence, avec un code de sortie 0, est plus
dangereuse qu'une étape absente : elle laisse la chaîne réussir et le défaut partir chez
l'utilisateur. Le `mkdir || exit 0` de l'amont et le `|| true` d'un journal de CI sont la
même faute. Partout où une étape peut ne pas avoir lieu, le contrôle doit porter sur son
**résultat**, pas sur son code de retour.

## 14. Le dernier pas manquait : du nom de la chaîne au nom publié

Les paquets ne sortent pas de la chaîne sous le nom qu'ils portent sur le portail.
`jpackage` les nomme d'après le produit et la version qu'il connaît, sans notre suffixe :

| Nom à la sortie de la chaîne | Nom attendu par le portail |
|---|---|
| `trustiatis-secure_x86_64.deb` | `trustiatis-secure-7.1.4-ts1-amd64.deb` |
| `Trustiatis Secure-7.1.4.msi` | `trustiatis-secure-7.1.4-ts1-windows-x86_64.msi` |
| `Trustiatis Secure-7.1.4.exe` | `trustiatis-secure-7.1.4-ts1-windows-x86_64.exe` |
| `Trustiatis Secure-7.1.4.dmg` | `trustiatis-secure-7.1.4-ts1-macos-aarch64.dmg` |
| `Trustiatis Secure-7.1.4.dmg` | `trustiatis-secure-7.1.4-ts1-macos-x86_64.dmg` |

Les deux dernières lignes disent tout : **les deux `.dmg` sortent sous exactement le même
nom**, et rien, dans le fichier lui même, ne dit lequel vient d'Apple Silicon. Seul le nom
du dossier d'artefact le dit. Pour 7.1.2-ts1, ce renommage a été fait à la main, et il
s'est trouvé juste. C'est une chance qu'on ne reconduit pas.

`tools/nommer-livraison.py` fait ce pas, et vérifie avant de ranger :

```bash
tools/nommer-livraison.py --version 7.1.4-ts1 --artefacts ~/telechargements/artefacts --simuler
tools/nommer-livraison.py --version 7.1.4-ts1 --artefacts ~/telechargements/artefacts
```

Il refuse de deviner : un fichier qu'aucune règle ne reconnaît n'est pas rangé au hasard,
il est signalé. Et il recalcule l'empreinte de chaque paquet pour la comparer à celle que
la machine de construction a inscrite dans `empreintes-<cible>.txt` : un paquet de 250 Mio
qui a mal voyagé est écarté ici, et non chez l'utilisateur. À la première anomalie, rien
n'est rangé du tout.

### Le premier essai de cet outil est tombé dans le piège qu'il devait fermer

Sa table d'empreintes était indexée sur le seul nom de fichier. Les deux `.dmg` portant le
même nom, le second écrasait le premier, et l'outil comparait le paquet d'Apple Silicon à
l'empreinte de celui d'Intel : il annonçait un écart sur un fichier intact. La clé est
maintenant la paire (cible, nom), la cible étant lue dans le nom du fichier d'empreintes.
Éprouvé dans les deux sens : cinq paquets rangés sous leur bon nom, puis un `.dmg` abîmé et
un fichier inconnu, tous deux refusés, sans que rien ne soit rangé.

## 15. Deux pièges du poste, rencontrés au report de la 7.1.4

### Le cache d'amont ne portait pas la version

`libs-depuis-amont.sh` garde les paquets téléchargés sous `build/amont/`, et il les y
range sous le nom que l'amont leur donne. Or l'amont ne versionne pas ses noms de
fichiers : `simplex-desktop-ubuntu-22_04-x86_64.deb` désigne la 7.1.2 comme la 7.1.4. Le
fichier de la version précédente reste donc en place, et le script, qui ne télécharge que
si le fichier manque, se contente de ce qu'il trouve.

Le contrôle d'empreinte rattrape le cas : le script s'arrête. Mais son message dit
« empreinte inattendue » et invite à relever l'empreinte publiée par l'API de GitHub, ce
qui envoie chercher une republication ou une falsification là où il n'y a qu'un cache
périmé. Le 17 novembre 2026, le poste gardait encore le `.deb` de la 7.1.2 sous ce nom
après le passage à la 7.1.4 ; il a fallu comparer les empreintes à la main pour s'assurer
que les bibliothèques posées n'étaient pas celles de la version précédente.

Les paquets sont maintenant rangés dans un sous dossier par tag, `build/amont/v7.1.4/`.
Deux versions coexistent sans se recouvrir, et un retour en arrière ne retélécharge rien.

### L'ordre des deux scripts se paie comptant

C'était déjà écrit en tête de `verifier-vlc-linux.sh`, et cela s'est reproduit le même
jour : `libs-depuis-amont.sh` commence par un `rm -rf` sur le répertoire de destination,
donc il efface `vlc/`. Relancé après `prepare-vlc-linux.sh`, ne serait ce que pour
éprouver une correction du cache, il défait le travail du second, qui rendra ensuite la
main en silence si on le relance sans supprimer le répertoire.

La seule défense qui tienne est celle qui est déjà en place : `paquet-deb-trustiatis.sh`
appelle `verifier-vlc-linux.sh` avant d'empaqueter, et refuse de construire sur un arbre
incomplet. Elle a fonctionné : l'arbre a été refait, et vérifié, avant l'empaquetage.

### Le tas de la compilation ne se rogne pas

La section 8 donne `-Xmx2200m`. Ce n'est pas une valeur de confort. Essayée à 1,6 Gio, la
compilation du module `common` échoue sur un `OutOfMemoryError` pendant l'abaissement IR
du compilateur Kotlin, et elle y met douze minutes : l'échec arrive à la fin, pas au
début. Le réglage vit désormais dans `~/.gradle/gradle.properties`, hors du dépôt, pour
qu'il s'applique aussi aux lancements qui ne passent pas par une ligne de commande à nous,
`paquet-deb-trustiatis.sh` en premier.

## 16. La version du paquet Linux dépendait de la machine qui compile

Relevé au report de la 7.1.4-ts3, et jamais vu avant parce qu'il ne se montre qu'à la
mise à jour, chez l'utilisateur.

Le script d'empaquetage reprenait de l'amont le suffixe de distribution sur la version :
`VERSION_CODENAME` de `/etc/os-release`, d'où `7.1.4-1~jammy` depuis la chaîne hébergée
(Ubuntu 22.04) et `7.1.4-1~trixie` depuis le poste du groupe. Chez l'amont, c'est juste :
il publie un paquet par Ubuntu, et le suffixe les distingue.

Nous n'en publions qu'un, bâti sur la glibc la plus ancienne que nous servions. Le
suffixe n'y distinguait donc rien, et il y faisait dépendre **l'ordre** des versions de
l'endroit où le paquet avait été construit :

```
$ dpkg --compare-versions 7.1.4-1~jammy gt 7.1.4-1~trixie ; echo $?
1
```

La 7.1.4-ts2 était sortie du poste, la ts3 de la chaîne : le paquet le plus récent
paraissait le plus vieux. `dpkg -i` l'aurait installé sans rien dire, mais
`apt install ./paquet.deb` refuse ce qu'il prend pour un retour en arrière, et c'est le
geste que la notice d'utilisation donne.

La révision Debian porte maintenant notre rang de version, relu dans
`REVISION_TRUSTIATIS` plutôt que passé en argument, pour qu'il n'y ait rien à tenir à jour
en double. Le rang ne fait que croître, et un test le garde solidaire des compteurs de
`gradle.properties`. L'ordre attendu, vérifié par `dpkg` lui même :

| Comparaison | Résultat |
|---|---|
| `7.1.4-ts3` contre `7.1.4-1~trixie` | plus récent |
| `7.1.4-ts3` contre `7.1.4-1~jammy` | plus récent |
| `7.1.4-ts10` contre `7.1.4-ts9` | plus récent |
| `7.1.5-ts1` contre `7.1.4-ts9` | plus récent |

Un contrôle d'après construction refuse désormais un paquet dont la version ne porte pas
`-ts<rang>` : le défaut était invisible à la construction, il se vérifie donc là où il
pouvait sortir.
