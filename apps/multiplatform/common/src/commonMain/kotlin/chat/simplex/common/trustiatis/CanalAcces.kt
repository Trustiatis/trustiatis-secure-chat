package chat.simplex.common.trustiatis

import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant

/**
 * Le contrat du canal d'accès TrustiVPN, version `TRUSTIATIS-VPN/1`.
 *
 * Spécification : `docs/20-trustivpn/CONTRAT-ACCES.md` du dépôt trusti-gateway. Chaque refus
 * nommé là bas a son cas de test ici, et c'est le seul moyen de garder les deux bouts
 * d'accord sur ce qui est un message et ce qui n'en est pas un.
 *
 * Trois messages, et la répartition des pouvoirs d'ADR-0026 D1 est dans leur forme même :
 *
 *  - `demande` ne porte ni adresse, ni routes, ni durée. Un appareil ne demande pas ses
 *    droits, il demande une session pour des droits qui existent déjà. Ce canal ne peut ni
 *    créer un utilisateur, ni changer une adresse, ni changer des `AllowedIPs` ;
 *  - `session` porte ce que la passerelle décide, et **jamais** de clé privée. Le champ
 *    n'existe pas, et son absence est la garantie : une clé privée et une clé publique
 *    X25519 font toutes deux 32 octets et sont indiscernables par leur forme (ADR-0025 D6) ;
 *  - `revocation` informe, elle n'autorise rien. La passerelle retire la clé de ses pairs
 *    d'abord, prévient ensuite, donc un message perdu ne laisse aucun accès ouvert.
 *
 * Ce fichier ne parle pas au réseau. Il lit, il valide, il écrit du texte.
 */

/** Marque de la première ligne. Le contrat fige la version, donc elle n'est pas négociée. */
const val MARQUE_VPN = "TRUSTIATIS-VPN/1"

/** Les trois motifs d'ADR-0025 D3. La distinction porte une conséquence, pas une nuance. */
enum class MotifRevocation(val cle: String) {
  /** La session meurt, l'utilisateur repasse suspendu. Il peut en redemander une. */
  CLE_REVOQUEE("cle_revoquee"),

  /** Aucune nouvelle clé ne sera délivrée. Redemander ne sert à rien. */
  UTILISATEUR_SUSPENDU("utilisateur_suspendu"),

  /** Le droit d'entrer a disparu. État terminal. */
  UTILISATEUR_SUPPRIME("utilisateur_supprime");

  companion object {
    fun depuis(cle: String?): MotifRevocation? = entries.firstOrNull { it.cle == cle }
  }
}

/**
 * Une session accordée par la passerelle.
 *
 * `clePubliqueServeur` est un tableau, donc `equals` et `hashCode` sont écrits à la main :
 * un `data class` les dériverait par référence et deux sessions identiques se compareraient
 * différentes, ce qui ferait croire à un changement de session à chaque relecture.
 */
class SessionVpn(
  val adresse: String,
  val routes: List<String>,
  val passerelleHote: String,
  val passerellePort: Int,
  clePubliqueServeur: ByteArray,
  val echeance: Instant,
  val dns: String? = null,
) {
  val clePubliqueServeur: ByteArray = clePubliqueServeur.copyOf()

  val passerelle: String get() =
    if (passerelleHote.contains(':')) "[$passerelleHote]:$passerellePort" else "$passerelleHote:$passerellePort"

  fun expiree(maintenant: Instant): Boolean = echeance <= maintenant

  /** Le renouvellement est possible à partir de T moins une heure (ADR-0025 D4). */
  fun renouvelable(maintenant: Instant): Boolean =
    maintenant >= echeance.minus(DUREE_RENOUVELLEMENT_AVANT)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SessionVpn) return false
    return adresse == other.adresse && routes == other.routes &&
      passerelleHote == other.passerelleHote && passerellePort == other.passerellePort &&
      clePubliqueServeur.contentEquals(other.clePubliqueServeur) &&
      echeance == other.echeance && dns == other.dns
  }

  override fun hashCode(): Int {
    var h = adresse.hashCode()
    h = 31 * h + routes.hashCode()
    h = 31 * h + passerelleHote.hashCode()
    h = 31 * h + passerellePort
    h = 31 * h + clePubliqueServeur.contentHashCode()
    h = 31 * h + echeance.hashCode()
    h = 31 * h + (dns?.hashCode() ?: 0)
    return h
  }

  /** Ni la clé du serveur ni l'adresse interne ne sont des secrets, mais la trace reste brève. */
  override fun toString(): String = "SessionVpn(adresse=$adresse, passerelle=$passerelle, echeance=$echeance)"
}

data class RevocationVpn(val motif: MotifRevocation, val instant: Instant)

sealed interface MessageVpn {
  data class Accordee(val session: SessionVpn) : MessageVpn
  data class Revoquee(val revocation: RevocationVpn) : MessageVpn
}

/** Le renouvellement s'ouvre une heure avant l'échéance (ADR-0025 D4). */
internal val DUREE_RENOUVELLEMENT_AVANT = 1.hours

private fun instantStrict(valeur: String?): Instant? {
  if (valeur.isNullOrEmpty()) return null
  // Le Z est exigé avant l'analyse : kotlinx-datetime accepte « +01:00 », que le contrat
  // refuse, parce qu'une heure sans UTC laisse l'utilisateur incapable de dater le message.
  if (!valeur.endsWith("Z")) return null
  return runCatching { Instant.parse(valeur) }.getOrNull()
}

/**
 * Valide une notation CIDR, et rend l'adresse sans son préfixe.
 *
 * Une adresse sans masque n'est pas une adresse de pair : WireGuard en déduirait un masque,
 * et le pair couvrirait plus ou moins que prévu. On ne devine pas.
 */
internal fun adresseCidr(valeur: String): String? {
  val coupe = valeur.indexOf('/')
  if (coupe <= 0 || coupe == valeur.length - 1) return null
  val hote = valeur.substring(0, coupe)
  val prefixeTexte = valeur.substring(coupe + 1)
  if (prefixeTexte.any { !it.isDigit() } || prefixeTexte.length > 3) return null
  val prefixe = prefixeTexte.toIntOrNull() ?: return null
  // « 032 » et « 32 » se liraient pareil, et l'une des deux écritures est une faute de saisie
  // qu'il vaut mieux voir refusée que corrigée en silence.
  if (prefixeTexte.length > 1 && prefixeTexte[0] == '0') return null
  // « :: » est l'adresse non spécifiée : elle n'a aucun groupe, donc estIPv6 la refuse, et
  // c'est juste pour un hôte de passerelle. Mais « ::/0 » est la route par défaut d'IPv6, et
  // sans elle le tunnel ne porterait que le trafic v4 en se croyant complet.
  val v6 = estIPv6(hote) || hote == "::"
  return when {
    estIPv4(hote) && prefixe in 0..32 -> hote
    v6 && prefixe in 0..128 -> hote
    else -> null
  }
}

private fun routes(valeur: String?): List<String>? {
  if (valeur.isNullOrEmpty()) return null
  val liste = valeur.split(',').map { it.trim() }.filter { it.isNotEmpty() }
  // Un tunnel sans route ne porte rien. Le laisser monter donnerait à l'utilisateur
  // l'illusion d'être protégé, ce qui est pire que ne pas monter du tout.
  if (liste.isEmpty()) return null
  return if (liste.all { adresseCidr(it) != null }) liste else null
}

/**
 * Lit un message de la passerelle, ou rend `null`.
 *
 * `null` veut dire « ce texte n'est pas un message de ce contrat ». Il reste du texte
 * ordinaire dans la conversation : rendre un message incomplet comme une session accordée
 * donnerait l'apparence du service à ce qui n'en est pas, et c'est cette apparence qu'un
 * attaquant cherche.
 */
fun lireMessageVpn(texte: String, maintenant: Instant): MessageVpn? {
  val champs = lireChamps(texte, MARQUE_VPN) ?: return null
  return when (champs["type"]) {
    "session" -> lireSession(champs, maintenant)?.let(MessageVpn::Accordee)
    "revocation" -> lireRevocation(champs)?.let(MessageVpn::Revoquee)
    // Une extension future ne se devine pas, et « demande » n'est pas un message que
    // l'appareil reçoit : s'il en reçoit un, quelqu'un rejoue son propre trafic.
    else -> null
  }
}

private fun lireSession(champs: Map<String, String>, maintenant: Instant): SessionVpn? {
  val adresse = champs["adresse"]?.takeIf { it.isNotEmpty() } ?: return null
  if (adresseCidr(adresse) == null) return null
  val routes = routes(champs["routes"]) ?: return null
  val autorite = champs["passerelle"]?.takeIf { it.isNotEmpty() } ?: return null
  val (hote, port) = hoteEtPort(autorite)
  // Pas de port par défaut deviné ici : le contrat l'exige, et un port absent est le genre
  // d'oubli qui ferait monter un tunnel vers la mauvaise extrémité.
  if (port == null || port !in 1..65535) return null
  if (hote.isEmpty() || !(estIPv4(hote) || estIPv6(hote) || estNomDns(hote))) return null
  val cleServeur = champs["cle_serveur"]?.let(::decoderClePublique) ?: return null
  val echeance = instantStrict(champs["echeance"]) ?: return null
  // Une session reçue déjà expirée est une erreur du serveur, pas une session. L'accepter
  // ferait monter un tunnel que la passerelle refuse déjà, sans message utile.
  if (echeance <= maintenant) return null
  val dns = champs["dns"]?.takeIf { it.isNotEmpty() }
  if (dns != null && !(estIPv4(dns) || estIPv6(dns))) return null
  return SessionVpn(
    adresse = adresse,
    routes = routes,
    passerelleHote = hote,
    passerellePort = port,
    clePubliqueServeur = cleServeur,
    echeance = echeance,
    dns = dns,
  )
}

private fun lireRevocation(champs: Map<String, String>): RevocationVpn? {
  val motif = MotifRevocation.depuis(champs["motif"]?.takeIf { it.isNotEmpty() }) ?: return null
  val instant = instantStrict(champs["instant"]) ?: return null
  return RevocationVpn(motif, instant)
}

/**
 * Écrit la demande d'ouverture de session.
 *
 * Le libellé d'appareil est nettoyé de ses sauts de ligne, et de rien d'autre : un saut de
 * ligne dans une valeur produirait une ligne qui se lit comme un champ, donc une injection
 * de champ dans un message que le serveur croit bien formé.
 */
fun ecrireDemande(clePubliqueBase64: String, appareil: String, instant: Instant): String {
  val libelle = appareil.replace('\n', ' ').replace('\r', ' ').trim().take(64)
  require(libelle.isNotEmpty()) { "le libellé de l'appareil est obligatoire" }
  require(decoderClePublique(clePubliqueBase64) != null) { "clé publique invalide" }
  return listOf(
    MARQUE_VPN,
    "type=demande",
    "cle=$clePubliqueBase64",
    "appareil=$libelle",
    "instant=$instant",
  ).joinToString("\n")
}
