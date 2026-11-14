# Règles de travail

Ce dépôt est un **dérivé** d'un logiciel libre maintenu activement par un tiers. Chaque ligne
qui s'écarte de l'amont devra être reportée à chaque mise à jour de sécurité. La règle
première est donc la **divergence minimale**.

## Les trois cercles de modification

Repris de l'audit de Phase 1, chapitre 13.

| Cercle | Contenu | Effet sur la synchronisation |
|---|---|---|
| **1. Ressources remplacées** | icônes, logos, illustrations, valeurs de couleurs, nom de l'application, fichiers de distribution | nul ou presque nul : les chemins ne changent pas, seuls les octets changent |
| **2. Identifiants et configuration** | `applicationId`, `bundleID`, UUID du MSI, URL de mise à jour, domaines, groupes d'application | faible : modifications ponctuelles et localisées |
| **3. Code** | tri des chaînes de marque, retrait des fonctions de tiers, écrans spécifiques | réel : à maintenir au minimum strict |

**Tout ce qui peut être fait dans le cercle 1 doit l'être. Rien n'entre dans le cercle 3 sans
justification écrite dans `docs/BRANDING.md`.**

## Interdits absolus

1. Ne pas modifier les algorithmes cryptographiques, les protocoles, le format de base.
2. Ne pas modifier `src/` (coeur Haskell) tant que la décision D-2 reste en option « Légère ».
3. Ne pas renommer une énumération sérialisée, persistée ou échangée avec le coeur.
4. Ne pas faire de rechercher-remplacer global sur « SimpleX » : cela casse le typage, la
   sérialisation JSON et le schéma d'URI. Voir `tools/verifier-marque.py`.
5. Ne pas toucher au garde-fou de variantes de `apps/multiplatform/android/build.gradle.kts`
   qui empêche de livrer un APK de production contenant Play Billing.
6. Ne jamais committer une clé privée, un magasin de clés, un certificat ou un mot de passe.
   Les secrets vivent dans KeePassXC, puis dans Vault, et sont injectés par variables de CI
   protégées.
7. Ne jamais affirmer qu'une compilation a réussi sans joindre le journal et la somme SHA-256.

## Branches

| Branche | Rôle |
|---|---|
| `main` | versions stables Trustiatis, **protégée**, alimentée uniquement par fusion depuis `develop` |
| `develop` | intégration |
| `feature/branding` | identité graphique, cercle 1 |
| `feature/desktop` | Windows, macOS, Linux |
| `feature/android` | Android et GrapheneOS |
| `feature/ios` | iOS, **bloquée** tant que la décision D-1 n'est pas rendue |
| `feature/distribution` | empaquetage, signature, portail de téléchargement |
| `feature/server-configuration` | configuration des serveurs, **sans objet en V1** (serveurs communautaires) |
| `upstream/vX.Y.Z` | branches techniques de report d'une version d'amont |

## Commits

- **Atomiques** : une intention par commit.
- Message en français, à l'impératif, première ligne de 72 caractères au plus.
- Préfixer par le périmètre quand c'est utile : `android:`, `desktop:`, `ios:`, `branding:`,
  `docs:`, `ci:`, `upstream:`.
- Tout commit du cercle 2 ou 3 cite la décision ou le point d'audit qui le justifie.

## Demandes de fusion

Aucun envoi direct sur `main`. Une demande de fusion par intention, revue avant fusion, avec
le gabarit de `.gitlab/merge_request_templates/`. La CI doit passer, le contrôle de marque
compris.

## Typographie des documents

Décision du Membre : **jamais de tiret long ni de tiret demi-long** dans les documents
produits. Incises en virgules ou parenthèses, plages en « de ... à ... ». Contrôler avant de
rendre :

```bash
# Motifs construits en octal pour que ce fichier ne contienne pas lui-meme de tiret long.
em=$(printf '\342\200\224'); en=$(printf '\342\200\223')
grep -rn -e "$em" -e "$en" docs/*.md *.md
```
