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

**Conséquence d'ensemble, et c'est la bonne nouvelle du cadrage :** la version 1 se limite aux
**cercles 1 et 2** de `CONTRIBUTING.md`. Aucune ligne de `src/` n'est touchée. La divergence
avec l'amont reste faible et les mises à jour de sécurité restent peu coûteuses à reporter.

### Décisions devenues sans objet pour la version 1

`S-3` à `S-16` portent toutes sur l'exploitation de serveurs Trustiatis. Elles sont **gelées**,
pas annulées : elles redeviendront actives le jour où l'hébergement sera repris, et
l'audit des serveurs reste la référence à ce moment là.

---

## 2. Décisions ouvertes, bloquantes

| N° | Décision | Ce qu'elle bloque | Recommandation |
|---|---|---|---|
| **D-1** | **Publication App Store d'une oeuvre AGPLv3 dont Trustiatis n'est pas titulaire** (risque J-02). À soumettre à un conseil juridique | **toute la branche `feature/ios`** | faire examiner avant d'engager le moindre travail iOS. Replis possibles : TestFlight interne, distribution d'entreprise, ou renoncement à iOS |
| **P-1** | **iOS sans notification : y aller ou non.** Nouvelle décision, née de S-2. Sans serveur de notification Trustiatis, un client iOS ne reçoit **aucun** message tant qu'il n'est pas ouvert par l'utilisateur. Ce n'est pas une messagerie dégradée, c'est une messagerie qui ne prévient de rien | la pertinence même du lot iOS | **écarter iOS de la version 1.** Livrer Windows, macOS, Linux, Android et GrapheneOS, où les notifications fonctionnent réellement, et rouvrir iOS quand D-1 est tranchée et qu'un serveur de notification est acceptable |

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
| **D-9** | Budget matériel et comptes éditeurs | réduit par les décisions du 2026-11-15 : **plus besoin de Mac** si iOS sort du périmètre (P-1), plus besoin de machine de construction Haskell (D-2). Restent un appareil GrapheneOS réel, un certificat de signature de code Windows et un compte Google Play |
| **P-3** | **Rétention des binaires sur le portail de téléchargement.** Le plan gratuit de Cloudflare R2 offre 10 Go de stockage par mois, et un jeu complet d'artefacts pèse de 1,5 à 2 Go par version | **conserver les 3 dernières versions** sur le portail, les plus anciennes restant disponibles sur les versions publiées du dépôt GitLab. Voir [`DISTRIBUTION.md`](DISTRIBUTION.md) |
| **P-4** | **Point de départ du fork** : `v7.1.3`, dernier tag stable au jour de l'audit, ou suivi de la branche `stable` | **partir de `v7.1.3`**, et ne suivre que les tags stables, jamais `master` |
