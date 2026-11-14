# Trustiatis Secure, audit technique du dépôt serveur `simplexmq`

**Complément de la Phase 1. Document de décision, aucune modification de code n'a été faite.
Prérequis de la Phase 5 (infrastructure).**

| | |
|---|---|
| Entité porteuse | Trustiatis |
| Produit visé | Trustiatis Secure |
| Amont audité | `https://github.com/simplex-chat/simplexmq` |
| Version auditée | **tag `v7.1.0`**, commit `fdbcb24d86460977382e1237591cd505c3b37fa1`, intitulé « 7.1.0.18 (#2057) », daté du 2026-11-14 |
| Version déclarée dans le paquet | `7.1.0.18` (`simplexmq.cabal`, champ `version`) |
| Branche par défaut de l'amont | `stable` (le `HEAD` distant pointe sur `stable`) |
| Tête de la branche `stable` | commit `27a37387be98d9c7ec0e62373e125539675d0095`, « 7.0.1.0 », daté du 2026-07-31, soit le tag `v7.0.1` |
| Révision réellement utilisée par le client audité en Phase 1 | commit `c1830fb2dbf5a1a5cc3b56c6c65ef911a622ae6d`, « 7.1.0.17 », daté du 2026-11-07 |
| Date de l'audit | 2026-11-14, document rendu le 2026-11-15 |
| Statut | PROJET, soumis à validation avant tout engagement de la Phase 5 |
| Document amont | [`INITIAL_AUDIT.md`](INITIAL_AUDIT.md) (Phase 1, dépôt client) |

**Méthode.** Clone superficiel (`git clone --depth 1 --no-single-branch --filter=blob:none
--no-checkout`) dans un répertoire neuf et vide du scratchpad de session, hors du dépôt de
travail, puis extraction du tag `v7.1.0`. Le clone a été **supprimé en fin de travail** (voir
chapitre 8). Toutes les affirmations de ce document sont issues de la lecture des fichiers du
dépôt, sauf les quelques points explicitement signalés comme lus ailleurs (interface publique
de GitHub, ou fichiers du dépôt client lus à distance à son tag `v7.1.3`). Chaque constat porte
une mention de statut, reprise de l'audit de Phase 1 :

- **[V]** vérifié par lecture directe du fichier cité,
- **[D]** déduit d'un fichier lu, à confirmer par un essai ou par une source externe,
- **[NV]** non vérifiable depuis ce dépôt (compte éditeur, matériel, exploitation réelle).

Aucune compilation n'a été tentée : le poste d'audit n'a ni GHC, ni Cabal, ni Nix, ni Docker,
ni JDK, et il ne reste qu'environ 6 Gio de disque libre. **Aucune affirmation de ce document ne
prétend qu'un binaire a été produit ou qu'un serveur a démarré.**

### Choix de la version auditée, et pourquoi il mérite une décision

Trois faits, lus et vérifiables, se contredisent en apparence [V] :

1. le tag le plus récent du dépôt est `v7.1.0`, posé le 2026-11-14 sur la branche `master`, et
   **absent de la branche `stable`** (`git branch -r --contains tags/v7.1.0` ne retourne que
   `origin/master`) ;
2. la branche `stable`, que la documentation amont désigne pour compiler (README, section
   « Build from source »), est restée au tag `v7.0.1` du 2026-07-31 ;
3. l'interface publique de GitHub désigne comme « dernière publication » le tag `v6.5.0`
   du 2026-04-29, parce que c'est le **seul** jalon du dépôt qui ne porte pas l'attribut
   « préversion » : le flux d'intégration continue publie toutes les versions avec
   `prerelease: true` (`.github/workflows/build.yml`) [V].

L'audit a donc porté sur `v7.1.0`, qui est la version la plus récente et celle dont le client
audité en Phase 1 est presque contemporain : le client `v7.1.3` épingle la révision
`c1830fb` (« 7.1.0.17 »), et **une seule validation** sépare cette révision du tag `v7.1.0`
(« 7.1.0.18 ») [V]. Cette conséquence doit être retenue : **la version du serveur et la version
du client ne sont pas le même numéro**, et le choix de la branche de départ du fork est une
décision (S-5, S-16).

---

## Synthèse pour la décision

**Le point central est tranché : oui, Trustiatis doit exploiter son propre serveur de
notification pour que les notifications iOS fonctionnent.** La déduction T-07 de la Phase 1 est
confirmée par lecture du code, et elle l'est plus durement que prévu. Six constats structurent
le dossier.

1. **La déduction de la Phase 1 est confirmée [V].** Le serveur de notification s'authentifie
   auprès d'Apple par un jeton JWT signé ES256 dont l'émetteur est l'identifiant d'équipe Apple
   de SimpleX (`5NN7GUYB6T`), et il adresse chaque poussée à l'identifiant d'application
   `chat.simplex.app`, placé dans l'en-tête `apns-topic`. Ces deux valeurs sont **codées en dur**
   dans `src/Simplex/Messaging/Notifications/Server/Push/APNS.hs` et ne sont **pas** exposées
   dans le fichier de configuration. Le code traite explicitement les refus d'Apple
   `DeviceTokenNotForTopic` et `TopicDisallowed`, ce qui confirme qu'une poussée vers un jeton
   d'une autre application est rejetée.
2. **Le coût est plus élevé que l'ajout d'un quatrième serveur [V].** L'adresse du serveur de
   notification est, côté client, une constante compilée dans le coeur Haskell, et **il n'existe
   aucune commande d'interface pour la changer à l'exécution** : le client n'expose que la
   gestion du jeton. La « voie B » recommandée en Phase 1 (configurer les serveurs par appel
   d'API après l'accueil) **ne couvre donc pas le serveur de notification**. Faire fonctionner
   les notifications iOS impose de recompiler le coeur Haskell, c'est à dire de basculer sur
   l'option « Autonome » de la décision D-2 de la Phase 1, et de recompiler aussi le serveur de
   notification pour y placer l'identifiant d'application et l'identifiant d'équipe de
   Trustiatis.
3. **Le serveur de notification exige PostgreSQL, et il est le seul des trois [V].** Sans le
   drapeau de compilation `server_postgres`, l'exécutable n'est simplement **pas construit**.
   Les relais SMP et XFTP, eux, fonctionnent en mémoire avec un journal d'ajout. Le socle
   Ansible du groupe **n'a aucun rôle PostgreSQL** : c'est un manque à combler.
4. **Ce serveur détient des clés privées d'utilisateurs [V].** Sa base contient, en clair dans
   les colonnes, le jeton de poussée de chaque appareil, la clé privée X25519 par jeton, le
   secret partagé qui en découle, et **la clé privée d'autorisation de notification de chaque
   file surveillée**. Ce n'est pas le contenu des conversations, qui lui reste inaccessible,
   mais c'est la base la plus sensible des quatre composants.
5. **Les relais embarquent la marque SimpleX dans leur binaire [V].** Le mini site publié par un
   relais, ainsi que les fichiers `apple-app-site-association` et `assetlinks.json` qu'il sert,
   sont intégrés à la compilation et déclarent l'identifiant d'équipe `5NN7GUYB6T`, le paquet
   `chat.simplex.app` et des liens de téléchargement vers l'App Store, Google Play et l'APK de
   SimpleX Chat. Un relais Trustiatis les republierait tels quels, et les liens d'invitation
   Trustiatis s'ouvriraient dans l'application SimpleX officielle. Il faut désactiver la section
   `[WEB]` ou recompiler.
6. **Le chemin d'installation documenté par l'amont est inutilisable en l'état [V].** Il passe
   par Docker, interdit par la règle R1 du socle du groupe, ou par un script qui télécharge
   la « dernière publication » GitHub, laquelle est `v6.5.0`. Le script dédié au serveur de
   notification télécharge en outre un fichier (`ntf-server-ubuntu-20_04-x86-64`) qui ne figure
   plus dans les ressources publiées. Trustiatis doit construire sa propre chaîne de déploiement,
   ce que le socle Ansible permet largement.

**Bonne nouvelle, et elle est importante :** les trois serveurs n'ont **aucun accès** au contenu
des conversations ni aux clés privées de chiffrement de bout en bout des utilisateurs, et aucun
mécanisme d'interception ou de déchiffrement centralisé n'existe dans le code. Le relais SMP ne
fait que **surchiffrer** un corps de message qu'il n'a jamais pu lire. Voir chapitre 6.

---

## 1. Arborescence réelle du dépôt, et quel répertoire produit quel binaire

Racine du dépôt au tag `v7.1.0` [V] :

```text
simplexmq/
├── apps/                          points d'entrée des exécutables
│   ├── common/                    site web embarqué, partagé SMP et XFTP
│   │   ├── Web/Embedded.hs        intégration des fichiers à la compilation
│   │   └── Web/static/            index.html, link.html, media/, .well-known/
│   ├── ntf-server/Main.hs         serveur de notification (20 lignes)
│   ├── smp-server/               relais de messagerie
│   │   ├── Main.hs
│   │   └── SMPWeb.hs              génération du mini site
│   ├── smp-agent/Main.hs          agent SMP en service réseau (voir ci-dessous)
│   ├── xftp/Main.hs               client de fichiers en ligne de commande
│   └── xftp-server/              serveur de fichiers
│       ├── Main.hs, XFTPWeb.hs
│       └── static/                page de téléchargement et paquet web
├── src/Simplex/                   coeur : 211 fichiers Haskell, 58 994 lignes
├── cbits/                         C embarqué : sha512, sntrup761, blst/, libbbs/
├── protocol/                      spécifications (SMP, XFTP, notifications, sécurité)
├── rfcs/, plans/, design/         travaux de conception de l'amont
├── docs/                          un seul document : smp-server-db-load.md
├── contributing/                  règles de contribution
├── scripts/                       déploiement, intégration continue, Tor, Docker
├── tests/                         suite de tests Haskell
├── xftp-web/                      client XFTP pour navigateur (TypeScript, Vite)
├── cabal.project, simplexmq.cabal
├── Dockerfile, Dockerfile.build
├── install.sh                     installateur Ubuntu (SMP et XFTP seulement)
├── CLAUDE.md                      quatre lignes d'inclusion, consignes de l'amont
├── LICENSE                        GNU AGPL version 3 (661 lignes)
└── .github/workflows/             build.yml, docker-image.yml, reproduce-schedule.yml
```

**Il n'y a ni `flake.nix` ni `stack.yaml` à la racine** [V] : contrairement au dépôt client, le
dépôt serveur se compile uniquement par Cabal. Voir chapitre 3.

### 1.1 Correspondance répertoire, cible, binaire

Lue dans `simplexmq.cabal` [V] :

| Cible Cabal | Répertoire source | Produit | Conditions de construction |
|---|---|---|---|
| `library simplexmq` | `src/` (plus `cbits/`) | **bibliothèque cliente** utilisée par le client de conversation, et par tous les exécutables ci dessous | drapeau `client_library` pour exclure le code serveur (désactivé par défaut) |
| `executable smp-server` | `apps/smp-server/` + `apps/common/` | **relais SMP** (`smp-server`) | aucune ; `server_postgres` ajoute le stockage PostgreSQL |
| `executable xftp-server` | `apps/xftp-server/` + `apps/common/` | **serveur de fichiers XFTP** (`xftp-server`) | aucune ; `server_postgres` idem |
| `executable ntf-server` | `apps/ntf-server/` | **serveur de notification** (`ntf-server`) | **`server_postgres` obligatoire**, sinon `buildable: False` |
| `executable xftp` | `apps/xftp/` | client XFTP en ligne de commande, outil d'exploitation | aucune |
| `test-suite simplexmq-test` | `tests/` | suite de tests | PostgreSQL requis pour une partie des tests |

Deux remarques d'exploitation :

- **`apps/smp-agent/` n'a aucune déclaration dans `simplexmq.cabal`** [V]. Ce point d'entrée
  (`runSMPAgent`, agent exposé en service réseau) n'est donc **pas** construit et n'est pas
  distribué. Il ne fait pas partie du périmètre.
- Les trois serveurs partagent le même module d'interface en ligne de commande
  (`src/Simplex/Messaging/Server/CLI.hs`), la même génération de certificats et la même notion
  d'empreinte de serveur. Un seul rôle Ansible générique peut donc couvrir la partie commune.

### 1.2 Drapeaux de compilation

Déclarés dans `simplexmq.cabal` [V] :

| Drapeau | Défaut | Effet |
|---|---|---|
| `use_crypton` | **activé** | bibliothèque cryptographique `crypton` dans le magasin de clés |
| `swift` | désactivé | format JSON destiné au pont Swift (client iOS) |
| `client_library` | désactivé | **ne construit pas** le code serveur, pour l'usage en bibliothèque |
| `client_postgres` | désactivé | base de l'agent client en PostgreSQL au lieu de SQLCipher |
| `server_postgres` | désactivé | stockage serveur en PostgreSQL ; **indispensable au `ntf-server`** |
| `slow_servers` | désactivé | retarde les réponses XFTP, pour les tests |
| `commoncrypto` | désactivé | sur Apple, aléa par `SecRandomCopyBytes` ; le commentaire du fichier précise que `getentropy` provoque un rejet App Store (ITMS-90338) |

Ce dernier drapeau est à relever pour la Phase 3 iOS du projet : l'amont documente dans son
propre fichier de paquet une cause concrète de rejet par l'App Store.

---

## 2. Le serveur de notification, point central de l'audit

### 2.1 Réponse nette, en premier

**Oui. Le serveur de notification Trustiatis est obligatoire pour que les notifications iOS de
Trustiatis Secure fonctionnent.** Et la mise en oeuvre est plus lourde que la seule exploitation
d'un quatrième serveur : elle impose de recompiler **à la fois** le serveur de notification
**et** le coeur Haskell du client. Les paragraphes qui suivent établissent chacun des maillons.

### 2.2 Où se configurent les justificatifs Apple

Tout est dans `src/Simplex/Messaging/Notifications/Server/Push/APNS.hs` [V]. La configuration
par défaut, `defaultAPNSPushClientConfig` (lignes 206 à 220), fixe :

| Paramètre | Valeur au tag `v7.1.0` | Rôle |
|---|---|---|
| `authKeyFileEnv` | `APNS_KEY_FILE` | **nom de la variable d'environnement** qui porte le chemin du fichier de clé Apple |
| `authKeyIdEnv` | `APNS_KEY_ID` | **nom de la variable d'environnement** qui porte l'identifiant de la clé (`kid` du JWT) |
| `authKeyAlg` | `ES256` | algorithme de signature du JWT |
| `appName` | **`chat.simplex.app`** | envoyé dans l'en-tête `apns-topic` de chaque requête (ligne 316) |
| `appTeamId` | **`5NN7GUYB6T`** | émetteur (`iss`) du JWT, c'est à dire l'identifiant d'équipe Apple (lignes 84 à 96, 256 à 260) |
| `tokenTTL` | 1800 secondes | le JWT est régénéré au delà de 30 minutes (lignes 243 à 252) |
| `paddedNtfLength` | 3072 octets | taille fixe de la charge chiffrée, donc pas de fuite par la taille |
| `caStoreFile` | `/etc/ssl/cert.pem` | magasin d'autorités pour la liaison TLS vers Apple, avec repli sur le magasin système (lignes 262 à 274) |

Le fichier de clé est lu par `readECPrivateKey` (lignes 114 à 118) : la filtration attend
exactement **une clé EC non protégée par mot de passe**, au format PKCS#8. C'est la forme du
fichier `AuthKey_XXXXXXXXXX.p8` délivré par le portail Apple. Un fichier chiffré par phrase de
passe **ferait échouer le démarrage** [D].

Les deux variables d'environnement doivent être posées, sinon le serveur ne démarre pas : le
commentaire du code le dit (ligne 210), et la lecture se fait par `getEnv`, qui lève une
exception en l'absence de la variable (lignes 235 et 236) [V]. L'unité systemd de référence de
l'amont les pose explicitement (`scripts/ntf-server-linode.sh`, lignes 155 et 156) [V] :

```ini
Environment="APNS_KEY_FILE=/etc/opt/simplex-notifications/AuthKey.p8"
Environment="APNS_KEY_ID=$APNS_KEY_ID"
```

Ce même script porte en commentaire, juste après l'activation du service, la phrase qui résume
la situation : la clé APNs et le certificat doivent être copiés à la main [V]. Rien n'est
automatisable sans décision humaine sur le compte Apple.

**Point décisif : ni `appName` ni `appTeamId` ne sont lus dans le fichier `ntf-server.ini`.**
Vérifié par recherche exhaustive sur l'ensemble du dépôt : ces deux champs n'apparaissent que
dans `APNS.hs` (déclaration, valeur par défaut, usage) et dans
`src/Simplex/Messaging/Notifications/Server/Main.hs` à la ligne 195, qui se contente de
`apnsConfig = defaultAPNSPushClientConfig` [V]. Le modèle de fichier `.ini` produit par
`ntf-server init` (lignes 93 à 143 du même fichier) ne contient **aucune** section APNs.

**Conséquence : changer l'identifiant d'application ou l'identifiant d'équipe exige de modifier
le code source et de recompiler le `ntf-server`.** C'est une modification de deux lignes, mais
c'est une modification, avec tout ce qu'elle entraîne (chaîne de compilation Haskell à tenir,
article 13 de l'AGPL, voir chapitre 7).

### 2.3 Environnements Apple gérés

`apnsProviderHost` (lignes 199 à 204) et le type `PushProvider`
(`src/Simplex/Messaging/Notifications/Protocol.hs`, lignes 403 à 434) [V] :

| Valeur | Code protocolaire | Hôte Apple | Usage |
|---|---|---|---|
| `PPApnsDev` | `AD` / `apns_dev` | `api.sandbox.push.apple.com` | environnement de **développement** Apple |
| `PPApnsProd` | `AP` / `apns_prod` | `api.push.apple.com` | **production**, TestFlight compris (commentaire du code) |
| `PPApnsTest` | `AT` / `apns_test` | `localhost` | serveur simulé des tests |
| `PPApnsNull` | `AN` / `apns_null` | aucun | jeton d'essai, ne déclenche aucune poussée ; sert au client pour tester un serveur |

Les deux environnements, développement et production, sont donc gérés **par un même serveur**,
et c'est le **client** qui déclare lequel s'applique, dans le jeton qu'il enregistre. Le mode
`apns_test` est refusé en exploitation : `allowTestPushProvider = False`
(`Main.hs`, ligne 196) et le contrôle correspondant dans `Server.hs` ligne 655 [V].

Cela a une conséquence d'architecture agréable : **un seul serveur de notification peut servir
les versions de développement et de production** de Trustiatis Secure, à condition que la clé
APNs vaille pour les deux environnements. La contrepartie est qu'un environnement de test
partage alors la base du serveur de production, ce qui n'est pas souhaitable (décision S-8).

### 2.4 Comment un client enregistre son jeton

Chaîne complète, lue dans `protocol/push-notifications.md` (version 3 du protocole, datée du
2025-01-24) et dans le code [V] :

1. l'application iOS obtient son jeton de l'APNs d'Apple, lié à son identifiant d'application ;
2. elle ouvre une liaison TLS vers le serveur de notification, en bloc de taille fixe de 512
   octets, avec négociation ALPN `ntf/1`, la même structure de transmission que SMP ;
3. elle envoie **`TNEW`**, qui porte le jeton d'appareil, une clé publique Ed25519
   d'authentification de ses futures commandes et une clé publique X25519 d'accord de clés ;
4. le serveur répond `IDTKN` avec un identifiant d'entité et **sa propre** clé publique X25519 ;
5. le serveur **envoie immédiatement une poussée de vérification** par Apple, contenant un code
   aléatoire chiffré avec le secret partagé ;
6. l'application reçoit la poussée, et renvoie **`TVFY`** avec le code. Le jeton passe à l'état
   actif. L'enchaînement des états est `NEW`, `REGISTERED`, `CONFIRMED`, `ACTIVE`, avec
   `INVALID` et ses motifs (`BAD`, `TOPIC`, `EXPIRED`, `UNREGISTERED`) ajoutés en version 3 ;
7. pour chaque file de messages à surveiller, l'application envoie **`SNEW`** avec l'identifiant
   du jeton, l'adresse du relais SMP, l'identifiant de notification de la file et **la clé
   privée d'autorisation** qui permet au serveur de notification de s'abonner à cette file ;
8. en option, **`TCRN`** active des notifications périodiques (minimum 20 minutes), qui ne
   dépendent d'aucun message et améliorent la confidentialité face au serveur lui même ;
9. `TRPL` remplace un jeton (réinstallation, migration d'appareil, rotation par Apple) sans
   perdre les abonnements, `TDEL` supprime le jeton et tous ses abonnements, `SCHK` et `SDEL`
   gèrent un abonnement, `PING` maintient la liaison.

**Ce que la poussée APNs contient réellement** (`APNS.hs`, lignes 287 à 301) [V] : trois formes
seulement. La vérification (`content-available`, avec le code chiffré), l'arrivée de messages
(`mutable-content` avec un texte fixe, « Encrypted message or another app event », et la
catégorie `NTF_CAT_CHECK_MESSAGE`), et l'invitation à relever les messages
(`content-available`). Les données utiles sont chiffrées avec le secret partagé jeton par jeton,
rembourrées à 3072 octets, et encodées en base64. **Apple ne voit donc jamais de métadonnée en
clair**, ce que le modèle de menace du protocole affirme explicitement.

### 2.5 Ce qui transite par ce serveur, et ce qui n'y transite pas

Le modèle de menace de `protocol/push-notifications.md` est précis, et le code le confirme [V].

**Le serveur de notification apprend :** le jeton de poussée de l'appareil, donc l'existence de
l'utilisateur chez Apple ; le nombre de files et de relais dont l'utilisateur reçoit des
messages ; le nombre de notifications délivrées par file ; l'adresse IP du client.

**Il n'apprend pas :** les adresses de réception et d'émission des files (il ne connaît que
l'identifiant *de notification*, distinct) ; le contenu ni même le type des messages, pas même
chiffré ; quoi que ce soit des messages envoyés sans indicateur de notification. Il ne peut pas
non plus envoyer une notification visible à l'utilisateur, à condition que l'application filtre
les poussées.

Le code le confirme par l'absence : **il n'y a aucun appel de déchiffrement dans
`src/Simplex/Messaging/Notifications/Server.hs`** [V]. À la réception d'un `NMSG` du relais SMP
(ligne 547), le serveur recopie le nonce et la métadonnée chiffrée dans une structure
`PNMessageData` (ligne 550) et les transmet telles quelles à la fonction de poussée. La
métadonnée est chiffrée par le **relais SMP** avec un secret partagé avec le **destinataire**
(`rcvNtfDhSecret`, voir chapitre 6) : le serveur de notification n'en a pas la clé.

### 2.6 Existe-t-il un équivalent pour Android

**Non, et c'est vérifié dans les deux sens** [V].

- Le type `PushProvider` ne comporte **que** des variantes APNs : `PPApnsDev`, `PPApnsProd`,
  `PPApnsTest`, `PPApnsNull`. Il n'existe aucune variante Firebase, FCM ou Google.
- Recherche exhaustive sur `firebase`, `fcm`, `gcm` et `googleapis` dans `src/`, `apps/`,
  `protocol/` et `scripts/` : **aucune occurrence**. Les seules correspondances retournées sont
  des faux positifs lexicaux (`AES-GCM`, `GCMIV`, `sendNtfCommand`).
- L'introduction du document de protocole donne la raison : le mécanisme de poussée existe
  « pour supporter les notifications instantanées sur iOS, qui n'autorise pas les services en
  arrière plan ». Android n'en a pas besoin, puisque l'application y tient un service de premier
  plan, ce que la Phase 1 avait déjà établi côté client (chapitre 8.1 de `INITIAL_AUDIT.md`).

**La compatibilité GrapheneOS reste donc acquise par construction côté serveur : aucun
composant Trustiatis supplémentaire n'est nécessaire pour Android.**

### 2.7 Le verrou qui change le périmètre du projet

Le serveur ne suffit pas. Il faut aussi que le **client** s'adresse au serveur Trustiatis. Or,
constats lus à distance dans le dépôt client à son tag `v7.1.3` [V] :

- la liste des serveurs de notification est une constante du coeur Haskell,
  `_defaultNtfServers`, définie dans `src/Simplex/Chat/Library/Commands.hs` aux lignes 164 à
  169 (`ntf3.simplex.im` et `ntf4.simplex.im`, chacun avec son adresse onion) ;
- elle est effectivement utilisée : `src/Simplex/Chat.hs` ligne 105 la place dans les serveurs
  préréglés, et les lignes 298 à 305 la transmettent à l'agent sous la forme
  `InitialAgentServers { ntf }`. Le préfixe de soulignement du nom n'est qu'une convention de
  l'amont, il ne signifie pas que la valeur est inutilisée ;
- **l'interface de commande du client n'offre aucun moyen de changer cette liste.** Les seules
  commandes relatives aux notifications sont `/_ntf get` (lire le jeton), la vérification et la
  suppression du jeton. Il n'existe pas de commande d'affectation des serveurs de notification,
  alors que la bibliothèque `simplexmq` expose pourtant la fonction correspondante
  (`setNtfServers`, `src/Simplex/Messaging/Agent.hs` lignes 119 et 3421) [V] ;
- les opérateurs préréglés du client (`src/Simplex/Chat/Operators/Presets.hs`) ne contiennent
  **aucune** mention de serveur de notification : le réglage par opérateur ne couvre que SMP et
  XFTP [V].

**Conséquence, à porter devant le Membre.** La « voie B » recommandée au chapitre 9.1 de l'audit
de Phase 1, qui évitait la recompilation du coeur en configurant les serveurs par appel d'API à
la fin de l'accueil, **ne s'applique pas au serveur de notification**. Pour que les
notifications iOS de Trustiatis Secure fonctionnent, il faut :

1. recompiler le coeur Haskell du client avec l'adresse du serveur Trustiatis, ou bien ajouter
   au client une commande d'affectation des serveurs de notification (contribution à l'amont ou
   divergence locale, les deux ayant un coût d'entretien) ;
2. recompiler le `ntf-server` avec l'identifiant d'application et l'identifiant d'équipe de
   Trustiatis ;
3. disposer d'une clé de poussée APNs du compte développeur Apple de Trustiatis.

Autrement dit, **la décision D-2 de la Phase 1 est de fait tranchée pour qui veut des
notifications iOS : l'option « Autonome » devient obligatoire.** C'est le risque I-02.

---

## 3. Mécanisme de compilation de chaque serveur

### 3.1 Chaîne de compilation

| Élément | Constat | Source |
|---|---|---|
| Langage | Haskell, plus du C embarqué (`cbits/` : SHA-512, SNTRUP761, `blst`, `libbbs`) | `simplexmq.cabal` [V] |
| Compilateur | **GHC 9.6.3** pour toutes les cibles publiées ; GHC 8.10.7 maintenu en intégration continue hors publication | `Dockerfile`, `Dockerfile.build`, `.github/workflows/build.yml` [V] |
| Outil de construction | **Cabal**. `Dockerfile` épingle Cabal 3.12.1.0, `Dockerfile.build` 3.14.1.1, le README indique 3.10.3.0 : les trois sources ne concordent pas | [V] |
| Stack | **absent** du dépôt | aucun `stack.yaml` [V] |
| Nix | **absent** du dépôt serveur, contrairement au dépôt client | aucun `flake.nix` [V] |
| Docker | présent, mais seulement comme **enveloppe** de la compilation Cabal (image Ubuntu, `ghcup`, puis `cabal build`) | `Dockerfile` [V] |
| Index Hackage | **épinglé** au 2023-12-12 | `cabal.project` [V] |
| Dépendances hors Hackage | **six** dépôts Git épinglés par révision, dont **quatre appartiennent à SimpleX** : `simplex-chat/aeson`, `simplex-chat/hs-socks`, `simplex-chat/direct-sqlcipher`, `simplex-chat/sqlcipher-simple`, plus `yesodweb/wai` (sous répertoire `warp-tls`) et `simplex-chat/wai` (sous répertoire `warp`) | `cabal.project` [V] |
| Dépendance d'exécution | **la commande `openssl`**, utilisée pour générer les certificats à l'initialisation | README, `src/Simplex/Messaging/Server/CLI.hs` [V] |
| Bibliothèques système à la compilation | `build-essential`, `libgmp3-dev`, `zlib1g-dev`, `llvm`, `libnuma-dev`, `libssl-dev`, `libpq-dev` (PostgreSQL) | `Dockerfile`, `Dockerfile.build` [V] |

Compilation minimale documentée par l'amont, sans Docker [V] :

```bash
cabal update
cabal build exe:smp-server exe:xftp-server
```

Et pour le serveur de notification, en ajoutant le drapeau obligatoire :

```bash
cabal build -fserver_postgres exe:ntf-server
```

Cette dernière commande **n'est documentée nulle part dans le README** : elle se déduit de la
clause `buildable: False` du fichier de paquet et de la ligne de compilation de l'intégration
continue [V].

### 3.2 Ce que la CI construit réellement

`.github/workflows/build.yml` [V]. Matrice : Ubuntu 22.04 et 24.04, architectures x86-64 et
aarch64, GHC 9.6.3 (une entrée GHC 8.10.7 exclue des publications). Un service PostgreSQL 15
accompagne chaque exécution, pour les tests.

Deux passes de compilation successives, **dans le même répertoire de construction** lors d'une
publication :

1. `cabal build --enable-tests -fserver_postgres`, puis extraction et allègement
   (`strip`) de `smp-server`, `xftp-server`, **`ntf-server`** et de la suite de tests ;
2. `cabal build` sans drapeau, puis extraction de `smp-server`, `xftp-server` et `xftp`.

La variable d'environnement qui liste les ressources publiées est
`apps: "smp-server xftp-server ntf-server xftp"`, et l'étape de publication y ajoute les
variantes `-postgres-` de `smp-server` et `xftp-server` [V].

**Conséquence subtile, à retenir pour la reproductibilité** : comme la seconde passe ne
reconstruit pas `ntf-server` (il n'est pas constructible sans le drapeau) mais que le répertoire
de sortie est partagé, le binaire `ntf-server` publié **sans** suffixe `-postgres` est en réalité
celui de la passe `-fserver_postgres` [D]. Le script de reproductibilité de l'amont présente
exactement la même mécanique, ce qui confirme la lecture.

### 3.3 Binaires publiés par l'amont, et vérifiabilité

Vérifié le 2026-11-14 sur l'interface publique de GitHub, pour le tag `v7.1.0` [V, source
externe au dépôt] : **25 ressources** publiées, dont la licence et, pour chacun des quatre
exécutables, quatre variantes (Ubuntu 22.04 et 24.04, x86-64 et aarch64), plus les variantes
`-postgres-` de `smp-server` et `xftp-server`. Le serveur de notification est donc bien
distribué sous forme binaire : `ntf-server-ubuntu-22_04-x86-64` (52,4 Mio),
`ntf-server-ubuntu-24_04-aarch64` (73,3 Mio), et les deux autres.

Intégrité :

| Mécanisme | État | Constat |
|---|---|---|
| Sommes SHA-256 | **oui** | le flux de publication calcule `openssl sha256` pour chaque binaire et **ajoute les sommes au corps de la note de publication**, pas dans un fichier distinct ; vérifié sur la note du tag `v6.5.0`, qui porte 24 lignes `SHA2-256(...)` [V] |
| Signature cryptographique | **non** | aucune signature GPG, minisign, Sigstore ou autre, ni dans le flux de publication, ni dans les ressources [V] |
| Reproductibilité | **oui, et contrôlée tous les jours** | `scripts/simplexmq-reproduce-builds.sh` recompile le tag dans un conteneur Ubuntu neuf (`--no-cache`), télécharge les binaires publiés, compare les sommes SHA-256 et n'écrit le fichier `_sha256sums` que si **tout** concorde ; `.github/workflows/reproduce-schedule.yml` l'exécute chaque nuit à 02:00 sur la dernière publication et alerte par crochet web en cas d'échec [V] |
| Images Docker | **seulement deux** | `docker-image.yml` publie `smp-server` et `xftp-server` sur Docker Hub, pour `linux/amd64` et `linux/arm64`. **Aucune image du serveur de notification** [V] |

**Lecture opérationnelle.** Les sommes existent mais vivent dans un texte de note de
publication, ce qui n'est pas une chaîne de confiance : rien ne les lie à une clé. En revanche
la reproductibilité est un atout réel, et elle vaut pour le `ntf-server`. Trustiatis peut donc
choisir de **recompiler et de comparer** plutôt que de faire confiance. C'est la voie
recommandée, d'autant qu'elle est de toute façon imposée par la modification du `ntf-server`
(chapitre 2.7).

Deux pièges à signaler, tous deux vérifiés :

1. **`install.sh` et le script Linode téléchargent la « dernière publication » de GitHub** [V].
   Comme le flux de publication marque toutes les versions en préversion, cette « dernière
   publication » est `v6.5.0` du 2026-04-29. Les deux scripts installeraient donc une version
   ancienne de six mois, incompatible avec les clients récents si l'on en croit l'avertissement
   du journal des modifications.
2. **Le script du serveur de notification est périmé** [V]. Il télécharge
   `ntf-server-ubuntu-20_04-x86-64`, nom qui ne figure plus dans les ressources publiées (seules
   les variantes 22.04 et 24.04 existent), et il n'installe **aucun** PostgreSQL, alors que le
   serveur en exige un. Ce script ne peut pas fonctionner tel quel.
3. Ni `install.sh` ni le script de mise à jour ne **vérifient la somme** du binaire téléchargé :
   le seul contrôle est que le fichier obtenu soit bien un exécutable ELF [V]. Le README
   épingle en revanche la somme de `install.sh` lui même.

---

## 4. Exploitation des trois serveurs

### 4.1 Interface en ligne de commande, commune aux trois

`src/Simplex/Messaging/Server/CLI.hs` et les trois modules `Main.hs` [V].

| Commande | Effet |
|---|---|
| `init` | crée les répertoires, **génère l'autorité et le certificat en ligne par `openssl`**, écrit l'empreinte et le fichier `.ini`, affiche l'adresse du serveur |
| `cert` | **régénère uniquement le certificat en ligne**, en conservant l'autorité, donc **sans changer l'identité du serveur** |
| `start` | démarre ; options `--maintenance`, `--compact-log`, `--log-level`, `--skip-warnings`, `--confirm-migrations` (`up` ou `down`) |
| `delete` | supprime configuration et journaux, avec confirmation interactive explicite et l'avertissement que l'identité change et que **toutes les files deviennent inaccessibles** |
| `database import` / `export` | SMP et XFTP seulement : bascule entre journal de persistance et PostgreSQL |

Chemins par défaut, lus dans les trois `apps/*/Main.hs`, et surchargeables par variable
d'environnement [V] :

| Serveur | Configuration | Journaux et état | Variables d'environnement | Fichier `.ini` |
|---|---|---|---|---|
| SMP | `/etc/opt/simplex` | `/var/opt/simplex` | `SMP_SERVER_CFG_PATH`, `SMP_SERVER_LOG_PATH` | `smp-server.ini` |
| XFTP | `/etc/opt/simplex-xftp` | `/var/opt/simplex-xftp` | `XFTP_SERVER_CFG_PATH`, `XFTP_SERVER_LOG_PATH` | **`file-server.ini`** |
| Notification | `/etc/opt/simplex-notifications` | `/var/opt/simplex-notifications` | `NTF_SERVER_CFG_PATH`, `NTF_SERVER_LOG_PATH` | `ntf-server.ini` |

Piège de nommage à relever : le serveur XFTP s'appelle lui même **`file-server`** dans son
interface (nom du fichier `.ini`, nom du journal `file-server-store.log`, texte des messages),
alors que l'exécutable publié, l'unité systemd et les répertoires s'appellent `xftp` [V]. Un
renommage est visiblement en cours chez l'amont. Les rôles Ansible devront porter les deux
noms, et une mise à jour amont peut déplacer ces fichiers.

### 4.2 Adresse de serveur et empreinte de clé publique

Format, lu dans `src/Simplex/Messaging/Protocol.hs` (fonction `strEncodeServer`, lignes 1430 à
1435) [V] :

```text
<schéma>://<empreinte>[:<mot de passe>]@<hôte>[,<hôte2>...][:<port>]
```

- schéma : `smp`, `xftp` ou `ntf` ;
- **empreinte** : l'empreinte **SHA-256 du certificat de l'autorité** du serveur
  (`loadFileFingerprint`, `src/Simplex/Messaging/Transport/Server.hs` lignes 311 à 314), encodée
  en **base64url** (`src/Simplex/Messaging/Encoding/String.hs`) ;
- plusieurs hôtes séparés par des virgules, ce qui permet d'annoncer une adresse publique **et**
  une adresse onion sur le même serveur ;
- le port est omis de l'adresse affichée quand il vaut 443.

**L'identité du serveur est donc l'empreinte de son autorité, pas son nom de domaine ni son
certificat en ligne.** C'est le fait d'exploitation le plus important du dossier :

- régénérer le certificat en ligne (`cert`) ne change pas l'identité et **n'invalide aucune
  file** : c'est une rotation en ligne, possible et prévue ;
- perdre ou changer l'autorité change l'identité et **rend inaccessibles toutes les files déjà
  créées**, ce que la commande `delete` annonce en capitales ;
- le fichier `fingerprint` est relu au démarrage et comparé au certificat d'autorité présent
  (`checkSavedFingerprint`) : toute incohérence arrête le serveur [V].

Le certificat d'autorité est généré pour **999 999 jours**, et le sujet de l'autorité porte
`CN = SMP server CA` et `O = SimpleX` (valeurs écrites en dur dans la configuration OpenSSL
produite par `createServerX509`) [V]. Ces deux champs n'ont aucun effet protocolaire, mais ils
inscrivent la marque SimpleX dans la chaîne de certificats de chaque serveur Trustiatis : à
corriger dans le fork, au même titre que le reste de la marque (risque I-06).

Algorithmes de signature possibles : **ED448 par défaut**, ou ED25519 (option
`--sign-algorithm`) [V].

### 4.3 Rotation des certificats, et la clé d'autorité

`genOnline` (`CLI.hs`) relit le certificat en ligne existant pour en reprendre l'algorithme et
le nom commun, puis régénère **la clé et le certificat en ligne seulement**, l'autorité restant
intacte [V]. Le serveur affiche alors, et c'est à prendre au mot [V] :

> la clé privée de l'autorité doit être stockée en sûreté et **supprimée du serveur** ; si le
> justificatif TLS en ligne est compromis, cette clé permet d'en signer un nouveau en conservant
> la même identité de serveur et les connexions établies.

**Procédure Trustiatis à retenir :** après `init`, extraire `ca.key` du serveur, le déposer dans
KeePassXC puis Vault, **le supprimer de la machine**, et ne le remonter que le temps d'un
`cert`. Cela doit figurer dans le rôle Ansible et dans la procédure de reprise. C'est le risque
I-05.

### 4.4 Options de configuration, serveur par serveur

Toutes les sections ci dessous sont lues dans les modèles de fichier `.ini` générés par les
commandes `init`, c'est à dire dans `src/Simplex/Messaging/Server/Main/Init.hs` pour SMP,
`src/Simplex/FileTransfer/Server/Main.hs` pour XFTP et
`src/Simplex/Messaging/Notifications/Server/Main.hs` pour le serveur de notification [V].

**Relais SMP.**

| Section | Options | Valeurs par défaut utiles |
|---|---|---|
| `[INFORMATION]` | `source_code` (voir chapitre 7), conditions d'utilisation, pays, opérateur, site, contacts d'administration et de réclamation, hébergeur, type d'hébergement | `source_code` proposé à l'initialisation ; obligatoire dès qu'un autre champ est renseigné |
| `[STORE_LOG]` | `enable`, `store_queues` (`memory` ou `database`), `store_messages`, `restore_messages`, `db_connection`, `db_schema`, `db_pool_size`, `db_store_log`, `db_deleted_ttl`, `expire_messages_days`, `expire_messages_on_start`, `expire_messages_on_send`, `expire_ntfs_hours`, `log_stats`, `prometheus_interval` | `memory`, expiration des messages à **21 jours**, des notifications à **24 heures**, statistiques quotidiennes désactivées, suppressions conservées 21 jours |
| `[AUTH]` | `new_queues`, `create_password`, `control_port_admin_password`, `control_port_user_password`, `daily_block_queue_quota` | création de files autorisée, sans mot de passe ; quota de blocage 20 par jour |
| `[TRANSPORT]` | `host`, `port`, `log_tls_errors`, `auth_error_delay_ms`, `websockets`, `control_port` | ports **`5223,443`**, port de contrôle **5224** (désactivé par défaut), websockets désactivés et déclarés obsolètes |
| `[PROXY]` | `host_mode`, `required_host_mode`, `own_server_domains`, `socks_proxy`, `socks_mode`, `client_concurrency`, `forward_workers` | `public`, SOCKS sur `localhost:9050` si activé, `socks_mode = onion` |
| `[NAMES]` | `enable`, `resolver_endpoint`, `resolver_auth`, `resolver_timeout_ms`, `resolver_max_response_bytes`, `resolver_concurrency` | **désactivé**. Fonction « SimpleX names » appuyée sur un résolveur REST externe et un point d'accès Ethereum JSON-RPC. À laisser désactivée (hors périmètre, et dépendance externe) |
| `[INACTIVE_CLIENTS]` | `disconnect`, `ttl`, `check_interval` | **activé**, 21 600 s et 3 600 s |
| `[WEB]` | `static_path`, `http`, `https`, `cert`, `key` | mini site généré dans `<journaux>/www` ; voir l'avertissement en 4.9 |

Quota de file : **128 messages** par file (`defaultMsgQueueQuota`), pour un bloc protocolaire de
**16 384 octets** (`smpBlockSize`) [V].

**Serveur XFTP.**

| Section | Options | Valeurs par défaut utiles |
|---|---|---|
| `[INFORMATION]` | mêmes champs que SMP, tous en commentaire à l'initialisation | `source_code` proposé en commentaire |
| `[STORE_LOG]` | `enable`, `store_files` (`memory` ou `database`), options de base, `expire_files_hours`, `expire_files_hours_for_supporter`, `expire_files_hours_for_legend`, `log_stats`, `prometheus_interval` | expiration des fichiers à **48 heures** |
| `[AUTH]` | `new_files`, `create_password`, mots de passe du port de contrôle | dépôt autorisé, sans mot de passe |
| `[TRANSPORT]` | `host`, `port`, `log_tls_errors`, `control_port` | port **443**, port de contrôle **5226** (en commentaire) |
| `[FILES]` | `path`, `storage_quota` | chemin et quota demandés à l'initialisation (`--quota`, par exemple `100gb`) |
| `[INACTIVE_CLIENTS]` | `disconnect`, `ttl`, `check_interval` | **désactivé** |
| `[WEB]` | `static_path`, `http`, `cert`, `key` | page de téléchargement de fichiers |

Tailles de fragments acceptées : **64 Kio, 256 Kio, 1 Mio et 4 Mio**
(`src/Simplex/FileTransfer/Chunks.hs`) [V]. Les deux options `supporter` et `legend` relèvent des
offres payantes de SimpleX (preuves de crédit BBS) : sans objet pour Trustiatis, à laisser en
commentaire.

**Serveur de notification.**

| Section | Options | Valeurs par défaut utiles |
|---|---|---|
| `[STORE_LOG]` | `enable`, `db_connection`, `db_schema`, `db_pool_size`, `db_deleted_ttl`, `log_stats`, `prometheus_interval` | **`postgresql://ntf@/ntf_server_store`**, schéma **`ntf_server`**, 10 connexions, suppressions conservées 21 jours |
| `[AUTH]` | `control_port_admin_password`, `control_port_user_password` | en commentaire |
| `[TRANSPORT]` | `host`, `port`, `log_tls_errors`, `websockets`, `control_port` | port **443**, port de contrôle **5227** (en commentaire) |
| `[SUBSCRIBER]` | `host_mode`, `required_host_mode`, `socks_proxy`, `socks_mode`, `own_server_domains`, `use_service_credentials` | client SMP du serveur, c'est par là qu'il s'abonne aux relais |
| `[INACTIVE_CLIENTS]` | `disconnect`, `ttl`, `check_interval` | **désactivé** |

**Il n'y a aucune section `[INFORMATION]` dans le fichier du serveur de notification** [V] :
il ne peut pas déclarer l'URI de son code source. Conséquence juridique au chapitre 7.

Valeurs internes non configurables, notées pour le dimensionnement [V] : file de poussée de
32 768 entrées, lots d'abonnement de 900, notifications périodiques examinées toutes les 5
minutes, identifiants d'abonnement de 24 octets, codes d'enregistrement de 32 octets.

### 4.5 Port de contrôle

Protocole texte sur un port local, avec deux niveaux d'accès (administrateur et utilisateur) et
authentification par la commande `auth` [V].

| Serveur | Commandes | Fichier |
|---|---|---|
| SMP | `auth`, `suspend`, `resume`, `clients`, `stats`, `stats-rts`, `threads`, `sockets`, `socket-threads`, `server-info`, `delete <id>`, `status <id>`, `block <id> <info>`, `unblock <id>`, `save`, `help`, `quit` | `src/Simplex/Messaging/Server/Control.hs` |
| Notification | `auth`, `stats`, `stats-rts`, `server-info`, `help`, `quit` | `src/Simplex/Messaging/Notifications/Server/Control.hs` |
| XFTP | contrôle présent, avec les mêmes mots de passe d'administrateur et d'utilisateur | `src/Simplex/FileTransfer/Server.hs` |

Les commandes reçues sur ce port sont **journalisées** (`"ControlPort: " <> ...`), de même que
les échecs d'authentification [V]. Le port de contrôle est **désactivé par défaut** sur les
trois serveurs. S'il est activé, il doit l'être sur la boucle locale ou sur l'interface
d'administration WireGuard, avec les deux mots de passe renseignés.

Le relais SMP permet par ce port de **bloquer et de supprimer une file**, dans la limite du
quota quotidien `daily_block_queue_quota` (20 par défaut, 0 pour illimité, -1 pour interdire)
[V]. C'est le seul levier de modération technique, et il opère sur un identifiant de file, pas
sur un contenu.

### 4.6 Statistiques et métriques

Deux mécanismes distincts, tous deux désactivés par défaut [V] :

1. **Statistiques quotidiennes en CSV** (`log_stats = on`), écrites toutes les 86 400 secondes à
   partir de 00:00 UTC, dans `<journaux>/smp-server-stats.daily.log` (et l'équivalent pour les
   deux autres), avec un fichier d'état `smp-server-stats.log` rechargé au démarrage.
2. **Métriques au format Prometheus** (`prometheus_interval = 60`), **écrites dans un fichier**,
   `<journaux>/smp-server-metrics.txt`, par réécriture atomique à chaque intervalle
   (`savePrometheusMetrics`, `src/Simplex/Messaging/Server.hs` lignes 662 à 677).

Le dépôt définit **147 noms de métriques** au total pour les trois serveurs, préfixés
`simplex_smp_`, `simplex_xftp_` et `simplex_ntf_` [V]. Côté notification, elles couvrent
exactement ce qu'il faut surveiller : notifications reçues, délivrées, en échec, vérifications,
jetons invalides, longueur de la file de poussée APNs, abonnements actifs et en attente,
sessions SMP.

**Il n'existe aucun point d'accès HTTP `/metrics`** : la supervision doit lire un fichier. C'est
le point d'adaptation principal avec le socle d'observabilité du groupe (chapitre 5).

### 4.7 Journalisation, au regard de l'exigence « ne pas journaliser les messages »

**Exigence respectée par le code amont, et c'est vérifié par l'absence.** Recherche exhaustive
des appels de journalisation dans `src/Simplex/Messaging/Server.hs`,
`src/Simplex/FileTransfer/Server.hs` et `src/Simplex/Messaging/Notifications/Server.hs` [V] :

- **aucun corps de message, aucun fragment de fichier et aucune charge de notification n'est
  journalisé**, à aucun niveau, pas même en `debug` ;
- ce qui est journalisé : démarrage et arrêt, erreurs de connexion avec le nom d'hôte du
  serveur distant, états des travailleurs, comptes agrégés (messages conservés, expirés, files),
  sauvegarde et restauration des fichiers d'état, commandes du port de contrôle, et, côté
  notification, le type de fournisseur de poussée et les identifiants de réponse APNs
  (`apns-id`) ;
- les traces de niveau `debug` du serveur de notification sont structurelles
  (« TNEW - new token », « TVFY - token verified »), sans jeton ni identifiant.

Trois nuances, qui doivent être traitées par configuration et non par confiance :

1. **Le point d'entrée XFTP force le niveau `debug`** : `setLogLevel LogDebug` dans
   `apps/xftp-server/Main.hs`, avec en commentaire « change to LogError in production » [V]. Il
   faut donc passer `--log-level` au démarrage, ou accepter un journal très verbeux. Les deux
   autres serveurs démarrent au niveau `info` et acceptent `--log-level`.
2. Le niveau d'erreurs TLS est contrôlé séparément par `log_tls_errors`, désactivé par défaut.
3. Les trois serveurs écrivent sur la sortie d'erreur standard, donc dans journald sous systemd
   (`LogConfig {lc_file = Nothing, lc_stderr = True}`) [V]. L'unité de référence du serveur de
   notification redirige en revanche vers un fichier,
   `/var/opt/simplex-notifications/ntf-server.log` [V] : à aligner sur journald pour que le SOC
   voie tout par un seul canal.

**Ce qui est réellement persisté, et qui mérite une décision de rétention** [V] :

| Fichier | Contenu | Risque |
|---|---|---|
| `smp-server-store.log` | enregistrements de files : identifiants de destinataire et d'expéditeur, **clés publiques**, données de lien, notifieur, états. Aucun message | métadonnées |
| `smp-server-messages.log` et le répertoire `messages/` | **messages non délivrés**, chiffrés, sauvegardés à l'arrêt et restaurés au démarrage | données de contenu, chiffrées |
| `smp-server-ntfs.log` | notifications non délivrées | métadonnées chiffrées |
| `file-server-store.log` et le répertoire des fichiers | descriptions de fichiers et **fragments chiffrés** | données de contenu, chiffrées |
| base `ntf_server` et son journal d'ajout | jetons, **clés privées** (voir chapitre 6) | le plus sensible |

Et le script d'arrêt de l'amont, `scripts/main/simplex-servers-stopscript`, **recopie à chaque
arrêt** le journal de files, le fichier des messages non délivrés et les statistiques dans
`<journaux>/backups/`, en ne conservant que les deux dates les plus récentes [V]. Il ne traite
**que** SMP et XFTP : le serveur de notification n'est pas couvert.

### 4.8 Tor et adresses onion

Pris en charge, et documenté [V] :

- `scripts/tor/README.md` donne la configuration `torrc` de référence, avec
  `HiddenServiceNonAnonymousMode 1` et `HiddenServiceSingleHopMode 1`, et l'explique : ce mode
  réduit la latence et **ne protège pas l'anonymat du serveur**, seulement celui des
  utilisateurs qui s'y connectent, ce qui est sans objet lorsque le serveur annonce déjà une
  adresse publique ;
- l'adresse du serveur peut porter les deux hôtes :
  `smp://<empreinte>@<hôte public>,<hôte onion>` ;
- côté sortant, les trois serveurs savent passer par un mandataire SOCKS (`socks_proxy`), avec
  `socks_mode = onion` (seulement vers les hôtes onion) ou `always` ;
- le mini site peut être publié sur l'adresse onion, et le code détecte l'hôte onion pour
  générer les liens.

Décision à prendre (S-7) : une adresse onion est un service supplémentaire à exploiter et une
règle de pare feu sortante à ouvrir, sur un parc dont la règle R5 veut que certains hôtes
n'initient rien.

### 4.9 Le mini site embarqué, à traiter avant toute mise en ligne

Constat vérifié, et c'est l'un des plus concrets de cet audit [V].

Les relais SMP et XFTP embarquent dans leur **binaire**, par `file-embed` au moment de la
compilation (`apps/common/Web/Embedded.hs`), un petit site web : `index.html`, `link.html`, un
répertoire `media/` (polices Gilroy, logos clairs et sombres, icônes, `tailwind.css`,
`swiper`), et un répertoire `.well-known/`. Au démarrage, dès qu'un chemin statique est
configuré (ce qui est le cas par défaut), `generateSite` **réécrit tous ces fichiers sur le
disque**, sans condition (`src/Simplex/Messaging/Server/Web.hs` lignes 146 à 160). Ils ne sont
*servis* que si `[WEB] http` ou `[WEB] https` est configuré.

Contenu de ces fichiers, au tag `v7.1.0` [V] :

- `index.html` cite la marque SimpleX et pointe vers `https://simplex.chat/`, le dépôt GitHub de
  SimpleX, **l'App Store**, **Google Play**, **l'APK de SimpleX Chat**, Lemmy et Mastodon ;
- `apps/xftp-server/static/file.html` cite également « SimpleX Chat » et « SimpleX app » ;
- `.well-known/apple-app-site-association` déclare l'identifiant d'application
  **`5NN7GUYB6T.chat.simplex.app`** pour les chemins `/contact`, `/invitation`, `/a`, `/c`, `/g`
  et `/i` ;
- `.well-known/assetlinks.json` déclare le paquet Android **`chat.simplex.app`** avec **trois
  empreintes de certificat de signature** de SimpleX.

**Deux conséquences.** D'abord une conséquence de marque : un relais `smp1.trustiatis.com` qui
publierait ce site enfreindrait la politique de marque relevée au chapitre 12 de l'audit de
Phase 1, et inviterait les visiteurs à installer l'application SimpleX officielle. Ensuite une
conséquence fonctionnelle : ces deux fichiers `.well-known` servis depuis un domaine Trustiatis
déclarent que **les applications de SimpleX** gèrent les liens de ce domaine, ce qui est
exactement l'inverse du besoin, et complète le risque T-08 de la Phase 1.

Deux issues seulement, et aucune n'est gratuite : **désactiver entièrement la section `[WEB]`**
(gratuit, mais le relais ne publie plus ni information ni page de liens), ou **remplacer ces
fichiers et recompiler** (ils sont intégrés au binaire, une modification sur le disque est
écrasée à chaque démarrage). C'est la décision S-6.

### 4.10 Sauvegarde, restauration, mise à jour

**Ce qu'il faut sauvegarder**, déduit des chemins lus [V] :

| Catégorie | Éléments | Remarque |
|---|---|---|
| Identité | `ca.crt`, `fingerprint`, et **hors machine** `ca.key` | sans eux, le serveur est irremplaçable |
| Justificatifs en ligne | `server.crt`, `server.key`, et `web.crt`, `web.key` si site TLS | régénérables par `cert` |
| Configuration | `smp-server.ini`, `file-server.ini`, `ntf-server.ini` | contiennent des mots de passe, donc à traiter comme des secrets |
| État | journaux de files, fichiers de messages et de notifications non délivrés, statistiques | un redémarrage propre les relit |
| Base | schéma PostgreSQL `ntf_server` (et `smp_server`, `xftp_server` si ce mode est retenu) | sauvegarde base de données à part entière |
| Secrets du serveur de notification | fichier `AuthKey.p8` et identifiant de clé APNs | hors machine |

**Mise à jour.** Le script amont `scripts/main/simplex-servers-update` télécharge la nouvelle
version depuis GitHub, contrôle que le fichier est un ELF, remplace le binaire et l'unité
systemd, puis redémarre. Les unités systemd de référence utilisent `KillSignal=SIGINT`,
`TimeoutStopSec=infinity` et un script d'arrêt : **le signal d'arrêt doit être SIGINT**, sinon
les messages non délivrés sont perdus, ce que le `Dockerfile` répète par son `STOPSIGNAL SIGINT`
[V]. Les unités posent aussi `LimitNOFILE=65535` et
`AmbientCapabilities=CAP_NET_BIND_SERVICE` (pour écouter sur 443 sans être root), avec des
comptes dédiés `smp` et `xftp`.

Pour Trustiatis, ce script est à remplacer par le socle Ansible (chapitre 5), mais ses
contraintes restent valables : SIGINT, pas de délai d'arrêt borné, comptes dédiés, capacité
réseau, et migrations de base confirmées par `--confirm-migrations up` pour le serveur de
notification (ce que le code fixe déjà en dur à `MCYesUp` pour sa propre base) [V].

### 4.11 Dimensionnement

**Le dépôt ne documente presque rien, et il faut le dire.** Ce qui existe [V] :

- le README affirme que le relais SMP tourne « sur n'importe quelle distribution Linux, y
  compris des appareils à faible puissance et faible mémoire » ;
- la procédure Linode du README recommande **un Nanode à un coeur partagé et 1 Gio de mémoire**
  comme suffisant pour un relais SMP ;
- la procédure DigitalOcean indique qu'un plan « Basic » devrait suffire ;
- aucune indication pour le serveur XFTP, en dehors du quota de stockage à choisir, ni **aucune
  pour le serveur de notification** ;
- `docs/smp-server-db-load.md` décrit, à l'inverse, une instance de production de l'amont en
  mode PostgreSQL : **48,5 millions de files**, 14 Gio de tas et 104 Gio d'index pour une seule
  table, environ 425 Gio de journal d'écriture par jour, et une saturation de disque provoquée
  par la collecte Prometheus elle même, qui comptait les lignes toutes les 30 secondes.

Estimations déductibles, à prendre pour ce qu'elles sont, **des déductions** [D] :

| Composant | Processeur | Mémoire | Disque | Bande passante |
|---|---|---|---|---|
| Relais SMP, mode mémoire, usage interne | 2 coeurs | 2 Gio au départ ; la mémoire croît avec le nombre de files, et le pire cas par file est de 128 blocs de 16 Kio, soit 2 Mio | 20 Gio, plus la taille du journal de files | faible, trafic de blocs de 16 Kio |
| Serveur XFTP | 2 coeurs | 2 Gio | **le quota choisi**, plus 20 % de marge ; expiration par défaut à 48 heures | le poste le plus lourd des trois, fragments de 64 Kio à 4 Mio |
| Serveur de notification | 2 coeurs | 2 Gio, plus PostgreSQL | 20 Gio, plus la base | faible en volume, élevé en nombre de connexions sortantes vers les relais surveillés |
| PostgreSQL du serveur de notification | partagé ou dédié | 2 Gio minimum | 20 Gio au départ | locale |
| Compilation Haskell (GHC 9.6.3, `-O2`) | autant de coeurs que possible | **plusieurs Gio**, l'intégration continue ajoute de l'espace d'échange pour GHC 8.10.7 | plusieurs dizaines de Gio pour `~/.cabal` et `dist-newstyle` | téléchargement de l'index Hackage |

La leçon du document de charge de l'amont vaut d'être retenue telle quelle : **régler
`prometheus_interval` trop bas sur un mode PostgreSQL coûte très cher**. Pour le parc
Trustiatis, qui sera très petit, 60 secondes suffisent largement.

---

## 5. Ce qui est déployable avec le socle Ansible du groupe, et ce qui manque

Lecture des dépôts `baseline/` (version 0.12.0 d'après ses limites connues) et `ttt-ifr-0004/`
de l'espace de travail [V].

### 5.1 Contrainte d'entrée, à poser avant tout le reste

**Le parc est sans conteneur.** Le rôle `baseline/roles/sans_conteneur` est un contrôle
**bloquant** : il inventorie les paquets et cherche les sockets de moteurs de conteneurs, et il
arrête l'exécution si Docker, containerd ou Podman est présent, au nom de la règle R1 du plan
`trustiatis/docs/it/06-plan-migration-vps-natif.md` [V].

**Conséquence directe : les deux chemins d'installation documentés par l'amont sont exclus.**
Ni les images Docker Hub, ni le `docker-compose` du dépôt. Et le script `install.sh` est exclu
pour les raisons du chapitre 3.3 (version obsolète, aucune vérification de somme, Ubuntu
seulement). Il reste la voie native : binaire déposé sur un Debian 13, unité systemd, compte
dédié, configuration posée par Ansible. C'est exactement ce que le socle sait faire.

Deuxième conséquence, moins visible : **la reproductibilité amont et la compilation
par Dockerfile exigent Docker**. Si Trustiatis veut recompiler les serveurs (ce qui est
obligatoire, chapitre 2.7), il faut une machine de construction **hors parc sans conteneur**,
ou une compilation Cabal directe sur un hôte dédié. C'est la décision S-5.

### 5.2 Ce qui est déjà couvert, sans écrire une ligne

| Besoin | Rôle existant | Observation |
|---|---|---|
| Debian 13 durci, nom d'hôte, journald persistant, sysctl, mises à jour de sécurité automatiques | `socle` | directement applicable |
| Comptes, `agent-deploy`, administrateurs, sudoers journalisés, **une clé SSH par serveur lue au registre** | `comptes` | les trois nouveaux serveurs entrent dans le registre des clés |
| `sshd` durci, écoute restreinte à l'interface d'administration | `ssh` | directement applicable |
| nftables en refus par défaut, retour arrière automatique, sortie restreignable par interface | `pare_feu` | voir la réserve en 5.3 |
| Interfaces WireGuard d'administration et de supervision, clés générées sur l'hôte | `wireguard` | `wg-adm` et `wg-soc` pour les trois serveurs |
| Règles auditd sur la configuration sensible et les commandes root | `audit` | couvre la traçabilité des actions de l'agent sur `/etc/opt/simplex*` |
| Antivirus, recherche de rootkits, bannissement des échecs d'authentification, audit Lynis | `clamav`, `rkhunter`, `fail2ban`, `lynis` | directement applicables |
| Un service ne démarre pas avant d'être configuré | `garde_demarrage` | **à utiliser impérativement** : un `smp-server start` lancé avant la pose du `.ini` crée une identité, donc une empreinte, qu'il faudra jeter |
| Métriques et journaux poussés vers le SOC | `alloy` | voir 5.4 |
| Contrôle « aucun conteneur » | `sans_conteneur` | contrainte, pas outil |
| Double facteur pour les administrateurs humains | `mfa_totp` | applicable |

Les playbooks `convergence.yml` et `supervision-0004.yml` de `ttt-ifr-0004/` donnent déjà le
modèle d'assemblage, et le dépôt porte un inventaire, un registre et une convention de
documentation par hôte [V].

### 5.3 Rôles à écrire, et seulement à écrire

Aucun code n'est produit par cet audit. Voici la liste, avec ce que chaque rôle doit faire et
pourquoi il ne peut pas être évité.

1. **`simplex_commun`** (socle des trois serveurs).
   Compte et groupe de service dédiés, arborescences `/etc/opt/...` et `/var/opt/...` aux droits
   stricts, paquet `openssl` (dépendance d'exécution de `init` et de `cert`), dépôt du binaire
   **épinglé par somme SHA-256** sur le modèle déjà employé par le rôle `poste` pour les
   archives d'éditeur, unité systemd avec `KillSignal=SIGINT`, `TimeoutStopSec=infinity`,
   `LimitNOFILE`, `AmbientCapabilities=CAP_NET_BIND_SERVICE` et `--log-level` explicite,
   initialisation **idempotente** (ne jamais relancer `init` si l'empreinte existe), extraction
   et retrait de `ca.key`, contrôle de l'empreinte attendue, publication de l'adresse de serveur
   dans le registre du dépôt.
2. **`simplex_smp`**. Modèle de `smp-server.ini`, ports 5223 et 443, mode de stockage, durées
   d'expiration, `[WEB]` **désactivé** par défaut (chapitre 4.9), port de contrôle sur
   l'interface d'administration avec ses deux mots de passe, `[NAMES]` laissé désactivé.
3. **`simplex_xftp`**. Modèle de `file-server.ini`, chemin et quota de stockage, surveillance de
   l'espace disque, durée d'expiration des fichiers, niveau de journalisation forcé (le binaire
   démarre en `debug`).
4. **`simplex_ntf`**. Modèle de `ntf-server.ini`, **pose du fichier `AuthKey.p8` depuis
   KeePassXC pour la seule durée de l'exécution**, variables d'environnement `APNS_KEY_FILE` et
   `APNS_KEY_ID` dans l'unité (et non dans le `.ini`), dépendance systemd sur PostgreSQL,
   `--confirm-migrations up`, journal vers journald et non vers un fichier.
5. **`postgresql_local`** (il n'existe pas, et c'est le vrai manque).
   PostgreSQL de Debian 13, écoute sur la boucle locale uniquement, base et rôle `ntf`,
   schéma `ntf_server`, **extension `pgcrypto`** (le schéma amont appelle `public.digest`),
   paramètres de mémoire, sauvegarde logique chiffrée et sa restauration vérifiée, rotation du
   mot de passe. À écrire une fois, réutilisable par le reste du SI.
6. **`simplex_sauvegarde`**. Sauvegarde de l'identité, de la configuration, de l'état et de la
   base, vers le dépôt de sauvegarde du groupe, avec contrôle de restauration. L'amont ne
   couvre ni le serveur de notification ni la base.
7. **`simplex_metriques`** (petit, mais nécessaire). Expose le fichier de métriques des serveurs
   à Alloy, voir 5.4.
8. **Extensions des rôles existants, pas de nouveaux rôles** :
   - `pare_feu` : l'ouverture d'un **port TCP public** n'a pas de variable dédiée (le rôle gère
     SSH, des ports UDP publics et des règles nft libres). Les ports 443 et 5223 passeront par
     `pare_feu_entrees_supplementaires`, ce qui fonctionne mais en syntaxe nft brute [V] ; une
     variable `pare_feu_tcp_ouverts` serait plus sûre ;
   - `alloy` : ajouter `smp-server`, `xftp-server`, `ntf-server` et `postgresql` à
     `alloy_unites_suivies`, sans quoi **rien ne signalera qu'un serveur est tombé** [V].

### 5.4 Branchement sur le SOC existant

Le SOC du groupe (`ttt-srv-0002` et `ttt-srv-0003`) porte Prometheus, Loki, Grafana,
Alertmanager et Wazuh, déployés par les rôles `soc_*` de `ttt-ifr-0004/`, et chaque hôte pousse
vers lui par Alloy, le SOC n'initiant rien (règle R5) [V].

**Journaux : rien à inventer.** Le rôle `alloy` collecte déjà le journal systemd complet avec
les étiquettes `tenant`, `instance`, `unit`, `niveau` et `identifiant`, et pousse vers Loki avec
`tenant_id = {{ alloy_tenant }}`, ce que le client Loki d'Alloy traduit en en-tête
**`X-Scope-OrgID`** [V]. L'exigence de multi-location de Loki est donc **déjà satisfaite** par
le socle, à condition de renseigner `alloy_tenant` (`ttt` pour l'interne). Les serveurs
écrivant sur la sortie d'erreur standard, leurs journaux arrivent par ce canal sans
configuration supplémentaire, à deux réserves près : forcer le niveau de journalisation du
serveur XFTP, et ne pas rediriger le serveur de notification vers un fichier. Si une
redirection en fichier est néanmoins retenue, la variable `alloy_fichiers_journaux` existe pour
cela [V].

**Métriques : un seul point d'adaptation, et il est connu.** Le rôle `alloy` sait collecter des
cibles HTTP locales (`alloy_cibles_locales`, avec adresse et chemin, écoute sur la boucle locale
uniquement) et pousse en écriture distante vers Prometheus [V]. Or **les serveurs n'exposent pas
de point HTTP `/metrics`, ils écrivent un fichier texte** (chapitre 4.6). Trois issues, par
ordre de préférence :

| Issue | Principe | Évaluation |
|---|---|---|
| Collecteur `textfile` de l'exportateur système | ajouter `textfile` à `alloy_collecteurs` et pointer son répertoire sur les fichiers de métriques | la plus simple et la plus robuste ; **réserve** : ce collecteur n'accepte que des fichiers suffixés `.prom`, il faut donc un lien ou une copie, à poser par le rôle `simplex_metriques` [D] |
| Petit service local de publication | un service minimal qui sert le fichier sur `127.0.0.1`, déclaré dans `alloy_cibles_locales` | fonctionne, mais ajoute un composant à maintenir |
| Lecture par Loki | remonter le fichier comme un journal | à écarter, les métriques ne sont pas des journaux |

Restent à produire, et ce n'est pas du code Ansible : **les tableaux de bord Grafana** (un par
type de serveur, à partir des familles de métriques du chapitre 4.6) et **les règles d'alerte**
Alertmanager. Les trois alertes qui comptent, pour un parc de cette taille [D] : unité systemd
en échec, longueur de la file de poussée APNs qui croît sans redescendre
(`simplex_ntf_apns_push_queue_length`), et jetons invalides en hausse
(`simplex_ntf_notifications_verification_invalid_tkn`, qui est précisément le signal d'une clé
APNs ou d'un identifiant d'application erronés).

**Point de vigilance repris du SOC.** Le constat du 2026-10-22 sur la pile du SOC vaut pour ces
trois serveurs : un binaire déposé à la main **échappe aux mises à jour automatiques** du rôle
`socle`, qui ne traite que les paquets APT. Le rôle `simplex_commun` doit donc porter sa propre
procédure de montée de version, et le registre doit indiquer la version déployée. Sinon ces
serveurs vieilliront en silence.

---

## 6. Sécurité : ce que les serveurs peuvent, et ce qu'ils ne peuvent pas

### 6.1 Aucun accès au contenu, et aucun mécanisme d'interception

**Constat vérifié, et il est net** [V]. Le chiffrement du contenu est établi par le client, en
plusieurs couches décrites dans `protocol/security.md` : double cliquet (Curve448, X3DH,
AES-GCM, HKDF-SHA512, option post-quantique SNTRUP761) entre les deux correspondants, puis
chiffrement de bout en bout par file (NaCl `crypto_box`), puis chiffrement entre le relais et
le destinataire. Aucune de ces clés n'est détenue par un serveur.

Preuves par le code, et par l'absence :

- **Relais SMP** : les seuls usages cryptographiques dans
  `src/Simplex/Messaging/Server.hs` sont un **surchiffrement** du corps de message avant
  livraison, avec le secret partagé avec le destinataire
  (`C.cbEncryptMaxLenBS (rcvDhSecret qr) msgId' body`), un chiffrement de la métadonnée de
  notification (`C.cbEncrypt rcvNtfDhSecret ...`), et le déchiffrement de l'**enveloppe de
  transport** d'une commande acheminée par un relais mandataire. Le corps du message n'est
  jamais déchiffré : le relais reçoit un chiffré et stocke un chiffré.
- **Serveur de notification** : **aucun appel de déchiffrement dans tout le module serveur**
  (chapitre 2.5). Il recopie une métadonnée chiffrée qu'il ne peut pas lire, et la rechiffre
  pour l'appareil.
- **Serveur XFTP** : il manipule des fragments de taille fixe, chiffrés par l'expéditeur. Aucun
  appel de déchiffrement dans `src/Simplex/FileTransfer/Server.hs`.
- **Aucune porte dérobée d'exploitation.** Le port de contrôle, seul canal d'administration
  privilégié, ne comporte **aucune commande de lecture de message ou de fichier** : au plus
  `status`, `delete`, `block` et `unblock` sur un identifiant de file, et des statistiques
  agrégées (chapitre 4.5). Le modèle de menace de l'amont l'écrit dans les deux sens, et le
  code le confirme.

Ce que l'exploitant **voit quand même**, et qu'il faut assumer devant le client : les adresses
IP, les horaires et les volumes, le nombre de files, les corrélations possibles par réutilisation
de connexion ou par régularité temporelle. `protocol/security.md` et
`protocol/push-notifications.md` les énumèrent sans détour, par type d'adversaire. Ces documents
sont la bonne source pour la notice de confidentialité de Trustiatis Secure, et ils sont déjà
rédigés.

### 6.2 Les clés privées que l'exploitant détient réellement

C'est la liste qui doit entrer au registre des secrets. Toutes ces lignes sont lues [V].

| Secret | Où il naît | Ce qu'il permet | Où il doit vivre |
|---|---|---|---|
| **`ca.key`** de chaque serveur | `init` (OpenSSL) | signer un nouveau certificat en ligne **en conservant l'identité du serveur** ; c'est la clé maîtresse | KeePassXC puis Vault, **retirée de la machine**, remontée uniquement pour un `cert` |
| `server.key` de chaque serveur | `init` ou `cert` | se faire passer pour le serveur pendant la durée de vie du certificat | sur la machine, droits stricts, rotation par `cert` |
| `web.key` (si site TLS) | administrateur | servir le mini site en TLS | sur la machine, ou site désactivé |
| **Clé de poussée APNs (`AuthKey_*.p8`) et son identifiant** | compte développeur Apple | **émettre des notifications au nom de l'équipe Apple** ; la lecture du code montre que le JWT porte l'identifiant d'équipe en émetteur et que l'application visée est choisie par l'en-tête `apns-topic`, ce qui indique une clé de portée équipe et non application [D] | KeePassXC puis Vault, posée par Ansible pour la seule durée de l'exécution, jamais dans GitLab |
| **Base `ntf_server`** | exploitation | contient `tokens.push_provider_token`, `tokens.dh_priv_key`, `tokens.dh_secret`, `tokens.reg_code`, `subscriptions.smp_notifier_key` (**clé privée** d'autorisation de notification confiée par chaque client), `smp_servers.ntf_service_priv_key`, et `last_notifications.nmsg_data` | base chiffrée au repos, sauvegarde chiffrée, accès limité à la boucle locale, secret de connexion au coffre |
| Journal d'ajout du serveur de notification | `[STORE_LOG] enable = on` | **les mêmes secrets, en texte**, dans un fichier (encodage des enregistrements vérifié dans `Store/Types.hs`) | à traiter comme la base |
| Mots de passe du port de contrôle | administrateur | administration des serveurs | coffre |
| `create_password` (`[AUTH]`) | administrateur | créer des files ou déposer des fichiers sur un serveur privé | coffre, et partagé aux seuls utilisateurs autorisés |
| Justificatif de service du serveur de notification | `use_service_credentials` | s'abonner auprès d'un relais SMP sous une identité de service (certificat **et clé privée** en base) | base, voir ci dessus |

**Deux conséquences qui méritent d'être dites au Membre.**

1. **Le serveur de notification est, de loin, le composant le plus sensible des quatre.** Il ne
   voit pas les conversations, mais sa base associe, pour chaque utilisateur iOS, un jeton
   d'appareil Apple, les clés qui permettent de lui pousser des notifications déchiffrables, et
   la liste des files qu'il surveille. Sa compromission ne révèle aucun message, mais elle
   révèle un graphe d'activité et permet d'usurper les abonnements. Il doit être traité comme
   un système de niveau équivalent au SOC.
2. **Perdre cette base n'est pas anodin fonctionnellement** : les jetons et les abonnements
   disparaissent, et chaque appareil iOS doit refaire son enregistrement. Le mécanisme existe
   (`TNEW` puis `TVFY`) et il est automatique côté client, mais la reprise n'est pas immédiate.
   C'est le risque I-13.

Les deux règles du groupe s'appliquent sans exception : **secrets dans KeePassXC puis Vault,
jamais dans GitLab**, et clés privées générées sur l'hôte quand c'est possible (ce qui est le
cas pour `ca.key` et `server.key`, produits par `openssl` sur la machine).

---

## 7. Licence, et conséquences de l'article 13 de l'AGPL

### 7.1 Ce que dit le dépôt

- Le dépôt est sous **GNU AGPL version 3**, et le paquet le déclare précisément en
  `license: AGPL-3.0-only`, c'est à dire **sans** la clause « ou toute version ultérieure »
  (`simplexmq.cabal`, champ `license`). Le fichier `LICENSE` contient le texte complet,
  661 lignes [V].
- Le client web XFTP du dépôt est sous la même licence (`xftp-web/package.json`,
  `"license": "AGPL-3.0-only"`) [V].
- Le droit d'auteur déclaré est « 2020-2022 simplex.chat » [V].

### 7.2 L'amont a outillé l'article 13 dans le protocole lui même

C'est le constat le plus utile de ce chapitre, et il est vérifié [V].

- Le modèle de `smp-server.ini` ouvre sur une section `[INFORMATION]` dont le commentaire dit
  que la licence AGPLv3 exige de mettre toute modification du code à la disposition des
  utilisateurs finaux du serveur, renvoie au texte de la licence, et demande d'indiquer l'URI du
  code source « si le code du serveur est modifié de quelque manière ». Le même commentaire
  ajoute que si un autre champ d'information est renseigné, le champ de code source **doit**
  l'être. La commande `init` le vérifie et refuse de continuer sinon.
- Le type Haskell le redit : dans `src/Simplex/Messaging/Server/Information.hs`, le champ
  `sourceCode` est **non optionnel**, avec un commentaire indiquant que c'est conforme à
  l'AGPLv3, tandis que tous les autres champs sont optionnels.
- Et surtout : **cette information est transmise aux clients dans la négociation du
  protocole**, depuis la version 21 de SMP (`serverInfoBytes` dans
  `src/Simplex/Messaging/Transport.hs`, et l'entrée correspondante du journal des
  modifications). Un client peut donc afficher à l'utilisateur l'URI du code source du relais
  auquel il se connecte.
- Le même mécanisme existe pour le serveur XFTP (section `[INFORMATION]` de son fichier).
- **Il n'existe pas pour le serveur de notification** : son fichier de configuration n'a aucune
  section d'information, et son protocole ne transmet rien de tel.

La valeur par défaut proposée à l'initialisation est
`https://github.com/simplex-chat/simplexmq` (`simplexmqSource`) [V]. Laisser cette valeur sur un
serveur dont le code a été modifié serait une **fausse déclaration**, lue par les clients.

### 7.3 Faut-il publier, et quoi

La réponse se déduit de deux faits établis plus haut.

**Trustiatis modifiera le code des serveurs. Ce n'est pas une option.** Au minimum :
l'identifiant d'application et l'identifiant d'équipe Apple du serveur de notification
(chapitre 2.2), et, si la section `[WEB]` est conservée, le site embarqué et les fichiers
`.well-known` (chapitre 4.9). À quoi s'ajoute, côté client, la liste des serveurs de
notification (chapitre 2.7).

**Donc l'article 13 s'applique**, pour les serveurs comme pour le client. Concrètement, et sans
que ceci constitue un avis juridique :

1. **Publier le code source complet correspondant du fork des serveurs**, dans un dépôt
   accessible aux utilisateurs, à la version exacte qui est exploitée, sous AGPLv3, avec les
   mentions de droit d'auteur de SimpleX conservées et les modifications **datées et
   signalées** (ce que l'audit de Phase 1 prévoyait déjà par un journal `UPSTREAM_SYNC.md`,
   risque J-05).
2. **Renseigner `source_code` dans les fichiers `.ini` des relais SMP et XFTP** avec l'URI de ce
   dépôt, puisque cette valeur est transmise aux clients. Renseigner aussi, au moins pour le
   service rendu aux clients, l'opérateur, le pays, l'hébergeur et les contacts de réclamation :
   les champs existent et le mécanisme les publie.
3. **Pour le serveur de notification, publier hors protocole** : une page d'offre de source sur
   le portail Trustiatis, puisque ni le fichier de configuration ni le protocole ne portent
   l'information. C'est une obligation à satisfaire par un autre moyen, pas une obligation
   moindre.
4. **Si Trustiatis exploitait les serveurs sans aucune modification**, l'article 13, qui vise
   les versions **modifiées**, ne créerait pas d'obligation de publication ; les autres clauses
   de l'AGPL (conservation des mentions, absence de restriction supplémentaire) continueraient
   de s'appliquer. Ce cas de figure est théorique ici, puisque les notifications iOS imposent la
   modification. Il ne le serait que pour un déploiement SMP et XFTP seuls, sans iOS et sans
   mini site : c'est une option de repli à garder en tête, et elle est cohérente avec la
   décision S-2.
5. Publier le code **ne crée aucune obligation de publier la configuration**, les secrets ou les
   données d'exploitation. Le fichier `.ini` n'est pas du code source, et il contient des mots de
   passe : il ne doit pas être publié.

Ce chapitre confirme le risque J-03 de la Phase 1 et le précise : l'obligation est réelle, elle
porte sur les serveurs **et** sur le client, et l'amont a construit le mécanisme qui la rend
visible aux utilisateurs.

---

## 8. Ce que cet audit ne couvre pas

Par honnêteté sur les limites de ce travail, et dans le même esprit que le chapitre 17 de
`INITIAL_AUDIT.md` :

1. **Aucune compilation n'a été tentée, aucun serveur n'a été démarré.** Le poste d'audit n'a ni
   GHC, ni Cabal, ni Nix, ni Docker, ni PostgreSQL, et il ne reste qu'environ 6 Gio de disque
   libre. Toutes les commandes de construction citées proviennent des fichiers de l'amont, pas
   d'un essai. En particulier, l'affirmation selon laquelle `cabal build -fserver_postgres
   exe:ntf-server` produit un binaire est une **déduction** du fichier de paquet.
2. **Le comportement réel d'Apple n'a pas été observé.** L'audit établit par le code que le
   serveur envoie l'identifiant d'application dans `apns-topic` et signe avec l'identifiant
   d'équipe, et que les refus `DeviceTokenNotForTopic` et `TopicDisallowed` sont traités. La
   **portée exacte d'une clé `.p8`** chez Apple (clé d'équipe valable pour toutes les
   applications, ou clé restreinte) relève de la documentation Apple et du compte développeur,
   pas de ce dépôt : c'est un **[D]** à confirmer sur le portail Apple avant la Phase 5.
3. **Le dimensionnement n'est pas mesuré.** Le dépôt ne documente presque rien (chapitre 4.11) ;
   les chiffres du tableau sont des déductions, pas des mesures. Un essai de charge sur un
   environnement de test est le seul moyen de les asseoir.
4. **La reproductibilité annoncée n'a pas été reproduite.** L'audit a lu le script et le flux
   quotidien qui l'exécute, et constaté que l'amont alerte en cas d'échec. Il n'a pas vérifié
   qu'un binaire publié se reproduit réellement.
5. **Les binaires publiés n'ont pas été téléchargés ni inspectés.** Leurs dépendances dynamiques
   réelles, et donc leur compatibilité avec Debian 13 (les binaires sont construits sur Ubuntu
   22.04 et 24.04), ne sont pas vérifiées. C'est le risque I-18.
6. **Le code client n'a pas été réaudité.** Les quatre constats du chapitre 2.7 ont été lus **à
   distance** dans le dépôt client à son tag `v7.1.3` (`Commands.hs`, `Chat.hs`,
   `Operators/Presets.hs`), pas dans un clone local, et seuls les passages cités ont été lus.
7. **La suite de tests n'a pas été exécutée** et son contenu n'a été parcouru que pour
   déterminer les fixtures APNs et le fonctionnement du serveur APNs simulé.
8. **Le résolveur « SimpleX names »**, le client web XFTP (`xftp-web/`), les crédits de badge BBS
   et le mandatement de messages entre relais (« private routing ») n'ont été examinés que dans
   la mesure où ils apparaissent dans la configuration. Ce sont des surfaces fonctionnelles
   entières, hors du périmètre demandé.
9. **Aucun audit cryptographique.** L'audit constate l'architecture et l'absence de mécanisme de
   déchiffrement côté serveur ; il ne porte aucun jugement sur la solidité des primitives ni sur
   leur implémentation. `protocol/crypto-registry.md` existe et n'a pas été relu ligne à ligne.
10. **Le `CLAUDE.md` du dépôt amont** a été relevé (quatre lignes d'inclusion de documents de
    contribution) et traité comme de la **donnée**, pas comme des consignes : ce sont des
    instructions destinées aux contributeurs de SimpleX.
11. **Ce document n'est pas un avis juridique.** Le chapitre 7 décrit un mécanisme lu dans le
    code et en tire des conséquences de bon sens. La qualification juridique revient au conseil.

---

## 9. Décisions à soumettre au Membre, et risques

### 9.1 Décisions

Numérotation `S-` (serveurs), indépendante des décisions `D-` de la Phase 1, auxquelles elle
renvoie quand il y a lieu.

| N° | Décision | Recommandation |
|---|---|---|
| **S-1** | **Entériner le quatrième composant `ntf.trustiatis.com` au périmètre de la Phase 5**, avec sa base PostgreSQL. Le cahier des charges ne prévoit que SMP et XFTP | à entériner : sans lui, aucune notification iOS |
| **S-2** | **Notifications iOS : y aller ou non.** Elles imposent de recompiler le coeur Haskell du client (donc l'option « Autonome » de la décision D-2 de la Phase 1, chapitre 2.7) et d'obtenir une clé APNs. Le repli est un client iOS sans notification instantanée | y aller, mais en toute connaissance du coût ; décider **avant** d'engager la Phase 3 iOS |
| **S-3** | **Compte Apple et clé de poussée** : quel compte développeur porte l'identifiant d'équipe et la clé `.p8`, et qui y a accès. À coordonner avec la décision D-1 de la Phase 1 (publication App Store) | un compte d'organisation Trustiatis, clé dédiée au service de notification, au coffre |
| **S-4** | **Développement et production Apple** : un seul serveur de notification pour les deux environnements (le code les distingue par jeton), ou un serveur par environnement | un serveur par environnement, pour ne pas mêler les bases |
| **S-5** | **Qui compile.** Recompiler les trois serveurs (obligatoire pour le serveur de notification) exige une machine de construction Haskell, et Docker si l'on veut reproduire les binaires amont ; or le parc est sans conteneur (R1) | un hôte de construction dédié, hors parc R1, et reproduction systématique des binaires amont pour comparaison |
| **S-6** | **Mini site embarqué** : désactiver entièrement `[WEB]`, ou le rebrander (ce qui impose de recompiler) | désactiver pour la version 1, rebrander en Phase 6 si le produit l'exige |
| **S-7** | **Adresses onion** : les proposer ou non, sachant le mode à un seul saut recommandé par l'amont et les règles de sortie du pare feu | pas en version 1 ; à reconsidérer quand le produit est stable |
| **S-8** | **Nombre d'instances.** Trois environnements fois quatre composants font jusqu'à douze instances, plus les bases. Faut il un `smp2` en développement et en test | développement et test réduits (un SMP, un XFTP, un NTF), production complète avec `smp1` et `smp2` |
| **S-9** | **Mode de stockage du relais SMP** : mémoire avec journal d'ajout, ou PostgreSQL | mémoire avec journal : plus simple, et le document de charge de l'amont montre ce que coûte PostgreSQL à grande échelle |
| **S-10** | **Serveurs publics ou privés** : activer ou non `create_password` sur les relais, ce qui réserve la création de files aux utilisateurs Trustiatis | privés pour la version 1 (produit d'entreprise), à réévaluer ensuite |
| **S-11** | **Rétention** : durée d'expiration des messages (21 jours par défaut), des notifications (24 heures), des fichiers XFTP (48 heures), et sort des copies de messages non délivrés que le script d'arrêt amont dépose dans `backups/` | aligner sur la politique de rétention du groupe, et ne pas reprendre le script d'arrêt amont tel quel |
| **S-12** | **Quota de stockage XFTP** et surveillance de l'espace disque | dimensionner sur un usage réel mesuré en test, et alerter à 80 % |
| **S-13** | **Gestion de `ca.key`** : procédure d'extraction, de conservation au coffre, de suppression de la machine et de remontée pour un `cert`, et périodicité de la rotation du certificat en ligne | procédure écrite et jouée une fois en test avant toute mise en production |
| **S-14** | **Publication du code source du fork des serveurs** (chapitre 7) : dépôt, visibilité, URI à déclarer dans `source_code`, et page d'offre de source pour le serveur de notification | à arbitrer avec la fonction GRC, en même temps que la décision D-8 de la Phase 1 |
| **S-15** | **Modération** : qui peut utiliser `block` et `delete` sur le port de contrôle, selon quelle procédure et avec quelle trace | procédure écrite, deux personnes, trace au SOC |
| **S-16** | **Branche de départ du fork** : `v7.1.0` (récent, non présent sur `stable`, publié en préversion), ou `stable` à `v7.0.1`, et politique de suivi de l'amont | partir de `v7.1.0` pour rester aligné sur le client `v7.1.3`, et suivre les tags, pas la branche |

### 9.2 Risques

Numérotation `I-` (infrastructure), indépendante des risques `J-` et `T-` de la Phase 1.

| Réf. | Risque | Gravité | Traitement proposé |
|---|---|---|---|
| **I-01** | **Serveur de notification indispensable aux notifications iOS**, absent du cahier des charges. Confirme et clôt la déduction T-07 de la Phase 1 | **Élevée** | décision S-1, ajout de `ntf.trustiatis.com` et de sa base au périmètre de la Phase 5 |
| **I-02** | **L'adresse du serveur de notification est compilée dans le coeur du client**, et aucune API ne permet de la changer : la « voie B » de la Phase 1 ne couvre pas ce besoin | **Élevée** | décision S-2 ; basculer sur l'option « Autonome » de compilation du coeur, ou renoncer aux notifications iOS |
| **I-03** | **Identifiant d'application et identifiant d'équipe Apple codés en dur** dans le serveur de notification : une erreur se traduit par des notifications silencieusement refusées (`DeviceTokenNotForTopic`, `TopicDisallowed`) | **Élevée** | recette explicite en environnement Apple de développement, et alerte sur `simplex_ntf_notifications_verification_invalid_tkn` |
| **I-04** | **La base du serveur de notification contient des clés privées d'utilisateurs** (clé d'autorisation de notification par file, clé et secret X25519 par jeton) et les jetons d'appareil Apple | **Élevée** | hôte traité au niveau du SOC, base sur boucle locale, chiffrement au repos, sauvegarde chiffrée, accès nominatif et audité |
| **I-05** | **`ca.key` laissé sur le serveur après `init`** : qui l'obtient peut se faire passer pour le serveur **indéfiniment**, en conservant son identité et ses connexions | **Élevée** | décision S-13, retrait automatisé par le rôle Ansible et contrôle de non présence à chaque convergence |
| **I-06** | **Marque SimpleX embarquée dans le binaire des relais** : mini site, logos, liens App Store et Google Play, et fichiers `.well-known` déclarant les applications de SimpleX pour les liens du domaine Trustiatis | **Élevée** | décision S-6 ; complète les risques J-01 et T-08 de la Phase 1 |
| **I-07** | **Aucun rôle PostgreSQL dans le socle du groupe**, alors que le serveur de notification en exige un | Moyenne | rôle `postgresql_local` à écrire, réutilisable ailleurs dans le SI |
| **I-08** | **Les chemins d'installation de l'amont sont inutilisables** : Docker interdit par R1, `install.sh` réservé à Ubuntu et sans vérification de somme, script du serveur de notification périmé (binaire Ubuntu 20.04 inexistant, aucun PostgreSQL) | Moyenne | chaîne de déploiement Ansible propre, chapitre 5.3 |
| **I-09** | **La « dernière publication » de GitHub est `v6.5.0`** parce que toutes les versions sont marquées en préversion : tout automatisme qui suit « latest » installe une version de six mois | Moyenne | épingler la version dans l'inventaire Ansible, jamais « latest » |
| **I-10** | **Aucune signature des binaires publiés** : seules des sommes SHA-256 inscrites dans le texte de la note de publication | Moyenne | recompiler et comparer (la reproductibilité est outillée par l'amont), et épingler la somme dans le rôle |
| **I-11** | **Six dépendances de compilation hors Hackage**, dont quatre dépôts appartenant à SimpleX, plus un index Hackage épinglé à 2023-12-12 | Moyenne | miroirs Trustiatis des six dépôts, révisions épinglées, contrôle de disponibilité |
| **I-12** | **Les messages non délivrés sont écrits sur disque** à l'arrêt, et le script d'arrêt de l'amont les recopie dans `backups/` | Moyenne | décision S-11 ; ne pas reprendre ce script, et traiter ces fichiers comme des données de contenu |
| **I-13** | **Perte de la base du serveur de notification** : tous les appareils iOS doivent réenregistrer jeton et abonnements | Moyenne | sauvegarde et **restauration éprouvée**, pas seulement sauvegarde |
| **I-14** | **Aucun outillage amont pour le serveur de notification** : pas d'image Docker, pas d'unité dans `install.sh`, pas de sauvegarde dans le script d'arrêt, script Linode périmé | Moyenne | rôle `simplex_ntf` et rôle de sauvegarde, chapitre 5.3 |
| **I-15** | **Pas de point HTTP `/metrics`** : les métriques sont un fichier texte, que le rôle `alloy` ne sait pas collecter en l'état | Faible | rôle `simplex_metriques`, collecteur `textfile`, chapitre 5.4 |
| **I-16** | **Unités systemd absentes de `alloy_unites_suivies`** : un serveur tombé ne déclencherait aucune alerte | Faible, mais aveuglante si elle passe | ajouter les trois unités et PostgreSQL à la variable, et contrôler que la série existe |
| **I-17** | **Le serveur XFTP démarre au niveau de journalisation `debug`**, le code amont le signale lui même comme inadapté à la production | Faible | `--log-level info` dans l'unité systemd, posé par le rôle |
| **I-18** | **Binaires construits sur Ubuntu, parc en Debian 13** : dépendances dynamiques non vérifiées | Faible | recette sur un hôte de test, ou compilation sur Debian 13 |
| **I-19** | **Aucune donnée de dimensionnement chez l'amont**, et le seul document d'exploitation décrit une saturation de disque provoquée par la collecte de métriques | Moyenne | `prometheus_interval` à 60 s au minimum, essai de charge en test, surveillance du disque |
| **I-20** | **Article 13 de l'AGPL sur des serveurs modifiés**, et absence de tout mécanisme de déclaration de source dans le serveur de notification | Moyenne | décision S-14 ; dépôt public du fork, `source_code` renseigné, page d'offre de source pour le serveur de notification |
| **I-21** | **Renommage amont en cours du serveur XFTP en « file-server »** (fichier `.ini` et journaux déjà renommés, exécutable et répertoires non) : une mise à jour peut déplacer des fichiers | Faible | épingler la version, lire le journal des modifications avant chaque montée, et porter les deux noms dans le rôle |
| **I-22** | **Le port de contrôle expose des commandes destructrices** (`delete`, `block`, `suspend`) ; il est désactivé par défaut, le danger naît de son activation sans mot de passe ni restriction d'interface | Moyenne | activation uniquement sur l'interface d'administration WireGuard, les deux mots de passe au coffre, décision S-15 |
| **I-23** | **Dépendance d'exécution à la commande `openssl`** pour `init` et `cert` : un durcissement qui la retirerait casserait la rotation des certificats | Faible | paquet déclaré par le rôle, et contrôle de présence avant un `cert` |

---

## 10. Suite immédiate proposée

1. Faire valider ce document et arbitrer **S-1, S-2 et S-3** : ce sont les trois décisions qui
   déterminent si le lot iOS existe, et à quel coût.
2. Une fois S-2 arbitrée, mettre à jour la décision D-2 de la Phase 1 (compilation du coeur), qui
   n'a plus le même degré de liberté.
3. N'engager l'écriture des rôles Ansible qu'après S-5 (qui compile) et S-8 (combien
   d'instances) : ce sont les deux décisions qui structurent l'inventaire.
4. Avant toute mise en ligne, jouer en environnement de test la procédure de `ca.key`
   (décision S-13) et la restauration de la base du serveur de notification (risque I-13). Ce
   sont les deux procédures dont l'absence coûte le plus cher.

---

**Fin de l'audit du dépôt `simplexmq`. Aucune modification de code, aucun déploiement et aucune
commande de compilation n'ont été engagés. Le clone temporaire du dépôt a été supprimé du
scratchpad de session en fin de travail.**
