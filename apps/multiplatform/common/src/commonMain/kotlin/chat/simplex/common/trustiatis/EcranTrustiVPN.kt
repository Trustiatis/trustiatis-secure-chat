package chat.simplex.common.trustiatis

import SectionBottomSpacer
import SectionDividerSpaced
import SectionTextFooter
import SectionView
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.ColumnWithScrollBar
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.helpers.AppBarTitle
import chat.simplex.res.*
import dev.icerock.moko.resources.compose.stringResource

/**
 * L'écran de configuration de la passerelle TrustiVPN du client.
 *
 * Il ne fait qu'une chose : saisir, valider, ranger. Il n'ouvre aucun tunnel et ne
 * contacte rien. Tant que l'enrôlement n'existe pas, il le dit au lieu de proposer un
 * bouton qui ne mènerait nulle part.
 */
@Composable
fun EcranTrustiVPN() {
  val initiale = remember { MagasinTrustiatis.configuration() }
  val hote = rememberSaveable { mutableStateOf(initiale.hote) }
  val port = rememberSaveable { mutableStateOf(initiale.portVpn.toString()) }
  val endpoint = rememberSaveable { mutableStateOf(initiale.endpoint) }
  val anomalies = remember { mutableStateOf(emptyList<Anomalie>()) }
  val enregistree = remember { mutableStateOf(false) }
  val etat = remember { mutableStateOf(MagasinTrustiatis.etat()) }

  fun courante() = ConfigurationPasserelle(
    hote = hote.value.trim(),
    portVpn = port.value.trim().toIntOrNull() ?: -1,
    endpoint = endpoint.value.trim(),
  )

  fun raison(champ: ChampConfig): String? = anomalies.value.firstOrNull { it.champ == champ }?.raison

  ColumnWithScrollBar {
    AppBarTitle(stringResource(MR.strings.ts_vpn_titre))

    SectionView(stringResource(MR.strings.ts_vpn_section_passerelle)) {
      Champ(stringResource(MR.strings.ts_vpn_hote), hote, raison(ChampConfig.HOTE))
      Champ(stringResource(MR.strings.ts_vpn_port), port, raison(ChampConfig.PORT), KeyboardType.Number)
      Champ(stringResource(MR.strings.ts_vpn_endpoint), endpoint, raison(ChampConfig.ENDPOINT))
    }
    SectionTextFooter(stringResource(MR.strings.ts_vpn_pied_passerelle))

    SectionDividerSpaced()

    SectionView {
      Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp)) {
        Button(
          onClick = {
            val a = MagasinTrustiatis.enregistrer(courante())
            anomalies.value = a
            enregistree.value = a.isEmpty()
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(MR.strings.ts_vpn_enregistrer))
        }
      }
      if (enregistree.value && anomalies.value.isEmpty()) {
        SectionTextFooter(stringResource(MR.strings.ts_vpn_enregistree))
      }
    }

    SectionDividerSpaced()

    SectionView(stringResource(MR.strings.ts_vpn_section_appareil)) {
      Ligne(
        stringResource(MR.strings.ts_vpn_etat),
        when (etat.value) {
          EtatEnrolement.NON_ENROLE -> stringResource(MR.strings.ts_vpn_etat_non_enrole)
          EtatEnrolement.ENROLE -> stringResource(MR.strings.ts_vpn_etat_enrole)
          EtatEnrolement.REVOQUE -> stringResource(MR.strings.ts_vpn_etat_revoque)
        }
      )
      MagasinTrustiatis.appareil()?.let { Ligne(stringResource(MR.strings.ts_vpn_appareil), it) }
    }
    SectionTextFooter(stringResource(MR.strings.ts_vpn_pied_appareil))

    SectionDividerSpaced()

    SectionView {
      Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp)) {
        Button(
          onClick = {
            MagasinTrustiatis.oublier()
            hote.value = ""
            port.value = PORT_VPN_DEFAUT.toString()
            endpoint.value = ""
            anomalies.value = emptyList()
            enregistree.value = false
            etat.value = MagasinTrustiatis.etat()
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
        ) {
          Text(stringResource(MR.strings.ts_vpn_oublier))
        }
      }
    }
    SectionTextFooter(stringResource(MR.strings.ts_vpn_pied_oublier))

    SectionBottomSpacer()
  }
}

@Composable
private fun Champ(
  etiquette: String,
  valeur: MutableState<String>,
  erreur: String?,
  clavier: KeyboardType = KeyboardType.Text,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 4.dp)) {
    OutlinedTextField(
      value = valeur.value,
      onValueChange = { valeur.value = it },
      label = { Text(etiquette) },
      singleLine = true,
      isError = erreur != null,
      keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = clavier),
      modifier = Modifier.fillMaxWidth(),
    )
    if (erreur != null) {
      Text(erreur, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
    }
  }
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
