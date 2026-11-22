package chat.simplex.common.trustiatis

import java.util.Base64

/**
 * La paire de clés X25519 du pair TrustiVPN, et sa génération.
 *
 * Pourquoi ce fichier existe et ce qu'il refuse de faire
 * ------------------------------------------------------
 * ADR-0025 D6 du programme TrustiGateway pose que le serveur ne reçoit **jamais** de clé
 * privée, et que le refus tient à l'absence du champ et non à une inspection : une clé
 * privée et une clé publique X25519 font toutes deux 32 octets et sont indiscernables par
 * leur forme. Il n'y a donc, ici, aucune fonction qui exporte, sérialise ou transmette la
 * partie privée ailleurs que vers le tunnel local. La seule sortie est `publiqueBase64`.
 *
 * Pourquoi deux implémentations
 * -----------------------------
 * `KeyPairGenerator("XDH")` n'existe sur Android qu'à partir de l'API 33, et `minSdk`
 * vaut 26. Le Desktop embarque un JVM 17 qui l'a. Android emploie donc la Curve25519 de
 * `com.wireguard.android:tunnel`, celle du tunnel lui même, ce qui évite d'avoir deux
 * implémentations de courbe dans le même appareil. Décision P-9, arbitrée par le Membre.
 *
 * Écrire notre propre courbe elliptique a été écarté, et ce n'est pas une question de
 * temps : une erreur de bit dans une multiplication scalaire ne se voit pas à l'essai,
 * elle se voit à l'audit, ou jamais.
 */

/** Longueur d'une clé X25519, en octets. Fixée par la courbe, elle ne varie pas. */
const val LONGUEUR_CLE_X25519 = 32

/**
 * Une paire de clés du pair.
 *
 * La partie privée est copiée à la construction et effaçable : un tableau partagé avec
 * l'appelant survivrait à l'effacement, et l'effacement est le seul moyen de ne pas
 * laisser la clé dans le tas jusqu'au prochain ramasse miettes.
 */
class PaireClesVpn(privee: ByteArray, publique: ByteArray) {
  init {
    require(privee.size == LONGUEUR_CLE_X25519) {
      "clé privée de ${privee.size} octets, ${LONGUEUR_CLE_X25519} attendus"
    }
    require(publique.size == LONGUEUR_CLE_X25519) {
      "clé publique de ${publique.size} octets, ${LONGUEUR_CLE_X25519} attendus"
    }
  }

  private val privee: ByteArray = privee.copyOf()

  /** La clé publique, seule valeur qui quitte l'appareil. */
  val publique: ByteArray = publique.copyOf()

  val publiqueBase64: String get() = Base64.getEncoder().encodeToString(publique)

  /**
   * La clé privée au format que le tunnel attend, pour la lui passer et rien d'autre.
   *
   * Elle n'est pas un getter : un getter se lit comme une propriété, et une propriété
   * finit dans un journal, une trace ou un `toString`. Appeler une fonction nommée ainsi
   * est un acte visible à la relecture.
   */
  fun priveePourLeTunnelSeulement(): String = Base64.getEncoder().encodeToString(privee)

  /** Efface la partie privée en place. La paire n'est plus utilisable ensuite. */
  fun effacerPrivee() {
    privee.fill(0)
  }

  /** Ni la clé privée ni sa longueur ne paraissent. */
  override fun toString(): String = "PaireClesVpn(publique=$publiqueBase64)"
}

/** Génère une paire X25519 sur l'appareil. La partie privée ne sort pas de cette méthode. */
expect fun genererPaireClesVpn(): PaireClesVpn

/**
 * Une clé publique reçue, décodée et vérifiée.
 *
 * La passerelle annonce les clés publiques de ses pairs en base64. Une valeur de la
 * bonne forme mais de la mauvaise longueur ne doit pas aller plus loin : le tunnel
 * l'accepterait en la complétant, et le pair ainsi formé ne serait pas celui qu'on croit.
 */
fun decoderClePublique(base64: String): ByteArray? {
  val octets = try {
    Base64.getDecoder().decode(base64.trim())
  } catch (_: IllegalArgumentException) {
    return null
  }
  return if (octets.size == LONGUEUR_CLE_X25519) octets else null
}
