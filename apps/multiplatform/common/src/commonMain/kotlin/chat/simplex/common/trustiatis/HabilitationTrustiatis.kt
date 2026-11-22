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
private const val CLE_JETON = "TrustiatisJetonEnAttente"

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

  /**
   * Le jeton d'enrôlement en attente, s'il y en a un.
   *
   * C'est le seul secret que cet appareil range, et il est rangé pour un temps borné, dont la
   * fin est définie : l'enrôlement le consomme. P-8 refuse un secret **permanent**, et c'est la
   * bonne frontière. Le jeton doit survivre à la fermeture de l'écran, parce qu'il est collé
   * depuis un courriel ou un portail avant que la passerelle ne soit joignable, et qu'exiger de
   * le recoller au bon moment pousserait l'utilisateur à le garder quelque part de moins sûr.
   *
   * Ce qui est refusé, en revanche : le garder après l'enrôlement. Un jeton consommé qui reste
   * là est un secret qui ne sert plus à rien et se vole encore.
   */
  fun jetonEnAttente(): String? = settings.getStringOrNull(CLE_JETON)

  fun poserJeton(jeton: String): Anomalie? {
    val anomalie = validerJeton(jeton)
    if (anomalie != null) return anomalie
    settings.putString(CLE_JETON, jeton)
    return null
  }

  fun consommerJeton() = settings.remove(CLE_JETON)

  fun etat(): EtatEnrolement = EtatEnrolement.depuis(settings.getStringOrNull(CLE_ETAT))

  /** L'identifiant que la passerelle a donné à cet appareil. Jamais un secret. */
  fun appareil(): String? = settings.getStringOrNull(CLE_APPAREIL)

  fun poserEnrolement(idAppareil: String) {
    settings.putString(CLE_APPAREIL, idAppareil)
    settings.putString(CLE_ETAT, EtatEnrolement.ENROLE.name)
    // L'enrôlement consomme le jeton. Il n'y a aucun cas où il faudrait le resservir : une
    // seconde demande avec le même jeton est soit un rejeu, soit un enrôlement qui recommence
    // et réclame alors un jeton neuf.
    consommerJeton()
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
    listOf(CLE_HOTE, CLE_PORT, CLE_ENDPOINT, CLE_ETAT, CLE_APPAREIL, CLE_JETON).forEach { settings.remove(it) }
    MagasinCanaux.oublierTout()
  }
}

/** Confort d'affichage seulement. Voir l'avertissement en tête de fichier. */
val estClient: Boolean get() = MagasinTrustiatis.etat() == EtatEnrolement.ENROLE

/**
 * Faut il montrer les écrans de canaux de service sur cet appareil.
 *
 * L'enrôlement seul ne peut pas servir de condition, et c'est une erreur qu'il valait mieux
 * voir tout de suite : le support Trustiatis est tenu par Trustiatis, pas par la passerelle du
 * client. Un client qui a du support sans TrustiVPN n'est jamais enrôlé, et serait écarté de
 * son propre canal de support.
 *
 * La condition est donc : cet appareil a été préparé pour un client, soit parce qu'il est
 * enrôlé, soit parce qu'une passerelle complète y a été rangée. Comme `estClient`, c'est du
 * confort : ce qui arrête un non client est la passerelle, qui n'accepte que les pairs créés
 * par un administrateur client, et le correspondant de service lui même, qui n'existe que
 * pour les locataires.
 */
val afficherCanauxService: Boolean
  get() = estClient || MagasinTrustiatis.configuration().complete
