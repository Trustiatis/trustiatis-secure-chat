package chat.simplex.common.trustiatis

import com.wireguard.crypto.KeyPair

/**
 * Génération de la paire X25519 sur Android, par la Curve25519 du tunnel.
 *
 * `KeyPairGenerator("XDH")` n'existe qu'à partir de l'API 33 et `minSdk` vaut 26, donc le
 * JDK ne sert à rien ici. La bibliothèque `com.wireguard.android:tunnel` apporte sa propre
 * Curve25519, et c'est **la même** que celle qu'emploiera le tunnel : une seule
 * implémentation de courbe dans l'appareil, pas deux qui pourraient diverger.
 *
 * `KeyPair()` sans argument tire une clé privée de l'aléa du système, puis dérive la
 * publique. La privée est recopiée dans `PaireClesVpn`, qui sait l'effacer ; l'objet de la
 * bibliothèque, lui, ne le sait pas, et c'est pourquoi on ne le garde pas.
 */
actual fun genererPaireClesVpn(): PaireClesVpn {
  val paire = KeyPair()
  return PaireClesVpn(paire.privateKey.bytes, paire.publicKey.bytes)
}
