package dev.ori.core.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Brand — Indigo
// Mockup tokens: --accent #6366F1, --accent-hover #4F46E5, --accent-light/subtle
// ============================================================================
val Indigo50 = Color(0xFFEEF2FF)
val Indigo100 = Color(0xFFE0E7FF)
val Indigo200 = Color(0xFFC7D2FE)
val Indigo400 = Color(0xFF818CF8)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)

// Semantic Indigo aliases used throughout the mockups.
// IndigoSubtle (06%) is the hero-eyebrow / subtle hover background;
// IndigoLight (10%) is the icon-wrap hover; IndigoBg (#EEF2FF) is the
// 32 dp settings icon container background and the SFTP protocol pill bg.
val IndigoSubtle = Color(0x0F6366F1) // rgba(99,102,241, 0.06)
val IndigoLight = Color(0x1A6366F1) // rgba(99,102,241, 0.10)
val IndigoBg = Indigo50 // alias for the named token used in mockups

// ============================================================================
// Neutrals
// Mockup tokens: --bg #FAFAFA, --bg-white #FFFFFF, --text-primary #111827,
//                --text-body #374151, --text-secondary #6B7280,
//                --text-tertiary #9CA3AF, --border #E5E7EB,
//                --border-hover #D1D5DB, --border-light #F3F4F6
// ============================================================================
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray700 = Color(0xFF374151)
val Gray900 = Color(0xFF111827)

// Semantic neutral aliases used in mockups.
val BorderLight = Gray100 // #F3F4F6 — used between non-last rows of settings sections

// ============================================================================
// Top bar background
// ----------------------------------------------------------------------------
// OriTopBar uses a 92 %-opaque white surface to evoke the mockup's `backdrop-filter:
// blur(12px)` + 85 %-white semi-transparent topbar. Compose has no real backdrop
// blur primitive (Modifier.blur blurs the composable itself, not what's behind),
// so we ship a solid translucent white. Defined here as a named token to satisfy
// the CLAUDE.md "always use MaterialTheme.colorScheme.* / no hardcoded colors"
// rule — feature code references TopBarBackground, never an inline hex.
// ============================================================================
val TopBarBackground = Color.White.copy(alpha = 0.92f)

// ============================================================================
// Status palette (success / error / warning / info)
// ============================================================================
val StatusConnected = Color(0xFF10B981)
val StatusDisconnected = Color(0xFFEF4444)
val StatusWarning = Color(0xFFF59E0B)
val StatusInfo = Color(0xFF3B82F6)

// ============================================================================
// Badge backgrounds + text colors used for protocol pills, transfer status
// badges, VM status pills, and connection status chips. Every pair below is
// referenced literally in one of the mockups (transfer-queue.html,
// connection-manager.html, proxmox.html) — keep the comments so future
// designers can grep the hex code back to the originating mockup.
// ============================================================================
val IndigoBadgeBg = Indigo50 // #EEF2FF — Upload badge bg / SFTP pill bg
val IndigoBadgeText = Indigo600 // #4F46E5 — Upload badge text / SFTP pill text

val SkyBg = Color(0xFFF0F9FF) // Download badge bg / FTP+FTPS pill bg
val SkyText = Color(0xFF0284C7) // Download badge text / FTP+FTPS pill text

val YellowBg = Color(0xFFFEF3C7) // SSH pill bg / Paused VM badge bg
val YellowText = Color(0xFF92400E) // SSH pill text / Paused VM badge text

val RedBg = Color(0xFFFEF2F2) // Failed transfer bg / Stopped VM bg / Proxmox pill bg
val RedText = Color(0xFFB91C1C) // Failed transfer text / Stopped VM text / Proxmox pill text

val GreenBg = Color(0xFFECFDF5) // Completed transfer bg / Running VM bg
val GreenText = StatusConnected // #10B981 — Completed transfer text / Running VM text

// ============================================================================
// Terminal (Tokyo Night) — explicitly dark inside the otherwise light app per
// terminal.html mockup. Kept as a flagged exception in plan v6 §10 risks.
// ============================================================================
val TerminalBackground = Color(0xFF1A1B26)
val TerminalText = Color(0xFFA9B1D6)
val TerminalGreen = Color(0xFF9ECE6A)
val TerminalRed = Color(0xFFF7768E)
val TerminalYellow = Color(0xFFE0AF68)
val TerminalBlue = Color(0xFF7AA2F7)
val TerminalPurple = Color(0xFFBB9AF7)

// ============================================================================
// Code editor — GitHub light syntax palette per code-editor.html mockup.
// Applied to Sora-Editor's EditorColorScheme inside SoraEditorView in PR 4b.
// ============================================================================
val SyntaxKeyword = Color(0xFFCF222E)
val SyntaxString = Color(0xFF0A3069)
val SyntaxComment = Color(0xFF6E7781)
val SyntaxFunction = Color(0xFF8250DF)
val SyntaxType = Color(0xFF0550AE)
val SyntaxNumber = Color(0xFF0550AE)

// ============================================================================
// Wear OS OLED palette (used by :wear's OriDevWearTheme, not by phone).
// True black bg saves OLED battery; the surface tint is dark enough to remain
// readable against #000 cards while distinct from pure black for borders.
// ============================================================================
val OledBlack = Color(0xFF0F0F0F)
val OledSurface = Color(0xFF1A1A2E)

// ============================================================================
// Premium / monetisation accent (kept for future Phase 12)
// ============================================================================
val PremiumGold = Color(0xFFFFD700)
