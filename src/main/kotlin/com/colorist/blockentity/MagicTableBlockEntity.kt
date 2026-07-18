package com.colorist.blockentity

import com.colorist.Colorist
import com.colorist.block.MagicTableBlock
import com.colorist.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Block entity backing the [MagicTableBlock]. Stores exactly one item stack —
 * whatever the player has placed onto the table for processing.
 *
 * Port of the KubeJS `blockEntity(e => e.initialData({ item: "minecraft:air", nbt: {} }))`
 * definition (DOCUMENTATION.md §4.2). The original mod tracked `item: string` and
 * `nbt: object` separately; in 1.21.1 we use a single [ItemStack] field which
 * transparently carries any data components.
 */
class MagicTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlockEntities.MAGIC_TABLE.get(), pos, state) {

    /** The item currently placed on the table. [ItemStack.EMPTY] when the table is clear. */
    var storedItem: ItemStack = ItemStack.EMPTY
        private set

    fun hasItem(): Boolean = !storedItem.isEmpty

    /**
     * Replaces the stored item and refreshes the [MagicTableBlock.HAS_ITEM] block
     * state. Triggers a client sync and a chunk mark.
     */
    fun setStoredItem(stack: ItemStack) {
        storedItem = stack.copy()
        updateBlockState()
        setChanged()
        if (level is ServerLevel) {
            val s = level!!.getBlockState(blockPos)
            level!!.sendBlockUpdated(blockPos, s, s, 3)
        }
    }

    fun clearStoredItem() {
        storedItem = ItemStack.EMPTY
        updateBlockState()
        setChanged()
    }

    /**
     * Hands the stored item back to [player] (into their inventory if possible,
     * otherwise dropped on the floor) and clears the slot.
     */
    fun popItemTo(player: Player) {
        if (!hasItem()) return
        if (!player.addItem(storedItem.copy())) {
            // `BlockEntity.level` is `Level?` (nullable) — popResource requires a non-null
            // `Level`. We bind to a local `val` so Kotlin smart-casts it to non-null inside
            // the `if` block. If the entity has no level (e.g. it's being torn down), we
            // skip the drop rather than crash.
            val level = this.level
            if (level != null) {
                popResource(level, blockPos, storedItem.copy())
            }
        }
        clearStoredItem()
    }

    private fun updateBlockState() {
        val level = this.level ?: return
        val state = level.getBlockState(blockPos)
        if (state.block is MagicTableBlock) {
            val newState = state.setValue(MagicTableBlock.HAS_ITEM, hasItem())
            level.setBlock(blockPos, newState, 3)
        }
    }

    // ------------------------------------------------------------------
    //  Persistence & sync
    // ------------------------------------------------------------------

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        if (!storedItem.isEmpty) {
            tag.put(KEY_STORED_ITEM, storedItem.save(registries))
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        storedItem = if (tag.contains(KEY_STORED_ITEM)) {
            ItemStack.parse(registries, tag.getCompound(KEY_STORED_ITEM)).orElse(ItemStack.EMPTY)
        } else {
            ItemStack.EMPTY
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(registries)
        saveAdditional(tag, registries)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    companion object {
        private const val KEY_STORED_ITEM = "stored_item"

        @Suppress("unused")
        private const val MOD_ID = Colorist.MOD_ID
    }
}
