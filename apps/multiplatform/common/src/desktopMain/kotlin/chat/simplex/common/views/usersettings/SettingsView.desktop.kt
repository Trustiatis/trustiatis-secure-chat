package chat.simplex.common.views.usersettings

import SectionTextFooter
import SectionView
import androidx.compose.runtime.*
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

@Composable
actual fun AdvancedSettingsAppSection(
  showSettingsModal: (@Composable (ChatModel) -> Unit) -> (() -> Unit),
  withAuth: (title: String, desc: String, block: () -> Unit) -> Unit,
) {
  SectionView {
    SettingsActionItem(painterResource(MR.images.ic_code), stringResource(MR.strings.settings_developer_tools), showSettingsModal { DeveloperView(withAuth) })
  }
  // Le sélecteur de canal de mise à jour est remplacé par un texte. Risque T-02
  // de l'audit de Phase 1 : le vérificateur de l'amont interroge en dur l'API
  // GitHub de SimpleX Chat, donc le choisir ferait proposer, télécharger et
  // installer l'application officielle par dessus Trustiatis Secure. Sur macOS
  // il écarterait même /Applications/SimpleX.app pour la remplacer.
  //
  // Les trois cas de AppUpdatesChannel sont CONSERVÉS : la préférence est
  // persistée par le nom de la valeur. Seule la liste offerte disparaît, ce qui
  // neutralise le chemin sans toucher au reste.
  //
  // À rouvrir quand le portail servira son index, voir DISTRIBUTION.md
  // chapitre 5 : l'index est déjà spécifié et porte, lui, une empreinte
  // SHA-256 que l'amont ne vérifie pas.
  SectionTextFooter(stringResource(MR.strings.trustiatis_updates_from_portal))
}

@Composable
actual fun AppShutdownItem() {}
