# Distribution de Trustiatis Secure

Comment les paquets arrivent chez l'utilisateur, et à quel coût.

| | |
|---|---|
| Portail | **`downloads.trustiatis.com`** |
| Dépôt du portail | `trustiatis-secure-downloads`, séparé de celui du produit |
| Hébergement | Cloudflare, **plan gratuit**, compte Trustiatis |
| Statut | portail **en ligne depuis le 2026-11-16**, version **7.1.4-ts1 publiée le 2026-11-18**, sept paquets servis depuis R2 pour les quatre plateformes |
| Pages | `/` accueil à deux voies, `/trustiatis-secure/` les paquets, `/logiciels/` les adresses officielles de logiciels tiers |

## 1. Pourquoi un dépôt séparé pour le portail

Le dépôt du produit pèse environ 573 Mo, le code de l'amont compris. Si le portail y vivait,
chaque construction Cloudflare clonerait cette masse pour publier quelques fichiers HTML.
Le portail a donc son propre dépôt, léger, conformément à l'usage de l'espace de travail
(un dépôt par projet).

## 2. Architecture, et la contrainte qui la dicte

**Cloudflare Pages refuse tout fichier de plus de 25 Mio.** Les paquets mesurés sur la
publication `v7.1.3` de l'amont vont de 89 à 268 Mo. Pages est donc exclu pour les binaires.

| Composant | Choix | Palier gratuit |
|---|---|---|
| Portail (HTML, CSS, JS, manifestes) | **ressources statiques d'un Worker** | **gratuit et illimité**, les requêtes vers les ressources statiques ne sont pas facturées |
| Paquets | **bucket R2**, servis par le Worker sous `/fichiers/*` | 10 Go de stockage par mois, 1 M d'écritures, 10 M de lectures, **sortie réseau gratuite** |
| Invocations du Worker | seulement sur `/fichiers/*` (`run_worker_first` restreint) | 100 000 requêtes par jour |

Un seul sous domaine porte l'ensemble. Le Worker gère les requêtes partielles, ce qui permet
de reprendre un téléchargement de 250 Mo interrompu.

**Deux limites à connaître :**

1. Sur le plan gratuit, une requête vers `/fichiers/*` au delà de 100 000 par jour reçoit un
   `429` et **ne se replie pas** sur les ressources statiques. Pour un portail de
   téléchargement, cette limite est très au dessus du trafic attendu, mais elle est réelle.
2. Un fichier de plus de 512 Mo ne serait pas mis en cache par Cloudflare sur les plans
   gratuit, Pro et Business. Nos paquets restent bien en dessous.

## 3. Ce que montre le portail

Pour chaque plateforme et chaque architecture : nom du paquet, version, date, taille, format,
architecture, **empreinte SHA-256**, instructions d'installation, et un lien vers les notes de
version. Le système de l'utilisateur est détecté et son bloc mis en avant.

Tant qu'aucune version n'est publiée, le portail affiche « **Aucune version publiée** » et
**n'affiche aucun lien**. Il ne ment pas sur l'état du produit.

Le portail ne mesure aucune audience, ne pose aucun cookie et ne collecte rien.

## 4. Rétention, et la limite du plan gratuit

Un jeu complet d'artefacts pèse de **1,5 à 2 Go** par version. Mesure de référence :
**1 216 Mo pour 9 artefacts** de la publication `v7.1.3` de l'amont, relevés le 2026-11-14,
auxquels s'ajoutent les paquets Linux et macOS Intel.

| Versions conservées | Stockage | Tient dans les 10 Go gratuits |
|---|---|---|
| 3 | environ 6 Go | **oui** |
| 5 | environ 10 Go | à la limite exacte |
| toutes | croissance sans fin | **non** |

**« Toutes les versions » n'est pas tenable en plan gratuit.** D'où la décision **P-3** :
conserver les **trois dernières versions** sur le portail, les plus anciennes restant
accessibles par les publications du dépôt GitLab, qui conservent notes et empreintes. Si le
Membre veut réellement tout conserver en ligne, il faut passer R2 au delà du palier gratuit,
pour un coût de l'ordre de 0,015 dollar par gigaoctet et par mois, soit environ 0,30 dollar
par mois pour 20 Go.

## 5. Mise à jour du client Desktop

Le client Desktop de l'amont interroge **en dur** l'API GitHub de SimpleX
(`AppUpdater.kt`, ligne 188). Non corrigé, Trustiatis Secure proposerait d'installer le paquet
SimpleX par dessus lui même : c'est le **risque T-02** de l'audit de Phase 1.

**Le chemin est neutralisé depuis le 2026-11-15**, en deux endroits, selon la méthode déjà
employée pour P-5 sur iOS :

| Endroit | Ce qui change |
|---|---|
| `SettingsView.desktop.kt` | le sélecteur de canal est remplacé par un texte qui renvoie au portail |
| `AppUpdater.kt`, `setupUpdateChecker()` | verrou de dernier recours : la préférence est ramenée au cas désactivé, et la fonction sort |

Le verrou n'est pas une précaution de principe. La préférence est lue dans un fichier de
réglages qui peut venir d'ailleurs, d'une installation antérieure de l'application officielle
ou d'une archive importée, et `setupUpdateChecker()` est le **seul** chemin par lequel la
vérification démarre. Les trois cas de `AppUpdatesChannel` sont **conservés** : la préférence
est persistée par le nom de la valeur.

Ce qu'il restait de l'identité de l'amont dans la même zone a été corrigé en même temps, parce
que ces constantes décident de vraies associations sur le poste de l'utilisateur :

| Constante | Pourquoi elle ne pouvait pas rester |
|---|---|
| `WINDOWS_PROG_ID_PREFIX`, `WINDOWS_CAPABILITIES_KEY` | notre installation et celle de l'application officielle écrivaient **les mêmes clés de registre**, et se volaient donc l'association de protocole |
| `DEB_ENTRY_NAME` | il nommait `simplex-simplex.desktop` alors que notre paquet installe `trustiatis-secure-trustiatis-secure.desktop`, donc le contrôle d'enregistrement répondait **toujours non** |
| `APPIMAGE_ENTRY_NAME`, `APP_DISPLAY_NAME`, `APP_DESCRIPTION` | vus de l'utilisateur, dans les applications par défaut de Windows |
| `ISSUES_URL`, `SUPPORT_EMAIL` de `StartupError.kt` | la fenêtre d'erreur au démarrage envoyait la **trace d'exécution du poste d'un client** à l'éditeur amont |

Les sept tests de `AppLinkSchemeTest` qui affirment ces valeurs ont été mis à jour, et ce sont
eux qui prouvent la cohérence entre le code et le paquet.

**Ce que le paquet Debian ne sait toujours pas faire.** L'entrée de bureau que jpackage écrit
porte `MimeType=` vide, et Compose Desktop n'expose pas le moyen de la remplir. Installé par
`.deb`, le client **n'enregistre donc pas** `x-scheme-handler/simplex`, et un lien d'invitation
cliqué dans un navigateur ne lui parviendra pas. L'AppImage, elle, écrit sa propre entrée et
n'a pas ce défaut. À traiter par un post-traitement du paquet.

Le portail publie déjà l'index de remplacement, à l'adresse
`https://downloads.trustiatis.com/maj/stable.json`, servi comme ressource statique, donc
gratuitement. Il liste pour chaque artefact son URL, sa plateforme, son architecture, sa
taille et son **empreinte SHA-256**.

C'est l'occasion de combler un manque de l'amont : le client ne vérifie aujourd'hui **aucune
empreinte** du fichier téléchargé, il s'en remet à TLS puis à l'installeur du système. En
pointant vers notre index, nous pouvons vérifier le SHA-256 avant de proposer l'installation.

## 6. Canaux de distribution

| Canal | Statut en version 1 | Remarque |
|---|---|---|
| **Portail `downloads.trustiatis.com`** | **canal principal** | Windows, macOS, Linux, Android, GrapheneOS |
| Publications GitLab | archive et notes de version | conserve les versions sorties de la rétention du portail |
| Google Play | à étudier en Phase 6 | exige le bundle `bundleGoogleRelease` et un compte éditeur |
| App Store et TestFlight | **bloqué** | décision D-1, risque J-02 |
| Microsoft Store, Flathub | hors version 1 | |
| F-Droid | hors version 1 | décision D-7 |

## 7. Nommage des artefacts

L'outil de publication reconnaît la plateforme d'après le nom du fichier et **refuse de
deviner** si le nom ne correspond à aucun motif. Convention à respecter par la chaîne de
construction :

```text
trustiatis-secure-<version>-android-arm64-v8a.apk
trustiatis-secure-<version>-android-armeabi-v7a.apk
trustiatis-secure-<version>-desktop-windows-x86_64.msi
trustiatis-secure-<version>-desktop-macos-aarch64.dmg
trustiatis-secure-<version>-desktop-macos-x86_64.dmg
trustiatis-secure-<version>-desktop-linux-x86_64.deb
trustiatis-secure-<version>-desktop-linux-aarch64.deb
trustiatis-secure-<version>-desktop-linux-x86_64.AppImage
```

## 8. Ce qui reste à faire

- [x] activer R2 sur le compte Cloudflare Trustiatis et créer le bucket, fait le 2026-11-16
- [x] déployer le Worker et attacher `downloads.trustiatis.com`, fait le 2026-11-16
- [x] neutraliser le vérificateur de mises à jour de l'amont (risque T-02), fait le 2026-11-15
- [x] remplir le `MimeType` de l'entrée de bureau du paquet Debian, par post-traitement :
      `scripts/desktop/paquet-deb-trustiatis.sh`, fait le 2026-11-16, après que le paquet
      7.1.2-ts1 est sorti sans
- [x] construire et publier une première version, 7.1.2-ts1 le 2026-11-16. **Signée pour
      Android seulement** : les paquets de bureau ne le sont pas, faute de certificat Apple
      et Windows, et la décision appartient au Membre
- [ ] faire lire l'index du portail par le client Desktop, en remplacement
- [ ] ajouter la vérification du SHA-256 avant installation dans le client Desktop
- [ ] trancher D-1, la mise à disposition sur l'App Store sous AGPLv3
- [x] **notice d'utilisation simplifiée**, demandée par le Membre le 2026-11-22 et écrite le
      même jour dans [`NOTICE-UTILISATION.md`](NOTICE-UTILISATION.md), en dix huit sections
      avec les libellés exacts de l'interface en français. Le guide de l'amont ne tenait pas ce
      rôle : anglais, il décrit SimpleX Chat, et il décrit des fonctions que nous avons
      neutralisées, les modes de notification iOS en premier (P-5)
- [ ] **servir la notice par le portail**, à côté des paquets, et la relire à chaque version :
      une notice qui décrit une fonction retirée est pire que pas de notice
- [x] publier la 7.1.4-ts1, le 2026-11-18, sept paquets et quatre plateformes. Les quatre
      paquets de bureau viennent de la construction hébergée numéro 4 sur `bec2a3e2d`, les
      trois autres du poste. Même réserve de signature que la 7.1.2-ts1
- [x] rendre les sources disponibles avant les binaires, obligation AGPL : le miroir
      `github.com/Trustiatis/trustiatis-secure-chat` est **public sous AGPL-3.0 depuis le
      2026-11-16**, et c'est de là que la source accompagne l'exécutable. Le dépôt GitLab
      reste privé, il n'est pas celui que l'obligation vise
