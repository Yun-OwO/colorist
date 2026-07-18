package com.colorist.core

import com.colorist.Colorist
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

/**
 * Port of the formatting helpers in `lib.js`:
 *  - progress-bar rendering for 虹彩 (rainbow) and 阴阳 (yin-yang) bars
 *  - `global.GRADIENT_TEXT(text, startColor, endColor)` (DOCUMENTATION.md §10.4)
 *
 * The original mod used Minecraft §-codes for inline color formatting. NeoForge
 * 1.21.1 prefers typed [Component] / [Style], so the port renders each bar segment
 * as a separately-styled text fragment — visually identical to the JS version.
 */
object TooltipFormatter {

    /** The ▍ glyph used by the original mod to render bar segments. */
    private const val BAR_GLYPH = "▍"

    /** Legacy §-codes used by the original color names (DOCUMENTATION.md §11). */
    private const val SECTION = "\u00a7"

    /** Builds a 2-digit hex prefix from a [Hex] color (e.g. `#F9801D` → `§e§8§0§1§d`). */
    private fun hexToStyle(hex: String): Style {
        val rgb = ColorSystem.hexToRgb(hex) ?: return Style.EMPTY
        val color = net.minecraft.network.chat.TextColor.fromRgb(
            ((rgb.first and 0xFF) shl 16) or ((rgb.second and 0xFF) shl 8) or (rgb.third and 0xFF)
        )
        return Style.EMPTY.withColor(color)
    }

    /**
     * Builds the 虹彩 (rainbow) progress bar from r/g/b proportions.
     *
     * Each segment is colored with the corresponding channel's pure hue
     * (red / green / blue). Segments are sized in proportion to the channel values,
     * and the total length is [Colorist.PROG_LENGTH].
     */
    fun rainbowBar(attr: Attr, length: Int = Colorist.PROG_LENGTH): MutableComponent {
        val total = (attr.r + attr.g + attr.b).coerceAtLeast(1)
        val rCount = (attr.r.toDouble() / total * length).roundToInt().coerceAtLeast(0)
        val gCount = (attr.g.toDouble() / total * length).roundToInt().coerceAtLeast(0)
        val bCount = (length - rCount - gCount).coerceAtLeast(0)

        val bar = Component.empty()
        repeat(rCount) { bar.append(Component.literal(BAR_GLYPH).withStyle(hexToStyle("#FF0000"))) }
        repeat(gCount) { bar.append(Component.literal(BAR_GLYPH).withStyle(hexToStyle("#00FF00"))) }
        repeat(bCount) { bar.append(Component.literal(BAR_GLYPH).withStyle(hexToStyle("#0000FF"))) }
        // Pad with empty segments if total < length (e.g. when r+g+b = 0).
        val remaining = length - rCount - gCount - bCount
        if (remaining > 0) {
            bar.append(Component.literal(BAR_GLYPH.repeat(remaining)).withStyle(ChatFormatting.DARK_GRAY))
        }
        return bar
    }

    /**
     * Builds the 阴阳 (yin-yang) progress bar from brightness / darkness proportions.
     *
     * Bright segments are colored white, dark segments dark gray, matching the
     * "white / dark gray" pairing documented in §10.3.
     */
    fun yinYangBar(attr: Attr, length: Int = Colorist.PROG_LENGTH): MutableComponent {
        val total = (attr.brightness + attr.darkness).coerceAtLeast(1)
        val yangCount = (attr.brightness.toDouble() / total * length).roundToInt().coerceAtLeast(0)
        val yinCount = (length - yangCount).coerceAtLeast(0)

        val bar = Component.empty()
        repeat(yangCount) { bar.append(Component.literal(BAR_GLYPH).withStyle(ChatFormatting.WHITE)) }
        repeat(yinCount)  { bar.append(Component.literal(BAR_GLYPH).withStyle(ChatFormatting.DARK_GRAY)) }
        val remaining = length - yangCount - yinCount
        if (remaining > 0) {
            bar.append(Component.literal(BAR_GLYPH.repeat(remaining)).withStyle(ChatFormatting.DARK_GRAY))
        }
        return bar
    }

    /**
     * Port of `global.GRADIENT_TEXT(text, startColor, endColor)` (DOCUMENTATION.md §10.4).
     *
     * Linearly interpolates from [startColor] to [endColor] across the characters of [text].
     * Each character is wrapped in a separately-styled span.
     */
    fun gradientText(text: String, startColor: String, endColor: String): MutableComponent {
        if (text.isEmpty()) return Component.empty()
        val (r1, g1, b1) = ColorSystem.hexToRgb(startColor) ?: return Component.literal(text)
        val (r2, g2, b2) = ColorSystem.hexToRgb(endColor) ?: return Component.literal(text)
        val result = Component.empty()
        val last = text.length - 1
        for (i in text.indices) {
            val t = if (last == 0) 0.0 else i.toDouble() / last
            val r = (r1 + (r2 - r1) * t).roundToInt()
            val g = (g1 + (g2 - g1) * t).roundToInt()
            val b = (b1 + (b2 - b1) * t).roundToInt()
            val color = net.minecraft.network.chat.TextColor.fromRgb(
                ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
            )
            result.append(Component.literal(text[i].toString()).withStyle(Style.EMPTY.withColor(color)))
        }
        return result
    }

    /**
     * Localized name lookup keys for the standard colorist multi-color "魔虹" naming.
     *
     * DOCUMENTATION.md §11 specifies these as §d魔§e虹§3术§a书-style sequences. The port
     * expresses them as colored Components assembled at runtime so they adapt to
     * language files.
     */
    fun magicGradientName(prefix: String, suffix: String): MutableComponent {
        // §d粉 §e黄 §3青 §a绿 — matching the documented "魔虹" pattern.
        val chars = listOf("$prefix" to "#FF55FF", "虹" to "#FED83D", "术" to "#169C9C", suffix to "#80C71F")
        val result = Component.empty()
        for ((ch, color) in chars) {
            result.append(Component.literal(ch).withStyle(hexToStyle(color)))
        }
        return result
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
}
