package com.colorist.block

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

/**
 * Magic Crystal Ore — the source of magic crystals.
 *
 * Per DOCUMENTATION.md §4.1 the ore has hardness 2.0, requires a pickaxe to drop,
 * and is tagged `minecraft:mineable/pickaxe` + `forge:ore`. Smelting it produces
 * a magic crystal (see §7.6).
 *
 * The block itself is a plain [Block]; all special behavior (drops, smelting) is
 * configured via data files. We keep a dedicated subclass to ease future
 * extensibility (e.g. redstone power based on density).
 */
class MagicCrystalOreBlock(properties: Properties) : Block(properties) {
    companion object {
        /** Hardness documented in §4.1. */
        const val HARDNESS: Float = 2.0f

        /** Resistance matches hardness in vanilla ores. */
        const val RESISTANCE: Float = 2.0f
    }
}
