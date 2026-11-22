package chat.simplex.app

import chat.simplex.common.trustiatis.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ConfigurationTrustiVPNTest {
  private fun config(hote: String = "10.8.0.1", port: Int = PORT_VPN_DEFAUT, endpoint: String = "https://10.8.0.1/api") =
    ConfigurationPasserelle(hote, port, endpoint)

  @Test
  fun uneConfigurationNormaleEstAcceptee() {
    assertEquals(emptyList(), valider(config()))
    assertTrue(config().complete)
  }

  @Test
  fun lHoteRefuseCeQuiNEstPasUneAdresse() {
    assertNull(validerHote("192.168.1.1"))
    assertNull(validerHote("passerelle.interne.example"))
    assertNull(validerHote("fd00::1"))
    assertEquals(ChampConfig.HOTE, validerHote("")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote("https://10.8.0.1")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote("10.8.0.1/api")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote("admin@10.8.0.1")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote(" 10.8.0.1")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote("10.8.0.256")!!.champ)
    assertEquals(ChampConfig.HOTE, validerHote("10.8.0")!!.champ)
  }

  @Test
  fun unOctetEcritEnOctalEstRefuse() {
    // « 010 » vaut 8 pour qui lit en octal et 10 pour qui lit en décimal. Une adresse qui
    // change de sens selon le lecteur n'est pas une adresse.
    assertFalse(estIPv4("10.08.0.1"))
    assertFalse(estIPv4("010.8.0.1"))
    assertTrue(estIPv4("10.8.0.1"))
    assertTrue(estIPv4("0.0.0.0"))
  }

  @Test
  fun lePortResteDansSesBornes() {
    assertNull(validerPort(51820))
    assertNull(validerPort(1))
    assertNull(validerPort(65535))
    assertEquals(ChampConfig.PORT, validerPort(0)!!.champ)
    assertEquals(ChampConfig.PORT, validerPort(65536)!!.champ)
    assertEquals(ChampConfig.PORT, validerPort(-1)!!.champ)
  }

  @Test
  fun lePointDEntreeRefuseLeClair() {
    val a = validerEndpoint("http://10.8.0.1/api", "10.8.0.1")
    assertEquals(ChampConfig.ENDPOINT, a!!.champ)
    assertTrue(a.raison.contains("jamais"))
  }

  @Test
  fun lePointDEntreeRefuseUneAdressePublique() {
    // ADR-0021 : le portail n'est jamais public. Accepter ici apprendrait le contraire.
    assertEquals(
      ChampConfig.ENDPOINT,
      validerEndpoint("https://portail.trustiatis.com/api", "10.8.0.1")!!.champ
    )
    // Sauf si c'est exactement l'hôte configuré : le client peut nommer sa passerelle.
    assertNull(validerEndpoint("https://passerelle.interne.example/api", "passerelle.interne.example"))
  }

  @Test
  fun lePointDEntreeAccepteLesEspacesPrives() {
    assertNull(validerEndpoint("https://10.8.0.1/api", "10.8.0.1"))
    assertNull(validerEndpoint("https://192.168.30.2:8443/api", "192.168.30.2"))
    assertNull(validerEndpoint("https://172.16.4.9/api", "172.16.4.9"))
    assertNull(validerEndpoint("https://[fd00::1]:8443/api", "fd00::1"))
    assertNull(validerEndpoint("https://100.64.1.1/api", "100.64.1.1"))
  }

  @Test
  fun lePointDEntreeRefuseUnIdentifiantDansLAdresse() {
    assertEquals(
      ChampConfig.ENDPOINT,
      validerEndpoint("https://admin:secret@10.8.0.1/api", "10.8.0.1")!!.champ
    )
  }

  @Test
  fun lePointDEntreeRefuseUnPortImpossible() {
    assertEquals(ChampConfig.ENDPOINT, validerEndpoint("https://10.8.0.1:0/api", "10.8.0.1")!!.champ)
    assertEquals(ChampConfig.ENDPOINT, validerEndpoint("https://10.8.0.1:99999/api", "10.8.0.1")!!.champ)
  }

  @Test
  fun leJetonEstJugeSurSaFormeEtRienDAutre() {
    val bon = "TG-ENR-4f2c91ab7d0e5634aa10bc77"
    assertNull(validerJeton(bon))
    assertEquals(ChampConfig.JETON, validerJeton("")!!.champ)
    assertEquals(ChampConfig.JETON, validerJeton("trop-court")!!.champ)
    assertEquals(ChampConfig.JETON, validerJeton("a".repeat(129))!!.champ)
    assertEquals(ChampConfig.JETON, validerJeton("TG ENR 4f2c91ab7d0e5634aa10bc77")!!.champ)
    assertEquals(ChampConfig.JETON, validerJeton("TG-ENR-4f2c91ab7d0e5634aa10bc77 ")!!.champ)
    assertEquals(ChampConfig.JETON, validerJeton("TG-ENR-4f2c91ab7d0e5634aa10b/77")!!.champ)
  }

  @Test
  fun lEspacePriveEstReconnu() {
    assertTrue(estAdressePrivee("10.0.0.1"))
    assertTrue(estAdressePrivee("172.31.255.254"))
    assertTrue(estAdressePrivee("192.168.0.1"))
    assertTrue(estAdressePrivee("100.64.0.1"))
    assertTrue(estAdressePrivee("fd00::1"))
    assertFalse(estAdressePrivee("172.15.0.1"))
    assertFalse(estAdressePrivee("172.32.0.1"))
    assertFalse(estAdressePrivee("100.63.0.1"))
    assertFalse(estAdressePrivee("8.8.8.8"))
    assertFalse(estAdressePrivee("2001:db8::1"))
    assertFalse(estAdressePrivee("exemple.com"))
  }

  @Test
  fun uneConfigurationIncompleteNommeChaqueChamp() {
    val anomalies = valider(ConfigurationPasserelle("", 0, ""))
    assertEquals(3, anomalies.size)
    assertEquals(setOf(ChampConfig.HOTE, ChampConfig.PORT, ChampConfig.ENDPOINT), anomalies.map { it.champ }.toSet())
  }
}
