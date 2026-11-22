# Notice d'utilisation de Trustiatis Secure

**Pour quelqu'un qui ne l'a jamais ouverte.** Chaque fonction est décrite avec le nom exact
qu'elle porte à l'écran, en français. Les libellés cités entre guillemets sont ceux de
l'application.

Cette notice accompagne la version **7.1.4-ts2**. Elle ne remplace pas le guide de l'amont
(`docs/guide/`), qui est en anglais, qui décrit SimpleX Chat, et qui décrit des fonctions que
cette version a neutralisées.

## 1. Ce que c'est, en une page

Une messagerie chiffrée de bout en bout, sans compte, sans nom d'utilisateur et sans numéro de
téléphone. Vous ne vous inscrivez nulle part : vous choisissez un nom de profil, qui ne sera vu
que des personnes à qui vous vous connecterez vous même.

Les messages sont chiffrés sur votre appareil et déchiffrés sur celui de votre correspondant. Les
serveurs qui les relaient les transmettent sans savoir qui parle à qui, et **Trustiatis n'exploite
aucun de ces serveurs** : nous ne pourrions pas lire vos messages même si on nous le demandait.

C'est le canal à privilégier avec Trustiatis, et pour les informations sensibles c'est le seul que
nous utilisons. La même application porte aussi vos conversations internes, entre vos collègues,
sans licence à acheter ni compte à créer.

## 2. La première ouverture

1. Vous choisissez un **nom de profil**. Rien d'autre n'est demandé : ni adresse, ni numéro, ni
   mot de passe.
2. Sur Android, l'application affiche une **notification permanente**. Elle n'est pas un défaut :
   c'est le service qui reçoit vos messages sans passer par les serveurs de notification de Google.
   La masquer coupe la réception quand l'application est fermée.
3. Rien n'est envoyé nulle part tant que vous n'avez pas créé une première connexion.

## 3. Se connecter à quelqu'un

C'est la seule façon d'entrer en relation : il n'existe pas d'annuaire, et personne ne peut vous
écrire sans que vous ayez partagé quelque chose au préalable.

| Moyen | Où | Quand s'en servir |
|---|---|---|
| **Lien à usage unique** | « Nouveau message », puis créer un lien | le cas courant, pour une personne précise. Il ne sert qu'une fois |
| **QR code** | le même écran, onglet du code | en face à face, c'est le plus sûr : rien ne transite |
| **Votre adresse SimpleX** | « Vos paramètres », puis votre profil | une adresse durable, que vous pouvez publier. Chaque demande reste à accepter |

Un lien d'invitation peut être transmis par n'importe quel moyen, y compris un courriel ou une
autre messagerie. Ce n'est pas lui qui protège la conversation : c'est la vérification de l'étape
suivante.

## 4. Vérifier à qui vous parlez

**Cette étape n'est pas facultative chez Trustiatis.** Le réseau ne porte aucune identité : il
garantit que personne ne lit au passage, pas que votre correspondant est bien celui que vous
croyez.

1. Ouvrez la conversation, puis le profil du correspondant, puis **« Vérifier le code de
   sécurité »**. Comparez le code affiché avec le sien, de vive voix ou par vidéo. Les deux
   appareils doivent montrer le même.
2. Demandez sa **carte d'identité** sur `id.trustiatis.com`. Tout interlocuteur Trustiatis la
   présente à l'ouverture d'une conversation, et nous vous demandons d'en faire autant.
3. **Ne discutez de rien de sensible avant d'avoir les deux.**

## 5. Écrire

| Fonction | Comment |
|---|---|
| Envoyer | la zone de saisie, en bas |
| Répondre à un message précis | appui long sur le message, puis « Répondre » |
| Modifier | appui long sur votre message, puis « Modifier ». Le correspondant voit qu'il a été modifié |
| Supprimer | appui long, puis « Supprimer ». Vous choisissez entre supprimer pour vous ou pour tout le monde, si le correspondant l'autorise |
| Réagir | appui long, puis une réaction |
| **Message éphémère** | à activer dans les « Préférences de chat » de la conversation, avec une durée. Le message disparaît des deux côtés une fois le délai écoulé |

## 6. Fichiers, images et messages vocaux

Le trombone joint un fichier ou une image, le micro enregistre un message vocal. Les fichiers
voyagent chiffrés, par des serveurs distincts de ceux des messages, et ne sont déchiffrables que
par le destinataire.

Un fichier reçu reste sur votre appareil tant que vous ne le supprimez pas. Un message éphémère
emporte le fichier qu'il contient.

## 7. Appels audio et vidéo

Depuis la conversation, l'icône d'appel. Le réglage global est dans « Vos paramètres », puis
« Appels audio et vidéo ». Les appels sont chiffrés de bout en bout eux aussi.

## 8. Groupes

« Créer un groupe secret » dans le menu de création. Un groupe n'a pas d'adresse publique : on y
entre par une invitation, comme pour une conversation à deux. Les membres ont des rôles, du
propriétaire au simple observateur, qui décident de qui peut écrire, inviter ou exclure.

## 9. Plusieurs profils, et le mode Incognito

« Vos profils de messagerie » permet d'en tenir plusieurs sur le même appareil, cloisonnés : un
correspondant d'un profil ne voit rien des autres.

Le mode **« Incognito »**, activable au moment de créer une connexion, génère un nom aléatoire
pour cette connexion là. Utile quand votre nom habituel n'a pas à être connu de ce
correspondant.

## 10. Protéger l'appareil

| Réglage | Où | Ce qu'il fait |
|---|---|---|
| **« Verrouillage Trustiatis »** | « Vos paramètres », puis « Votre confidentialité » | demande votre code ou votre biométrie à l'ouverture de l'application |
| **« Protéger l'écran de l'app »** | même écran | empêche les captures d'écran et masque l'application dans la liste des tâches |
| **Phrase secrète de la base** | « Base de données de la messagerie » | la base est chiffrée. Donnez lui une phrase secrète que vous seul connaissez, et notez la ailleurs : **elle ne se récupère pas** |

## 11. Notifications

| Plateforme | Ce qui se passe |
|---|---|
| **Android** | un service tourne en arrière plan et reçoit les messages. Aucune notification ne passe par Google. C'est la notification permanente de l'étape 2 |
| **Ordinateur** | l'application reste dans la zone de notification, et l'icône porte une pastille quand il y a du non lu |
| **iOS** | **les notifications sont volontairement désactivées dans cette version.** Les recevoir supposerait d'enregistrer un jeton auprès d'Apple, ce que nous avons retiré. Vous recevez donc les messages quand vous ouvrez l'application, et pas avant |

## 12. Lier le téléphone et l'ordinateur

« Lier un téléphone » sur l'ordinateur. L'ordinateur utilise alors le profil et les conversations
du téléphone, par le réseau local, sans copier la base. Le téléphone reste le détenteur des
données ; fermez la liaison et l'ordinateur n'a plus rien.

## 13. Réseau et serveurs

« Vos paramètres », puis « Réseau et serveurs ». Cette version utilise les **serveurs publics de
la communauté SimpleX**, ceux de l'amont, et non des serveurs Trustiatis. C'est un choix assumé et
documenté : il évite de faire de Trustiatis un point de passage, et il garde l'interopérabilité.
Vous pouvez y ajouter vos propres serveurs, et faire passer le trafic par Tor si votre réseau le
permet.

## 14. Vos données

Tout est sur votre appareil, dans une base chiffrée. Depuis « Base de données de la messagerie » :

- **« Phrase secrète et exportation de la base de données »** : la sauvegarde. Le fichier exporté
  est chiffré par votre phrase secrète ;
- **migration vers un autre appareil** : déplace la base, elle ne se duplique pas ;
- la suppression de l'application emporte tout. Il n'existe **aucune copie chez Trustiatis**, donc
  aucune restauration possible de notre côté.

## 15. Mettre à jour

Cette application ne vient d'aucun magasin et **ne se met pas à jour toute seule** : le
vérificateur de mises à jour de l'amont a été retiré, pour qu'elle n'interroge pas un serveur
tiers.

Les versions sont publiées sur **`downloads.trustiatis.com`**, avec pour chaque paquet son
empreinte SHA-256. Comparez l'empreinte du fichier téléchargé à celle du portail avant d'installer.

## 16. Ce que Trustiatis ne vous demandera jamais

Ni mot de passe, ni clé, ni code d'accès, **par courriel, par téléphone ou par une autre
messagerie**. Une demande qui vous parvient par une autre route ne vient pas de nous.

Certains seconds facteurs vous seront livrés ici. Un code reçu dans cette application n'est passé
ni par votre boîte aux lettres, ni par le réseau mobile, qui sont les deux endroits où ces codes
se volent.

## 17. Ce que cette application ne fait pas, et qu'il vaut mieux savoir

- elle ne protège pas un appareil déverrouillé entre les mains de quelqu'un d'autre. Le
  verrouillage de l'étape 10 est ce qui s'en approche le plus ;
- elle n'efface rien à distance. Un appareil perdu se traite en révoquant ce à quoi il donnait
  accès, pas en effaçant ses messages ;
- elle ne cache pas à votre réseau d'entreprise le fait que vous l'utilisez. Elle cache le
  contenu, les correspondants et les horaires d'une conversation, pas l'existence d'un trafic ;
- elle ne sauvegarde rien dans un nuage. La seule sauvegarde est celle que vous exportez ;
- elle ne vérifie pas l'identité à votre place. C'est l'étape 4, et elle est à vous.

## 18. En cas de doute

Écrivez au département concerné **dans cette application**. Si quelque chose vous paraît anormal,
un correspondant qui ne présente pas sa carte, un code de sécurité qui ne correspond pas, une
demande inhabituelle, signalez le avant d'y donner suite.
