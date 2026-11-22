package chat.simplex.app

import chat.simplex.common.trustiatis.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiseAJourTest {
  private val manifesteReel = """
    {"schema":1,"canal":"stable","version":"7.1.4-ts1","amont":"v7.1.4","date":"2026-11-18",
     "notes_url":null,
     "artefacts":[
      {"nom":"a.deb","url":"https://downloads.trustiatis.com/fichiers/7.1.4-ts1/a.deb","plateforme":"linux","format":"DEB","architecture":"x86-64","taille":289225672,"sha256":"0456"},
      {"nom":"b.apk","url":"https://downloads.trustiatis.com/fichiers/7.1.4-ts1/b.apk","plateforme":"android","format":"APK","architecture":"arm64-v8a","taille":270682086,"sha256":"21e3"},
      {"nom":"c.msi","url":"https://downloads.trustiatis.com/fichiers/7.1.4-ts1/c.msi","plateforme":"windows","format":"MSI","architecture":"x86-64","taille":257085440,"sha256":"baea"}
     ]}
  """.trimIndent()

  private fun v(s: String) = assertNotNull(VersionTrustiatis.depuis(s), "version illisible : $s")

  @Test
  fun laVersionSeLitEtSeCompare() {
    assertEquals(VersionTrustiatis(7, 1, 4, 2), v("7.1.4-ts2"))
    assertEquals(VersionTrustiatis(7, 1, 4, 2), v("v7.1.4-ts2"))
    assertTrue(v("7.1.4-ts1") < v("7.1.4-ts2"))
    assertTrue(v("7.1.4-ts9") < v("7.1.5-ts1"))
    assertTrue(v("7.1.4-ts1") < v("7.2.0-ts1"))
    assertTrue(v("7.9.9-ts9") < v("8.0.0-ts1"))
    // Dix après neuf, et non avant : une comparaison de texte se tromperait ici.
    assertTrue(v("7.1.4-ts9") < v("7.1.4-ts10"))
  }

  @Test
  fun onNeDevinePasUneVersionQuOnNeReconnaitPas() {
    assertNull(VersionTrustiatis.depuis("7.1.4"))
    assertNull(VersionTrustiatis.depuis("7.1.4-ts"))
    assertNull(VersionTrustiatis.depuis("7.1-ts2"))
    assertNull(VersionTrustiatis.depuis("7.1.4-beta2"))
    assertNull(VersionTrustiatis.depuis(""))
  }

  @Test
  fun leManifesteSeLit() {
    val m = assertNotNull(lireManifeste(manifesteReel))
    assertEquals("7.1.4-ts1", m.version)
    assertEquals(3, m.artefacts.size)
    assertEquals(1, artefactsPour(m, Plateforme.ANDROID).size)
    assertEquals(0, artefactsPour(m, Plateforme.MACOS).size)
  }

  @Test
  fun unSchemaInconnuEstRefuseAuLieuDEtreDevine() {
    assertNull(lireManifeste(manifesteReel.replace("\"schema\":1", "\"schema\":2")))
    assertNull(lireManifeste("ceci n'est pas du JSON"))
    assertNull(lireManifeste("{}"))
  }

  @Test
  fun uneVersionPlusRecenteEstProposee() {
    val m = assertNotNull(lireManifeste(manifesteReel))
    val r = evaluer(v("7.1.3-ts1"), m, Plateforme.LINUX)
    assertTrue(r is ResultatMiseAJour.Disponible)
    assertEquals(1, r.artefacts.size)
  }

  @Test
  fun lamemeVersionOuPlusAncienneNeProposeRien() {
    val m = assertNotNull(lireManifeste(manifesteReel))
    assertTrue(evaluer(v("7.1.4-ts1"), m, Plateforme.LINUX) is ResultatMiseAJour.AJour)
    assertTrue(evaluer(v("7.1.4-ts2"), m, Plateforme.LINUX) is ResultatMiseAJour.AJour)
  }

  @Test
  fun ignorerNeVautQuePourLaVersionIgnoree() {
    val m = assertNotNull(lireManifeste(manifesteReel))
    assertTrue(evaluer(v("7.1.3-ts1"), m, Plateforme.LINUX, versionIgnoree = "7.1.4-ts1") is ResultatMiseAJour.AJour)
    // Une version suivante reparaît : un refus d'un jour ne devient pas un silence définitif.
    assertTrue(evaluer(v("7.1.3-ts1"), m, Plateforme.LINUX, versionIgnoree = "7.1.3-ts9") is ResultatMiseAJour.Disponible)
  }

  @Test
  fun uneVersionSansPaquetPourLaPlateformeLeDit() {
    val m = assertNotNull(lireManifeste(manifesteReel))
    val r = evaluer(v("7.1.3-ts1"), m, Plateforme.MACOS)
    assertTrue(r is ResultatMiseAJour.Illisible)
  }

  @Test
  fun leRangTrustiatisSuitLesCompteursDeVersion() {
    // Garde contre l'oubli le plus probable : relever gradle.properties sans relever
    // REVISION_TRUSTIATIS, ou l'inverse. Les deux se relèvent ensemble à chaque version.
    val props = File("../gradle.properties").readText()
    val codeAndroid = Regex("^android\\.version_code=(\\d+)$", RegexOption.MULTILINE)
      .find(props)?.groupValues?.get(1)?.toInt()
    assertNotNull(codeAndroid, "android.version_code introuvable")
    // 396 est le compteur de la dernière version de l'amont reportée sans version
    // Trustiatis par dessus. Chaque révision Trustiatis en consomme un.
    assertEquals(396 + REVISION_TRUSTIATIS, codeAndroid)
  }
}
