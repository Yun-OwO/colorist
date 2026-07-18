package com.colorist.core

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Port of `global.VALUE_COUNTER(attr, zero)` from `lib.js`.
 *
 * Converts a single color [Attr] into the RPG combat tuple documented in
 * DOCUMENTATION.md §6.1 / §6.2:
 *
 * | field    | formula                          | range        | meaning                |
 * |----------|----------------------------------|--------------|------------------------|
 * | cost     | 0.9 - b/150                      | ~0.83..0.90  | casting cost (level)   |
 * | atk      | r^1.1/10 + level^0.8/5           | 0..6+        | attack damage          |
 * | hp       | g^1.1/5 + level^0.8/5            | 0..12+       | bonus max HP           |
 * | br       | sqrt(brightness)*2.5/100         | 0..~0.079    | crit chance            |
 * | bd       | darkness/100                     | 0..0.10      | crit damage multiplier |
 *
 * The [zero] flag in the original JS forces all values to 0 (used when an item had
 * no attr). Here we model that case by passing [Attr.EMPTY] — see [ValueCounter.empty].
 */
data class AttrValue(
    val cost: Double,
    val atk: Double,
    val hp: Double,
    val br: Double,
    val bd: Double,
) {
    companion object {
        val EMPTY = AttrValue(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

object ValueCounter {

    /**
     * Computes the combat tuple for a single [Attr]. Returns [AttrValue.EMPTY] when
     * [attr] has zero total magnitude (the [Attr.EMPTY] sentinel).
     */
    fun count(attr: Attr): AttrValue {
        if (attr == Attr.EMPTY) return AttrValue.EMPTY
        val r = attr.r.toDouble()
        val g = attr.g.toDouble()
        val b = attr.b.toDouble()
        val level = attr.level
        val brightness = attr.brightness.toDouble()
        val darkness = attr.darkness.toDouble()

        // Math.pow is used instead of kotlin.math.pow to match the JS Math.pow semantics
        // (the original lib.js used Math.pow).
        val cost = 0.9 - b / 150.0
        val atk  = Math.pow(r, 1.1) / 10.0 + Math.pow(level, 0.8) / 5.0
        val hp   = Math.pow(g, 1.1) / 5.0  + Math.pow(level, 0.8) / 5.0
        val br   = sqrt(brightness) * 2.5 / 100.0
        val bd   = darkness / 100.0

        return AttrValue(cost, atk, hp, br, bd)
    }

    /** Convenience: aggregate-compute for a list of attrs (magic book). */
    fun countAggregated(attrs: List<Attr>): AttrValue = count(aggregateAttrs(attrs))
}
