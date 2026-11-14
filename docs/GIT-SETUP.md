# Mise en place du dépôt GitLab

Procédure à exécuter une seule fois. Elle part de l'état actuel du dossier
`trustiatis-secure/` (la couche Trustiatis seule, déjà sous Git) et aboutit à un fork complet
de SimpleX Chat poussé sur le GitLab de Trustiatis, avec `upstream` exploitable pour les
mises à jour de sécurité.

**Ordre imposé.** Ne pas pousser avant l'étape 3 : la greffe réécrit `main`.

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

1. Groupe `Trustiatis`, **nouveau projet vierge** nommé `trustiatis-secure`.
   - Visibilité : **privée** pour l'instant. Elle devra passer à **publique** avant toute
     distribution de binaire, pour satisfaire l'obligation de mise à disposition du code
     source de l'AGPLv3 (voir `NOTICE-AGPL.md`).
   - **Ne pas** cocher l'initialisation avec un README.
2. Relever l'URL SSH exacte du projet. La convention observée sur le dépôt frère
   `trustigateway` est `git@gitlab.com:Trustiatis/<projet>.git`, mais **ne pas la deviner** :
   la recopier depuis l'interface.
3. Noter cette URL ci dessous, pour la suite et pour les prochaines fois :

   ```text
   origin = git@gitlab.com:Trustiatis/trustiatis-secure.git     <- à confirmer
   ```

## Étape 2, vérifier l'état local

```bash
cd ~/Documents/claude/trustiatis-secure
git status --short          # doit être vide
git log --oneline           # doit montrer le commit de la couche Trustiatis
df -h .                     # il faut au moins 2 Go libres
```

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

Il affiche en fin de course la liste des fichiers de l'amont que la greffe aurait modifiés.
**Cette liste doit être vide**, hormis `.gitignore` qui est concaténé. Si elle ne l'est pas,
ne pas pousser et comprendre pourquoi.

## Étape 4, envoyer vers GitLab

```bash
git remote add origin <URL relevée à l'étape 1>
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

## Étape 6, vérifications finales

```bash
git log --oneline -3 main
git diff --stat upstream-v7.1.3 main      # ne doit montrer que la couche Trustiatis
python3 tools/verifier-marque.py          # signalements attendus : la marque de l'amont est encore partout
em=$(printf '\342\200\224'); en=$(printf '\342\200\223')
grep -rn -e "$em" -e "$en" docs/*.md *.md  # doit être vide
```

Le contrôle de marque **doit** signaler beaucoup de lignes à ce stade : c'est précisément le
travail de la Phase 2. Il devra être vert avant la première version publiée.

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
