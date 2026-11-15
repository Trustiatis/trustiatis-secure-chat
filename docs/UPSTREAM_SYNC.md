# Synchronisation avec l'amont

L'amont publie fréquemment, parfois plusieurs fois par mois, et ses publications contiennent
des corrections de sécurité. Ce document fixe la procédure de report et tient le **journal des
versions reportées**, qui sert aussi de preuve de l'obligation AGPLv3 d'indiquer les
modifications et leur date.

| | |
|---|---|
| Dépôt d'amont | `https://github.com/simplex-chat/simplex-chat` |
| Dépôt distant local | `upstream`, en lecture seule (`--push NO_PUSH`) |
| Référence suivie | **les tags stables uniquement**, jamais `master` (décision P-4) |
| Branche par défaut de l'amont | `stable` |

## Politique de suivi

1. **Ne suivre que les tags de version** de la forme `vX.Y.Z`, sans suffixe `-beta` ni
   `-armv7a`, et seulement ceux qui sont effectivement présents sur la branche `stable`.
2. Surveiller les publications de l'amont **au moins une fois par mois**, et sans délai
   lorsqu'un avis de sécurité est publié.
3. Reporter d'abord sur `develop`, jamais directement sur `main`.
4. Un report est une demande de fusion comme une autre : revue, CI verte, contrôle de marque
   vert.
5. **Vérifier que la version visée est publiée en entier avant de s'y greffer.** Une version
   stable de l'amont porte vingt-quatre actifs sur GitHub. La `v7.1.3` n'en avait que neuf,
   sans aucun paquet Linux, ce qui a coûté un aller-retour (décision P-6). Le contrôle tient
   en une ligne :

   ```bash
   curl -s https://api.github.com/repos/simplex-chat/simplex-chat/releases/tags/vX.Y.Z \
     | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["assets"]), "actifs")'
   ```
6. **La base d'amont est écrite dans `tools/amont-courant.txt`**, jamais déduite. Le job
   `coeur-intact` de la CI lit ce fichier, et `tools/greffer-upstream.sh` l'écrit. Un report
   qui oublie de le mettre à jour fait comparer le dépôt à la mauvaise version.

## Procédure de report d'une version

> **À lire avant de pousser `upstream-vX.Y.Z`.** Le motif `upstream-*` est une branche
> protégée dont la poussée est fermée à **personne**, mainteneurs compris, et GitLab compte la
> **création** d'une branche comme une poussée. `git push` est donc refusé :
>
> ```
> remote: GitLab: You can only create protected branches using the web interface and API.
>  ! [remote rejected]     upstream-v7.1.2 -> upstream-v7.1.2 (pre-receive hook declined)
> ```
>
> Le message dit la marche à suivre, et elle est meilleure que celle écrite ici d'abord :
> **créer la branche par l'interface ou par l'API, sans jamais retirer la protection.**
> `Repository > Branches > New branch`, nom `upstream-vX.Y.Z`, créée depuis l'empreinte du
> commit d'amont, qui est déjà sur le distant puisque `develop` le contient. Ne pas retirer
> puis reposer la protection : cela ouvre une fenêtre pendant laquelle la référence qui
> prouve l'intégrité du coeur est modifiable.
>
> Cela demande une session GitLab, donc le Membre : l'agent ne s'authentifie pas à sa place.
> C'est volontairement pénible : cette référence est la preuve que le coeur de l'amont n'a
> pas été touché, et le job `coeur-intact` s'appuie sur elle.

```bash
cd ~/Documents/claude/trustiatis-secure

# 1. récupérer la nouvelle version
git fetch --no-tags upstream "refs/tags/vX.Y.Z:refs/tags/vX.Y.Z"
git log --oneline upstream-<version précédente>..vX.Y.Z | wc -l     # ampleur du report

# 2. mettre à jour la référence d'amont pur
git branch -f upstream-vX.Y.Z vX.Y.Z

# 3. préparer la branche de report
git checkout -b upstream/vX.Y.Z develop
git merge vX.Y.Z
```

### Résoudre les conflits

Les conflits se concentrent, par construction, sur les **cercles 1 et 2** de
`CONTRIBUTING.md`, c'est à dire sur les fichiers que nous avons volontairement modifiés. La
liste de référence est celle de l'audit de Phase 1, chapitre 7. En pratique :

| Fichier | Résolution par défaut |
|---|---|
| Ressources graphiques (`res/`, `moko-resources/images/`, `Assets.xcassets/`) | **garder la version Trustiatis**, sauf si l'amont ajoute une ressource nouvelle |
| `ui/theme/Theme.kt`, `Color.kt`, `Theme.swift`, `Color.swift` | fusionner : prendre la **structure** de l'amont et nos **valeurs** de couleurs |
| `moko-resources/*/strings.xml` | prendre l'amont, puis **relancer le tri de marque** sur les chaînes nouvelles ou modifiées |
| `android/build.gradle.kts`, `desktop/build.gradle.kts` | fusionner : structure de l'amont, nos identifiants et nos UUID |
| `AndroidManifest.xml` | fusionner : structure de l'amont, nos schémas et nos hôtes |
| `AppUpdater.kt` | fusionner, et **revérifier que l'URL de mise à jour reste celle de Trustiatis** |
| `src/`, `scripts/`, `flake.nix` | **prendre l'amont sans discussion** : nous ne les modifions pas (décision D-2) |
| `README.md` | **garder la version Trustiatis.** C'est le seul fichier de l'amont que la greffe écrase entièrement, et il conflictera donc à chaque report |
| `docs/README-AMONT.md` | **remplacer par le README du nouveau tag** : `git show upstream-vX.Y.Z:README.md >> docs/README-AMONT.md` après l'en tête. Il n'existe que pour garder trace de ce que la greffe a écarté |

### Contrôles obligatoires avant de fusionner un report

```bash
python3 tools/verifier-marque.py                 # doit être vert
git diff --stat upstream-vX.Y.Z HEAD             # doit ne montrer que nos fichiers connus
git diff upstream-vX.Y.Z HEAD -- src/ scripts/ flake.nix flake.lock   # DOIT être vide
grep -rn 'simplex-chat/simplex-chat' apps/multiplatform/common/src/desktopMain/   # doit être vide
```

Le troisième contrôle est le garde-fou de la décision D-2 : si un report introduit une
différence dans `src/`, `scripts/` ou `flake.nix`, c'est que quelqu'un a touché au coeur, et le
report doit être refusé.

### Finir le report

```bash
git push origin upstream/vX.Y.Z
# demande de fusion vers develop, puis vers main, puis tag Trustiatis
```

## Numérotation des versions Trustiatis

La version Trustiatis Secure **reprend la version d'amont** et y ajoute un numéro de révision
Trustiatis, pour que la correspondance soit évidente lors d'un audit de sécurité :

```text
Trustiatis Secure 7.1.3-ts1      amont v7.1.3, première livraison Trustiatis
Trustiatis Secure 7.1.3-ts2      même amont, correction de notre couche
Trustiatis Secure 7.2.0-ts1      amont v7.2.0
```

Attention : `jpackage` n'accepte que `MAJEUR.MINEUR.CORRECTIF`, et le code de
`desktop/build.gradle.kts` retire déjà tout caractère non numérique de la version. Le suffixe
`-ts1` ne doit donc apparaître que dans le nom des artefacts, les notes de version et le
portail, pas dans la version interne du paquet.

---

## Journal des reports

Tenu à jour à chaque report. Sert de preuve de l'obligation de l'AGPLv3 d'indiquer les
modifications et leur date.

| Date | Version d'amont | Version Trustiatis | Nature du report | Auteur | Demande de fusion |
|---|---|---|---|---|---|
| 2026-11-15 | `v7.1.3` | (aucune livraison) | **greffe initiale, exécutée.** Point de départ du fork. Commit d'amont `19889b1a0ee11b6071c41146b6549f46e2b5b6af`, daté du 2026-11-11. Deux fichiers de l'amont touchés, et deux seulement : `.gitignore` concaténé, et `README.md` remplacé par le nôtre (son contenu est conservé dans `README-AMONT.md`). `git diff upstream-v7.1.3 main -- src/ scripts/ flake.nix flake.lock cabal.project` est **vide** | Trustiatis | sans objet, mise en place |
| 2026-11-15 | `v7.1.2` | (aucune livraison) | **retour en arrière, décision P-6.** La publication de `v7.1.2` est complète (24 actifs), celle de `v7.1.3` ne l'est pas (9 actifs, aucun paquet Linux), ce qui rendait le Desktop Linux impossible sans recompiler le coeur. Trois commits et six fichiers annulés, **aucun correctif de sécurité** dans le lot. Commit d'amont `50a575e44867a6f1b2cba485a6a655c24b2c9aef`. P-5 reportée sans conflit | Trustiatis | directe sur `develop`, commit `319380c0f` |
