package com.colorist.core

import com.colorist.Colorist
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Persistent, codec-backed color attribute record.
 *
 * Ports the implicit `nbt.attr` object from the original KubeJS mod. Each instance
 * describes the visible attributes of a single magic paper, or the aggregate of all
 * papers inside a magic book.
 *
 * Field semantics (DOCUMENTATION.md §5.3, §5.4):
 *  - [r], [g], [b]            : 0–10 normalized color channels (朱赤 / 碧青 / 苍蓝)
 *  - [brightness] / [yang]    : 0–10 (阳) — mean of R/G/B at 0–255 scale
 *  - [darkness] / [yin]       : 0–10 (阴) — `10 - brightness`
 *  - [level]                  : fractional level — accumulated via magic crystals & combat
 *  - [color]                  : the `#RRGGBB` string used for tooltip coloring / rendering
 */
data class Attr(
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0,
    val brightness: Int = 0,
    val darkness: Int = 0,
    val level: Double = 0.0,
    val color: String = ColorSystem.DEFAULT_PAPER_COLOR,
) {
    /** Convenience alias matching the Chinese yin/yang naming used in tooltips. */
    val yang: Int get() = brightness
    val yin: Int get() = darkness

    /** Returns a copy with [color] recomputed from r/g/b at the 0–10 → 0–255 scale. */
    fun withRecomputedColor(): Attr {
        val (cr, cg, cb) = Triple(
            (r * 255 / 10).coerceIn(0, 255),
            (g * 255 / 10).coerceIn(0, 255),
            (b * 255 / 10).coerceIn(0, 255),
        )
        return copy(color = ColorSystem.rgbToHex(cr, cg, cb))
    }

    companion object {
        val CODEC: Codec<Attr> = RecordCodecBuilder.create { it ->
            it.group(
                Codec.INT.fieldOf("r").forGetter(Attr::r),
                Codec.INT.fieldOf("g").forGetter(Attr::g),
                Codec.INT.fieldOf("b").forGetter(Attr::b),
                Codec.INT.fieldOf("brightness").forGetter(Attr::brightness),
                Codec.INT.fieldOf("darkness").forGetter(Attr::darkness),
                Codec.DOUBLE.fieldOf("level").forGetter(Attr::level),
                Codec.STRING.fieldOf("color").forGetter(Attr::color),
            ).apply(it, ::Attr)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Attr> = StreamCodec.composite(
            ByteBufCodecs.INT, Attr::r,
            ByteBufCodecs.INT, Attr::g,
            ByteBufCodecs.INT, Attr::b,
            ByteBufCodecs.INT, Attr::brightness,
            ByteBufCodecs.INT, Attr::darkness,
            ByteBufCodecs.DOUBLE, Attr::level,
            ByteBufCodecs.STRING_UTF8, Attr::color,
            ::Attr,
        )

        /** An empty/zero attr — used for fresh magic books before any paper is injected. */
        val EMPTY: Attr = Attr()

        /**
         * Builds an [Attr] from a hex color and an existing level.
         *
         * Port of `global.CALC_ATTR(nbt)` (DOCUMENTATION.md §5.3):
         * ```
         * for each channel c in {r, g, b}:
         *     attr[c] = round((c / 255) * 10)        // 0-255 → 0-10
         * attr.brightness = round(mean(r,g,b) / 255 * 10)
         * attr.darkness   = 10 - attr.brightness
         * ```
         */
        fun fromColor(color: String, level: Double): Attr {
            val (r0, g0, b0) = ColorSystem.hexToRgb(color)
                ?: return EMPTY.copy(level = level, color = color)
            val r  = Math.round(r0 / 255.0 * 10).toInt().coerceIn(0, 10)
            val g  = Math.round(g0 / 255.0 * 10).toInt().coerceIn(0, 10)
            val b  = Math.round(b0 / 255.0 * 10).toInt().coerceIn(0, 10)
            // brightness is the average across the three normalized channels, scaled to 0..10.
            val brightness = Math.round((r0 / 255.0 + g0 / 255.0 + b0 / 255.0) / 3.0 * 10).toInt().coerceIn(0, 10)
            val darkness = (10 - brightness).coerceIn(0, 10)
            return Attr(r, g, b, brightness, darkness, level, color)
        }

        /** Convenience for an attr carrying no level data (used during pure tooltip preview). */
        fun fromColor(color: String): Attr = fromColor(color, 0.0)
    }
}

/**
 * Port of `global.CALC_ATTRS(attrs)` — averages a list of [Attr] into a single aggregate.
 *
 * Each channel of the aggregate is the arithmetic mean of the corresponding channel across
 * all input attrs (sum / count). The resulting color is then recomputed by scaling 0-10
 * channels back to 0-255. See DOCUMENTATION.md §5.4.
 *
 * If [attrs] is empty, returns [Attr.EMPTY].
 */
fun aggregateAttrs(attrs: List<Attr>): Attr {
    if (attrs.isEmpty()) return Attr.EMPTY
    val n = attrs.size
    val sumR = attrs.sumOf { it.r }
    val sumG = attrs.sumOf { it.g }
    val sumB = attrs.sumOf { it.b }
    val sumBright = attrs.sumOf { it.brightness }
    val sumDark = attrs.sumOf { it.darkness }
    val sumLevel = attrs.sumOf { it.level }

    val avgR = (sumR.toDouble() / n).let { Math.round(it).toInt().coerceIn(0, 10) }
    val avgG = (sumG.toDouble() / n).let { Math.round(it).toInt().coerceIn(0, 10) }
    val avgB = (sumB.toDouble() / n).let { Math.round(it).toInt().coerceIn(0, 10) }
    val avgBright = (sumBright.toDouble() / n).let { Math.round(it).toInt().coerceIn(0, 10) }
    val avgDark = (sumDark.toDouble() / n).let { Math.round(it).toInt().coerceIn(0, 10) }
    val avgLevel = sumLevel / n

    val color = ColorSystem.rgbToHex(
        (avgR * 255 / 10).coerceIn(0, 255),
        (avgG * 255 / 10).coerceIn(0, 255),
        (avgB * 255 / 10).coerceIn(0, 255),
    )
    return Attr(avgR, avgG, avgB, avgBright, avgDark, avgLevel, color)
}

/**
 * Port of `global.ATTR_ADDER(attr, key, delta)` — mutates a numeric field by [delta],
 * producing a new [Attr]. Level is clamped at 0 from below to prevent negative-level
 * underflow when the magic book is cast repeatedly.
 *
 * The original JS implementation supported all numeric keys; we expose typed helpers
 * below for the two keys actually used in the codebase (level).
 */
fun Attr.addLevel(delta: Double): Attr {
    val newLevel = (level + delta).coerceAtLeast(0.0)
    return copy(level = newLevel)
}

/** Numeric fields actually mutated by ATTR_ADDER in the source mod. */
object AttrKeys {
    const val LEVEL = "level"
}

/** Cap helpers shared across the registry. */
object AttrLimits {
    const val MAX_R = 10
    const val MAX_G = 10
    const val MAX_B = 10
    const val MAX_BRIGHTNESS = 10
    const val MAX_DARKNESS = 10
    const val MAX_PAPERS_PER_BOOK = Colorist.MAX_ATTRS
}
