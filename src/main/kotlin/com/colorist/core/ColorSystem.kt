package com.colorist.core

import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack

/**
 * Port of `lib.js` color system: `global.COLOR`, `global.HEX_TO_RGB`, `global.RGB_TO_HEX`.
 *
 * The original mod maps Minecraft's 16 vanilla dyes plus one custom `soil_dye` to a fixed
 * palette of hex values. This table drives every color-mixing and attribute computation
 * in the mod.
 */
object ColorSystem {

    /** A 6-digit hex color string prefixed with `#`, e.g. `#F9801D`. */
    typealias Hex = String

    /**
     * Dye id → hex color. Keys are the dye item's registry path (without namespace).
     * Matches `global.COLOR` from the original `lib.js` line-for-line.
     *
     * See DOCUMENTATION.md §5.1.
     */
    val DYE_COLORS: Map<String, Hex> = linkedMapOf(
        "white_dye"        to "#F9FFFE",
        "orange_dye"       to "#F9801D",
        "magenta_dye"      to "#C74EBD",
        "light_blue_dye"   to "#3AB3DA",
        "yellow_dye"       to "#FED83D",
        "lime_dye"         to "#80C71F",
        "pink_dye"         to "#F38BAA",
        "gray_dye"         to "#474F52",
        "light_gray_dye"   to "#9D9D97",
        "cyan_dye"         to "#169C9C",
        "purple_dye"       to "#8932B8",
        "blue_dye"         to "#3C44AA",
        "brown_dye"        to "#835432",
        "green_dye"        to "#5E7C16",
        "red_dye"          to "#B02E26",
        "black_dye"        to "#1D1D21",
        // Custom colorist dye
        "soil_dye"         to "#8B7E6B",
    )

    /** Default paper color after a wash (see recipe §7.5). */
    const val DEFAULT_PAPER_COLOR: Hex = "#FFFFFF"

    /** Fallback color used when an item has no color data yet. */
    const val INITIAL_PAPER_COLOR: Hex = "#FFFFFF"

    /**
     * Resolves the hex color associated with an item stack, if it is a dye.
     * Returns null for non-dye stacks.
     */
    fun colorOfDye(stack: ItemStack): Hex? {
        if (stack.isEmpty) return null
        val key = stack.itemHolder.key()?.location()?.path ?: return null
        return DYE_COLORS[key]
    }

    /**
     * Returns true if [stack] is any vanilla or colorist dye tracked by [DYE_COLORS].
     */
    fun isDye(stack: ItemStack): Boolean = colorOfDye(stack) != null

    // ------------------------------------------------------------------
    //  Hex / RGB conversion — port of global.HEX_TO_RGB / global.RGB_TO_HEX
    // ------------------------------------------------------------------

    /** Parses a `#RRGGBB` string into a triple of 0-255 ints. Returns null on malformed input. */
    fun hexToRgb(hex: Hex): Triple<Int, Int, Int>? {
        val cleaned = hex.removePrefix("#")
        if (cleaned.length != 6) return null
        val r = cleaned.substring(0, 2).toIntOrNull(16) ?: return null
        val g = cleaned.substring(2, 4).toIntOrNull(16) ?: return null
        val b = cleaned.substring(4, 6).toIntOrNull(16) ?: return null
        return Triple(r, g, b)
    }

    /** Formats 0-255 ints back into `#RRGGBB`. */
    fun rgbToHex(r: Int, g: Int, b: Int): Hex {
        fun pad(v: Int) = v.coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${pad(r)}${pad(g)}${pad(b)}"
    }

    /** Convenience overload accepting a Triple. */
    fun rgbToHex(rgb: Triple<Int, Int, Int>): Hex = rgbToHex(rgb.first, rgb.second, rgb.third)

    /**
     * Linear interpolation between two hex colors.
     *
     * Port of `global.MERGE_COLOR(c1, c2, ratio)`:
     * ```
     * r = round(r1 * (1 - ratio) + r2 * ratio)
     * ```
     * where [ratio] is the weight of [c2] (the new color). Default ratio is 0.5.
     *
     * DOCUMENTATION.md §5.2 — the dye-staining pipeline uses `ratio = 1 / level`, so:
     *  - level 1 → ratio 1.0 → new color fully overrides
     *  - level 5 → ratio 0.2 → new color contributes 20%
     *  - Higher level → color becomes harder to change ("sticky").
     */
    fun mergeColor(c1: Hex, c2: Hex, ratio: Double = 0.5): Hex {
        val (r1, g1, b1) = hexToRgb(c1) ?: return c2
        val (r2, g2, b2) = hexToRgb(c2) ?: return c1
        val r = (r1 * (1 - ratio) + r2 * ratio).roundToInt()
        val g = (g1 * (1 - ratio) + g2 * ratio).roundToInt()
        val b = (b1 * (1 - ratio) + b2 * ratio).roundToInt()
        return rgbToHex(r, g, b)
    }

    /** Parses [DyeColor] to its canonical hex value. */
    fun dyeColorToHex(dye: DyeColor): Hex = when (dye) {
        DyeColor.WHITE       -> "#F9FFFE"
        DyeColor.ORANGE      -> "#F9801D"
        DyeColor.MAGENTA     -> "#C74EBD"
        DyeColor.LIGHT_BLUE  -> "#3AB3DA"
        DyeColor.YELLOW      -> "#FED83D"
        DyeColor.LIME        -> "#80C71F"
        DyeColor.PINK        -> "#F38BAA"
        DyeColor.GRAY        -> "#474F52"
        DyeColor.LIGHT_GRAY  -> "#9D9D97"
        DyeColor.CYAN        -> "#169C9C"
        DyeColor.PURPLE      -> "#8932B8"
        DyeColor.BLUE        -> "#3C44AA"
        DyeColor.BROWN       -> "#835432"
        DyeColor.GREEN       -> "#5E7C16"
        DyeColor.RED         -> "#B02E26"
        DyeColor.BLACK       -> "#1D1D21"
    }

    private fun Double.roundToInt(): Int = Math.round(this).toInt()
}
