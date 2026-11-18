package chat.simplex.common.ui.theme

import androidx.compose.ui.graphics.Color

// Fichier GÉNÉRÉ par tools/propager-branding.py depuis branding/colors/palette.json.
// Ne pas modifier à la main : la prochaine propagation écraserait la correction.
//
// Les couleurs de marque sont reprises telles quelles dans la palette. Les teintes
// dérivées gardent la clarté et la saturation de l'amont et ne reçoivent que notre
// teinte, pour que le contraste qu'il a dessiné soit conservé au chiffre près.
//
// La paire sentinelle reste intacte : le #0088ff des catalogues n'est pas une couleur
// affichée, c'est la marque que ReadableTextWithLink cherche pour la remplacer par
// MaterialTheme.colors.primary. Changer l'une sans l'autre éteindrait les liens.

// Encre des QR codes : l'amont 062D56 à notre teinte, contraste conservé
val TrustiatisQrEncre = Color(0xFF00362B)  // amont #062D56, teinte de la marque

// Incognito : l'accent de la palette, pris tel quel. Un signal ne se derive pas.
val TrustiatisIncognito = Color(0xFFBE7C7B)  // palette, marque.accent

// Surbrillance de selection : l'amont 0066FF a notre teinte, opacite 30 % conservee
val TrustiatisSelectionHighlight = Color(0x4D008971)  // amont #0066FF, teinte de la marque

// Canevas de la conversation : le motif par defaut de l'amont a la teinte de
// l'accent. Il PRIME sur les quatre themes tant qu'un motif est actif.
val TrustiatisCanevasFondClair = "#ffffeeed"  // amont #E7F5FF
val TrustiatisCanevasTeinteClair = "#ffffdddc"  // amont #CEEBFF
val TrustiatisCanevasTeinteSombre = "#ff3a1d1d"  // amont #0F293B
val TrustiatisCanevasTeinteSignature = "#ff422121"  // amont #112F43
val TrustiatisCanevasTeinteNoir = "#ff3a1d1d"  // amont #0F293B
val TrustiatisCanevasClairEnvoye = "#fffff4f4"  // amont #EEF9FF
val TrustiatisCanevasClairEnvoyeCitation = "#fffde2e0"  // amont #D6EDFA
val TrustiatisCanevasClairRecu = "#fff9f4f3"  // amont #F3F5F9
val TrustiatisCanevasClairRecuCitation = "#ffeee5e5"  // amont #E4E8EE
val TrustiatisCanevasSombreEnvoye = "#ff33201f"  // amont #172833
val TrustiatisCanevasSombreEnvoyeCitation = "#ff502f2f"  // amont #1C3E4F
val TrustiatisCanevasSombreRecu = "#ff2c2726"  // amont #26282C
val TrustiatisCanevasSombreRecuCitation = "#ff403a3a"  // amont #393C40
val TrustiatisCanevasSignatureEnvoye = "#ff592a2a"  // amont #1A3C5D
val TrustiatisCanevasSignatureEnvoyeCitation = "#ff7e4142"  // amont #235B80
val TrustiatisCanevasSignatureRecu = "#ff2c2726"  // amont #26282C
val TrustiatisCanevasSignatureRecuCitation = "#ff403a3a"  // amont #393C40
val TrustiatisCanevasNoirEnvoye = "#ff592a2a"  // amont #1A3C5D
val TrustiatisCanevasNoirEnvoyeCitation = "#ff7e4142"  // amont #235B80
val TrustiatisCanevasNoirRecu = "#ff221d1d"  // amont #1D1E22
val TrustiatisCanevasNoirRecuCitation = "#ff2f2a29"  // amont #292B2F
val TrustiatisCanevasFondSignature = "#ff260f0f"  // amont #111528

// Thème Dark
val TrustiatisDarkPrimary = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisDarkPrimaryVariant = Color(0xFF509482)  // palette, marque.primaire
val TrustiatisDarkTitle = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisDarkPrimaryVariant2 = Color(0xFF172823)  // amont #18262E, teinte de la marque
val TrustiatisDarkSentMessage = Color(0xFF172823)  // amont #18262E, teinte de la marque
val TrustiatisDarkSentQuote = Color(0xFF1A3C33)  // amont #1D3847, teinte de la marque

// Thème Light
val TrustiatisLightPrimary = Color(0xFF509482)  // palette, marque.primaire
val TrustiatisLightPrimaryVariant = Color(0xFF3D7566)  // palette, marque.primaire_fonce
val TrustiatisLightTitle = Color(0xFF509482)  // palette, marque.primaire
val TrustiatisLightPrimaryVariant2 = Color(0xFFE9F9F4)  // amont #E9F7FF, teinte de la marque
val TrustiatisLightSentMessage = Color(0xFFE9F9F4)  // amont #E9F7FF, teinte de la marque
val TrustiatisLightSentQuote = Color(0xFFD5F3EA)  // amont #D6F0FF, teinte de la marque

// Thème Signature
val TrustiatisSignaturePrimary = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisSignaturePrimaryVariant = Color(0xFF509482)  // palette, marque.primaire
val TrustiatisSignatureSecondaryVariant = Color(0xFF2E4740)  // amont #2C464D, teinte de la marque
val TrustiatisSignatureBackground = Color(0xFF070D18)  // palette, theme_sombre.fond
val TrustiatisSignatureSurface = Color(0xFF0A1220)  // palette, theme_sombre.fond_secondaire
val TrustiatisSignatureTitle = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisSignaturePrimaryVariant2 = Color(0xFF033027)  // amont #172941, teinte de la marque
val TrustiatisSignatureSentMessage = Color(0xFF033027)  // amont #172941, teinte de la marque
val TrustiatisSignatureSentQuote = Color(0xFF034236)  // amont #1C3A57, teinte de la marque
val TrustiatisSignatureReceivedMessage = Color(0xFF162F28)  // amont #25283A, teinte de la marque
val TrustiatisSignatureReceivedQuote = Color(0xFF293F39)  // amont #36394A, teinte de la marque

// Thème Black
val TrustiatisBlackPrimary = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisBlackPrimaryVariant = Color(0xFF509482)  // palette, marque.primaire
val TrustiatisBlackTitle = Color(0xFF6FB3A1)  // palette, marque.primaire_clair
val TrustiatisBlackPrimaryVariant2 = Color(0xFF1F3B34)  // amont #243747, teinte de la marque
val TrustiatisBlackSentMessage = Color(0xFF172823)  // amont #18262E, teinte de la marque
val TrustiatisBlackSentQuote = Color(0xFF1A3C33)  // amont #1D3847, teinte de la marque
