package chat.simplex.common.trustiatis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Mise à jour de l'application, lue sur le portail de Trustiatis.
 *
 * Pourquoi ce fichier existe alors que l'amont a déjà un `AppUpdater`. Celui de l'amont
 * interroge `api.github.com/repos/simplex-chat/simplex-chat/releases` : il annoncerait les
 * versions de l'éditeur amont, qui ne s'installent pas par dessus les nôtres, et il
 * ferait sortir une requête vers un tiers à chaque démarrage. C'est le risque T-02, et
 * c'est pourquoi il a été neutralisé. Le verrou qui le désactive reste en place : ce
 * fichier ne le rouvre pas, il ouvre un autre chemin, vers notre propre manifeste.
 *
 * Ce que ce mécanisme fait, et ce qu'il ne fait pas. Il **invite** à mettre à jour, il
 * n'installe rien. Il n'y a pas d'installation silencieuse, pas de téléchargement en
 * arrière plan, et pas de remplacement de binaire : l'utilisateur est renvoyé vers le
 * portail, où chaque paquet porte son empreinte SHA-256. Une application de sécurité qui
 * remplacerait son propre exécutable sans que personne ne regarde serait précisément le
 * mécanisme qu'un attaquant cherche à détourner.
 */

const val URL_MANIFESTE_STABLE = "https://downloads.trustiatis.com/maj/stable.json"
const val URL_PORTAIL = "https://downloads.trustiatis.com/trustiatis-secure/"

/**
 * Le rang de la version Trustiatis bâtie sur l'amont courant.
 *
 * `7.1.4-ts2` se lit : deuxième version Trustiatis bâtie sur la 7.1.4 de l'amont. Ce
 * nombre se relève en même temps que `android.version_code` et `desktop.version_code` de
 * `gradle.properties`, et un test vérifie qu'il n'a pas été oublié.
 */
const val REVISION_TRUSTIATIS = 3

@Serializable
data class ArtefactPublie(
  val nom: String,
  val url: String,
  val plateforme: String,
  val format: String,
  val architecture: String,
  val taille: Long,
  val sha256: String,
)

@Serializable
data class ManifesteVersion(
  val schema: Int,
  val canal: String,
  val version: String,
  val amont: String,
  val date: String,
  @SerialName("notes_url") val notesUrl: String? = null,
  val artefacts: List<ArtefactPublie> = emptyList(),
)

/** Une version publiée : la version de l'amont, et notre rang sur elle. */
data class VersionTrustiatis(
  val majeur: Int,
  val mineur: Int,
  val correctif: Int,
  val revision: Int,
) : Comparable<VersionTrustiatis> {
  override fun compareTo(other: VersionTrustiatis): Int = when {
    majeur != other.majeur -> majeur.compareTo(other.majeur)
    mineur != other.mineur -> mineur.compareTo(other.mineur)
    correctif != other.correctif -> correctif.compareTo(other.correctif)
    else -> revision.compareTo(other.revision)
  }

  override fun toString(): String = "$majeur.$mineur.$correctif-ts$revision"

  companion object {
    private val motif = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)-ts(\\d+)$")

    /** Rend null sur tout ce qui n'est pas exactement notre forme. On ne devine pas. */
    fun depuis(texte: String): VersionTrustiatis? {
      val g = motif.matchEntire(texte.trim())?.groupValues ?: return null
      return VersionTrustiatis(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt())
    }
  }
}

enum class Plateforme { LINUX, WINDOWS, MACOS, ANDROID }

/**
 * Le résultat d'une recherche, dit en toutes lettres.
 *
 * Pas de `null` qui voudrait dire trois choses : « à jour », « je n'ai pas pu regarder »
 * et « le manifeste est illisible » appellent trois phrases différentes à l'écran.
 */
sealed class ResultatMiseAJour {
  data class AJour(val version: String) : ResultatMiseAJour()
  data class Disponible(val manifeste: ManifesteVersion, val artefacts: List<ArtefactPublie>) : ResultatMiseAJour()
  data class Injoignable(val detail: String) : ResultatMiseAJour()
  data class Illisible(val detail: String) : ResultatMiseAJour()
}

private val lecteur = Json { ignoreUnknownKeys = true }

fun lireManifeste(texte: String): ManifesteVersion? = try {
  val m = lecteur.decodeFromString<ManifesteVersion>(texte)
  // Un manifeste d'un schéma futur se lit peut être de travers. On s'arrête plutôt que
  // d'annoncer une version sur une interprétation douteuse.
  if (m.schema == 1) m else null
} catch (e: Exception) {
  null
}

fun artefactsPour(m: ManifesteVersion, plateforme: Plateforme): List<ArtefactPublie> {
  val nom = plateforme.name.lowercase()
  return m.artefacts.filter { it.plateforme.lowercase() == nom }
}

/**
 * Compare la version installée à celle du manifeste.
 *
 * `versionIgnoree` est la version que l'utilisateur a demandé à ne plus voir. Elle ne
 * masque que celle là : une version plus récente reparaîtra, sans quoi un refus d'un jour
 * deviendrait un silence définitif.
 */
fun evaluer(
  installee: VersionTrustiatis,
  m: ManifesteVersion,
  plateforme: Plateforme,
  versionIgnoree: String? = null,
): ResultatMiseAJour {
  val publiee = VersionTrustiatis.depuis(m.version)
    ?: return ResultatMiseAJour.Illisible("version du manifeste non reconnue : ${m.version}")
  if (publiee <= installee) return ResultatMiseAJour.AJour(installee.toString())
  if (versionIgnoree != null && versionIgnoree == m.version) return ResultatMiseAJour.AJour(installee.toString())
  val artefacts = artefactsPour(m, plateforme)
  if (artefacts.isEmpty()) {
    return ResultatMiseAJour.Illisible("aucun paquet pour ${plateforme.name.lowercase()} dans le manifeste")
  }
  return ResultatMiseAJour.Disponible(m, artefacts)
}
