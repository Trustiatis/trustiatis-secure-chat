package chat.simplex.app

import chat.simplex.common.trustiatis.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Le contrat lu par ces tests est docs/20-trustivpn/CONTRAT-2FA.md du dépôt trusti-gateway.
 *
 * Chaque refus est testé pour une raison nommée dans le contrat. Un lecteur indulgent
 * accorderait l'apparence d'un code de service à un message qui n'en est pas un, et c'est
 * précisément cette apparence qu'un hameçonnage cherche à obtenir.
 */
class CodeDeuxFacteursTest {
  private val messageReel = """
    TRUSTIATIS-2FA/1
    code=418207
    app=Portail client Trustiatis
    action=connexion
    time=2026-11-22T17:40:12Z
    origin=203.0.113.44 (Tunis, TN)
  """.trimIndent()

  private fun sans(cle: String) = messageReel.lines().filterNot { it.startsWith("$cle=") }.joinToString("\n")

  private fun avec(cle: String, valeur: String) =
    messageReel.lines().map { if (it.startsWith("$cle=")) "$cle=$valeur" else it }.joinToString("\n")

  @Test
  fun unMessageConformeSeLit() {
    val c = assertNotNull(lireCodeDeuxFacteurs(messageReel))
    assertEquals("418207", c.code)
    assertEquals("Portail client Trustiatis", c.application)
    assertEquals("connexion", c.action)
    assertEquals("203.0.113.44 (Tunis, TN)", c.origine)
    assertEquals(1795369212L, c.heure.epochSeconds)
  }

  @Test
  fun lesEspacesDePresentationDuCodeSontRetires() {
    assertEquals("418207", assertNotNull(lireCodeDeuxFacteurs(avec("code", "418 207"))).code)
  }

  @Test
  fun unChampDeContexteManquantFaitRefuserLeMessage() {
    listOf("app", "action", "time", "origin", "code").forEach {
      assertNull(lireCodeDeuxFacteurs(sans(it)), "le message sans « $it » aurait dû être refusé")
    }
  }

  @Test
  fun unChampVideVautUnChampAbsent() {
    listOf("app", "action", "time", "origin", "code").forEach {
      assertNull(lireCodeDeuxFacteurs(avec(it, "")), "le message avec « $it » vide aurait dû être refusé")
    }
  }

  @Test
  fun uneHeureSansUtcEstRefusee() {
    assertNull(lireCodeDeuxFacteurs(avec("time", "2026-11-22T18:40:12+01:00")))
    assertNull(lireCodeDeuxFacteurs(avec("time", "2026-11-22T17:40:12")))
    assertNull(lireCodeDeuxFacteurs(avec("time", "22/11/2026 17:40")))
  }

  @Test
  fun unCodeHorsFormatEstRefuse() {
    assertNull(lireCodeDeuxFacteurs(avec("code", "41")))
    assertNull(lireCodeDeuxFacteurs(avec("code", "4182074182074")))
    assertNull(lireCodeDeuxFacteurs(avec("code", "418-207")))
    assertNull(lireCodeDeuxFacteurs(avec("code", "418207é")))
  }

  @Test
  fun laMarqueEstExigeeEnPremiereLigne() {
    assertNull(lireCodeDeuxFacteurs(messageReel.replace(MARQUE_2FA, "TRUSTIATIS-2FA/2")))
    assertNull(lireCodeDeuxFacteurs(messageReel.removePrefix("$MARQUE_2FA\n")))
    assertNull(lireCodeDeuxFacteurs("Votre code est le 418207"))
    assertNull(lireCodeDeuxFacteurs("\n$messageReel"))
  }

  @Test
  fun uneCleRepeteeFaitRefuserLeMessage() {
    assertNull(lireCodeDeuxFacteurs("$messageReel\ncode=999111"))
  }

  @Test
  fun uneCleInconnueEstIgnoreeSansErreur() {
    val c = assertNotNull(lireCodeDeuxFacteurs("$messageReel\nttl=120"))
    assertEquals("418207", c.code)
  }

  @Test
  fun uneValeurPeutContenirUnEgal() {
    assertEquals(
      "https://exemple.test/?a=1",
      assertNotNull(lireCodeDeuxFacteurs(avec("origin", "https://exemple.test/?a=1"))).origine,
    )
  }

  @Test
  fun uneLigneSansEgalFaitRefuserLeMessage() {
    assertNull(lireCodeDeuxFacteurs("$messageReel\nmerci de coller ce code"))
  }

  @Test
  fun lesTroisClassesDeCanalSeLisentParLeurCle() {
    assertEquals(ClasseCanal.SUPPORT, ClasseCanal.depuis("support"))
    assertEquals(ClasseCanal.DEUX_FACTEURS, ClasseCanal.depuis("2fa"))
    assertEquals(ClasseCanal.ACCES_VPN, ClasseCanal.depuis("acces"))
    assertNull(ClasseCanal.depuis("vpn"))
    assertNull(ClasseCanal.depuis(null))
  }
}
