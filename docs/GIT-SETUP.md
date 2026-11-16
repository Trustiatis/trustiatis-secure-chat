# Mise en place du dépôt GitLab

Procédure à exécuter une seule fois. Elle part de l'état actuel du dossier
`trustiatis-secure/` (la couche Trustiatis seule, déjà sous Git) et aboutit à un fork complet
de SimpleX Chat poussé sur le GitLab de Trustiatis, avec `upstream` exploitable pour les
mises à jour de sécurité.

**Ordre imposé.** Ne pas pousser avant l'étape 3 : la greffe réécrit `main`.

> **État au 2026-11-15 : les étapes 1 à 4 sont faites.** Le projet existe
> (`git@gitlab.com:Trustiatis/trustiatis-secure-chat.git`), l'amont `v7.1.3` est greffé, et
> les onze références sont envoyées. **Restent les étapes 5 à 7**, qui se font dans
> l'interface GitLab et que l'agent ne peut pas exécuter : protection des branches et des
> tags, réglages des demandes de fusion, et la liste de rappels. Voir aussi le journal de
> greffe en bas de `UPSTREAM_SYNC.md`.

---

## Pourquoi ce découpage

Le dépôt de l'amont pèse environ **573 Mo** (taille déclarée par l'API GitHub le 2026-11-14).
Le greffer localement puis le pousser ferait transiter cette masse deux fois par votre
connexion. Deux voies sont donc possibles, et la seconde est recommandée.

| Voie | Principe | Transfert |
|---|---|---|
| **A. Greffe locale puis envoi** | `tools/greffer-upstream.sh` récupère l'amont depuis GitHub, puis on pousse tout vers GitLab | environ 573 Mo en descente **et** 573 Mo en montée |
| **B. Import côté GitLab puis greffe** | GitLab importe lui même le dépôt depuis GitHub (serveur à serveur), on le clone, on y applique la couche | environ 573 Mo en descente seulement, aucune montée du code d'amont |

La voie B est plus rapide et n'use pas la bande montante. Elle est décrite en annexe. Le
corps de cette procédure décrit la **voie A**, plus simple et entièrement reproductible depuis
ce poste.

---

## Étape 1, créer le projet GitLab

À faire par le Membre, dans l'interface GitLab de Trustiatis.

**Fait.** Le Membre a créé le projet et transmis son URL le 2026-11-15 :

```text
origin = git@gitlab.com:Trustiatis/trustiatis-secure-chat.git
```

Le projet GitLab s'appelle donc **`trustiatis-secure-chat`**, alors que le dossier local
s'appelle `trustiatis-secure/`. L'écart est volontairement conservé : renommer le dossier
casserait les exclusions du dépôt de l'espace de travail, l'entrée de `.claude/launch.json`
et les liens de `INDEX-DOCUMENTATION.md`, pour un gain nul.

Rappels qui restent valables :

- **Le projet a été créé PUBLIC**, constaté le 2026-11-15 : il se consultait sans être
  connecté. Deux conséquences immédiates : tout l'historique poussé était lisible par
  n'importe qui, adresse de commit comprise, et le fork s'affichait avant toute
  personnalisation, donc avec la marque de l'amont encore partout.
  **Décision du Membre le 2026-11-15 : repasser le projet en privé. C'est fait**, le même
  jour, dans `Settings > General > Visibility, project features, permissions` (relevé à
  l'étape 5). L'AGPLv3 n'exige la mise à disposition du code qu'au moment où un binaire est
  **distribué** (voir `NOTICE-AGPL.md`), et rien n'est compilé. **À rouvrir en public avant
  la première distribution**, et cette case figure à l'étape 7.
- Le projet doit être **vierge**, sans README d'initialisation, sinon le premier envoi sera
  refusé pour divergence.

## Étape 2, vérifier l'état local

Le dépôt local **existe déjà** : il a été initialisé le 2026-11-15, et la couche Trustiatis
y est committée. Il n'y a donc pas de `git init` à faire.

```bash
cd ~/Documents/claude/trustiatis-secure
git status --short          # doit être vide
git log --oneline           # doit montrer les commits de la couche Trustiatis
git config --local user.email   # doit répondre une adresse que vous acceptez de rendre publique
df -h .                     # il faut au moins 2 Go libres
```

Deux points relevés le 2026-11-15 sur ce poste, à traiter avant l'envoi.

**L'adresse de commit a été camouflée le 2026-11-16, avant la mise en public.** L'identité
locale avait d'abord été alignée sur celle du dépôt de l'espace de travail, une adresse
personnelle. Le dépôt devant devenir public (obligation AGPL, voir l'étape 7), elle serait
apparue dans l'historique de façon permanente et moissonnable. Décision du Membre : la
remplacer, nom compris, par l'adresse de redirection que GitLab attribue au compte
Trustiatis, qui ne reçoit rien et ne désigne aucune personne.

Trente-huit commits sur neuf branches ont été réécrits, et le document que vous lisez avec
eux, puisqu'il citait l'adresse en toutes lettres. Ce qu'il faut retenir si le cas se
represente :

- **la réécriture a été bornée à nos commits**, par `^origin/upstream-v7.1.3`. Les 6 972
  commits de l'amont gardent leurs empreintes, donc `upstream-v7.1.3` et l'étiquette
  `v7.1.3` ne bougent pas et les reports futurs restent possibles ;
- **les métadonnées ne suffisent pas**. L'adresse vivait aussi dans le contenu de ce
  document, dans chacune de ses versions. Toujours chercher dans les fichiers, pas seulement
  dans `%ae` et `%ce` ;
- **les arbres ont été comparés avant et après** sur les neuf branches : contenu identique,
  seules les identités ont changé ;
- **`main` et `develop` refusent l'envoi forcé**, c'est la protection posée à l'étape 6. Il
  faut l'autoriser le temps de l'envoi, puis la rétablir.

L'identité à utiliser désormais sur ce dépôt :

```bash
git config --local user.name  "Trustiatis.com"
git config --local user.email "23182124-Trustiatis@users.noreply.gitlab.com"
```

**L'espace disque est juste.** Le poste avait 5 927 Mo libres, soit 85 % du disque occupé.
La voie A (greffe locale) tient, mais laisse peu de marge, et les compilations ultérieures
(chaîne Gradle, et surtout la chaîne Haskell par Nix) en demanderont bien davantage. C'est un
argument de plus pour la **voie B** décrite en annexe.

## Étape 2 bis, éprouver la greffe à vide

La greffe réécrit `main`. Avant de la lancer pour de vrai, on vérifie sa mécanique contre un
amont synthétique, ce qui ne récupère rien depuis GitHub et ne touche pas ce dépôt :

```bash
bash tools/essai-greffe.sh
```

Onze contrôles doivent passer, dont les quatre qui cassent en silence : les droits
d'exécution conservés, la concaténation du `.gitignore`, la branche de référence identique à
l'amont, et l'absence de fichier d'amont modifié. Cet essai a été passé avec succès le
2026-11-15. Il n'atteste **rien** sur la greffe réelle, qui reste à faire une fois.

## Étape 3, greffer l'amont

```bash
tools/greffer-upstream.sh v7.1.3
```

Le script refuse de travailler si l'arbre n'est pas propre, si le tag est déjà là, ou si
l'espace disque est insuffisant. Il produit :

| Référence | Contenu |
|---|---|
| `refs/tags/v7.1.3` | tag de l'amont, intact |
| `upstream-v7.1.3` | branche sur l'amont **pur**, aucune modification Trustiatis |
| `main` | amont `v7.1.3` + couche Trustiatis, en un seul commit |
| `develop` | créée depuis `main` |
| `feature/branding`, `feature/desktop`, `feature/android`, `feature/ios`, `feature/distribution`, `feature/server-configuration` | créées depuis `develop` |
| `couche-trustiatis-initiale` | la couche avant greffe, conservée pour traçabilité |

Le tag et la branche que le script réclame ont été vérifiés à distance le 2026-11-15 sur
`github.com/simplex-chat/simplex-chat` : `refs/tags/v7.1.3` existe et vaut bien
`19889b1a0ee11b6071c41146b6549f46e2b5b6af`, le commit audité en Phase 1, et `refs/heads/stable`
existe aussi.

Il affiche en fin de course la liste des fichiers de l'amont que la greffe aurait modifiés.
**Cette liste doit être vide**, hormis `.gitignore` qui est concaténé. Si elle ne l'est pas,
ne pas pousser et comprendre pourquoi.

## Étape 4, envoyer vers GitLab

**Fait le 2026-11-15.** Onze références envoyées, vérifiées par `git ls-remote origin` :
`main`, `develop`, `upstream-v7.1.3`, `couche-trustiatis-initiale`, les six `feature/*` et
le tag `v7.1.3`. La référence d'amont distante vaut bien
`19889b1a0ee11b6071c41146b6549f46e2b5b6af`.

Pour mémoire, les commandes employées :

```bash
git push -u origin main
git push origin develop
git push origin 'refs/heads/feature/*'
git push origin "refs/tags/v7.1.3"
git push origin upstream-v7.1.3
```

`upstream` reste un dépôt distant **en lecture seule vers GitHub**. Ne jamais pousser vers
lui :

```bash
git remote set-url --push upstream NO_PUSH
```

## Étape 5, protéger la branche `main`

Dans GitLab, `Settings > Repository > Protected branches` :

| Branche | Autorisé à fusionner | Autorisé à pousser | Envoi forcé |
|---|---|---|---|
| `main` | mainteneurs | **personne** | **interdit** |
| `develop` | développeurs et mainteneurs | développeurs et mainteneurs | interdit |
| `upstream-*` | mainteneurs | **personne** | interdit |

Puis `Settings > Merge requests` :

- fusion autorisée seulement si le pipeline réussit ;
- fusion autorisée seulement si les fils de discussion sont résolus ;
- suppression de la branche source après fusion ;
- gabarit de description par défaut : `.gitlab/merge_request_templates/Default.md`.

Et `Settings > Repository > Protected tags` : motif `v*`, création réservée aux mainteneurs.

### Appliqué le 2026-11-15

Le Membre a ouvert une session GitLab authentifiée dans le navigateur, et les réglages
ci-dessus ont été posés un par un. Relevé par l'API du projet (`id` 88961979), après coup :

| Branche | Autorisé à fusionner | Autorisé à pousser | Envoi forcé |
|---|---|---|---|
| `main` | Maintainers | No one | désactivé |
| `develop` | Developers + Maintainers | Developers + Maintainers | désactivé |
| `upstream-*` | Maintainers | No one | désactivé |

`upstream-*` est un motif générique, pas une branche : GitLab affiche « 1 matching branch »,
`upstream-v7.1.3`, et couvrira les greffes suivantes sans nouvelle manipulation. Motif de tag
`v*`, création réservée aux Maintainers, « 1 matching tag ».

Côté `Settings > Merge requests`, l'API confirme
`only_allow_merge_if_pipeline_succeeds: true`,
`only_allow_merge_if_all_discussions_are_resolved: true` et
`remove_source_branch_after_merge: true`.

Une nuance sur le gabarit de description : le réglage « Default description template for
merge requests » de l'interface est une fonction payante, elle n'existe pas sur ce projet.
Elle est inutile ici, car GitLab applique **automatiquement** le gabarit nommé `Default.md`
à toute nouvelle demande de fusion, sur toutes les formules. Le fichier
`.gitlab/merge_request_templates/Default.md` est déjà dans le dépôt : rien à régler.

**Le projet est passé en privé**, même jour, même session. Contrôle depuis une requête
anonyme, hors session :

```text
https://gitlab.com/Trustiatis/trustiatis-secure-chat       -> 302 (redirection vers la connexion)
https://gitlab.com/Trustiatis/trustiatis-secure-chat-site  -> 302
```

Un projet public répondrait 200. L'historique n'est donc plus lisible sans compte, mais il
l'a été : ce qui a été poussé le 2026-11-15 avant ce réglage doit être considéré comme ayant
circulé, adresse de commit comprise.

Le projet du portail, `trustiatis-secure-chat-site` (`id` 88964236), a été créé privé et sa
branche `main` est protégée par défaut (Maintainers des deux côtés). Ses contrôles de fusion
restent désactivés : il n'a pas encore de chaîne d'intégration à faire réussir.

## Étape 6, vérifications finales

```bash
git log --oneline -3 main
git diff --stat upstream-v7.1.3 main      # ne doit montrer que la couche Trustiatis
python3 tools/verifier-marque.py          # signalements attendus : la marque de l'amont est encore partout
sh tools/verifier-typographie.sh
```

Le contrôle de marque **doit** signaler beaucoup de lignes à ce stade : c'est précisément le
travail de la Phase 2. Il devra être vert avant la première version publiée.

## Étape 6 bis, débloquer l'intégration continue

> **Levé le 2026-11-15.** Le Membre a fait vérifier le compte, et la chaîne tourne.
> Premier pipeline complet, commit `253afdb4` sur `develop` :
>
> | Job | Résultat | Durée |
> |---|---|---|
> | `typographie` | réussi | 34 s |
> | `coeur-intact` | réussi | 43 s |
> | `secrets` | réussi | 31 s |
> | `marque` | **échec autorisé**, par construction | 40 s |
>
> L'échec de `marque` est l'état attendu : il signale les 2 957 occurrences de la marque de
> l'amont, et il restera rouge jusqu'à la fin de la Phase 2. Il passera en bloquant
> (`allow_failure: false`) quand le compte devra être à zéro.
>
> `coeur-intact` réussi veut bien dire quelque chose : le job échoue de lui même si la
> branche `upstream-*` est introuvable, donc sa réussite prouve qu'il a trouvé la référence
> d'amont et que le diff du coeur est réellement vide.
>
> Ce qui suit est conservé parce que le même blocage se représentera à chaque nouveau projet
> GitLab du groupe, et que la cause n'est pas devinable depuis le message affiché.

**Constaté le 2026-11-15 : aucun pipeline ne peut tourner.** Les dix-neuf premiers ont tous
échoué instantanément, sans démarrer un seul job. Ce n'est pas la configuration : interrogé
par son API GraphQL, GitLab répond `yamlErrors: false` et donne la raison, mot pour mot :

```text
The pipeline failed due to the user not being verified.
```

Vérifié le même jour : ce refus intervient **avant** l'évaluation de la configuration. Après
l'ajout du bloc `workflow` qui devait supprimer les pipelines sur les branches de travail,
GitLab en a tout de même créé un par branche, en échec, avec la même raison. Tant que le
compte n'est pas vérifié, **tout** envoi produit un pipeline en échec, quoi qu'on change au
fichier de CI.

### La procédure, telle que GitLab la documente

Source : `https://docs.gitlab.com/ci/debugging/`, section « Error: Identity verification is
required in order to run CI jobs ». **Il n'y a pas d'URL directe à ouvrir**, et c'est
pourquoi `https://gitlab.com/-/identity_verification` ne mène nulle part quand on la saisit
à la main. Le point d'entrée est une **bannière d'alerte sur la page du pipeline en échec**.

1. Se connecter sur gitlab.com.
2. Ouvrir un pipeline en échec :
   `https://gitlab.com/Trustiatis/trustiatis-secure-chat/-/pipelines`, puis cliquer sur l'un
   d'eux.
3. Dans la bannière rouge en haut de page, cliquer **« Verify my account »**, en français
   « Vérifier mon compte ».
4. Suivre les étapes : numéro de téléphone avec code à usage unique, et éventuellement carte
   bancaire si GitLab estime le risque plus élevé.
5. Créer un nouveau commit ou relancer un pipeline à la main.

Sur la carte bancaire, la documentation de GitLab est explicite et vaut d'être citée :
« GitLab does not store your card details directly or make any charges. This process is not
connected to any billing information for your groups. » Elle sert de preuve d'identité,
rien d'autre.

**C'est au Membre de la faire.** L'agent ne saisit jamais de coordonnées bancaires ni de
pièce d'identité, quelle que soit la demande.

### Sur quel compte, et ce que je ne sais pas

Deux comptes GitLab interviennent, et ce sont bien **deux comptes distincts**, pas un compte
et un groupe.

| Compte | Rôle | Identifiant |
|---|---|---|
| **`Trustiatis`**, affiché « Trustiatis.com » | **propriétaire de l'espace de noms** du projet, donc celui qui porte la consommation de CI. C'est un **compte utilisateur**, pas un groupe : `GET /groups/Trustiatis` répond 404 | id 23182124 |
| le **compte personnel du Membre** | le compte dont la clé SSH pousse depuis ce poste, et celui qui est enregistré comme déclencheur des pipelines | non nommé ici : ce dépôt est public |

Un fait, relevé le 2026-11-15 : dans l'espace de noms personnel du Membre, des pipelines
qu'il a déclenchés **réussissent**, le plus récent le 2026-10-27. Le même utilisateur, sur
`Trustiatis/trustiatis-secure-chat`, se voit répondre que l'utilisateur n'est pas vérifié.
La seule variable qui change est l'espace de noms.

J'en avais conclu que `Trustiatis` était le compte à vérifier. **Cette conclusion n'est pas
sûre**, et la documentation de GitLab explique pourquoi : la vérification d'identité y est
décrite comme **propre à l'utilisateur** et calculée sur un « score de risque » qui peut
évoluer dans le temps. Le contraste observé pourrait donc venir de l'espace de noms, mais
aussi d'un score de risque qui a changé entre octobre et novembre.

**La bannière tranche sans discussion.** Se connecter d'abord en tant que `Trustiatis`,
propriétaire de l'espace de noms, et ouvrir le pipeline. Si aucune bannière n'apparaît,
recommencer connecté avec le compte personnel. Celui des deux qui voit la bannière est celui
qu'il faut vérifier.

### La voie qu'il faudra prendre de toute façon : notre propre exécuteur

GitLab liste, parmi les solutions, « Use project or group runners instead of GitLab-hosted
runners ». Pour ce projet là, ce n'est pas un contournement, c'est la cible.

Trustiatis Secure finira par produire des binaires **signés** : certificat Authenticode
Windows, notarisation Apple, signature APK. Le chapitre 14 du cahier des charges impose de
protéger les clés de signature, et le chapitre 6 interdit de les mettre dans le dépôt. Or
une clé de signature exposée à un exécuteur partagé, mutualisé avec des inconnus, n'est pas
protégée, quelles que soient les précautions de GitLab. **Les compilations signées devront
tourner sur une machine du groupe**, et la question ne se pose pas qu'à la mise en
production : elle se pose dès qu'une variable de CI protégée porte un secret.

Déclarer dès maintenant un `gitlab-runner` sur une machine du groupe règle donc trois choses
d'un coup : le blocage de vérification, la dépendance aux exécuteurs de GitLab, et la
trajectoire de sécurité. À arbitrer avec le plan de refonte infra, qui sait déjà quelles
machines existent.

### En attendant, les contrôles se passent à la main

Tant que l'une ou l'autre voie n'est pas prise, les quatre contrôles doivent être passés à
la main avant chaque fusion, et la demande de fusion doit en porter la preuve :

```bash
sh tools/verifier-typographie.sh
python3 tools/verifier-marque.py
git diff --name-only upstream-v7.1.3 HEAD -- src/ scripts/ flake.nix flake.lock cabal.project
git ls-files | grep -E '\.jks$|\.p12$|\.p8$|^local\.properties$'
```

## Étape 7, ce qu'il ne faut pas oublier

- [ ] Visibilité du projet à repasser en **publique** avant la première distribution de binaire.
- [ ] Aucun secret dans le dépôt : vérifier que `.gitignore` couvre bien `*.jks`, `*.p8`,
      `local.properties` et `keystore.properties`.
- [ ] Variables de CI protégées pour les secrets de signature, jamais de fichier committé.
- [ ] `docs/UPSTREAM_SYNC.md` à renseigner dès la greffe.

---

## Annexe, voie B : import côté GitLab

1. GitLab, groupe `Trustiatis`, `New project > Import project > Repository by URL`.
   - URL : `https://github.com/simplex-chat/simplex-chat.git`
   - Nom : `trustiatis-secure`, visibilité **privée**.
   - Décocher le miroir automatique : ce dépôt doit **diverger** de l'amont, pas le suivre.
2. L'import ramène toutes les branches et les 398 tags de l'amont. Après import, supprimer
   côté GitLab les branches de travail de l'amont dont nous n'avons pas besoin, en conservant
   `stable`, `master` et les tags.
3. Cloner, puis appliquer la couche Trustiatis :

   ```bash
   git clone <URL GitLab> trustiatis-secure-gitlab
   cd trustiatis-secure-gitlab
   git checkout -b main v7.1.3
   # recopier le contenu de ~/Documents/claude/trustiatis-secure (hors .git)
   # puis committer comme le fait tools/greffer-upstream.sh
   ```

4. Reprendre à l'étape 5.

La voie B a un inconvénient : l'historique de GitLab contiendra toutes les branches de
développement de l'amont, ce qui encombre l'interface. La voie A donne un dépôt plus propre.
