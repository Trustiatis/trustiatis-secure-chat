# Trustiatis Secure

Client de messagerie chiffrée de bout en bout, édité par **Trustiatis**, dérivé du logiciel
libre **SimpleX Chat** et compatible avec le réseau SimpleX.

> Trustiatis Secure est un produit indépendant. Il n'est ni édité, ni soutenu, ni approuvé par
> SimpleX Chat Ltd. Les noms et logos SimpleX sont des marques de SimpleX Chat Ltd et ne sont
> employés ici que pour désigner le protocole et le réseau avec lesquels ce logiciel est
> compatible, ce que la politique de marque de l'amont autorise expressément.

| | |
|---|---|
| Amont | `https://github.com/simplex-chat/simplex-chat` |
| Version d'amont de référence | `v7.1.3` |
| Licence | GNU AGPL version 3 (voir `LICENSE` après la greffe de l'amont) |
| Plateformes visées | Windows, macOS, Linux, Android, GrapheneOS, iOS |
| Statut | **Phase 2, personnalisation graphique.** Aucun binaire publié à ce jour |

## État du projet

Ce dépôt ne contient pour l'instant que la **couche Trustiatis** : documentation, référentiel
de marque, outillage et intégration continue. Le code de l'amont y est **greffé** par la
procédure décrite dans [`docs/GIT-SETUP.md`](docs/GIT-SETUP.md), afin que `upstream` reste un
dépôt distant exploitable pour les mises à jour de sécurité.

Rien n'a encore été compilé. Aucune affirmation de ce dépôt ne prétend le contraire.

## Périmètre de la version 1

Décidé par le Membre le 2026-11-15, voir [`docs/DECISIONS.md`](docs/DECISIONS.md) :

- **Serveurs : ceux de la communauté SimpleX.** Trustiatis n'héberge aucun relais en V1.
- **Pas de notification instantanée sur iOS.** Aucun serveur de notification Trustiatis.
- **Conséquence : le coeur Haskell n'est pas modifié.** Les bibliothèques natives sont celles
  publiées par l'amont. La personnalisation reste confinée aux ressources, aux identifiants et
  à un nombre réduit de fichiers d'interface.

## Organisation du dépôt

| Chemin | Contenu |
|---|---|
| `docs/` | audits, décisions, procédures de compilation, de distribution et de synchronisation |
| `branding/` | **référentiel unique** de l'identité Trustiatis Secure, source des ressources propagées |
| `tools/` | outillage : contrôle de marque, greffe de l'amont, propagation des ressources |
| `.gitlab/` | gabarits de demandes de fusion et de tickets |
| le reste | code de l'amont après greffe (`apps/`, `src/`, `scripts/`, `flake.nix`, ...) |

## Documents à lire avant de contribuer

1. [`docs/INITIAL_AUDIT.md`](docs/INITIAL_AUDIT.md), audit du dépôt client au tag `v7.1.3`.
2. [`docs/SIMPLEXMQ_AUDIT.md`](docs/SIMPLEXMQ_AUDIT.md), audit du dépôt des serveurs.
3. [`docs/DECISIONS.md`](docs/DECISIONS.md), journal des décisions et de leur portée.
4. [`CONTRIBUTING.md`](CONTRIBUTING.md), règles de travail et de divergence minimale.
5. [`NOTICE-AGPL.md`](NOTICE-AGPL.md), obligations de licence et de marque.
