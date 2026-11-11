package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Fresh, Modern Executive Palette for Caja Maestra
val BrandNavy = Color(0xFF0F172A)          // Slate 900 - Deep, premium dark
val BrandNavyElevated = Color(0xFF1E293B)  // Slate 800 - For HUD & App Bar
val BrandNavyBorder = Color(0xFF334155)    // Slate 700 - Crisp dividers on dark

// Radiant Accents
val BrandEmerald = Color(0xFF10B981)       // Fresh Emerald - Active states, completed sales, money in
val BrandEmeraldDark = Color(0xFF059669)
val BrandEmeraldLight = Color(0xFFD1FAE5)  // Soft badge green

val BrandAmber = Color(0xFFF59E0B)         // Radiant Amber Gold - Primary action highlights & stars
val BrandAmberDark = Color(0xFFD97706)
val BrandAmberLight = Color(0xFFFEF3C7)

val BrandBlue = Color(0xFF2563EB)          // Vibrant Indigo Blue - Links, POS selections, Primary buttons
val BrandBlueLight = Color(0xFFDBEAFE)

// Crisp, High-Legibility Canvas & Backgrounds
val CanvasLight = Color(0xFFF8FAFC)        // Slate 50 - Ultra-clean, fresh canvas
val SurfaceWhite = Color(0xFFFFFFFF)       // Crisp pure white cards
val SurfaceSubtle = Color(0xFFF1F5F9)      // Slate 100 - Secondary inputs, filter chips
val SurfaceHover = Color(0xFFE2E8F0)       // Slate 200 - Borders & dividers

// High-Contrast Visible Typography Colors
val TextDark = Color(0xFF0F172A)           // 100% sharp contrast for main text
val TextMedium = Color(0xFF334155)         // Highly readable subheads & labels
val TextMuted = Color(0xFF64748B)          // Muted metadata
val TextLight = Color(0xFFFFFFFF)          // Crisp text on dark HUD
val TextLightSecondary = Color(0xFF94A3B8) // Subtitle text on dark HUD

// Fresh HUD Gradients
val HudGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F172A),
        Color(0xFF1E293B)
    )
)

val CardGlowGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF10B981)
    )
)

val GoldAccentGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFF59E0B),
        Color(0xFFD97706)
    )
)

// Legacy alias compatibility
val NavyPrimary = BrandNavy
val NavyDark = Color(0xFF090D16)
val NavySurface = BrandNavyElevated
val NavyLight = Color(0xFF2E4372)
val GoldAccent = BrandAmber
val GoldDark = BrandAmberDark
val GoldLight = BrandAmberLight
val BackgroundLight = CanvasLight
val SurfaceCard = SurfaceWhite
val SurfaceCardAlt = SurfaceSubtle
val TextSecondary = TextMedium
val BorderLight = SurfaceHover

// Vibrant, Fresh Report Tiles Palette
val TileGreen = Color(0xFF10B981)
val TileYellow = Color(0xFFF59E0B)
val TilePurple = Color(0xFF8B5CF6)
val TileSalmon = Color(0xFFF43F5E)
val TileTeal = Color(0xFF0D9488)
val TileLilac = Color(0xFF6366F1)
val TilePink = Color(0xFFEC4899)
val TileBrown = Color(0xFF78350F)
val TileOlive = Color(0xFF65A30D)
val TileBlue = Color(0xFF2563EB)
val TileOrange = Color(0xFFEA580C)

// Transaction & Cashflow Statuses
val StatusPaid = Color(0xFF16A34A)
val StatusPending = Color(0xFFD97706)
val StatusCancelled = Color(0xFFDC2626)
val StatusCredit = Color(0xFF4F46E5)
