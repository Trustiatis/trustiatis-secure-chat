package chat.simplex.common.trustiatis

import androidx.compose.runtime.mutableStateOf

/**
 * L'état du tunnel TrustiVPN et le client qui l'ouvre, côté plateforme.
 *
 * Deux décisions du Membre vivent ici, et elles tirent en sens contraire, donc elles sont
 * écrites ensemble pour qu'on ne puisse pas en appliquer une sans l'autre (voir P-10 de
 * docs/DECISIONS.md) :
 *
 *  - **l'interrupteur existe.** Le client peut avoir besoin de couper le tunnel pour
 *    retrouver son réseau local, et c'est une raison légitime. La messagerie continue de
 *    fonctionner tunnel éteint, parce que le tunnel ne porte que la passerelle du client,
 *    pas les serveurs de messagerie ;
 *  - **tunnel allumé, tout passe dedans.** Les routes viennent de la passerelle, aucune
 *    application n'est exclue, et rien ici n'ajoute d'exception. Une liste d'exclusion
 *    serait la première chose qu'un attaquant chercherait à allonger.
 *
 * Ce que cette couche ne peut pas garantir, et qu'il faut dire à l'utilisateur plutôt que
 * de le laisser croire : l'étanchéité absolue sous Android ne vient pas de l'application.
 * Ce sont « VPN permanent » et « Bloquer les connexions sans VPN », que seuls l'utilisateur
 * ou une politique d'administration d'appareil peuvent activer. Une application ne peut pas
 * se les accorder.
 */
enum class EtatTunnel {
  /** Aucun tunnel. Le trafic sort normalement, et la messagerie fonctionne. */
  ETEINT,

  /** Le tunnel se monte. Rien ne garantit encore qu'il portera le trafic. */
  DEMARRAGE,

  /** Le tunnel est monté et porte les routes de la session. */
  ALLUME,

  /** Le tunnel se démonte. */
  ARRET,

  /** La dernière tentative a échoué. `motif` dit pourquoi. */
  ECHEC,
}

/**
 * L'état observable par l'interface.
 *
 * Un état global et non un état par écran : il n'y a qu'un tunnel par appareil, et deux
 * écrans qui en afficheraient deux états différents mentiraient tous les deux une fois sur
 * deux.
 */
object EtatTunnelCourant {
  val etat = mutableStateOf(EtatTunnel.ETEINT)

  /** Le motif du dernier échec, ou du dernier refus. Effacé dès qu'une tentative aboutit. */
  val motif = mutableStateOf<String?>(null)

  internal fun poser(nouvel: EtatTunnel, raison: String? = null) {
    etat.value = nouvel
    motif.value = raison
  }
}

/**
 * Le client de tunnel de la plateforme.
 *
 * Les deux implémentations ne se ressemblent pas, et ce n'est pas un défaut de conception :
 * sous Android le tunnel est un service du système que l'application peut démarrer, alors
 * que sur un poste de bureau créer une interface réseau et réécrire les routes exige des
 * privilèges qu'une messagerie ne doit pas avoir. Le Desktop passe donc par un composant
 * privilégié séparé.
 */
interface ClientTunnel {
  /**
   * `null` si le tunnel est utilisable ici et maintenant, sinon la raison, destinée à
   * l'utilisateur. Appelé avant d'offrir l'interrupteur, pour ne pas proposer un bouton
   * qui ne mènerait nulle part.
   */
  fun indisponible(): String?

  /**
   * Demande l'autorisation que la plateforme exige, s'il en faut une.
   *
   * Rend `null` quand il n'y a plus rien à demander, sinon le texte à montrer. Séparé de
   * `allumer` parce que la demande est un acte de l'utilisateur, pas un échec : sous
   * Android, le système affiche sa propre fenêtre, et l'application n'apprend la réponse
   * qu'au tour suivant.
   */
  fun preparer(): String?

  /** Monte le tunnel. Rend `null` si c'est fait, sinon le motif de l'échec. */
  suspend fun allumer(session: SessionVpn, paire: PaireClesVpn): String?

  /** Démonte le tunnel. Rend `null` si c'est fait, sinon le motif de l'échec. */
  suspend fun eteindre(): String?
}

expect val tunnel: ClientTunnel
