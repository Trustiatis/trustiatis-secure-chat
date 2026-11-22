# Journal des décisions

Chaque décision porte un numéro stable, repris dans les messages de commit et les demandes de
fusion. **Tant qu'une décision est ouverte, le travail qu'elle conditionne ne commence pas.**

- `D-n` : décisions issues de l'audit du client, [`INITIAL_AUDIT.md`](INITIAL_AUDIT.md).
- `S-n` : décisions issues de l'audit des serveurs, [`SIMPLEXMQ_AUDIT.md`](SIMPLEXMQ_AUDIT.md).
- `P-n` : décisions nées du présent journal.

---

## 1. Décisions rendues

### 2026-11-15, décisions de cadrage de la version 1

Décidé par le Membre. Deux phrases qui changent tout le projet : « pour la V1 nous
utiliserons les serveurs de la communauté » et « on oublie aussi les notifications push iOS ».

| N° | Décision | Portée |
|---|---|---|
| **D-4** | **Serveurs : ceux de la communauté SimpleX, inchangés.** Ni voie B (configuration à l'accueil), ni voie C (opérateur Trustiatis préconfiguré) | **Aucune** modification des serveurs préconfigurés. Le risque T-01 (rupture des liens d'invitation courts) **disparaît** |
| **S-1** | **Pas de serveur de notification Trustiatis.** Le quatrième composant `ntf.trustiatis.com` n'entre pas au périmètre | aucun serveur à héberger en V1. L'obligation de l'article 13 de l'AGPL devient sans objet |
| **S-2** | **Pas de notification instantanée sur iOS.** Aucune clé de poussée Apple, aucun enregistrement de jeton | les notifications iOS restent celles de l'amont pointant vers les serveurs communautaires, c'est à dire fonctionnelles seulement si l'amont les accepte pour notre identifiant d'application, ce qui n'est **pas** le cas. Voir P-1 |
| **D-2** | **Compilation du coeur : option « Légère ».** Les bibliothèques natives sont celles publiées par l'amont, récupérées par `scripts/android/download-libs.sh` et l'équivalent desktop | **le coeur Haskell n'est pas modifié ni recompilé.** Pas de Nix, pas de machine de construction Haskell, pas de cache binaire. Le risque I-02 de l'audit des serveurs disparaît |

**Conséquence d'ensemble :** aucune ligne de `src/`, le coeur Haskell, n'est touchée. La
divergence avec l'amont reste faible et les mises à jour de sécurité restent peu coûteuses à
reporter.

*Nuance apportée le même jour par P-1 :* la version 1 n'est plus limitée aux cercles 1 et 2.
La couche Swift d'iOS doit être modifiée pour ne pas annoncer des notifications inexistantes
(P-5). Le coeur, lui, reste intact, ce qui est le point qui compte pour le coût des reports.

### 2026-11-15, iOS reste au périmètre de la version 1

Décidé par le Membre, en réponse à la recommandation inverse : « fait la version iOS aussi
même si elle sera restreinte par les notifications push ».

| N° | Décision | Portée |
|---|---|---|
| **P-1** | **iOS entre au périmètre de la version 1**, en assumant l'absence de notification instantanée. La recommandation d'écarter iOS n'est pas retenue | la branche `feature/ios` est **débloquée pour le développement**. Elle reste bloquée pour la **publication** par D-1, qui est une question juridique distincte |

Ce que cette décision implique, et qui n'était pas dans le cadrage du matin.

1. **Un Mac redevient nécessaire.** La chaîne iOS passe par Xcode, qui n'existe que sur
   macOS. D-9 est corrigée en ce sens : l'économie de matériel annoncée après le cadrage
   n'a plus lieu.
2. **Un compte Apple Developer payant devient nécessaire** dès qu'il faut installer sur un
   appareil réel au delà de la provision gratuite de 7 jours, et pour TestFlight.
3. **La version 1 n'est plus limitée aux cercles 1 et 2.** Voir P-5 : neutraliser le
   parcours de notification iOS demande de toucher la couche Swift, donc le cercle 3. C'est
   la première modification de code du projet, et elle est assumée comme telle. Le coeur
   Haskell, lui, reste intact.
4. **Le discours commercial doit porter la restriction.** Une messagerie qui ne prévient de
   rien tant qu'on ne l'ouvre pas n'est pas un détail de note de version : elle doit être
   annoncée sur le portail de téléchargement, sur la fiche de distribution et dans
   l'application. Rattaché à D-8.

### 2026-11-15, P-5 rendue : le parcours de notification iOS est neutralisé

Décidé par le Membre : « Pour P-5, retire les notifications instantanées et périodiques
d'iOS ». La recommandation est suivie telle quelle.

| N° | Décision | Portée |
|---|---|---|
| **P-5** | **Les modes « instantané » et « périodique » sont retirés de l'interface iOS.** Le mode est « désactivé », il n'est plus choisissable, et un texte explique pourquoi | `feature/ios`, première modification de cercle 3 du projet. Registre détaillé dans `BRANDING.md` chapitre 3 |

Ce que la mise en oeuvre a appris, et qui n'était pas dans l'énoncé de la décision.

1. **Le point de fuite n'était pas l'écran de réglages, c'était l'accueil.**
   `YourNetworkView` arme `instant` par défaut et appelle `apiRegisterToken` dès que
   l'utilisateur touche « Continue ». Un nouvel utilisateur n'avait donc rien à activer pour
   que son jeton d'appareil parte chez un tiers : il lui suffisait de ne rien faire.
2. **Une base importée d'une autre application rouvre le parcours.** `apiGetNtfToken` lit le
   mode dans le coeur, pas dans nos réglages, et peut donc ramener `instant`. Le verrou a
   été posé dans `registerToken`, le seul chemin qui enregistre le jeton au démarrage : il
   ne pose plus rien et ramène le mode à `.off`.
3. **Les cas `instant` et `periodic` de l'énumération sont conservés.** Ils sont échangés
   avec le coeur sous forme de chaînes (`/_ntf register ... INSTANT`). Les supprimer
   violerait l'interdit n° 3 de `CONTRIBUTING.md`. Seule la liste offerte à l'interface est
   réduite.
4. **Rien de tout cela n'est compilé.** La chaîne iOS exige Xcode, donc un Mac, que le
   groupe n'a pas encore (voir P-1, conséquence 1). Le changement est relu, pas éprouvé.

### 2026-11-15, nuit, P-7 est renversée : l'appel au soutien ne paraît pas

Décidé par le Membre après lecture de la réserve 1 ci dessous : « ne pas faire apparaître le
don de soutien, ce n'est pas possible ».

| N° | Décision | Portée |
|---|---|---|
| **P-7 bis** | **L'appel au soutien de SimpleX Chat n'apparaît pas dans Trustiatis Secure.** La décision P-7, qui le conservait, est annulée | cinq points d'entrée fermés, et un sixième réécrit |

**Ce qui est fermé, et comment.** La méthode est celle de P-5 et du risque T-02 : on neutralise
la porte, pas ses appelants, pour qu'un report de l'amont ne conflicte que sur une ligne.

| Point d'entrée | Traitement |
|---|---|
| bannière de financement de la liste des conversations, carte des nouveautés, section « Invest » des réglages | `crowdfundingAvailable()` rend faux. Les trois appelants et tout l'écran `GetStakeView` restent en place |
| entrée des badges dans les réglages | retirée. Sans badge, son intitulé est « Support SimpleX » : c'est l'appel au soutien, à l'endroit le plus vu |
| section « soutenir le projet » des réglages | retirée : elle menait à la page de contribution de l'amont, à sa fiche Google Play (`chat.simplex.app`, qui n'est pas notre application) et à son dépôt GitHub |
| canal d'actualités et canal du fondateur de l'amont | retirés : ils adressent un client de Trustiatis à un éditeur qui ne le connaît pas |
| lien vers le client en ligne de commande de l'amont, dans les réglages développeur | retiré. **Oubli de la première mise en oeuvre**, relevé en ouvrant le paquet construit : les cinq autres portes étaient fermées, celle ci s'affichait encore et menait au dépôt GitHub de l'amont |
| « nous écrire » | **réécrit** vers `support@trustiatis.com`, ce que `NOTICE-AGPL.md` prescrit pour cette catégorie |

**Ce que la décision coûte, et il faut le savoir.** Le badge était un **achat** qui rendait un
service réel : des fichiers de 2 à 5 Gio conservés de 7 à 21 jours. Fermer son entrée retire
cette capacité de l'interface. Le code des badges reste en place, et le badge d'un
correspondant continue de s'afficher dans son profil : c'est une information sur lui, pas une
sollicitation adressée à l'utilisateur. Rouvrir la seule entrée des badges, sans rouvrir le
financement, reste donc possible d'une ligne si le Membre le souhaite.

**Ce qui reste, et pourquoi.** Trois composables de l'amont ne sont plus appelés par personne
et restent dans le fichier : `ContributeItem`, `StarOnGithubItem`, `RateAppItem`,
`InstallTerminalAppItem`. Un écran qu'aucun appelant n'atteint ne s'affiche pas, et les
laisser en place évite un conflit de report à chaque version de l'amont. **En revanche, les
liens de `OperatorView` et de `NetworkAndServers` vers le dépôt de l'amont ne sont pas
touchés** : ils pointent les conditions d'utilisation du réseau et leurs commits, qui sont
réellement publiés là. Les réécrire serait faux, et la décision D-4 retient les serveurs de
la communauté.

**Ce qui n'est pas touché.** Les chaînes restent dans les quarante-deux catalogues. Les
retirer voudrait dire toucher quarante-deux fichiers que l'amont réécrit à chaque version,
pour un gain nul : une chaîne qu'aucun écran ne lit n'est pas vue. La liste
`EDITEUR_NEUTRALISE` de `tools/verifier-marque.py` porte désormais ce nom, qui dit la vérité.

**Et la mention de non affiliation reste indispensable**, bien que P-7 soit renversée : elle
ne dépendait pas de l'appel au soutien. Elle est en place dans l'écran « À propos ».

### 2026-11-15 au soir, P-7 rendue : l'appel au soutien de l'amont est conservé

> **Annulée la nuit même par P-7 bis, ci dessus.** La section est conservée telle quelle :
> c'est la réserve 1 qu'elle portait qui a conduit le Membre à décider l'inverse, et effacer
> le raisonnement effacerait la raison de la décision suivante.

| N° | Décision | Portée |
|---|---|---|
| **P-7** | **Trustiatis Secure garde l'appel au soutien de SimpleX Chat.** Badges, bannière de financement, section « Support SimpleX Chat » des réglages, contact des développeurs, lien vers le client en ligne de commande, pictogramme de l'équipe : rien n'est retiré ni réécrit. Ma recommandation de masquer ces écrans n'est pas retenue | les six points d'entrée restent en place. La dérogation est nommée et bornée dans `EDITEUR_CONSERVE_P7` de `tools/verifier-marque.py`, et testée, plutôt que dispersée en commentaires dans quarante catalogues |

**Ce que la décision gagne.** Le badge de soutien est un achat qui rend un service à
l'utilisateur, des fichiers de 2 à 5 Gio conservés de 7 à 21 jours : le retirer aurait coûté
une capacité réelle. Et un fork qui vit du travail de l'amont et en masque l'appel au soutien
se met dans une position difficile à défendre.

**Deux réserves à porter au dossier, non bloquantes.**

1. **Une partie de cet appel n'est pas un don.** « Invest in SimpleX Chat » et « Own a part of
   SimpleX Chat » renvoient à un financement participatif **en capital** sur Wefunder, c'est à
   dire à une offre de titres, réservée aux juridictions que l'offre couvre. Relayer une offre
   de titres d'un tiers à ses clients n'est pas le même acte que relayer un lien de don, et la
   promotion financière est encadrée dans plusieurs des juridictions où Trustiatis opère. À
   soumettre à la fonction GRC avant la première version publiée. Si la réponse est de ne
   garder que le don et le badge, la modification se borne au bloc
   `if (crowdfundingAvailable())` de `views/usersettings/SettingsView.desktop.kt` et aux deux
   appels à `GetStakeBanner`.
2. **La mention de non affiliation devient indispensable.** Garder « Soutenez SimpleX » dans
   une application nommée Trustiatis Secure peut se lire comme une affiliation, que la
   politique de marque de l'amont interdit expressément. Le texte existe déjà, dans
   `NOTICE-AGPL.md`, et il doit figurer dans l'écran « À propos ». C'est la contrepartie
   technique de cette décision, pas une formalité. **Faite le 2026-11-15** : clé
   `trustiatis_non_affiliation` dans les deux catalogues, affichée par `SimpleXInfoLayout`
   dans la branche où `onboardingStage` est nul, donc dans l'écran ouvert depuis les réglages
   et pas pendant l'accueil. Il reste à la porter sur le portail de téléchargement et dans les
   descriptions de magasins, qui ne sont pas du ressort de ce dépôt.

### 2026-11-15 au soir, P-6 : la greffe revient sur v7.1.2

Décidé par le Membre, en réponse au constat fait pendant la première compilation : « Passe la
greffe sur v7.1.2, qui a tous ses paquets ».

| N° | Décision | Portée |
|---|---|---|
| **P-6** | **La base d'amont passe de `v7.1.3` à `v7.1.2`.** P-4 est révisée en conséquence | tout le dépôt. La branche `upstream-v7.1.2` porte désormais l'amont pur, `upstream-v7.1.3` est conservée comme trace de la greffe initiale |

Le constat qui l'a provoquée. L'amont publie ses versions sur GitHub avec vingt-quatre
actifs : APK, paquets Debian, AppImage, MSI, DMG, exécutables. **La version 7.1.3 n'en a que
neuf**, et aucun paquet Linux. Or la pièce native du Desktop se lie à `libsimplex.so`, que
seul un paquet Linux publié permet d'obtenir sans recompiler le coeur Haskell, ce que D-2
exclut. Sur v7.1.3, le Desktop Linux était donc une impasse.

Ce que le retour coûte, mesuré et non supposé : **trois commits, six fichiers**.

| Fichier | Ce que nous perdons |
|---|---|
| `src/Simplex/Chat/Operators.hs` | **un commentaire de quatre lignes.** Aucun code |
| `apps/ios/.../AppAPITypes.swift` | `indirect enum` sur trois énumérations de réponse, qui réduit la pile consommée au décodage |
| `apps/ios/SimpleX.xcodeproj` | un doublon de ressource retiré, et le numéro de version iOS 369 au lieu de 371 |
| `.../chatlist/ChatListView.kt` | un correctif d'interface : « What's new » montré une seule fois tant que les conditions sont en attente |
| `gradle.properties` | versions 7.1.2 et codes 393 / 173 |
| `scripts/flatpak/...metainfo.xml` | l'entrée de version 7.1.3 |

**Aucun correctif de sécurité dans le lot.** C'est le point qui rend la décision peu coûteuse.

P-5 survit au retour sans conflit : la v7.1.3 ne touchait `AppAPITypes.swift` que vers la
ligne 780, loin de la ligne 1722 que P-5 modifie.

Deux conséquences d'outillage, tirées le jour même.

1. **La CI ne devine plus la base d'amont.** `coeur-intact` prenait la branche `upstream-*`
   de plus haut numéro de version, ce qui supposait que l'on n'avance jamais que vers
   l'avant. Elle lit désormais `tools/amont-courant.txt`. Sans cela, le contrôle aurait
   comparé le dépôt à une version abandonnée et échoué sans qu'il y ait de faute.
2. `tools/greffer-upstream.sh` écrit ce fichier à chaque greffe.

**À reprendre quand l'amont publiera une version stable complète.** Le retour sur v7.1.2 est
un contournement d'un défaut de publication, pas un choix technique. La v7.1.4 existe déjà,
mais en **préversion**, ce que la règle de P-4 interdit de suivre.

### 2026-11-22, P-8 rendue : la passerelle se configure, et aucun secret permanent n'est rangé

Le Membre demande que l'adresse du TrustiGateway et son point d'entrée, « avec token ou autre
sécurité maximale », soient configurables dans l'application, et que les canaux de service
ajoutés ensuite soient préservés des personnes qui utilisent l'application sans être clientes.

**Ce qui est rangé sur l'appareil : trois valeurs, et rien d'autre.** L'hôte de la passerelle,
le port UDP de WireGuard, le point d'entrée du plan de contrôle. Pas de mot de passe, pas de
jeton permanent, pas de secret partagé.

La raison n'est pas une préférence de style. ADR-0010 du programme TrustiGateway pose que
l'identité est **générée sur l'appareil**, et ADR-0025 D6 que la clé privée n'en sort jamais et
que le champ qui la porterait **ne doit pas exister**. Un jeton permanent rangé dans les
réglages serait exactement ce champ sous un autre nom : lisible par qui tient l'appareil,
rejouable, et copiable d'un appareil à l'autre. Le jeton d'enrôlement est donc **à usage
unique** : il sert une fois, il n'est pas conservé, et l'application n'en garde que l'identifiant
d'appareil que la passerelle lui rend, qui n'est pas un secret.

**Deux refus codés dans la validation**, parce qu'une règle qu'on ne peut pas enfreindre vaut
mieux qu'une consigne :

- le point d'entrée en clair est refusé. « http:// » n'est pas accepté, quelle que soit
  l'adresse ;
- un point d'entrée **public** est refusé. ADR-0021 dit que le portail n'est jamais public ;
  l'application n'accepte donc qu'une adresse d'espace privé, ou exactement l'hôte configuré.
  Accepter une adresse publique ici apprendrait à l'exploitant qu'un portail public est une
  configuration normale.

**Le cloisonnement des non clients.** L'application ne montre les écrans de service qu'à un
appareil enrôlé, et c'est du **confort, pas une sécurité**. Ce qui arrête un non client est la
passerelle, qui n'accepte que les pairs qu'un administrateur du client a créés (ADR-0025 D5).
L'avertissement est écrit en tête de `HabilitationTrustiatis.kt` : un écran masqué n'est pas un
contrôle d'accès, et toute lecture de `estClient` qui déciderait autre chose qu'un affichage est
un défaut.

Posé dans le paquet `chat.simplex.common.trustiatis`, qui n'entre en conflit avec aucun report de
l'amont. Une seule ligne est ajoutée à un écran de l'amont, l'entrée de menu. 12 cas de test.

### Décisions devenues sans objet pour la version 1

`S-3` à `S-16` portent toutes sur l'exploitation de serveurs Trustiatis. Elles sont **gelées**,
pas annulées : elles redeviendront actives le jour où l'hébergement sera repris, et
l'audit des serveurs reste la référence à ce moment là.

---

## 2. Décisions ouvertes, bloquantes

| N° | Décision | Ce qu'elle bloque | Recommandation |
|---|---|---|---|
| **P-9** | **Comment l'application obtient une paire de clés X25519.** Le tunnel TrustiVPN en a besoin, et il n'y a aucun chemin propre aujourd'hui : le dépôt n'embarque **aucune bibliothèque de cryptographie** ; `KeyPairGenerator("XDH")` de Java n'existe sur Android qu'à partir de l'API 33, alors que `minSdk` vaut 26 ; et faire produire la clé par le coeur Haskell supposerait de le recompiler, ce que **D-2 interdit**. Les trois voies restantes sont une dépendance vérifiée (`bcprov`, ou la bibliothèque `tunnel` de WireGuard qui apporte aussi le backend), une implémentation écrite ici (**à écarter** : on n'écrit pas sa propre courbe elliptique), ou relever `minSdk` à 33 et perdre les appareils antérieurs | **tout le client TrustiVPN** : sans clé, pas de pair, donc pas de tunnel. La configuration de la passerelle (P-8) n'en dépend pas et fonctionne déjà | **ajouter `com.wireguard.android:tunnel`** pour Android, qui apporte à la fois une implémentation Curve25519 éprouvée et le backend `GoBackend` du tunnel, et traiter le Desktop séparément. C'est une dépendance tierce dans une application de sécurité : la décision est au Membre, et elle conditionne le calendrier de l'essai TrustiVPN |
| **D-1** | **Publication App Store d'une oeuvre AGPLv3 dont Trustiatis n'est pas titulaire** (risque J-02). À soumettre à un conseil juridique | la **publication** iOS, et elle seule. Depuis P-1, le développement de `feature/ios` n'est plus bloqué | faire examiner **avant** d'engager les frais de compte éditeur et de matériel Apple, puisque c'est cette réponse qui dit si ces frais servent à quelque chose. Replis possibles : TestFlight interne, distribution d'entreprise, ou publication iOS abandonnée pendant que le reste sort |

---

## 3. Décisions ouvertes, non bloquantes

| N° | Décision | Recommandation |
|---|---|---|
| **D-3** | Conserver ou remplacer les schémas d'URI `simplex:` et `simplexchat:` | **conserver** : ce sont des identifiants de protocole, pas la marque commerciale, et la version 1 utilisant les serveurs communautaires, l'interopérabilité des liens d'invitation est indispensable |
| **P-2** | Que faire des liens applicatifs vérifiés du manifeste Android, qui portent sur `simplex.chat` et 23 hôtes `smp*.simplex.im` ou `simplexonflux.com`. Ils ne se vérifieront **jamais** pour Trustiatis, faute de pouvoir publier un `assetlinks.json` sur ces domaines | **retirer `android:autoVerify` et ces hôtes**, et conserver les seuls schémas `simplex:` et `simplexchat:`. Un lien collé s'ouvrira, un lien cliqué depuis un navigateur proposera le sélecteur d'applications. Documenter ce comportement dans l'aide |
| **D-5** | Conserver `armeabi-v7a`, et produire ou non un APK universel | conserver `armeabi-v7a`, **renoncer à l'APK universel** (doublement de la taille du paquet pour aucun bénéfice) |
| **D-6** | Portée de la traduction de la marque : 40 langues, ou français et anglais d'abord | **fichier de référence et français d'abord**, les autres langues par vagues. Les chaînes non traduites affichent le texte de référence, donc déjà corrigé |
| **D-7** | Publier un dépôt de mise à jour de type F-Droid pour la distribution directe Android | **hors version 1.** Mise à jour manuelle par APK, annoncée comme telle sur le portail |
| **D-8** | Nom exact, mentions légales et texte de compatibilité au réseau SimpleX dans l'application et sur les magasins | texte proposé dans [`../NOTICE-AGPL.md`](../NOTICE-AGPL.md), à valider par la fonction GRC |
| **D-9** | Budget matériel et comptes éditeurs | **corrigée le 2026-11-15 par P-1** : iOS restant au périmètre, un **Mac redevient nécessaire** (Xcode n'existe que sur macOS) ainsi qu'un **compte Apple Developer payant**. Seule l'économie de la machine de construction Haskell subsiste (D-2). Restent aussi un appareil GrapheneOS réel, un certificat de signature de code Windows et un compte Google Play. À chiffrer **après** D-1 : si la publication iOS est impossible, le Mac et le compte Apple ne servent qu'à une distribution interne |
| **P-3** | **Rétention des binaires sur le portail de téléchargement.** Le plan gratuit de Cloudflare R2 offre 10 Go de stockage par mois, et un jeu complet d'artefacts pèse de 1,5 à 2 Go par version | **conserver les 3 dernières versions** sur le portail, les plus anciennes restant disponibles sur les versions publiées du dépôt GitLab. Voir [`DISTRIBUTION.md`](DISTRIBUTION.md) |
| **P-4** | **Point de départ du fork** : `v7.1.3`, dernier tag stable au jour de l'audit, ou suivi de la branche `stable` | **révisée le 2026-11-15 au soir, voir P-6 : la base est `v7.1.2`.** La règle de ne suivre que les tags stables, jamais `master`, reste entière |
