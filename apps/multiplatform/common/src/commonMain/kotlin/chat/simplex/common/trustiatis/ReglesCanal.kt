package chat.simplex.common.trustiatis

import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ce que l'application fait des canaux de service, et ce qu'elle refuse d'en faire.
 *
 * Deux règles d'ADR-0026 sont appliquées ici, et une troisième est volontairement laissée à
 * l'autre bout. Il faut les distinguer, sans quoi on croira la décision tenue alors qu'une
 * moitié repose sur un service qui n'existe pas encore.
 *
 * **D2, les fichiers.** La fusion des préférences de l'amont est asymétrique, et sa lecture
 * compte :
 *
 *  - notre préférence gouverne ce que l'on **reçoit**. Mise à l'interdiction, elle écarte
 *    `forContact` même si le correspondant déclare `ALWAYS`. C'est la direction qui importe :
 *    un service compromis ne peut pas pousser un fichier sur l'appareil ;
 *  - la préférence du correspondant gouverne ce qu'il reçoit. Notre interdiction ne la change
 *    pas, et un correspondant qui déclare `ALWAYS` nous laisse lui envoyer un fichier.
 *
 * Donc, en toute honnêteté : l'interdiction posée ici tient la direction service vers appareil,
 * quoi que fasse le service. L'autre direction dépend du service, qui doit se créer avec les
 * fichiers interdits, et ce bout là est **non réalisé**.
 *
 * **D7, le rendu.** Un correspondant désigné mais non vérifié n'obtient aucun libellé de
 * service. `classeAffichable` est le seul chemin d'affichage, et il porte ce contrôle.
 *
 * **D1, le cloisonnement.** Un même correspondant ne peut pas tenir deux classes.
 */

private fun cleCanal(classe: ClasseCanal) = "TrustiatisCanal_${classe.cle}"

object MagasinCanaux {
  fun designation(classe: ClasseCanal): Long? = settings.getLongOrNull(cleCanal(classe))

  fun classeDe(contactId: Long): ClasseCanal? =
    ClasseCanal.entries.firstOrNull { designation(it) == contactId }

  /**
   * Désigne un correspondant pour une classe.
   *
   * Refuse si ce correspondant tient déjà une autre classe : trois rayons d'explosion qui se
   * recouvrent n'en font qu'un, et c'est tout ce que D1 cherche à éviter.
   */
  fun poser(classe: ClasseCanal, contactId: Long): Boolean {
    val dejaTenue = classeDe(contactId)
    if (dejaTenue != null && dejaTenue != classe) return false
    settings.putLong(cleCanal(classe), contactId)
    return true
  }

  fun retirer(classe: ClasseCanal) = settings.remove(cleCanal(classe))

  fun oublierTout() = ClasseCanal.entries.forEach { retirer(it) }
}

/**
 * La classe à afficher pour ce correspondant, ou `null`.
 *
 * `null` quand il n'est pas désigné, et aussi quand il l'est sans être vérifié. Le second cas est
 * le plus important : sans lui, le libellé « service » devient l'objet à usurper, et il suffirait
 * d'un correspondant qui se présente sous le bon nom.
 */
fun classeAffichable(contact: Contact): ClasseCanal? {
  val classe = MagasinCanaux.classeDe(contact.contactId) ?: return null
  return if (contact.verified) classe else null
}

/**
 * Pose l'interdiction de fichiers de notre côté pour ce correspondant.
 *
 * Rend `true` quand l'interdiction est en place à la sortie, qu'elle y fût déjà ou qu'elle vienne
 * d'être posée. Rend `false` quand le cœur a refusé : l'appelant doit alors le dire, et non
 * continuer comme si le canal était conforme.
 */
suspend fun interdireFichiers(rhId: Long?, contact: Contact): Boolean {
  if (contact.mergedPreferences.files.userPreference.pref.allow == FeatureAllowed.NO) return true
  val prefs = contact.userPreferences.setAllowed(ChatFeature.Files, FeatureAllowed.NO)
  val versContact = chatModel.controller.apiSetContactPrefs(rhId, contact.contactId, prefs) ?: return false
  withContext(Dispatchers.Main) {
    chatModel.chatsContext.updateContact(rhId, versContact)
  }
  return true
}

/**
 * Désigne un correspondant comme canal de service, en posant d'abord l'interdiction de fichiers.
 *
 * L'ordre n'est pas indifférent. Si l'interdiction échoue, rien n'est désigné : un canal marqué
 * « service » qui accepte encore les fichiers serait un mensonge d'interface, et il vaut mieux
 * qu'il ne porte aucune marque.
 */
suspend fun designerCanal(rhId: Long?, classe: ClasseCanal, contact: Contact): Boolean {
  if (MagasinCanaux.classeDe(contact.contactId).let { it != null && it != classe }) return false
  if (!interdireFichiers(rhId, contact)) return false
  return MagasinCanaux.poser(classe, contact.contactId)
}
