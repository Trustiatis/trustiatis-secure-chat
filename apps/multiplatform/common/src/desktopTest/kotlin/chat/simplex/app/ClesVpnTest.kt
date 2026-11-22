package chat.simplex.app

import chat.simplex.common.trustiatis.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

/**
 * La seule vérification qui prouve la conversion d'octets du Desktop.
 *
 * Comparer les octets produits à une valeur écrite ici ne prouverait rien : il faudrait
 * l'avoir calculée avec le même code. On fait donc un échange Diffie-Hellman dans les deux
 * sens **à partir des octets que `genererPaireClesVpn` rend**, et on compare les deux
 * secrets. Si la conversion de la coordonnée u était fautive, les deux secrets
 * différeraient, et c'est exactement le défaut qui, sans ce test, ne se verrait qu'à la
 * poignée de main du tunnel, sans message utile.
 */
class ClesVpnTest {
  private val x25519 = NamedParameterSpec.X25519

  private fun privee(octets: ByteArray) =
    KeyFactory.getInstance("XDH").generatePrivate(XECPrivateKeySpec(x25519, octets))

  private fun publique(u: BigInteger) =
    KeyFactory.getInstance("XDH").generatePublic(XECPublicKeySpec(x25519, u))

  private fun secretPartage(priveeOctets: ByteArray, publiqueOctets: ByteArray): ByteArray {
    val accord = KeyAgreement.getInstance("XDH")
    accord.init(privee(priveeOctets))
    accord.doPhase(publique(depuisOctetsPoidsFaibleEnTete(publiqueOctets)), true)
    return accord.generateSecret()
  }

  private fun priveeDe(paire: PaireClesVpn): ByteArray =
    Base64.getDecoder().decode(paire.priveePourLeTunnelSeulement())

  @Test
  fun `les deux clés font trente deux octets`() {
    val paire = genererPaireClesVpn()
    assertEquals(32, paire.publique.size)
    assertEquals(32, priveeDe(paire).size)
    assertEquals(44, paire.publiqueBase64.length)
  }

  @Test
  fun `deux générations ne rendent pas la même clé`() {
    assertNotEquals(genererPaireClesVpn().publiqueBase64, genererPaireClesVpn().publiqueBase64)
  }

  @Test
  fun `l'échange aboutit au même secret dans les deux sens`() {
    val a = genererPaireClesVpn()
    val b = genererPaireClesVpn()
    val deAversB = secretPartage(priveeDe(a), b.publique)
    val deBversA = secretPartage(priveeDe(b), a.publique)
    assertContentEquals(deAversB, deBversA)
    assertEquals(32, deAversB.size)
    assertFalse(deAversB.all { it == 0.toByte() }, "un secret entièrement nul signale une clé dégénérée")
  }

  @Test
  fun `la conversion d'octets fait l'aller et le retour`() {
    val paire = genererPaireClesVpn()
    val u = depuisOctetsPoidsFaibleEnTete(paire.publique)
    assertContentEquals(paire.publique, versOctetsPoidsFaibleEnTete(u))
  }

  @Test
  fun `une petite valeur garde ses octets de tête`() {
    // Un défaut d'alignement ne se voit pas sur une valeur de 32 octets pleins : il se
    // voit quand la coordonnée est courte, et que le remplissage part du mauvais bout.
    val octets = versOctetsPoidsFaibleEnTete(BigInteger.valueOf(258L)) // 0x0102
    assertEquals(2.toByte(), octets[0])
    assertEquals(1.toByte(), octets[1])
    assertTrue(octets.drop(2).all { it == 0.toByte() })
  }

  @Test
  fun `effacer la clé privée la met à zéro`() {
    val paire = genererPaireClesVpn()
    paire.effacerPrivee()
    assertTrue(priveeDe(paire).all { it == 0.toByte() })
  }

  @Test
  fun `la trace n'imprime pas la clé privée`() {
    val paire = genererPaireClesVpn()
    val trace = paire.toString()
    assertTrue(trace.contains(paire.publiqueBase64))
    assertFalse(trace.contains(paire.priveePourLeTunnelSeulement()))
  }

  @Test
  fun `une clé publique de la bonne longueur est acceptée`() {
    val paire = genererPaireClesVpn()
    assertContentEquals(paire.publique, decoderClePublique(paire.publiqueBase64))
  }

  @Test
  fun `une clé publique trop courte est refusée`() {
    assertNull(decoderClePublique(Base64.getEncoder().encodeToString(ByteArray(31))))
  }

  @Test
  fun `une clé publique trop longue est refusée`() {
    assertNull(decoderClePublique(Base64.getEncoder().encodeToString(ByteArray(33))))
  }

  @Test
  fun `une valeur qui n'est pas du base64 est refusée`() {
    assertNull(decoderClePublique("ceci n'est pas du base64 !"))
  }

  @Test
  fun `les espaces autour d'une clé collée sont tolérés`() {
    val paire = genererPaireClesVpn()
    assertContentEquals(paire.publique, decoderClePublique("  ${paire.publiqueBase64}\n"))
  }
}
