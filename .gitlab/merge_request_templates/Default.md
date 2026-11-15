## Intention

<!-- Une phrase. Que fait cette demande de fusion, et pourquoi. -->

## Cercle de modification

<!-- Voir CONTRIBUTING.md. Cocher un seul cercle. -->

- [ ] **1. Ressources remplacées** (icônes, logos, couleurs, nom, fichiers de distribution)
- [ ] **2. Identifiants et configuration**
- [ ] **3. Code** (justification obligatoire ci dessous, et entrée dans `docs/BRANDING.md`)
- [ ] Documentation ou outillage seulement
- [ ] Report d'une version de l'amont

### Justification si cercle 3

<!-- Pourquoi le cercle 1 ou 2 ne suffisait pas. Ce texte sera relu à chaque report. -->

## Décision ou point d'audit qui le justifie

<!-- Par exemple : décision D-2, risque T-02, chapitre 7.4 de l'audit de Phase 1. -->

## Contrôles

- [ ] `python3 tools/verifier-marque.py` passé, résultat joint ou expliqué
- [ ] aucun tiret long ni demi-long dans les documents touchés
- [ ] `git diff upstream-<version> HEAD -- src/ scripts/ flake.nix` est **vide**
- [ ] aucun secret, aucune clé, aucun mot de passe ajouté
- [ ] `docs/BRANDING.md` mis à jour si une ressource ou une chaîne a changé

## Preuve, si cette demande touche à la compilation ou à l'empaquetage

<!-- Journal de construction et somme SHA-256 de l'artefact. Une compilation sans preuve
     n'est pas une compilation. Si rien n'a été compilé, l'écrire. -->

## Plateformes touchées

- [ ] Windows  - [ ] macOS  - [ ] Linux  - [ ] Android  - [ ] GrapheneOS  - [ ] iOS
