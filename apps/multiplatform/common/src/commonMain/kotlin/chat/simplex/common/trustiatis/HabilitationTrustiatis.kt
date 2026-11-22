package chat.simplex.common.trustiatis

import chat.simplex.common.platform.settings

/**
 * Ce que l'appareil a le droit de voir, et où la configuration est rangée.
 *
 * Demande du Membre : les canaux de service ajoutés ensuite doivent être préservés des
 * personnes qui utilisent l'application sans être clientes. Le cloisonnement ne peut pas
 * reposer sur l'interface, parce qu'un écran masqué n'est pas un contrôle d'accès, et que
 * c'est écrit ainsi au chapitre 5 du dossier TrustiVPN. Il repose donc sur deux choses,
 * dans cet ordre :
 *
 *  1. la passerelle n'accepte que des pairs qu'un administrateur client a créés
 *     (ADR-0025 D5). C'est là qu'un non client est arrêté, et nulle part ailleurs ;
 *  2. l'application ne montre les écrans de service qu'à un appareil enrôlé. C'est du
 *     confort, pas une sécurité, et ce commentaire est là pour que personne ne l'oublie.
 *
 * Toute lecture de `estClient` qui servirait à décider autre chose qu'un affichage est un
 * défaut.
 */

private const val CLE_HOTE = "TrustiatisPasserelleHote"
private const val CLE_PORT = "TrustiatisPasserellePortVpn"
private const val CLE_ENDPOINT = "TrustiatisPasserelleEndpoint"
private const val CLE_ETAT = "TrustiatisEnrolementEtat"
private const val CLE_APPAREIL = "TrustiatisEnrolementAppareil"

enum class EtatEnrolement {
  /** Aucun jeton n'a jamais abouti sur cet appareil. */
  NON_ENROLE,

  /** Un jeton a été accepté : l'appareil a une identité connue de la passerelle. */
  ENROLE,

  /** La passerelle a révoqué cet appareil. Un nouveau jeton est nécessaire. */
  REVOQUE;

  companion object {
    fun depuis(nom: String?): EtatEnrolement = entries.firstOrNull { it.name == nom } ?: NON_ENROLE
  }
}

object MagasinTrustiatis {
  fun configuration(): ConfigurationPasserelle = ConfigurationPasserelle(
    hote = settings.getString(CLE_HOTE, ""),
    portVpn = settings.getInt(CLE_PORT, PORT_VPN_DEFAUT),
    endpoint = settings.getString(CLE_ENDPOINT, ""),
  )

  /** Refuse d'enregistrer une configuration invalide : un réglage à demi faux est pire qu'absent. */
  fun enregistrer(c: ConfigurationPasserelle): List<Anomalie> {
    val anomalies = valider(c)
    if (anomalies.isNotEmpty()) return anomalies
    settings.putString(CLE_HOTE, c.hote)
    settings.putInt(CLE_PORT, c.portVpn)
    settings.putString(CLE_ENDPOINT, c.endpoint)
    return emptyList()
  }

  fun etat(): EtatEnrolement = EtatEnrolement.depuis(settings.getStringOrNull(CLE_ETAT))

  /** L'identifiant que la passerelle a donné à cet appareil. Jamais un secret. */
  fun appareil(): String? = settings.getStringOrNull(CLE_APPAREIL)

  fun poserEnrolement(idAppareil: String) {
    settings.putString(CLE_APPAREIL, idAppareil)
    settings.putString(CLE_ETAT, EtatEnrolement.ENROLE.name)
  }

  fun marquerRevoque() {
    settings.putString(CLE_ETAT, EtatEnrolement.REVOQUE.name)
  }

  /**
   * Efface la configuration et l'enrôlement de cet appareil.
   *
   * Ce que cela ne fait pas, et qu'il ne faut pas laisser croire : révoquer le pair côté
   * passerelle. Un appareil oublié ici reste autorisé là bas jusqu'à ce qu'un
   * administrateur client le révoque. L'écran doit le dire.
   */
  fun oublier() {
    listOf(CLE_HOTE, CLE_PORT, CLE_ENDPOINT, CLE_ETAT, CLE_APPAREIL).forEach { settings.remove(it) }
  }
}

/** Confort d'affichage seulement. Voir l'avertissement en tête de fichier. */
val estClient: Boolean get() = MagasinTrustiatis.etat() == EtatEnrolement.ENROLE
