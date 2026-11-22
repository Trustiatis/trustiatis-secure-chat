package chat.simplex.common.trustiatis

import SectionBottomSpacer
import SectionDividerSpaced
import SectionTextFooter
import SectionView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.*
import chat.simplex.common.platform.ColumnWithScrollBar
import chat.simplex.common.platform.chatModel
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.helpers.*
import chat.simplex.res.*
import dev.icerock.moko.resources.compose.stringResource

/**
 * L'écran des canaux de service.
 *
 * Il désigne un correspondant par classe, et il pose l'interdiction de fichiers en même temps,
 * parce qu'une désignation sans interdiction serait un libellé sans la règle qu'il annonce.
 *
 * Ce qu'il ne fait pas, et qu'il est important de ne pas laisser croire : il ne crée aucun
 * correspondant et n'en vérifie aucun. Les correspondants de service viennent du portail du
 * client, et la vérification se fait par le code de sécurité, à l'écran de l'amont prévu pour
 * cela. Cet écran constate la vérification, il ne la remplace pas.
 */
@Composable
fun EcranCanaux() {
  val rhId = chatModel.remoteHostId
  val revision = remember { mutableStateOf(0) }
  val ouverte = remember { mutableStateOf<ClasseCanal?>(null) }
  val refus = remember { mutableStateOf<String?>(null) }

  ColumnWithScrollBar {
    AppBarTitle(stringResource(MR.strings.ts_canaux_titre))
    SectionTextFooter(stringResource(MR.strings.ts_canaux_intro))

    ClasseCanal.entries.forEach { classe ->
      SectionDividerSpaced()
      val id = remember(revision.value, classe) { MagasinCanaux.designation(classe) }
      val contact = remember(revision.value, id) { id?.let { contactDirect(it) } }
      SectionView(libelleClasse(classe)) {
        Ligne(stringResource(MR.strings.ts_canaux_correspondant), contact?.chatViewName ?: stringResource(MR.strings.ts_canaux_aucun))
        if (contact != null) {
          Ligne(
            stringResource(MR.strings.ts_canaux_verification),
            if (contact.verified) stringResource(MR.strings.ts_canaux_verifie) else stringResource(MR.strings.ts_canaux_non_verifie),
          )
          Ligne(
            stringResource(MR.strings.ts_canaux_fichiers),
            if (contact.mergedPreferences.files.userPreference.pref.allow == FeatureAllowed.NO) {
              stringResource(MR.strings.ts_canaux_fichiers_interdits)
            } else {
              stringResource(MR.strings.ts_canaux_fichiers_permis)
            },
          )
        }
        Bouton(
          if (ouverte.value == classe) stringResource(MR.strings.ts_canaux_fermer) else stringResource(MR.strings.ts_canaux_choisir),
        ) {
          ouverte.value = if (ouverte.value == classe) null else classe
          refus.value = null
        }
        if (contact != null) {
          Bouton(stringResource(MR.strings.ts_canaux_retirer), MaterialTheme.colors.error) {
            MagasinCanaux.retirer(classe)
            revision.value += 1
          }
        }
        if (ouverte.value == classe) {
          contactsDirects().forEach { c ->
            Row(
              Modifier.fillMaxWidth()
                .clickable {
                  withBGApi {
                    val pose = designerCanal(rhId, classe, c)
                    if (!pose) refus.value = generalGetString(MR.strings.ts_canaux_refus)
                    revision.value += 1
                    ouverte.value = null
                  }
                }
                .padding(horizontal = DEFAULT_PADDING, vertical = 10.dp)
            ) {
              Text(c.chatViewName)
            }
          }
          if (contactsDirects().isEmpty()) {
            SectionTextFooter(stringResource(MR.strings.ts_canaux_aucun_correspondant))
          }
        }
      }
      SectionTextFooter(piedClasse(classe))
      if (contact != null && !contact.verified) {
        SectionTextFooter(stringResource(MR.strings.ts_canaux_pied_non_verifie))
      }
    }

    refus.value?.let {
      SectionDividerSpaced()
      SectionTextFooter(it)
    }

    SectionBottomSpacer()
  }
}

private fun contactsDirects(): List<Contact> =
  chatModel.chats.value.mapNotNull { (it.chatInfo as? ChatInfo.Direct)?.contact }

private fun contactDirect(contactId: Long): Contact? =
  contactsDirects().firstOrNull { it.contactId == contactId }

@Composable
private fun libelleClasse(classe: ClasseCanal): String = when (classe) {
  ClasseCanal.SUPPORT -> stringResource(MR.strings.ts_canaux_support)
  ClasseCanal.DEUX_FACTEURS -> stringResource(MR.strings.ts_canaux_2fa)
  ClasseCanal.ACCES_VPN -> stringResource(MR.strings.ts_canaux_acces)
}

@Composable
private fun piedClasse(classe: ClasseCanal): String = when (classe) {
  ClasseCanal.SUPPORT -> stringResource(MR.strings.ts_canaux_pied_support)
  ClasseCanal.DEUX_FACTEURS -> stringResource(MR.strings.ts_canaux_pied_2fa)
  ClasseCanal.ACCES_VPN -> stringResource(MR.strings.ts_canaux_pied_acces)
}

@Composable
private fun Ligne(etiquette: String, valeur: String) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(etiquette)
    Text(valeur, color = MaterialTheme.colors.secondary)
  }
}

@Composable
private fun Bouton(texte: String, couleur: androidx.compose.ui.graphics.Color? = null, action: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 6.dp)) {
    Button(
      onClick = action,
      modifier = Modifier.fillMaxWidth(),
      colors = if (couleur != null) ButtonDefaults.buttonColors(backgroundColor = couleur) else ButtonDefaults.buttonColors(),
    ) {
      Text(texte)
    }
  }
}
