package chat.simplex.common.trustiatis

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Les deux cibles sont des JVM et l'implémentation est la même, mais `commonMain` ne peut
 * pas appeler `java.*` : d'où ce doublon, qui est une contrainte du langage et non un
 * oubli. Toute correction ici se reporte dans l'autre fichier.
 */
actual fun recupererTexte(url: String, socks: String?): Result<String> = runCatching {
  if (!url.startsWith("https://")) error("adresse en clair refusée")
  val mandataire = socks?.let {
    val hote = it.substringBeforeLast(':', "").ifEmpty { "localhost" }.trim('[', ']')
    val port = it.substringAfterLast(':', "").toIntOrNull() ?: error("mandataire SOCKS illisible")
    Proxy(Proxy.Type.SOCKS, InetSocketAddress(hote, port))
  }
  val c = (if (mandataire != null) URL(url).openConnection(mandataire) else URL(url).openConnection()) as HttpsURLConnection
  c.connectTimeout = 15_000
  c.readTimeout = 15_000
  c.instanceFollowRedirects = false
  // Rien qui identifie l'appareil ou la personne. Le serveur n'a pas à en apprendre plus
  // que le fait qu'une application demande son manifeste.
  c.setRequestProperty("User-Agent", "Trustiatis Secure")
  c.setRequestProperty("Accept", "application/json")
  try {
    if (c.responseCode != 200) error("réponse ${c.responseCode}")
    // Le manifeste pèse quelques kilooctets. Le plafond évite qu'une réponse inattendue
    // remplisse la mémoire.
    val octets = c.inputStream.readNBytes(512 * 1024)
    octets.toString(Charsets.UTF_8)
  } finally {
    c.disconnect()
  }
}

actual fun plateformeCourante(): Plateforme = Plateforme.ANDROID

actual fun ouvrirAdresse(url: String) {
  runCatching {
    val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    chat.simplex.common.platform.androidAppContext.startActivity(i)
  }.onFailure { chat.simplex.common.platform.Log.e("Trustiatis", "ouverture de l'adresse impossible: ${it.message}") }
}
