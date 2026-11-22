package chat.simplex.app

import chat.simplex.common.trustiatis.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Le cycle de vie d'une session, éprouvé sans appareil ni réseau.
 *
 * L'horloge est injectée, et ce n'est pas une coquetterie : l'expiration et la fenêtre de
 * renouvellement sont des règles de sécurité, et une règle de temps qu'on ne peut pas
 * éprouver sans attendre vingt-quatre heures n'est pas éprouvée.
 */
class GestionTunnelTest {
  private val cleServeur = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })

  private class DepotMemoire : DepotSession {
    var session: SessionVpn? = null
    var motif: MotifRevocation? = null
    var effacements = 0
    override fun lire(): SessionVpn? = session
    override fun ecrire(session: SessionVpn) { this.session = session }
    override fun effacer() { session = null; effacements++ }
    override fun revocation(): MotifRevocation? = motif
    override fun poserRevocation(motif: MotifRevocation?) { this.motif = motif }
  }

  private class TunnelFeint(
    private var raisonIndisponible: String? = null,
    private val echecMontee: String? = null,
  ) : ClientTunnel {
    var montees = 0
    var extinctions = 0
    var derniereSession: SessionVpn? = null
    var dernierePaire: PaireClesVpn? = null
    override fun indisponible(): String? = raisonIndisponible
    var preparations = 0
    override fun preparer(): String? { preparations++; return raisonIndisponible }
    override suspend fun allumer(session: SessionVpn, paire: PaireClesVpn): String? {
      montees++
      derniereSession = session
      dernierePaire = paire
      return echecMontee
    }
    override suspend fun eteindre(): String? { extinctions++; return null }
  }

  private val maintenant = Instant.parse("2026-11-22T23:00:00Z")

  private fun messageSession(echeance: String = "2026-11-23T23:00:00Z") = """
    $MARQUE_VPN
    type=session
    adresse=10.42.7.19/32
    routes=0.0.0.0/0, ::/0
    passerelle=203.0.113.44:51820
    cle_serveur=$cleServeur
    echeance=$echeance
  """.trimIndent()

  private fun messageRevocation(motif: String) =
    "$MARQUE_VPN\ntype=revocation\nmotif=$motif\ninstant=2026-11-22T23:40:00Z"

  private fun gestion(
    client: ClientTunnel = TunnelFeint(),
    depot: DepotSession = DepotMemoire(),
    instant: Instant = maintenant,
  ) = GestionTunnel(client, depot, horloge = { instant })

  // --- Acceptation d'un message ou d'un bloc d'amorçage -------------------------------

  @Test
  fun `une session accordée est rangée`() {
    val depot = DepotMemoire()
    val g = gestion(depot = depot)
    assertIs<Issue.Faite>(g.accepter(messageSession()))
    assertEquals("10.42.7.19/32", depot.session?.adresse)
  }

  @Test
  fun `un texte qui n'est pas un message est refusé sans rien ranger`() {
    val depot = DepotMemoire()
    val issue = gestion(depot = depot).accepter("bonjour")
    assertEquals(GestionTunnel.REFUS_MESSAGE, assertIs<Issue.Refusee>(issue).motif)
    assertNull(depot.session)
  }

  @Test
  fun `une session accordée efface une révocation antérieure`() {
    val depot = DepotMemoire().apply { motif = MotifRevocation.CLE_REVOQUEE }
    gestion(depot = depot).accepter(messageSession())
    assertNull(depot.revocation())
  }

  // --- Allumage ----------------------------------------------------------------------

  @Test
  fun `sans session, l'allumage est refusé plutôt que tenté`() {
    val client = TunnelFeint()
    val issue = runBlocking { gestion(client = client).allumer() }
    assertEquals(GestionTunnel.REFUS_SANS_SESSION, assertIs<Issue.Refusee>(issue).motif)
    assertEquals(0, client.montees)
  }

  @Test
  fun `sur une session expirée, l'allumage est refusé plutôt que tenté`() {
    val depot = DepotMemoire()
    val client = TunnelFeint()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    val plusTard = Instant.parse("2026-11-24T00:00:00Z")
    val issue = runBlocking { GestionTunnel(client, depot, horloge = { plusTard }).allumer() }
    assertEquals(GestionTunnel.REFUS_EXPIREE, assertIs<Issue.Refusee>(issue).motif)
    assertEquals(0, client.montees)
  }

  @Test
  fun `après une révocation, l'allumage est refusé et dit pourquoi`() {
    val depot = DepotMemoire()
    val client = TunnelFeint()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    g.accepter(messageRevocation("utilisateur_suspendu"))
    val issue = runBlocking { g.allumer() }
    assertTrue(assertIs<Issue.Refusee>(issue).motif.contains("suspendu"))
    assertEquals(0, client.montees)
  }

  @Test
  fun `quand le tunnel est indisponible, l'allumage dit la raison de la plateforme`() {
    val client = TunnelFeint(raisonIndisponible = "service absent")
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    assertEquals("service absent", assertIs<Issue.Refusee>(runBlocking { g.allumer() }).motif)
    assertEquals(0, client.montees)
  }

  @Test
  fun `un allumage réussi passe l'état à allumé et transmet la session et la clé`() {
    val client = TunnelFeint()
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    assertIs<Issue.Faite>(runBlocking { g.allumer() })
    assertEquals(EtatTunnel.ALLUME, EtatTunnelCourant.etat.value)
    assertNull(EtatTunnelCourant.motif.value)
    assertEquals(1, client.montees)
    assertEquals("10.42.7.19/32", client.derniereSession?.adresse)
    assertEquals(32, client.dernierePaire?.publique?.size)
  }

  @Test
  fun `un échec de montée laisse l'état en échec avec son motif`() {
    val client = TunnelFeint(echecMontee = "interface occupée")
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    assertEquals("interface occupée", assertIs<Issue.Refusee>(runBlocking { g.allumer() }).motif)
    assertEquals(EtatTunnel.ECHEC, EtatTunnelCourant.etat.value)
    assertEquals("interface occupée", EtatTunnelCourant.motif.value)
  }

  @Test
  fun `la clé publique est stable entre une demande et l'allumage qui suit`() {
    val client = TunnelFeint()
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    val annoncee = g.clePubliqueCourante()
    g.accepter(messageSession())
    runBlocking { g.allumer() }
    assertEquals(annoncee, client.dernierePaire?.publiqueBase64)
  }

  // --- Extinction, à la demande de l'utilisateur --------------------------------------

  @Test
  fun `éteindre repasse à éteint et garde la session, pour pouvoir rallumer`() {
    val client = TunnelFeint()
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    runBlocking { g.allumer() }
    assertIs<Issue.Faite>(runBlocking { g.eteindre() })
    assertEquals(EtatTunnel.ETEINT, EtatTunnelCourant.etat.value)
    assertEquals(1, client.extinctions)
    // La messagerie fonctionne tunnel éteint, et la session reste utilisable : couper pour
    // retrouver son réseau local ne doit pas obliger à tout redemander (décision P-10).
    assertEquals("10.42.7.19/32", depot.session?.adresse)
  }

  @Test
  fun `éteindre puis rallumer tire une clé neuve`() {
    val client = TunnelFeint()
    val depot = DepotMemoire()
    val g = GestionTunnel(client, depot, horloge = { maintenant })
    g.accepter(messageSession())
    runBlocking { g.allumer() }
    val premiere = client.dernierePaire?.publiqueBase64
    runBlocking { g.eteindre() }
    runBlocking { g.allumer() }
    assertTrue(premiere != client.dernierePaire?.publiqueBase64, "la clé doit être neuve")
  }

  // --- Révocation ---------------------------------------------------------------------

  @Test
  fun `une clé révoquée garde la session, car l'adresse reste réservée`() {
    val depot = DepotMemoire()
    val g = gestion(depot = depot)
    g.accepter(messageSession())
    g.accepter(messageRevocation("cle_revoquee"))
    assertEquals(MotifRevocation.CLE_REVOQUEE, depot.revocation())
    assertEquals(0, depot.effacements)
    assertEquals(EtatTunnel.ETEINT, EtatTunnelCourant.etat.value)
    assertTrue(EtatTunnelCourant.motif.value!!.contains("nouvelle"))
  }

  @Test
  fun `un utilisateur supprimé fait effacer la session`() {
    val depot = DepotMemoire()
    val g = gestion(depot = depot)
    g.accepter(messageSession())
    g.accepter(messageRevocation("utilisateur_supprime"))
    assertEquals(1, depot.effacements)
    assertNull(depot.session)
  }

  @Test
  fun `un utilisateur suspendu garde son adresse`() {
    val depot = DepotMemoire()
    val g = gestion(depot = depot)
    g.accepter(messageSession())
    g.accepter(messageRevocation("utilisateur_suspendu"))
    assertEquals(0, depot.effacements)
    assertEquals("10.42.7.19/32", depot.session?.adresse)
  }

  @Test
  fun `les trois motifs ont trois messages distincts`() {
    val textes = MotifRevocation.entries.map(GestionTunnel::motifLisible)
    assertEquals(3, textes.toSet().size)
    assertTrue(textes.all { it.isNotBlank() })
  }

  // --- Renouvellement -----------------------------------------------------------------

  @Test
  fun `la fenêtre de renouvellement s'ouvre une heure avant, et pas avant`() {
    val depot = DepotMemoire()
    gestion(depot = depot).accepter(messageSession())
    val s = depot.session!!
    assertFalse(s.renouvelable(Instant.parse("2026-11-23T21:59:00Z")))
    assertTrue(s.renouvelable(Instant.parse("2026-11-23T22:30:00Z")))
  }
}
