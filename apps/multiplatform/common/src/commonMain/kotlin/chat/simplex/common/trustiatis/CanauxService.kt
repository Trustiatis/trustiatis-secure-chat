package chat.simplex.common.trustiatis

import kotlinx.datetime.Instant

/**
 * Les canaux de service, et le code à deux facteurs qu'un seul d'entre eux transporte.
 *
 * Trois règles d'ADR-0026 vivent ici, et aucune n'est une préférence d'affichage :
 *
 *  - D1 : trois classes de canaux, trois correspondants distincts. Un même correspondant ne peut
 *    pas tenir deux classes, parce que le cloisonnement est tout l'objet de la décision ;
 *  - D5 : un code dit ce qu'il autorise. Les cinq champs du contrat sont requis, et un message
 *    incomplet n'obtient aucun rendu de service. Voir CONTRAT-2FA.md du dépôt trusti-gateway ;
 *  - D7 : un correspondant de service non vérifié n'obtient aucun rendu de service. Le contrôle
 *    n'est pas ici, il est au point d'affichage, parce que c'est là que se trouve le `Contact`.
 *
 * Ce fichier ne parle pas au réseau et ne touche pas aux réglages : il ne fait que lire et
 * valider. C'est ce qui le rend testable sans appareil.
 */

/** Marque de la première ligne du message de code. Le contrat fige la version. */
const val MARQUE_2FA = "TRUSTIATIS-2FA/1"

private const val CODE_MIN = 4
private const val CODE_MAX = 12

enum class ClasseCanal(val cle: String) {
  /** Opéré par Trustiatis. Ne peut rien sur l'infrastructure du client. */
  SUPPORT("support"),

  /** Opéré par le client. Livre un code, ne le valide jamais (D3). */
  DEUX_FACTEURS("2fa"),

  /** Opéré par le client, joignable depuis le tunnel seulement (ADR-0021). */
  ACCES_VPN("acces");

  companion object {
    fun depuis(cle: String?): ClasseCanal? = entries.firstOrNull { it.cle == cle }
  }
}

/**
 * Le correspondant qui tient une classe de canal sur cet appareil.
 *
 * On retient l'identifiant du correspondant, pas son lien : c'est le `Contact` qui porte l'état
 * de vérification, et D7 s'y adosse.
 */
data class DesignationCanal(val classe: ClasseCanal, val contactId: Long)

/** Un code livré, avec le contexte que D5 rend obligatoire. */
data class CodeDeuxFacteurs(
  val code: String,
  val application: String,
  val action: String,
  val heure: Instant,
  val origine: String,
)

private fun estCodeAcceptable(code: String): Boolean =
  code.length in CODE_MIN..CODE_MAX && code.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }

/**
 * Lit un message de code, ou rend `null`.
 *
 * `null` veut dire « ce message n'obtient aucun rendu de service ». Il reste du texte ordinaire
 * dans la conversation, ce qui est volontaire : décorer un message incomplet accorderait
 * l'apparence d'un code livré par le service à un message qui n'en est pas un, et c'est
 * exactement cette apparence qu'un attaquant cherche.
 */
/**
 * Le découpage commun aux contrats de canal : une marque, puis des couples `clé=valeur`.
 *
 * Partagé par le contrat 2FA et le contrat d'accès, qui ont la même forme de message et les
 * mêmes refus de forme. Les deux contrats diffèrent par leurs champs, pas par leur syntaxe,
 * et tenir deux découpages aurait fait diverger leurs refus.
 *
 * Rend `null` dès qu'une ligne n'est pas interprétable, et jamais une table partielle : une
 * table partielle se lit comme un message valide auquel il manque un champ.
 */
internal fun lireChamps(texte: String, marque: String): Map<String, String>? {
  val lignes = texte.trimEnd().lines()
  if (lignes.firstOrNull() != marque) return null
  val champs = mutableMapOf<String, String>()
  for (ligne in lignes.drop(1)) {
    if (ligne.isBlank()) continue
    val coupe = ligne.indexOf('=')
    if (coupe <= 0) return null
    val cle = ligne.substring(0, coupe).trim()
    if (cle.isEmpty() || cle.any { it !in 'a'..'z' && it != '_' }) return null
    // Une clé répétée est refusée : départager deux valeurs serait un choix arbitraire, et
    // un émetteur qui en envoie deux a un défaut qu'il vaut mieux voir.
    if (champs.containsKey(cle)) return null
    champs[cle] = ligne.substring(coupe + 1).trim()
  }
  return champs
}

fun lireCodeDeuxFacteurs(texte: String): CodeDeuxFacteurs? {
  val champs = lireChamps(texte, MARQUE_2FA) ?: return null
  val code = champs["code"]?.filterNot { it == ' ' }
  val app = champs["app"]
  val action = champs["action"]
  val heureTexte = champs["time"]
  val origine = champs["origin"]
  if (code.isNullOrEmpty() || app.isNullOrEmpty() || action.isNullOrEmpty() ||
    heureTexte.isNullOrEmpty() || origine.isNullOrEmpty()
  ) return null
  if (!estCodeAcceptable(code)) return null
  // Le suffixe Z est exigé avant l'analyse : kotlinx-datetime accepte « +01:00 », que le contrat
  // refuse, parce qu'une heure sans UTC laisse l'utilisateur incapable de dater la demande.
  if (!heureTexte.endsWith("Z")) return null
  val heure = runCatching { Instant.parse(heureTexte) }.getOrNull() ?: return null
  return CodeDeuxFacteurs(code = code, application = app, action = action, heure = heure, origine = origine)
}
