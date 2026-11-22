package chat.simplex.common.trustiatis

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec

/**
 * Génération de la paire X25519 sur le Desktop, par le JVM embarqué.
 *
 * Aucune dépendance : `XDH` est dans le JDK depuis la version 11, et le paquet embarque
 * un JVM 17. C'est la raison pour laquelle le Desktop n'emporte pas la bibliothèque du
 * tunnel d'Android, qui n'existe que pour Android de toute façon.
 *
 * Le seul travail réel de ce fichier est un changement de convention d'octets, et c'est
 * là qu'une erreur serait silencieuse. Java expose la clé publique X25519 comme un entier,
 * la coordonnée u ; WireGuard, comme 32 octets de poids faible en tête. Une conversion
 * fautive produirait une clé de la bonne longueur, acceptée partout, et le tunnel
 * échouerait à la poignée de main sans dire pourquoi. Le test de ce fichier fait donc un
 * échange Diffie-Hellman dans les deux sens à partir des octets produits ici, et compare
 * les deux secrets : c'est la seule vérification qui prouve la conversion.
 */

/** 32 octets de poids faible en tête, la convention de WireGuard, depuis un entier Java. */
internal fun versOctetsPoidsFaibleEnTete(u: BigInteger): ByteArray {
  val grandBoutiste = u.toByteArray() // Java : poids fort en tête, avec un 0 de signe éventuel
  val sortie = ByteArray(LONGUEUR_CLE_X25519)
  var i = grandBoutiste.size - 1
  var j = 0
  while (i >= 0 && j < LONGUEUR_CLE_X25519) {
    sortie[j] = grandBoutiste[i]
    i--
    j++
  }
  // Un u plus long que 32 octets utiles n'est pas une coordonnée X25519 valide : on ne
  // tronque pas en silence une valeur qui ne devrait pas exister.
  require(i < 0 || grandBoutiste.copyOfRange(0, i + 1).all { it == 0.toByte() }) {
    "coordonnée u de plus de $LONGUEUR_CLE_X25519 octets"
  }
  return sortie
}

/** L'inverse, pour reconstruire une clé publique Java depuis les octets de WireGuard. */
internal fun depuisOctetsPoidsFaibleEnTete(octets: ByteArray): BigInteger {
  val grandBoutiste = ByteArray(octets.size + 1) // le 0 de tête garde le signe positif
  for (i in octets.indices) grandBoutiste[octets.size - i] = octets[i]
  return BigInteger(grandBoutiste)
}

actual fun genererPaireClesVpn(): PaireClesVpn {
  val generateur = KeyPairGenerator.getInstance("XDH")
  generateur.initialize(NamedParameterSpec.X25519)
  val paire = generateur.generateKeyPair()

  val privee = (paire.private as XECPrivateKey).scalar.orElseThrow {
    // Un fournisseur qui refuse de rendre le scalaire laisserait une clé que nous ne
    // pouvons pas donner au tunnel. Mieux vaut l'échec ici que plus loin.
    IllegalStateException("le fournisseur de clés ne rend pas le scalaire privé")
  }
  val publique = versOctetsPoidsFaibleEnTete((paire.public as XECPublicKey).u)

  val resultat = PaireClesVpn(privee, publique)
  privee.fill(0) // la copie de travail s'en va, celle de la paire reste effaçable
  return resultat
}
