package chat.simplex.common.trustiatis

/**
 * Configuration de la passerelle TrustiVPN du client, et sa validation.
 *
 * Ce fichier est à Trustiatis. Il ne reprend rien de l'amont et n'entre en conflit avec
 * aucun report : c'est la raison pour laquelle tout ce qui nous appartient vit dans ce
 * paquet, et non dispersé dans les écrans de l'amont.
 *
 * Trois valeurs, et pas une de plus :
 *
 *  - l'hôte de la passerelle, l'adresse IP dure ou le nom du TrustiVPN du client ;
 *  - le port UDP de WireGuard ;
 *  - le point d'entrée du plan de contrôle, qui n'est joignable QUE dans le tunnel.
 *
 * Ce qui n'est PAS ici, et qui n'y sera pas : un mot de passe, un jeton permanent, un
 * secret partagé quelconque. ADR-0010 du programme TrustiGateway pose que l'identité est
 * générée sur l'appareil, et ADR-0025 D6 que la clé privée ne sort jamais. Un jeton
 * d'enrôlement est à usage unique : il sert une fois, il n'est pas rangé, et il ne
 * réapparaît nulle part. Un champ « jeton » persistant serait la porte par laquelle tout
 * cela se déferait, donc ce champ n'existe pas.
 */

/** Port UDP par défaut de WireGuard, celui du rôle `trustivpn_exposition`. */
const val PORT_VPN_DEFAUT = 51820

enum class ChampConfig { HOTE, PORT, ENDPOINT, JETON }

data class Anomalie(val champ: ChampConfig, val raison: String)

data class ConfigurationPasserelle(
  val hote: String = "",
  val portVpn: Int = PORT_VPN_DEFAUT,
  val endpoint: String = "",
)

private val NOM_DNS = Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)*[A-Za-z]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

fun estIPv4(valeur: String): Boolean {
  val octets = valeur.split(".")
  if (octets.size != 4) return false
  return octets.all { o ->
    // « 01 » n'est pas une écriture valide : selon la bibliothèque qui la lit, elle vaut 1
    // ou 0 en octal. Une adresse qui change de sens selon le lecteur n'est pas une adresse.
    o.isNotEmpty() && o.length <= 3 && o.all { it.isDigit() } &&
      (o.length == 1 || o[0] != '0') && o.toInt() in 0..255
  }
}

fun estIPv6(valeur: String): Boolean {
  if (valeur.count { it == ':' } < 2 || valeur.contains(' ')) return false
  if (valeur.contains(":::")) return false
  val doubles = Regex("::").findAll(valeur).count()
  if (doubles > 1) return false
  val groupes = valeur.split(":").filter { it.isNotEmpty() }
  if (groupes.isEmpty() || groupes.size > 8) return false
  return groupes.all { g -> g.length <= 4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
}

fun estNomDns(valeur: String): Boolean = NOM_DNS.matches(valeur)

/**
 * L'adresse est elle dans un espace privé, donc plausiblement dans le tunnel.
 *
 * Ce n'est pas une preuve, et le commentaire est là pour qu'on ne la prenne pas pour
 * telle : une adresse privée peut être routée ailleurs. C'est un garde fou contre
 * l'erreur de saisie la plus coûteuse, celle qui pointerait le plan de contrôle sur une
 * adresse publique et défairait ADR-0021 sans que personne ne s'en aperçoive.
 */
fun estAdressePrivee(hote: String): Boolean {
  if (estIPv4(hote)) {
    val o = hote.split(".").map { it.toInt() }
    return when {
      o[0] == 10 -> true
      o[0] == 172 && o[1] in 16..31 -> true
      o[0] == 192 && o[1] == 168 -> true
      o[0] == 100 && o[1] in 64..127 -> true // espace partagé des opérateurs, RFC 6598
      else -> false
    }
  }
  if (estIPv6(hote)) {
    val debut = hote.lowercase().trimStart('[')
    return debut.startsWith("fc") || debut.startsWith("fd")
  }
  return false
}

/** Découpe « hôte » ou « hôte:port » en tenant compte des crochets d'IPv6. */
internal fun hoteEtPort(autorite: String): Pair<String, Int?> {
  if (autorite.startsWith("[")) {
    val fin = autorite.indexOf(']')
    if (fin < 0) return autorite to null
    val h = autorite.substring(1, fin)
    val reste = autorite.substring(fin + 1)
    return h to if (reste.startsWith(":")) reste.drop(1).toIntOrNull() ?: -1 else null
  }
  val i = autorite.lastIndexOf(':')
  if (i < 0) return autorite to null
  return autorite.substring(0, i) to (autorite.substring(i + 1).toIntOrNull() ?: -1)
}

fun validerHote(hote: String): Anomalie? = when {
  hote.isBlank() -> Anomalie(ChampConfig.HOTE, "L'adresse de la passerelle est obligatoire.")
  hote != hote.trim() -> Anomalie(ChampConfig.HOTE, "L'adresse ne doit pas commencer ni finir par une espace.")
  hote.contains("://") -> Anomalie(ChampConfig.HOTE, "Indiquez une adresse ou un nom, sans « https:// ».")
  hote.contains('/') -> Anomalie(ChampConfig.HOTE, "Indiquez une adresse ou un nom, sans chemin.")
  hote.contains('@') -> Anomalie(ChampConfig.HOTE, "Une adresse ne porte pas d'identifiant.")
  estIPv4(hote) || estIPv6(hote) || estNomDns(hote) -> null
  else -> Anomalie(ChampConfig.HOTE, "Ni une adresse IP valide, ni un nom d'hôte valide.")
}

fun validerPort(port: Int): Anomalie? = when (port) {
  in 1..65535 -> null
  else -> Anomalie(ChampConfig.PORT, "Le port doit être compris entre 1 et 65535.")
}

/**
 * Le point d'entrée du plan de contrôle.
 *
 * Deux refus qui ne sont pas des détails de forme. En clair, jamais : le plan de contrôle
 * porte la politique réseau et les autorisations. Et hors du tunnel, jamais non plus :
 * ADR-0021 dit que le portail n'est jamais public, et que le double verrou est la liaison
 * d'interface ET le pare feu. Une application qui accepterait ici une adresse publique
 * apprendrait à l'exploitant qu'un portail public est une configuration normale.
 */
fun validerEndpoint(endpoint: String, hote: String): Anomalie? {
  if (endpoint.isBlank()) return Anomalie(ChampConfig.ENDPOINT, "Le point d'entrée est obligatoire.")
  if (endpoint.startsWith("http://")) {
    return Anomalie(ChampConfig.ENDPOINT, "En clair, jamais. Le point d'entrée est en « https:// ».")
  }
  if (!endpoint.startsWith("https://")) {
    return Anomalie(ChampConfig.ENDPOINT, "Le point d'entrée commence par « https:// ».")
  }
  val sansSchema = endpoint.removePrefix("https://")
  if (sansSchema.contains('@')) {
    return Anomalie(ChampConfig.ENDPOINT, "Pas d'identifiant dans l'adresse : il finirait dans les journaux.")
  }
  val autorite = sansSchema.substringBefore('/')
  if (autorite.isBlank()) return Anomalie(ChampConfig.ENDPOINT, "Adresse incomplète.")
  val (h, port) = hoteEtPort(autorite)
  if (port != null && port !in 1..65535) {
    return Anomalie(ChampConfig.ENDPOINT, "Le port du point d'entrée doit être compris entre 1 et 65535.")
  }
  if (h.isBlank() || !(estIPv4(h) || estIPv6(h) || estNomDns(h))) {
    return Anomalie(ChampConfig.ENDPOINT, "Ni une adresse IP valide, ni un nom d'hôte valide.")
  }
  if (!estAdressePrivee(h) && h != hote) {
    return Anomalie(
      ChampConfig.ENDPOINT,
      "Le plan de contrôle n'est joignable que dans le tunnel. Donnez son adresse interne."
    )
  }
  return null
}

/**
 * Le jeton d'enrôlement, qui sert une fois.
 *
 * On valide sa forme pour écarter un collage de travers, et rien de plus : sa validité
 * réelle est dite par la passerelle, et par elle seule. Une application qui prétendrait
 * juger un jeton hors ligne se tromperait, ou donnerait de l'information à qui essaie.
 */
fun validerJeton(jeton: String): Anomalie? = when {
  jeton.isBlank() -> Anomalie(ChampConfig.JETON, "Le jeton d'enrôlement est obligatoire.")
  jeton != jeton.trim() -> Anomalie(ChampConfig.JETON, "Le jeton ne doit porter aucune espace.")
  jeton.any { it.isWhitespace() } -> Anomalie(ChampConfig.JETON, "Le jeton ne doit porter aucune espace.")
  jeton.length < 24 -> Anomalie(ChampConfig.JETON, "Jeton trop court : il en manque une partie.")
  jeton.length > 128 -> Anomalie(ChampConfig.JETON, "Jeton trop long : ce n'est pas un jeton d'enrôlement.")
  !jeton.all { it.isLetterOrDigit() || it in "-._" } ->
    Anomalie(ChampConfig.JETON, "Le jeton ne contient que des lettres, des chiffres, et « - . _ ».")
  else -> null
}

fun valider(c: ConfigurationPasserelle): List<Anomalie> =
  listOfNotNull(validerHote(c.hote), validerPort(c.portVpn), validerEndpoint(c.endpoint, c.hote))

val ConfigurationPasserelle.complete: Boolean get() = valider(this).isEmpty()
