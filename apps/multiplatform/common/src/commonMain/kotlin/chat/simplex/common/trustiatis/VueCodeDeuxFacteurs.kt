package chat.simplex.common.trustiatis

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatInfo
import chat.simplex.common.model.ChatItem
import chat.simplex.common.model.MsgContent
import chat.simplex.common.model.localTimestamp
import chat.simplex.res.*
import dev.icerock.moko.resources.compose.stringResource

/**
 * Le rendu d'un code à deux facteurs livré sur un canal de service.
 *
 * Trois conditions, toutes nécessaires, sont réunies par `codeDeServiceAffichable`. Aucune n'est
 * décorative :
 *
 *  - le message est **reçu**. Un code que l'on a envoyé soi même n'est pas un code livré ;
 *  - le correspondant est désigné canal 2FA **et vérifié** (ADR-0026 D7). Sans la vérification,
 *    ce rendu serait exactement ce qu'un imposteur chercherait à obtenir ;
 *  - le message respecte le contrat, ses cinq champs compris (D5).
 *
 * Quand l'une manque, le message reste du texte ordinaire. Il n'est pas perdu : il n'est pas
 * décoré, et son contenu brut demeure lisible, ce que le contrat prévoit.
 *
 * Aucune valeur n'est rendue cliquable. Un canal qui livre des codes n'a aucune raison de livrer
 * des liens, et l'action comme l'origine viennent d'un service, donc d'ailleurs.
 */
fun codeDeServiceAffichable(chatInfo: ChatInfo, ci: ChatItem): CodeDeuxFacteurs? {
  if (ci.chatDir.sent) return null
  val contact = (chatInfo as? ChatInfo.Direct)?.contact ?: return null
  if (classeAffichable(contact) != ClasseCanal.DEUX_FACTEURS) return null
  val mc = ci.content.msgContent as? MsgContent.MCText ?: return null
  return lireCodeDeuxFacteurs(mc.text)
}

@Composable
fun VueCodeDeuxFacteurs(code: CodeDeuxFacteurs) {
  Column(Modifier.fillMaxWidth()) {
    Text(
      stringResource(MR.strings.ts_code_titre),
      style = MaterialTheme.typography.caption,
      color = MaterialTheme.colors.secondary,
    )
    Text(
      code.code,
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.padding(vertical = 4.dp),
    )
    Champ(stringResource(MR.strings.ts_code_application), code.application)
    Champ(stringResource(MR.strings.ts_code_action), code.action)
    Champ(stringResource(MR.strings.ts_code_heure), localTimestamp(code.heure))
    Champ(stringResource(MR.strings.ts_code_origine), code.origine)
    Text(
      stringResource(MR.strings.ts_code_avertissement),
      style = MaterialTheme.typography.caption,
      color = MaterialTheme.colors.error,
      modifier = Modifier.padding(top = 6.dp),
    )
  }
}

@Composable
private fun Champ(etiquette: String, valeur: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
    Text(
      "$etiquette : ",
      style = MaterialTheme.typography.caption,
      color = MaterialTheme.colors.secondary,
    )
    Text(valeur, style = MaterialTheme.typography.caption)
  }
}
