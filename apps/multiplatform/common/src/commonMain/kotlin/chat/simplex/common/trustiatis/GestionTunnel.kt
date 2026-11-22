package chat.simplex.common.trustiatis

import chat.simplex.common.platform.settings
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Base64

/**
 * Le cycle de vie d'une session TrustiVPN sur l'appareil.
 *
 * La clé privée n'est jamais écrite
 * --------------------------------
 * ADR-0025 D4 dit qu'une session dure 24 heures, que renouveler n'est pas prolonger mais
 * produire une clé **neuve**, et qu'un geste local est obligatoire à chaque ouverture. Il
 * n'y a donc aucune raison de conserver la clé privée entre deux lancements : elle vit en
 * mémoire le temps de la session, et une nouvelle session en tire une nouvelle.
 *
 * C'est plus sûr que n'importe quel scellement, et cela évite un piège réel : le `cryptor`
 * du Desktop, chez l'amont, est un leurre. Son `decryptData` rend `String(data)`, c'est à
 * dire le texte en clair. S'en servir pour « sceller » la clé aurait écrit un secret en
 * clair tout en affichant le contraire, ce qui est pire que de ne rien sceller.
 *
 * Ce qui est rangé, et qui n'est pas secret : l'adresse réservée, les routes, l'adresse de
 * la passerelle, sa clé **publique**, l'échéance. Ces valeurs sont déjà connues de la
 * passerelle et d'un administrateur du client ; les perdre à chaque lancement obligerait à
 * recoller le bloc d'amorçage sans rien protéger de plus.
 */

private const val CLE_SESSION_ADRESSE = "TrustiatisSessionAdresse"
private const val CLE_SESSION_ROUTES = "TrustiatisSessionRoutes"
private const val CLE_SESSION_HOTE = "TrustiatisSessionHote"
private const val CLE_SESSION_PORT = "TrustiatisSessionPort"
private const val CLE_SESSION_CLE_SERVEUR = "TrustiatisSessionCleServeur"
private const val CLE_SESSION_ECHEANCE = "TrustiatisSessionEcheance"
private const val CLE_SESSION_DNS = "TrustiatisSessionDns"
private const val CLE_SESSION_REVOCATION = "TrustiatisSessionRevocation"

/** Où la session est rangée. Une interface, pour que l'orchestration se teste sans réglages. */
interface DepotSession {
  fun lire(): SessionVpn?
  fun ecrire(session: SessionVpn)
  fun effacer()
  fun revocation(): MotifRevocation?
  fun poserRevocation(motif: MotifRevocation?)
}

/** Le dépôt réel, dans les réglages de l'application. */
object DepotSessionReglages : DepotSession {
  override fun lire(): SessionVpn? {
    val adresse = settings.getString(CLE_SESSION_ADRESSE, "")
    if (adresse.isEmpty()) return null
    val routes = settings.getString(CLE_SESSION_ROUTES, "").split(',').filter { it.isNotEmpty() }
    val hote = settings.getString(CLE_SESSION_HOTE, "")
    val port = settings.getInt(CLE_SESSION_PORT, 0)
    val cle = decoderClePublique(settings.getString(CLE_SESSION_CLE_SERVEUR, "")) ?: return null
    val echeance = runCatching { Instant.parse(settings.getString(CLE_SESSION_ECHEANCE, "")) }
      .getOrNull() ?: return null
    if (routes.isEmpty() || hote.isEmpty() || port !in 1..65535) return null
    val dns = settings.getString(CLE_SESSION_DNS, "").takeIf { it.isNotEmpty() }
    return SessionVpn(adresse, routes, hote, port, cle, echeance, dns)
  }

  override fun ecrire(session: SessionVpn) {
    settings.putString(CLE_SESSION_ADRESSE, session.adresse)
    settings.putString(CLE_SESSION_ROUTES, session.routes.joinToString(","))
    settings.putString(CLE_SESSION_HOTE, session.passerelleHote)
    settings.putInt(CLE_SESSION_PORT, session.passerellePort)
    settings.putString(
      CLE_SESSION_CLE_SERVEUR,
      Base64.getEncoder().encodeToString(session.clePubliqueServeur),
    )
    settings.putString(CLE_SESSION_ECHEANCE, session.echeance.toString())
    settings.putString(CLE_SESSION_DNS, session.dns ?: "")
  }

  override fun effacer() {
    listOf(
      CLE_SESSION_ADRESSE, CLE_SESSION_ROUTES, CLE_SESSION_HOTE, CLE_SESSION_PORT,
      CLE_SESSION_CLE_SERVEUR, CLE_SESSION_ECHEANCE, CLE_SESSION_DNS,
    ).forEach { settings.remove(it) }
  }

  override fun revocation(): MotifRevocation? =
    MotifRevocation.depuis(settings.getString(CLE_SESSION_REVOCATION, "").takeIf { it.isNotEmpty() })

  override fun poserRevocation(motif: MotifRevocation?) {
    if (motif == null) settings.remove(CLE_SESSION_REVOCATION)
    else settings.putString(CLE_SESSION_REVOCATION, motif.cle)
  }
}

/** Ce qu'une tentative a produit. Un texte destiné à l'utilisateur, ou rien si c'est fait. */
sealed interface Issue {
  object Faite : Issue
  data class Refusee(val motif: String) : Issue
}

/**
 * L'orchestration, testable : le client de tunnel, le dépôt et l'horloge sont injectables.
 *
 * Une horloge injectable n'est pas une coquetterie : l'expiration et la fenêtre de
 * renouvellement sont des règles de sécurité, et une règle de temps qu'on ne peut pas
 * éprouver sans attendre vingt-quatre heures n'est pas éprouvée.
 */
class GestionTunnel(
  private val client: ClientTunnel,
  private val depot: DepotSession,
  private val horloge: () -> Instant = { Clock.System.now() },
  private val generer: () -> PaireClesVpn = ::genererPaireClesVpn,
) {
  /** La clé de la session en cours. En mémoire, jamais écrite, effacée à l'extinction. */
  private var paire: PaireClesVpn? = null

  fun session(): SessionVpn? = depot.lire()

  fun revocation(): MotifRevocation? = depot.revocation()

  /** La clé publique à mettre dans une demande, en ouvrant une paire si besoin. */
  fun clePubliqueCourante(): String {
    val existante = paire ?: generer().also { paire = it }
    return existante.publiqueBase64
  }

  /**
   * Prend un message reçu de la passerelle, ou un bloc d'amorçage collé.
   *
   * Les deux ont le même format, et c'est voulu : ADR-0026 D6 dit que le premier accès ne
   * peut pas venir du tunnel, donc l'amorçage arrive hors canal, mais rien ne justifie deux
   * analyseurs pour une même chose.
   */
  fun accepter(texte: String): Issue {
    val maintenant = horloge()
    return when (val message = lireMessageVpn(texte, maintenant)) {
      null -> Issue.Refusee(REFUS_MESSAGE)
      is MessageVpn.Accordee -> {
        depot.ecrire(message.session)
        // Une session nouvellement accordée efface une révocation antérieure : la passerelle
        // ne l'aurait pas accordée si le droit n'avait pas été rétabli.
        depot.poserRevocation(null)
        Issue.Faite
      }
      is MessageVpn.Revoquee -> {
        appliquerRevocation(message.revocation)
        Issue.Faite
      }
    }
  }

  /**
   * Allume le tunnel pour la session rangée.
   *
   * Refuse plutôt que d'essayer quand une règle le dit : sans session, sur une session
   * expirée, ou après une révocation. Essayer quand même produirait un échec de poignée de
   * main sans message utile, et l'utilisateur chercherait au mauvais endroit.
   */
  suspend fun allumer(): Issue {
    val indisponible = client.indisponible()
    if (indisponible != null) return refus(indisponible)
    val revocation = depot.revocation()
    if (revocation != null) return refus(motifLisible(revocation))
    val session = depot.lire() ?: return refus(REFUS_SANS_SESSION)
    if (session.expiree(horloge())) return refus(REFUS_EXPIREE)

    EtatTunnelCourant.poser(EtatTunnel.DEMARRAGE)
    val cles = paire ?: generer().also { paire = it }
    val echec = client.allumer(session, cles)
    return if (echec == null) {
      EtatTunnelCourant.poser(EtatTunnel.ALLUME)
      Issue.Faite
    } else {
      EtatTunnelCourant.poser(EtatTunnel.ECHEC, echec)
      Issue.Refusee(echec)
    }
  }

  /**
   * Éteint le tunnel, à la demande de l'utilisateur.
   *
   * La clé privée est effacée : la session suivante en tirera une neuve, ce que D4 demande
   * de toute façon. Garder la clé après extinction serait conserver un secret sans emploi.
   */
  suspend fun eteindre(): Issue {
    EtatTunnelCourant.poser(EtatTunnel.ARRET)
    val echec = client.eteindre()
    paire?.effacerPrivee()
    paire = null
    return if (echec == null) {
      EtatTunnelCourant.poser(EtatTunnel.ETEINT)
      Issue.Faite
    } else {
      EtatTunnelCourant.poser(EtatTunnel.ECHEC, echec)
      Issue.Refusee(echec)
    }
  }

  /**
   * Prend acte d'une révocation.
   *
   * La passerelle a déjà retiré la clé de ses pairs avant d'envoyer ce message, donc le
   * tunnel est déjà tombé ou va tomber. On ne « coupe » pas : on cesse de réessayer, et on
   * le dit. Une clé révoquée n'est jamais réactivée, elle est remplacée (ADR-0025 D3).
   */
  fun appliquerRevocation(revocation: RevocationVpn) {
    depot.poserRevocation(revocation.motif)
    paire?.effacerPrivee()
    paire = null
    // L'adresse reste réservée à l'utilisateur (D2), sauf suppression : dans ce seul cas la
    // session rangée ne vaut plus rien et la garder ferait proposer un bouton inutile.
    if (revocation.motif == MotifRevocation.UTILISATEUR_SUPPRIME) depot.effacer()
    EtatTunnelCourant.poser(EtatTunnel.ETEINT, motifLisible(revocation.motif))
  }

  private fun refus(motif: String): Issue {
    EtatTunnelCourant.poser(EtatTunnel.ECHEC, motif)
    return Issue.Refusee(motif)
  }

  companion object {
    const val REFUS_MESSAGE = "Ce texte n'est pas un message du canal d'accès TrustiVPN."
    const val REFUS_SANS_SESSION = "Aucune session : demandez l'ouverture, ou collez le bloc d'amorçage."
    const val REFUS_EXPIREE = "La session a expiré. Demandez son renouvellement."

    fun motifLisible(motif: MotifRevocation): String = when (motif) {
      MotifRevocation.CLE_REVOQUEE ->
        "La clé de session a été révoquée. Vous pouvez en demander une nouvelle."
      MotifRevocation.UTILISATEUR_SUSPENDU ->
        "Votre accès est suspendu. Aucune nouvelle clé ne sera délivrée tant qu'il l'est."
      MotifRevocation.UTILISATEUR_SUPPRIME ->
        "Votre accès a été supprimé. Adressez vous à l'administrateur de votre organisation."
    }
  }
}
