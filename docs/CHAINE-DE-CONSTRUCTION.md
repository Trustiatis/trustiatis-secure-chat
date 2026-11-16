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
bibliothèques ont donc été extraites de l'APK officiel de la version greffée, ce qui garantit
qu'elles proviennent de la compilation de l'amont pour ce commit précis.

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
./gradlew assembleFossRelease --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx2200m -Dfile.encoding=UTF-8" \
  -Pkotlin.compiler.execution.strategy=in-process
```

Contrôle d'après construction, qui ne demande aucun secret :

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify -v --print-certs \
  android/build/outputs/apk/foss/release/android-foss-arm64-v8a-release.apk
```

Attendu : `Signer #1 certificate SHA-256 digest:`
`502cbc06654a134d536bf496ac44f1013a50c3dcfcd5beba5d61dd70f7c9c82f`, v2 et v3 vraies.
**v1 fausse est normal** à `minSdk 26`, ce n'est pas un défaut à corriger.

**Piège de `/tmp` sur cette VM.** `jpackage` assemble l'image du paquet dans
`/tmp/jdk.jpackage*`, or `/tmp` est un **tmpfs de 3,4 Gio** pris sur la mémoire. Un dossier
de travail volumineux laissé là, par exemple un `.deb` de 600 Mo décompressé pour vérifier
son contenu, fait échouer `packageDeb` sur `dpkg-deb ... exited with 2 code`, sans message
sur l'espace. Vider `/tmp` avant d'empaqueter, et décompresser ailleurs.

**Reconstruire après chaque modification de l'interface.** Un APK signé ne vaut que pour
l'état des sources au moment où il a été empaqueté, et les deux plateformes ne se
construisent pas ensemble sur cette VM : comparer la date du paquet à celle des sources avant
de livrer. C'est ainsi qu'un paquet Debian antérieur aux retraits de P-7 bis a été écarté.
