package chat.simplex.common.trustiatis

import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.desktopPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Le tunnel sur un poste de bureau, par un composant privilégié séparé.
 *
 * Pourquoi l'application ne monte pas le tunnel elle même
 * ------------------------------------------------------
 * Créer une interface réseau et réécrire les routes exige `CAP_NET_ADMIN`. Une messagerie
 * qui tournerait avec cette capacité serait un défaut de sécurité, pas une fonction : toute
 * faille d'analyse d'un message reçu deviendrait une prise de contrôle du réseau de la
 * machine. L'application parle donc à un service, `trustivpn-aide`, qui détient seul le
 * privilège et n'accepte qu'un jeu de commandes fermé.
 *
 * Ce que la socket protège, et ce qu'elle ne protège pas
 * ----------------------------------------------------
 * La socket est dans `/run/trustivpn/`, appartient à root, et son groupe est `trustivpn` en
 * mode 0660 : seul un membre de ce groupe peut parler au service. La clé privée traverse
 * cette socket, en mémoire, et **n'est jamais écrite sur le disque**, ni par l'application
 * ni par le service, qui la passe à `wg` par son entrée standard.
 *
 * Ce qui n'est donc pas protégé, et il faut le dire : un attaquant qui a déjà le compte de
 * l'utilisateur peut parler à la socket, donc monter ou démonter le tunnel. Il pourrait de
 * toute façon lire les conversations de cet utilisateur. La socket ne prétend pas défendre
 * contre un compte compromis, elle évite que l'application ait le privilège.
 */
private val CHEMIN_SOCKET: Path = Path.of("/run/trustivpn/aide.sock")

private const val MARQUE_AIDE = "TRUSTIVPN-AIDE/1"
private const val ATTENTE_MS = 15_000

private class ClientTunnelDesktop : ClientTunnel {
  override fun indisponible(): String? = when {
    !desktopPlatform.isLinux() ->
      "Le tunnel TrustiVPN n'est pas encore porté sur cette plateforme de bureau."
    !Files.exists(CHEMIN_SOCKET) ->
      "Le service trustivpn-aide n'est pas installé ou n'est pas démarré sur ce poste."
    else -> null
  }

  // Rien à demander ici : le privilège est au service, et son installation est un acte
  // d'administration que l'application ne peut pas accomplir à la place de l'utilisateur.
  override fun preparer(): String? = indisponible()

  override suspend fun allumer(session: SessionVpn, paire: PaireClesVpn): String? =
    withContext(Dispatchers.IO) {
      val commande = buildString {
        appendLine(MARQUE_AIDE)
        appendLine("type=monter")
        appendLine("adresse=${session.adresse}")
        appendLine("routes=${session.routes.joinToString(",")}")
        appendLine("passerelle=${session.passerelle}")
        appendLine("cle_serveur=${Base64.getEncoder().encodeToString(session.clePubliqueServeur)}")
        session.dns?.let { appendLine("dns=$it") }
        // La clé privée est la dernière ligne, et elle ne va nulle part ailleurs.
        append("cle_privee=${paire.priveePourLeTunnelSeulement()}")
      }
      dialoguer(commande)
    }

  override suspend fun eteindre(): String? = withContext(Dispatchers.IO) {
    dialoguer("$MARQUE_AIDE\ntype=demonter")
  }

  /**
   * Envoie une commande et lit la réponse. Rend `null` si le service a répondu `ok`.
   *
   * La réponse est lue en entier avant d'être jugée : un service qui répondrait `ok` suivi
   * d'une erreur serait pris pour un succès si on s'arrêtait au premier mot.
   */
  private fun dialoguer(commande: String): String? {
    val adresse = UnixDomainSocketAddress.of(CHEMIN_SOCKET)
    return try {
      SocketChannel.open(StandardProtocolFamily.UNIX).use { canal ->
        canal.connect(adresse)
        canal.write(ByteBuffer.wrap(commande.toByteArray(StandardCharsets.UTF_8)))
        canal.shutdownOutput()
        val tampon = ByteBuffer.allocate(4096)
        val debut = System.currentTimeMillis()
        val sortie = StringBuilder()
        while (System.currentTimeMillis() - debut < ATTENTE_MS) {
          tampon.clear()
          val lus = canal.read(tampon)
          if (lus < 0) break
          if (lus == 0) continue
          tampon.flip()
          sortie.append(StandardCharsets.UTF_8.decode(tampon))
        }
        val reponse = sortie.toString().trim()
        when {
          reponse == "ok" -> null
          reponse.startsWith("erreur=") -> reponse.removePrefix("erreur=")
          reponse.isEmpty() -> "Le service trustivpn-aide n'a pas répondu."
          else -> "Réponse inattendue du service trustivpn-aide."
        }
      }
    } catch (e: Throwable) {
      // Le message de l'exception peut contenir le chemin de la socket, pas la clé : la
      // commande n'est jamais incluse dans une trace, et c'est pour cela qu'elle n'est pas
      // passée à Log ici.
      Log.e(TAG, "TrustiVPN : dialogue avec le service impossible : ${e.javaClass.simpleName}")
      "Dialogue impossible avec le service trustivpn-aide : ${e.message ?: e.javaClass.simpleName}"
    }
  }
}

actual val tunnel: ClientTunnel = ClientTunnelDesktop()
