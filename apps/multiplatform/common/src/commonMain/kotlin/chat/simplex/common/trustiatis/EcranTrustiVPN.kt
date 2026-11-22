package chat.simplex.common.trustiatis

import SectionBottomSpacer
import SectionDividerSpaced
import SectionTextFooter
import SectionView
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
 * Il porte l'interrupteur du tunnel, la session en cours, et la configuration de la
 * passerelle. Chaque refus est affiché tel quel plutôt que traduit en « une erreur est
 * survenue » : l'utilisateur ne peut agir que sur ce qu'on lui nomme.
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
  val jeton = rememberSaveable { mutableStateOf("") }
  val jetonEnAttente = remember { mutableStateOf(MagasinTrustiatis.jetonEnAttente() != null) }
  val anomalieJeton = remember { mutableStateOf<Anomalie?>(null) }

  fun courante() = ConfigurationPasserelle(
    hote = hote.value.trim(),
    portVpn = port.value.trim().toIntOrNull() ?: -1,
    endpoint = endpoint.value.trim(),
  )

  fun raison(champ: ChampConfig): String? = anomalies.value.firstOrNull { it.champ == champ }?.raison

  ColumnWithScrollBar {
    AppBarTitle(stringResource(MR.strings.ts_vpn_titre))

    SectionTunnel()

    SectionDividerSpaced()

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

    SectionView(stringResource(MR.strings.ts_vpn_section_jeton)) {
      Champ(stringResource(MR.strings.ts_vpn_jeton), jeton, anomalieJeton.value?.raison)
      Ligne(
        stringResource(MR.strings.ts_vpn_jeton_etat),
        if (jetonEnAttente.value) stringResource(MR.strings.ts_vpn_jeton_present) else stringResource(MR.strings.ts_vpn_jeton_absent),
      )
      Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp)) {
        Button(
          onClick = {
            val a = MagasinTrustiatis.poserJeton(jeton.value)
            anomalieJeton.value = a
            if (a == null) {
              jeton.value = ""
              jetonEnAttente.value = true
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(MR.strings.ts_vpn_jeton_enregistrer))
        }
      }
      if (jetonEnAttente.value) {
        Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp)) {
          Button(
            onClick = {
              MagasinTrustiatis.consommerJeton()
              jetonEnAttente.value = false
              anomalieJeton.value = null
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
          ) {
            Text(stringResource(MR.strings.ts_vpn_jeton_effacer))
          }
        }
      }
    }
    SectionTextFooter(stringResource(MR.strings.ts_vpn_pied_jeton))

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
            jeton.value = ""
            jetonEnAttente.value = false
            anomalieJeton.value = null
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

/**
 * L'interrupteur du tunnel, et ce que la session en cours permet d'en dire.
 *
 * Décision P-10 du Membre, et les deux moitiés sont inséparables : l'interrupteur existe,
 * parce que couper le tunnel pour retrouver son réseau local est une raison légitime ; et
 * tunnel allumé, tout passe dedans. Le pied de section dit aussi ce que l'application **ne
 * peut pas** garantir, parce que le laisser croire serait pire que de ne rien promettre.
 */
@Composable
private fun SectionTunnel() {
  val portee = rememberCoroutineScope()
  val gestion = remember { GestionTunnel(tunnel, DepotSessionReglages) }
  val session = remember { mutableStateOf(gestion.session()) }
  val revocation = remember { mutableStateOf(gestion.revocation()) }
  val bloc = rememberSaveable { mutableStateOf("") }
  val refus = remember { mutableStateOf<String?>(null) }
  val etat = EtatTunnelCourant.etat

  SectionView(stringResource(MR.strings.ts_tunnel_section)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(stringResource(MR.strings.ts_tunnel_interrupteur))
      Switch(
        checked = etat.value == EtatTunnel.ALLUME || etat.value == EtatTunnel.DEMARRAGE,
        onCheckedChange = { allumer ->
          refus.value = null
          portee.launch {
            if (allumer) {
              // La plateforme peut exiger une autorisation, et sous Android c'est une
              // fenêtre du système : on la demande avant d'essayer, sinon l'utilisateur
              // verrait un échec là où il n'y a qu'une permission à accorder.
              val aPreparer = tunnel.indisponible()?.let { tunnel.preparer() }
              if (aPreparer != null) refus.value = aPreparer
              else (gestion.allumer() as? Issue.Refusee)?.let { refus.value = it.motif }
            } else {
              (gestion.eteindre() as? Issue.Refusee)?.let { refus.value = it.motif }
            }
          }
        },
      )
    }
    Ligne(
      stringResource(MR.strings.ts_tunnel_etat),
      when (etat.value) {
        EtatTunnel.ETEINT -> stringResource(MR.strings.ts_tunnel_eteint)
        EtatTunnel.DEMARRAGE -> stringResource(MR.strings.ts_tunnel_demarrage)
        EtatTunnel.ALLUME -> stringResource(MR.strings.ts_tunnel_allume)
        EtatTunnel.ARRET -> stringResource(MR.strings.ts_tunnel_arret)
        EtatTunnel.ECHEC -> stringResource(MR.strings.ts_tunnel_echec)
      },
    )
    // Le motif global et le refus local disent deux choses différentes : le premier vient
    // de la plateforme ou de la passerelle, le second du geste qui vient d'être fait.
    (refus.value ?: EtatTunnelCourant.motif.value)?.let { Avertissement(it) }
    revocation.value?.let { Avertissement(GestionTunnel.motifLisible(it)) }
  }
  SectionTextFooter(stringResource(MR.strings.ts_tunnel_pied))

  SectionDividerSpaced()

  SectionView(stringResource(MR.strings.ts_tunnel_section_session)) {
    val courante = session.value
    if (courante == null) {
      Ligne(stringResource(MR.strings.ts_tunnel_adresse), stringResource(MR.strings.ts_tunnel_sans_session))
    } else {
      Ligne(stringResource(MR.strings.ts_tunnel_adresse), courante.adresse)
      Ligne(stringResource(MR.strings.ts_tunnel_echeance), courante.echeance.toString())
    }
    Ligne(stringResource(MR.strings.ts_tunnel_cle), gestion.clePubliqueCourante())
    Champ(stringResource(MR.strings.ts_tunnel_bloc), bloc, null)
    Row(Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 8.dp)) {
      Button(
        onClick = {
          when (val issue = gestion.accepter(bloc.value)) {
            is Issue.Faite -> {
              bloc.value = ""
              refus.value = null
              session.value = gestion.session()
              revocation.value = gestion.revocation()
            }
            is Issue.Refusee -> refus.value = issue.motif
          }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = bloc.value.isNotBlank(),
      ) {
        Text(stringResource(MR.strings.ts_tunnel_accepter))
      }
    }
  }
  SectionTextFooter(stringResource(MR.strings.ts_tunnel_pied_session))
}

@Composable
private fun Avertissement(texte: String) {
  Text(
    texte,
    color = MaterialTheme.colors.error,
    style = MaterialTheme.typography.caption,
    modifier = Modifier.fillMaxWidth().padding(horizontal = DEFAULT_PADDING, vertical = 4.dp),
  )
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
