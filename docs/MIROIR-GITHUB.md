# Le miroir GitHub, et pourquoi il existe

Le dépôt de référence reste `gitlab.com/Trustiatis/trustiatis-secure-chat`. Le miroir
`github.com/Trustiatis/trustiatis-secure-chat` n'existe que pour une raison : construire les
paquets Windows et macOS. Un `.msi`, un `.exe` et un `.dmg` ne se fabriquent que sur leur
propre système, le poste de construction est une machine Linux, et il n'y a ni machine
Windows ni Mac. Les machines hébergées par GitHub sont gratuites et sans plafond pour un
dépôt public ; celles de GitLab ne le sont pas, ses machines Windows étant en bêta de
l'offre gratuite et ses machines macOS réservées à l'offre Premium.

Le compte, relevé le 2026-11-16 : `Trustiatis`, affiché « Trustiatis.com », identifiant
141129460. C'est un **compte utilisateur**, pas une organisation, et il porte quatre dépôts
publics sans rapport avec ce projet. Le dépôt `trustiatis-secure-chat` n'y existe pas encore.

## Ce que l'agent ne fait pas, et pourquoi

Créer le dépôt, déposer une clé et changer la visibilité d'un dépôt supposent une
authentification sur le compte. Aucune n'est présente sur ce poste, et l'agent ne se connecte
pas à un compte à la place du Membre. Les quatre premières étapes ci dessous lui reviennent
donc. L'agent prend la suite à partir de la poussée.

## 1. Créer le dépôt, actions désactivées d'abord

L'ordre compte. Le dépôt hérite de **huit** fichiers de compilation continue de l'amont, et
trois se déclenchent tout seuls sur un dépôt public. Les créer puis pousser déclencherait ces
trois là avant qu'on ait eu le temps de les neutraliser.

1. Créer `Trustiatis/trustiatis-secure-chat`, **vide**, sans README ni licence, en
   **privé** pour l'instant.
2. Settings puis Actions puis General, cocher **Disable actions**.

## 2. Déposer la clé de déploiement

Une clé propre à ce dépôt a été produite sur le poste de construction, conformément à la
décision du Membre d'une clé par serveur et par compte. Sa partie privée ne quitte pas le
poste et ne figure dans aucun dépôt.

Empreinte : `SHA256:ujqG/iSM4yp2jzIZR+jpYD7omc/D3+ena8xBtEotwwc`

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIB4wy3aMPpx6F+YGas5GP2cS8CyOiewKVwxbyea9kzM0 agent-infra@ttt-vms-0001 pour Trustiatis/trustiatis-secure-chat sur GitHub
```

À déposer dans Settings puis **Deploy keys** puis Add deploy key, en cochant **Allow write
access**. Une clé de déploiement, et non une clé du compte : elle ne vaut que pour ce dépôt,
alors qu'une clé de compte vaudrait pour les quatre autres.

## 3. La poussée, et ce qu'elle ne doit pas emporter

`tools/pousser-miroir-github.sh` pousse les neuf branches qui sont les nôtres, plus les deux
branches de référence de l'amont dont nous partons, et **rien d'autre**. Il ne pousse pas les
étiquettes de l'amont : `build.yml` se déclenche sur `push` d'une étiquette `v*`, et `v7.1.2`
en est une. C'est aussi pourquoi `git push --mirror` est à éviter ici, il les emporterait.

```bash
tools/pousser-miroir-github.sh
```

## 4. Neutraliser les huit fichiers hérités

Cette neutralisation ne passe **pas** par une modification des fichiers : les éditer créerait
un conflit à chaque report depuis l'amont. Elle passe par le réglage du dépôt, qui n'est pas
dans Git.

| Fichier | Déclencheur | Risque sur un dépôt public |
|---|---|---|
| `reproduce-schedule.yml` | `schedule`, tous les jours à 2 h | **S'exécute depuis la branche par défaut**, donc tous les jours |
| `cla.yml` | `issue_comment`, `pull_request_target` | S'exécute au premier commentaire venu |
| `build.yml` | `push` sur `master`/`stable` et étiquettes `v*`, `pull_request` | Pousser une étiquette de l'amont le déclenche |
| `packages.yml` | `pull_request` | S'exécute à la première proposition de modification |
| `web.yml` | `push` sur `master` | Sans branche `master`, inerte |
| `android-emu.yml`, `desktop-linux.yml`, `ios-sim.yml` | `workflow_dispatch` seul | Inertes tant que personne ne les lance |

Après la poussée, et **avant** de réactiver les actions : dans l'onglet Actions, ouvrir
chacun de ces huit fichiers et choisir **Disable workflow**. Puis Settings puis Actions,
repasser sur **Allow all actions**, et vérifier que seul « Paquets de bureau » est actif.

À refaire pour tout fichier que l'amont ajouterait lors d'un report.

## 5. Rendre le dépôt public

Settings puis General puis Danger Zone puis **Change repository visibility** puis Public.

C'est ce qui débloque les machines Windows et macOS gratuites, et c'est aussi ce que l'AGPL
exige avant toute diffusion de binaires : les sources d'abord, les paquets ensuite.

## 6. Lancer une construction

Onglet Actions, « Paquets de bureau », Run workflow, choisir la branche et cocher ce qu'on
veut. Trois cases : Windows, macOS Apple Silicon, macOS Intel. Rien ne se déclenche
automatiquement, et rien n'est signé ni publié : chaque travail dépose son paquet et ses
empreintes en artefact, qu'on récupère depuis la page de la construction.

En ligne de commande, une fois `gh` installé et authentifié :

```bash
gh workflow run paquets-bureau.yml --repo Trustiatis/trustiatis-secure-chat --ref feature/branding
gh run download <identifiant> --repo Trustiatis/trustiatis-secure-chat
```

## Ce que le miroir ne devient pas

Le miroir ne reçoit pas de contributions et ne sert pas de dépôt de référence. Les
propositions de modification se font sur GitLab. Si des contributions arrivent malgré tout
sur GitHub, c'est la licence AGPLv3 qui s'applique, comme pour le reste.
