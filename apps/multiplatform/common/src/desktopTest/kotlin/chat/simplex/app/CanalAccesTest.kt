package chat.simplex.app

import chat.simplex.common.trustiatis.*
import kotlinx.datetime.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Un cas par refus nommé dans CONTRAT-ACCES.md, plus les acceptations.
 *
 * Le contrat et ces cas vont ensemble : un refus retiré du contrat sans son cas de test, ou
 * l'inverse, et les deux bouts ne sont plus d'accord sur ce qui est un message.
 */
class CanalAccesTest {
  private val maintenant = Instant.parse("2026-11-22T23:00:00Z")
  private val cleServeur = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

  private fun session(
    type: String = "session",
    adresse: String = "10.42.7.19/32",
    routes: String = "0.0.0.0/0, ::/0",
    passerelle: String = "203.0.113.44:51820",
    cle: String = cleServeur,
    echeance: String = "2026-11-23T23:00:00Z",
    dns: String? = "10.42.0.1",
    marque: String = MARQUE_VPN,
  ): String = buildString {
    appendLine(marque)
    appendLine("type=$type")
    appendLine("adresse=$adresse")
    appendLine("routes=$routes")
    appendLine("passerelle=$passerelle")
    appendLine("cle_serveur=$cle")
    append("echeance=$echeance")
    if (dns != null) append("\ndns=$dns")
  }

  // --- Acceptations ------------------------------------------------------------------

  @Test
  fun `une session complète est lue`() {
    val m = lireMessageVpn(session(), maintenant)
    val accordee = assertIs<MessageVpn.Accordee>(m)
    val s = accordee.session
    assertEquals("10.42.7.19/32", s.adresse)
    assertEquals(listOf("0.0.0.0/0", "::/0"), s.routes)
    assertEquals("203.0.113.44", s.passerelleHote)
    assertEquals(51820, s.passerellePort)
    assertEquals("203.0.113.44:51820", s.passerelle)
    assertEquals("10.42.0.1", s.dns)
    assertContentEquals(ByteArray(32) { 7 }, s.clePubliqueServeur)
  }

  @Test
  fun `le résolveur est facultatif`() {
    val accordee = assertIs<MessageVpn.Accordee>(lireMessageVpn(session(dns = null), maintenant))
    assertNull(accordee.session.dns)
  }

  @Test
  fun `une passerelle IPv6 est encadrée de crochets dans l'adresse rendue`() {
    val m = lireMessageVpn(session(passerelle = "[fd00::1]:51820"), maintenant)
    val s = assertIs<MessageVpn.Accordee>(m).session
    assertEquals("fd00::1", s.passerelleHote)
    assertEquals("[fd00::1]:51820", s.passerelle)
  }

  @Test
  fun `une clé inconnue est ignorée, pour que le contrat puisse grandir`() {
    val texte = session() + "\nchamp_du_futur=quelque chose"
    assertIs<MessageVpn.Accordee>(lireMessageVpn(texte, maintenant))
  }

  @Test
  fun `une révocation est lue, avec son motif`() {
    val texte = "$MARQUE_VPN\ntype=revocation\nmotif=utilisateur_supprime\ninstant=2026-11-22T23:40:00Z"
    val r = assertIs<MessageVpn.Revoquee>(lireMessageVpn(texte, maintenant)).revocation
    assertEquals(MotifRevocation.UTILISATEUR_SUPPRIME, r.motif)
    assertEquals(Instant.parse("2026-11-22T23:40:00Z"), r.instant)
  }

  // --- Refus nommés par le contrat ---------------------------------------------------

  @Test
  fun `marque absente`() {
    assertNull(lireMessageVpn(session(marque = "TRUSTIATIS-VPN/2"), maintenant))
    assertNull(lireMessageVpn("type=session", maintenant))
  }

  @Test
  fun `type inconnu`() {
    assertNull(lireMessageVpn(session(type = "renouvellement"), maintenant))
  }

  @Test
  fun `une demande reçue n'est pas un message pour l'appareil`() {
    val texte = "$MARQUE_VPN\ntype=demande\ncle=$cleServeur\nappareil=x\ninstant=2026-11-22T23:00:00Z"
    assertNull(lireMessageVpn(texte, maintenant))
  }

  @Test
  fun `clé répétée`() {
    assertNull(lireMessageVpn(session() + "\nadresse=10.42.7.20/32", maintenant))
  }

  @Test
  fun `valeur vide`() {
    assertNull(lireMessageVpn(session(adresse = ""), maintenant))
    assertNull(lireMessageVpn(session(routes = ""), maintenant))
    assertNull(lireMessageVpn(session(cle = ""), maintenant))
  }

  @Test
  fun `clé serveur qui ne décode pas trente deux octets`() {
    assertNull(lireMessageVpn(session(cle = Base64.getEncoder().encodeToString(ByteArray(31))), maintenant))
    assertNull(lireMessageVpn(session(cle = Base64.getEncoder().encodeToString(ByteArray(33))), maintenant))
    assertNull(lireMessageVpn(session(cle = "pas du base64 !"), maintenant))
  }

  @Test
  fun `adresse sans préfixe, ou de préfixe hors limites`() {
    assertNull(lireMessageVpn(session(adresse = "10.42.7.19"), maintenant))
    assertNull(lireMessageVpn(session(adresse = "10.42.7.19/33"), maintenant))
    assertNull(lireMessageVpn(session(adresse = "10.42.7.19/"), maintenant))
    assertNull(lireMessageVpn(session(adresse = "/32"), maintenant))
    assertNull(lireMessageVpn(session(adresse = "fd00::1/129"), maintenant))
  }

  @Test
  fun `un préfixe à zéro de tête est refusé plutôt que corrigé`() {
    assertNull(lireMessageVpn(session(adresse = "10.42.7.19/032"), maintenant))
  }

  @Test
  fun `échéance sans Z, ou déjà passée`() {
    assertNull(lireMessageVpn(session(echeance = "2026-11-23T23:00:00+01:00"), maintenant))
    assertNull(lireMessageVpn(session(echeance = "2026-11-22T22:59:59Z"), maintenant))
    assertNull(lireMessageVpn(session(echeance = maintenant.toString()), maintenant))
    assertNull(lireMessageVpn(session(echeance = "hier"), maintenant))
  }

  @Test
  fun `routes invalides`() {
    assertNull(lireMessageVpn(session(routes = ","), maintenant))
    assertNull(lireMessageVpn(session(routes = "0.0.0.0"), maintenant))
    assertNull(lireMessageVpn(session(routes = "0.0.0.0/0, pas une route"), maintenant))
  }

  @Test
  fun `passerelle sans port, ou port hors limites`() {
    assertNull(lireMessageVpn(session(passerelle = "203.0.113.44"), maintenant))
    assertNull(lireMessageVpn(session(passerelle = "203.0.113.44:0"), maintenant))
    assertNull(lireMessageVpn(session(passerelle = "203.0.113.44:65536"), maintenant))
    assertNull(lireMessageVpn(session(passerelle = "203.0.113.44:vingt"), maintenant))
  }

  @Test
  fun `une adresse IPv6 sans crochets est refusée plutôt que devinée`() {
    // Le dernier groupe peut être un port ou une partie de l'adresse. Le service
    // privilégié du Desktop refuse la même écriture, et wg l'exige aussi.
    assertNull(lireMessageVpn(session(passerelle = "fd00::1:51820"), maintenant))
  }

  @Test
  fun `résolveur qui n'est pas une adresse`() {
    assertNull(lireMessageVpn(session(dns = "resolveur.interne"), maintenant))
  }

  @Test
  fun `motif de révocation inconnu`() {
    val texte = "$MARQUE_VPN\ntype=revocation\nmotif=parce que\ninstant=2026-11-22T23:40:00Z"
    assertNull(lireMessageVpn(texte, maintenant))
  }

  // --- Échéance et renouvellement ----------------------------------------------------

  @Test
  fun `la session est renouvelable une heure avant l'échéance, pas avant`() {
    val s = assertIs<MessageVpn.Accordee>(lireMessageVpn(session(), maintenant)).session
    assertFalse(s.renouvelable(Instant.parse("2026-11-23T21:59:59Z")))
    assertTrue(s.renouvelable(Instant.parse("2026-11-23T22:00:00Z")))
    assertFalse(s.expiree(Instant.parse("2026-11-23T22:59:59Z")))
    assertTrue(s.expiree(Instant.parse("2026-11-23T23:00:00Z")))
  }

  @Test
  fun `deux sessions de même contenu sont égales malgré leur tableau de clé`() {
    val a = assertIs<MessageVpn.Accordee>(lireMessageVpn(session(), maintenant)).session
    val b = assertIs<MessageVpn.Accordee>(lireMessageVpn(session(), maintenant)).session
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  // --- Écriture de la demande --------------------------------------------------------

  @Test
  fun `la demande est écrite dans l'ordre du contrat`() {
    val paire = genererPaireClesVpn()
    val texte = ecrireDemande(paire.publiqueBase64, "Portable de Mathieu", maintenant)
    assertEquals(
      listOf(
        MARQUE_VPN,
        "type=demande",
        "cle=${paire.publiqueBase64}",
        "appareil=Portable de Mathieu",
        "instant=2026-11-22T23:00:00Z",
      ),
      texte.lines(),
    )
  }

  @Test
  fun `un saut de ligne dans le libellé n'injecte pas de champ`() {
    val paire = genererPaireClesVpn()
    val texte = ecrireDemande(paire.publiqueBase64, "Portable\nadresse=10.0.0.1/32", maintenant)
    assertEquals(5, texte.lines().size)
    assertTrue(texte.lines()[3].startsWith("appareil=Portable adresse=10.0.0.1/32"))
  }

  @Test
  fun `la demande refuse une clé qui n'est pas une clé`() {
    val echec = runCatching { ecrireDemande("trop court", "Portable", maintenant) }
    assertTrue(echec.isFailure)
  }

  @Test
  fun `la demande refuse un libellé vide`() {
    val paire = genererPaireClesVpn()
    assertTrue(runCatching { ecrireDemande(paire.publiqueBase64, "   ", maintenant) }.isFailure)
  }
}
