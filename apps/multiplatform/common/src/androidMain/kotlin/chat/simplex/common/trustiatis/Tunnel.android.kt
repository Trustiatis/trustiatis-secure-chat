package chat.simplex.common.trustiatis

import android.content.Intent
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.androidAppContext
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wireguard.android.backend.Tunnel as TunnelWireGuard

/**
 * Le tunnel sous Android, par `wireguard-go`.
 *
 * Aucun fichier de configuration, et ce n'est pas un durcissement ajouté : `GoBackend` ne
 * lit pas de `.conf`. La configuration lui est passée en mémoire, par la socket de contrôle
 * de `wireguard-go`. Il n'y a donc pas de fichier à cacher, parce qu'il n'y a pas de
 * fichier. La clé privée n'existe que dans l'objet `Config`, le temps de l'appel.
 *
 * Le service de VPN est déclaré par le manifeste de la bibliothèque, pas par le nôtre : il
 * s'appelle `com.wireguard.android.backend.GoBackend$VpnService` et porte déjà
 * `BIND_VPN_SERVICE`. Notre manifeste n'ajoute que le type de service de premier plan, que
 * la bibliothèque ne déclare pas et qu'Android exige depuis la version 14.
 *
 * Ce que cette classe ne fait pas, volontairement : exclure une application du tunnel.
 * `Interface.Builder` sait le faire, et c'est précisément pour cela que l'appel n'est pas
 * écrit ici. Décision P-10 : tunnel allumé, tout passe dedans.
 */
private const val NOM_TUNNEL = "trustivpn"

private class TunnelTrustiatis : TunnelWireGuard {
  override fun getName(): String = NOM_TUNNEL

  override fun onStateChange(nouvel: TunnelWireGuard.State) {
    // Le système peut démonter le tunnel sans nous demander notre avis : perte de
    // permission, « VPN permanent » repris par un autre, mise en veille profonde. Sans ce
    // rappel, l'interface afficherait « allumé » pendant que le trafic sort en clair, ce
    // qui est le pire des deux états possibles.
    Log.i(TAG, "TrustiVPN : le tunnel passe à $nouvel")
    when (nouvel) {
      TunnelWireGuard.State.UP -> EtatTunnelCourant.poser(EtatTunnel.ALLUME)
      TunnelWireGuard.State.DOWN -> EtatTunnelCourant.poser(EtatTunnel.ETEINT)
      else -> {}
    }
  }
}

private class ClientTunnelAndroid : ClientTunnel {
  private val tunnelWg = TunnelTrustiatis()

  private val moteur: Backend by lazy { GoBackend(androidAppContext) }

  /**
   * L'autorisation du système, demandée une fois par installation.
   *
   * `VpnService.prepare` rend un `Intent` tant que l'utilisateur n'a pas accepté, et `null`
   * ensuite. On ne peut pas la contourner, et c'est voulu par Android : une application ne
   * détourne pas le trafic d'un appareil sans que son porteur l'ait vu.
   */
  fun intentionAutorisation(): Intent? = android.net.VpnService.prepare(androidAppContext)

  override fun indisponible(): String? =
    if (intentionAutorisation() == null) null
    else "Android doit autoriser le tunnel. Appuyez sur l'interrupteur pour voir la demande."

  override fun preparer(): String? {
    val intention = intentionAutorisation() ?: return null
    // NEW_TASK : cette couche n'a pas d'activité, et la fenêtre de consentement est une
    // activité du système. Sans ce drapeau, Android refuse le lancement.
    intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
      androidAppContext.startActivity(intention)
      "Android demande votre autorisation. Revenez ici après l'avoir accordée."
    } catch (e: Throwable) {
      Log.e(TAG, "TrustiVPN : la demande d'autorisation n'a pas pu être affichée : ${e.javaClass.simpleName}")
      "La demande d'autorisation n'a pas pu être affichée."
    }
  }

  override suspend fun allumer(session: SessionVpn, paire: PaireClesVpn): String? =
    withContext(Dispatchers.IO) {
      try {
        moteur.setState(tunnelWg, TunnelWireGuard.State.UP, configuration(session, paire))
        null
      } catch (e: Throwable) {
        Log.e(TAG, "TrustiVPN : le tunnel n'a pas pu monter : ${e.stackTraceToString()}")
        e.message ?: "Le tunnel n'a pas pu monter."
      }
    }

  override suspend fun eteindre(): String? = withContext(Dispatchers.IO) {
    try {
      moteur.setState(tunnelWg, TunnelWireGuard.State.DOWN, null)
      null
    } catch (e: Throwable) {
      Log.e(TAG, "TrustiVPN : le tunnel n'a pas pu tomber : ${e.stackTraceToString()}")
      e.message ?: "Le tunnel n'a pas pu être arrêté."
    }
  }

  /**
   * La configuration du tunnel, construite en mémoire.
   *
   * `setPrivateKey` reçoit la clé par la fonction nommée pour que l'appel se voie à la
   * relecture. C'est le seul endroit de l'application Android où la clé privée sort de son
   * objet, et il n'y en aura pas d'autre.
   */
  private fun configuration(session: SessionVpn, paire: PaireClesVpn): Config {
    val interfaceBuilder = Interface.Builder()
      .setKeyPair(
        com.wireguard.crypto.KeyPair(
          Key.fromBase64(paire.priveePourLeTunnelSeulement())
        )
      )
      .addAddress(InetNetwork.parse(session.adresse))
    session.dns?.let { interfaceBuilder.addDnsServer(java.net.InetAddress.getByName(it)) }

    val pair = Peer.Builder()
      .setPublicKey(Key.fromBytes(session.clePubliqueServeur))
      .setEndpoint(InetEndpoint.parse(session.passerelle))
      // Le battement garde la traversée de NAT ouverte. 25 secondes est la valeur de
      // WireGuard, et la passerelle compte dessus pour voir un pair disparaître.
      .setPersistentKeepalive(25)
    session.routes.forEach { pair.addAllowedIp(InetNetwork.parse(it)) }

    return Config.Builder()
      .setInterface(interfaceBuilder.build())
      .addPeer(pair.build())
      .build()
  }
}

actual val tunnel: ClientTunnel = ClientTunnelAndroid()

/**
 * L'intention à lancer pour obtenir l'autorisation du système, ou `null` si elle est acquise.
 *
 * Exposée à part parce que la lancer exige une activité, que cette couche n'a pas. L'écran
 * s'en charge.
 */
fun intentionAutorisationTunnel(): Intent? = android.net.VpnService.prepare(androidAppContext)
