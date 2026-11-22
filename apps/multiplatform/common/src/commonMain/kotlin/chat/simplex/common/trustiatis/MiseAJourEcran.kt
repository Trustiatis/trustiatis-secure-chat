package chat.simplex.common.trustiatis

import chat.simplex.common.BuildConfigCommon
import chat.simplex.common.model.ChatController
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La recherche de mise à jour, et l'invitation qui va avec.
 *
 * Trois règles tenues ici, et chacune a sa raison :
 *
 *  - on **invite**, on n'installe pas. Le renvoi se fait vers le portail, où chaque paquet
 *    porte son empreinte ;
 *  - on respecte le mandataire SOCKS de l'application. Sortir en direct pour vérifier une
 *    version trahirait précisément ceux qui ont réglé l'application pour ne pas le faire ;
 *  - un refus porte sur **une version**, pas sur le mécanisme. « Ignorer » masque celle là,
 *    la suivante reparaît.
 */

private const val CLE_VERSION_IGNOREE = "TrustiatisMajVersionIgnoree"
private const val CLE_DERNIERE_RECHERCHE = "TrustiatisMajDerniereRecherche"
private const val UN_JOUR_MS = 24L * 60 * 60 * 1000

fun versionInstallee(): VersionTrustiatis? {
  val nom = if (appPlatform.isAndroid) BuildConfigCommon.ANDROID_VERSION_NAME else BuildConfigCommon.DESKTOP_VERSION_NAME
  return VersionTrustiatis.depuis("$nom-ts$REVISION_TRUSTIATIS")
}

private fun socksCourant(): String? =
  ChatController.getNetCfg().socksProxy?.takeIf { it.isNotBlank() }

suspend fun chercherMiseAJour(): ResultatMiseAJour = withContext(Dispatchers.IO) {
  val installee = versionInstallee()
    ?: return@withContext ResultatMiseAJour.Illisible("version installée non reconnue")
  val texte = recupererTexte(URL_MANIFESTE_STABLE, socksCourant())
    .getOrElse { return@withContext ResultatMiseAJour.Injoignable(it.message ?: "sans détail") }
  val manifeste = lireManifeste(texte)
    ?: return@withContext ResultatMiseAJour.Illisible("manifeste illisible ou de schéma inconnu")
  settings.putLong(CLE_DERNIERE_RECHERCHE, System.currentTimeMillis())
  evaluer(installee, manifeste, plateformeCourante(), settings.getStringOrNull(CLE_VERSION_IGNOREE))
}

private fun enMegaoctets(octets: Long): String = "${(octets + 524_288) / 1_048_576} Mo"

fun proposerMiseAJour(d: ResultatMiseAJour.Disponible) {
  val m = d.manifeste
  val taille = d.artefacts.minOf { it.taille }
  AlertManager.shared.showAlertDialog(
    title = generalGetString(MR.strings.ts_maj_dispo_titre),
    text = String.format(generalGetString(MR.strings.ts_maj_dispo_texte), m.version, m.date, enMegaoctets(taille)),
    confirmText = generalGetString(MR.strings.ts_maj_ouvrir_portail),
    onConfirm = { ouvrirAdresse(URL_PORTAIL) },
    dismissText = generalGetString(MR.strings.ts_maj_plus_tard),
    // Le texte vient du manifeste, donc d'ailleurs : il est affiché littéralement.
    parseHtml = false,
  )
}

/**
 * Recherche lancée par l'utilisateur : elle dit toujours quelque chose, y compris
 * « je n'ai pas pu regarder ». Une recherche manuelle silencieuse laisse croire que tout
 * va bien alors que le réseau était coupé.
 */
fun chercherMiseAJourManuellement() = withLongRunningApi {
  when (val r = chercherMiseAJour()) {
    is ResultatMiseAJour.Disponible -> proposerMiseAJour(r)
    is ResultatMiseAJour.AJour -> AlertManager.shared.showAlertMsg(
      generalGetString(MR.strings.ts_maj_titre),
      String.format(generalGetString(MR.strings.ts_maj_a_jour), r.version),
    )
    is ResultatMiseAJour.Injoignable -> AlertManager.shared.showAlertMsg(
      generalGetString(MR.strings.ts_maj_titre),
      generalGetString(MR.strings.ts_maj_injoignable),
    )
    is ResultatMiseAJour.Illisible -> AlertManager.shared.showAlertMsg(
      generalGetString(MR.strings.ts_maj_titre),
      generalGetString(MR.strings.ts_maj_illisible),
    )
  }
}

/**
 * Recherche au démarrage, au plus une fois par jour, et muette sauf s'il y a une version.
 *
 * Muette est voulu : une application qui annonce « vous êtes à jour » à chaque lancement
 * apprend à l'utilisateur à fermer ses fenêtres sans les lire, et c'est exactement
 * l'habitude qu'il ne faut pas lui donner.
 */
fun chercherMiseAJourAuDemarrage() = withLongRunningApi {
  val derniere = settings.getLong(CLE_DERNIERE_RECHERCHE, 0)
  if (System.currentTimeMillis() - derniere < UN_JOUR_MS) return@withLongRunningApi
  val r = chercherMiseAJour()
  if (r is ResultatMiseAJour.Disponible) proposerMiseAJour(r)
}

fun ignorerVersion(version: String) = settings.putString(CLE_VERSION_IGNOREE, version)
