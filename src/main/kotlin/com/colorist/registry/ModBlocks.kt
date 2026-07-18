package com.colorist.registry

import com.colorist.Colorist
import com.colorist.block.MagicCrystalOreBlock
import com.colorist.block.MagicTableBlock
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.core.registries.Registries

/**
 * Port of `startup_scripts/block.js` — registers Colorist's two custom blocks
 * and their corresponding [BlockItem]s.
 *
 * Blocks (DOCUMENTATION.md §4.1):
 *  - magic_table         (hardness 1.0, light 3, no tool required)
 *  - magic_crystal_ore   (hardness 2.0, requires pickaxe, tag forge:ore)
 *
 * Tags are added through data JSON files in `data/colorist/tags/...` (the
 * "mineable/pickaxe" tag, the "forge:ore" tag, etc.).
 */
object ModBlocks {

    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Colorist.MOD_ID)

    val MAGIC_TABLE: DeferredHolder<Block, MagicTableBlock> =
        BLOCKS.register("magic_table") {
            MagicTableBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f)
                    .lightLevel { 3 }
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
            )
        }

    val MAGIC_CRYSTAL_ORE: DeferredHolder<Block, MagicCrystalOreBlock> =
        BLOCKS.register("magic_crystal_ore") {
            MagicCrystalOreBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(MagicCrystalOreBlock.HARDNESS, MagicCrystalOreBlock.RESISTANCE)
                    .requiresCorrectToolForDrops()
            )
        }

    // --- Block items ---------------------------------------------------

    /**
     * BlockItem registrations are kept inside [ModItems] in vanilla convention
     * because items and block-items share the same registry. We expose the
     * BlockItem suppliers here for convenience.
     */
    val MAGIC_TABLE_ITEM: DeferredHolder<Item, BlockItem> = ModItems.ITEMS.register("magic_table") {
        BlockItem(MAGIC_TABLE.get(), Item.Properties())
    }

    val MAGIC_CRYSTAL_ORE_ITEM: DeferredHolder<Item, BlockItem> = ModItems.ITEMS.register("magic_crystal_ore") {
        BlockItem(MAGIC_CRYSTAL_ORE.get(), Item.Properties())
    }
}
