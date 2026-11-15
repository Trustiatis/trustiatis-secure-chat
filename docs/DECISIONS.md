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

### Décisions devenues sans objet pour la version 1

`S-3` à `S-16` portent toutes sur l'exploitation de serveurs Trustiatis. Elles sont **gelées**,
pas annulées : elles redeviendront actives le jour où l'hébergement sera repris, et
l'audit des serveurs reste la référence à ce moment là.

---

## 2. Décisions ouvertes, bloquantes

| N° | Décision | Ce qu'elle bloque | Recommandation |
|---|---|---|---|
| **D-1** | **Publication App Store d'une oeuvre AGPLv3 dont Trustiatis n'est pas titulaire** (risque J-02). À soumettre à un conseil juridique | la **publication** iOS, et elle seule. Depuis P-1, le développement de `feature/ios` n'est plus bloqué | faire examiner **avant** d'engager les frais de compte éditeur et de matériel Apple, puisque c'est cette réponse qui dit si ces frais servent à quelque chose. Replis possibles : TestFlight interne, distribution d'entreprise, ou publication iOS abandonnée pendant que le reste sort |
| **P-5** | **Que fait l'interface de notification iOS, puisqu'elle ne peut pas aboutir.** Née de P-1 combinée à D-2 et D-4. Le coeur n'étant pas recompilé, il porte toujours les serveurs de notification de la communauté. Si l'utilisateur active les notifications, le parcours de l'audit des serveurs (§2.4) se déroule ainsi : l'application obtient un jeton Apple, l'**envoie** au serveur communautaire par `TNEW`, et le serveur tente une poussée de vérification vers l'identifiant d'application `chat.simplex.app` et l'équipe `5NN7GUYB6T`, qui ne seront jamais les nôtres. Apple répond `DeviceTokenNotForTopic` ou `TopicDisallowed`. Le jeton n'atteint donc **jamais** l'état `ACTIVE` et finit `INVALID`, motif `TOPIC`. Deux conséquences : l'utilisateur reçoit une erreur obscure au lieu d'une explication, et **le jeton d'appareil de nos utilisateurs a été transmis à un serveur tiers pour rien** | le contenu de `feature/ios`, pas son ouverture | **retirer les modes « instantané » et « périodique » de l'interface iOS, forcer le mode « désactivé », et afficher à la place un texte expliquant la restriction.** Laisser le parcours en place ferait envoyer des jetons d'appareil à un tiers en échange d'un message d'erreur. C'est une modification de cercle 3, la première du projet, à inscrire au registre de `BRANDING.md` et à justifier dans la demande de fusion |

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
| **P-4** | **Point de départ du fork** : `v7.1.3`, dernier tag stable au jour de l'audit, ou suivi de la branche `stable` | **partir de `v7.1.3`**, et ne suivre que les tags stables, jamais `master` |
