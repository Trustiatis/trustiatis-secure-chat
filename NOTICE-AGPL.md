# Licence, marque et obligations de redistribution

## 1. Licence

Trustiatis Secure est un **dérivé de SimpleX Chat**, logiciel placé sous **GNU Affero General
Public License version 3** par SimpleX Chat Ltd. Trustiatis n'est pas titulaire des droits sur
l'oeuvre d'origine et ne peut donc pas en changer la licence.

Trustiatis Secure est par conséquent distribué **sous AGPLv3**, et uniquement sous AGPLv3.

### Ce que Trustiatis doit faire, pour chaque binaire distribué

| Obligation | Mise en oeuvre |
|---|---|
| Fournir le **code source complet correspondant** | dépôt public en lecture, ou archive liée depuis le portail de téléchargement, pour **chaque version publiée** et aussi longtemps qu'elle est distribuée |
| Conserver les mentions de copyright et de licence | le fichier `LICENSE` de l'amont est conservé **tel quel**, jamais réécrit |
| **Indiquer les modifications et leur date** | journal tenu dans `docs/UPSTREAM_SYNC.md` et dans l'historique Git, qui doit rester public |
| Distribuer sous AGPLv3, sans restriction supplémentaire | aucune clause contractuelle Trustiatis ne peut restreindre les droits de l'AGPL |
| Respecter les licences tierces | VLC en **LGPLv2** (la bibliothèque doit rester remplaçable, donc dynamique et non statique), WebRTC et SQLCipher en BSD, l'arbre Haskell documenté dans `docs/dependencies/HASKELL.md` de l'amont |

### Article 13 de l'AGPL

Si Trustiatis exploite un jour une version **modifiée** d'un serveur du réseau (relais SMP,
serveur de fichiers XFTP, serveur de notification), les utilisateurs qui interagissent avec ce
serveur doivent pouvoir en obtenir la source.

**En version 1, ce point est sans objet : Trustiatis n'héberge aucun serveur et le produit
utilise les serveurs de la communauté SimpleX.** Voir la décision S-1 de
`docs/DECISIONS.md`. Il redeviendra applicable si l'hébergement est repris.

## 2. Marque

La politique de marque de l'amont (`docs/TRADEMARK.md` du dépôt SimpleX) **interdit
expressément** l'usage du nom et du logo SimpleX dans la marque, le nom ou le logo d'un
logiciel, « **including forks** ».

### Usages interdits dans Trustiatis Secure

- le nom « SimpleX » dans le nom du produit, sa marque, son logo, ses icônes ;
- toute suggestion d'affiliation, de partenariat, de soutien ou d'approbation par
  SimpleX Chat Ltd ;
- la réutilisation des ressources de marque de l'amont (`media-logos/`), du logo Flux et des
  badges de l'amont.

### Usages autorisés, et nécessaires à l'exactitude

La même politique autorise la mention du réseau et des objets du protocole. Trois catégories
de chaînes sont donc à distinguer, et `tools/verifier-marque.py` les fait respecter :

| Catégorie | Traitement | Exemple |
|---|---|---|
| **Nom du produit** | remplacé par « Trustiatis Secure » | « SimpleX » comme nom de l'application |
| **Réseau et protocole** | **conservé**, car le remplacer serait inexact | « adresse SimpleX », « réseau SimpleX », « compatible avec le réseau SimpleX » |
| **Renvois vers l'éditeur amont** | retirés ou réécrits vers Trustiatis | site, conditions, dons, badges de SimpleX Chat Ltd |

### Mention à faire figurer

Dans l'écran « À propos », sur le portail de téléchargement et dans les descriptions de
magasins :

> Trustiatis Secure est un produit indépendant édité par Trustiatis, dérivé du logiciel libre
> SimpleX Chat et compatible avec le réseau SimpleX. Il n'est ni édité, ni soutenu, ni
> approuvé par SimpleX Chat Ltd. « SimpleX » est une marque de SimpleX Chat Ltd.

## 3. Risque juridique non tranché

**Risque J-02 de l'audit de Phase 1, décision D-1, toujours ouverte.** La publication sur
l'App Store d'une oeuvre sous AGPLv3 dont Trustiatis n'est pas titulaire des droits est
juridiquement contestée : les conditions de l'App Store imposent des restrictions d'usage que
la Free Software Foundation considère incompatibles avec la GPL et l'AGPL. SimpleX Chat Ltd
peut publier parce qu'elle détient les droits et peut concéder une autre licence. Trustiatis ne
peut pas.

**La branche `feature/ios` reste bloquée jusqu'à l'avis du conseil juridique.** Ce document
n'est pas un avis juridique.
