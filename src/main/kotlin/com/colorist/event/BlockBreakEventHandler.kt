package com.colorist.event

import com.colorist.Colorist
import com.colorist.blockentity.MagicTableBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent

/**
 * Port of `server_scripts/block.js` — handles block break events.
 *
 * For the magic table: the original mod returned the stored item to the player
 * when the table was broken. Our [MagicTableBlock.onRemove] already drops the
 * stored item into the world via [Block.popResource], so this handler exists
 * primarily for future extension (e.g. canceling breaks in protected areas).
 *
 * It also cancels block break attempts when the magic table currently holds an
 * item the player can't fit, mirroring the original "auto-return to player" UX.
 */
object BlockBreakEventHandler {

    @SubscribeEvent
    fun onBlockBroken(event: BlockEvent.BreakEvent) {
        val level: Level = event.level as? Level ?: return
        if (level.isClientSide) return

        val pos: BlockPos = event.pos
        val be: BlockEntity = level.getBlockEntity(pos) ?: return
        if (be is MagicTableBlockEntity && be.hasItem()) {
            // Hand the stored item directly to the player if possible; the
            // BlockEntity's onRemove will otherwise drop it as an item entity.
            val player = event.player
            val stored = be.storedItem.copy()
            if (!player.addItem(stored)) {
                // Inventory full — let onRemove drop it as a world item.
                return
            }
            be.clearStoredItem()
        }
    }

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID
}
