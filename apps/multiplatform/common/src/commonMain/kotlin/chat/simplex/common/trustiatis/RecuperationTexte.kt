package chat.simplex.common.trustiatis

/**
 * Récupère un petit document texte en HTTPS.
 *
 * `socks` porte le mandataire SOCKS de l'application quand elle en utilise un. Il n'est
 * pas facultatif par confort : une application réglée pour tout faire passer par Tor qui
 * sortirait en direct pour vérifier ses mises à jour révélerait son adresse à chaque
 * démarrage, et le ferait justement pour ceux qui ont pris la peine de l'éviter.
 */
expect fun recupererTexte(url: String, socks: String?): Result<String>

/** La plateforme pour laquelle choisir un paquet dans le manifeste. */
expect fun plateformeCourante(): Plateforme

/**
 * Ouvre une adresse dans le navigateur du système.
 *
 * `LocalUriHandler` de Compose ne se lit que dans une fonction composable, et l'invitation
 * à mettre à jour est posée depuis une coroutine. D'où cette paire, qui ne sert qu'à cela.
 */
expect fun ouvrirAdresse(url: String)
