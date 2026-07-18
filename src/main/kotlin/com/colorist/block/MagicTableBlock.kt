package com.colorist.block

import com.colorist.Colorist
import com.colorist.blockentity.MagicTableBlockEntity
import com.colorist.core.Attr
import com.colorist.core.ColorSystem
import com.colorist.core.aggregateAttrs
import com.colorist.registry.ModDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * The Magic Table — Colorist's central crafting station.
 *
 * Ports `startup_scripts/block.js` and `startup_scripts/magic_table.js`.
 *
 * Interaction matrix (DOCUMENTATION.md §4.2):
 *
 * | Action              | Condition                              | Effect                                |
 * |---------------------|----------------------------------------|---------------------------------------|
 * | Place item          | table empty, hand has item             | item placed on table                  |
 * | Take item           | sneak + right-click, table occupied    | item returned to player               |
 * | Dye paper           | paper on table, hand has dye           | dye consumed, color merged into paper |
 * | Add crystal         | item on table, hand has magic_crystal  | item level += 5                       |
 * | Inject paper        | book on table, hand has magic_paper    | paper pushed into book (max 12)       |
 *
 * The block uses a [MagicTableBlockEntity] to store the placed item stack; the
 * floating-item display is achieved by spawning an [ItemEntity] above the table
 * (DOCUMENTATION.md §4.3).
 */
class MagicTableBlock(properties: Properties) : BaseEntityBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(HAS_ITEM, false))
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        MagicTableBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    /**
     * The table is shorter than a full block (12/16 height, DOCUMENTATION.md §4.2).
     * The shape matches the documented "16×12×16 pixel" footprint.
     */
    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    override fun pushReaction(state: BlockState): PushReaction = PushReaction.BLOCK

    // ------------------------------------------------------------------
    //  Right-click interaction — the heart of the magic table
    // ------------------------------------------------------------------

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val be = level.getBlockEntity(pos) as? MagicTableBlockEntity ?: return InteractionResult.PASS
        // Sneak + right-click with empty hand: take the item back.
        if (player.isShiftKeyDown && player.mainHandItem.isEmpty) {
            return if (be.hasItem()) {
                be.popItemTo(player)
                InteractionResult.CONSUME
            } else {
                InteractionResult.PASS
            }
        }
        return InteractionResult.PASS
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): ItemInteractionResult {
        // NOTE: `ItemInteractionResult` is a vanilla enum (net.minecraft.world.ItemInteractionResult)
        // whose constants are SUCCESS, CONSUME, CONSUME_PARTIAL, PASS_TO_DEFAULT_BLOCK_INTERACTION,
        // SKIP_DEFAULT_BLOCK_INTERACTION, FAIL. There are no static factory methods such as
        // `succeedAndConsume()` — that pattern belongs to `InteractionResult`. We use the enum
        // constants directly here.
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        val be = level.getBlockEntity(pos) as? MagicTableBlockEntity
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        // Case 1: table empty + player holding something → place item.
        if (!be.hasItem()) {
            if (stack.isEmpty) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            val placed = stack.copy()
            placed.count = 1
            be.setStoredItem(placed)
            stack.shrink(1)
            if (stack.isEmpty) {
                player.setItemInHand(hand, ItemStack.EMPTY)
            }
            spawnFloatingItem(level, pos, placed)
            player.displayClientMessage(
                Component.translatable("colorist.magic_table.placed")
                    .withStyle(net.minecraft.ChatFormatting.GRAY),
                true,
            )
            return ItemInteractionResult.SUCCESS
        }

        // Case 2: sneak + right-click → take item back.
        if (player.isShiftKeyDown) {
            be.popItemTo(player)
            return ItemInteractionResult.SUCCESS
        }

        // Case 3: hand has dye and stored item is a magic paper → dye it.
        val stored = be.storedItem
        val dyeColor = ColorSystem.colorOfDye(stack) ?: com.colorist.registry.ModItems.specialDyeColor(stack)
        if (dyeColor != null && isMagicPaper(stored)) {
            val result = dyePaper(stored, dyeColor)
            be.setStoredItem(result)
            stack.shrink(1)
            player.displayClientMessage(
                Component.translatable("colorist.magic_table.dyed")
                    .withStyle(net.minecraft.ChatFormatting.GRAY),
                true,
            )
            level.playSound(
                null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0f, 1.2f,
            )
            return ItemInteractionResult.SUCCESS
        }

        // Case 4: hand has magic_crystal → raise stored item's level by 5.
        if (isMagicCrystal(stack) && canGainLevel(stored)) {
            val leveled = addLevel(stored, Colorist.CRYSTAL_LEVEL_BONUS.toDouble())
            be.setStoredItem(leveled)
            stack.shrink(1)
            player.displayClientMessage(
                Component.translatable("colorist.magic_table.crystal")
                    .withStyle(net.minecraft.ChatFormatting.GRAY),
                true,
            )
            level.playSound(
                null, pos, SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.BLOCKS, 1.0f, 1.5f,
            )
            return ItemInteractionResult.SUCCESS
        }

        // Case 5: hand has magic_paper and stored item is a magic book → inject paper.
        if (isMagicPaper(stack) && isMagicBook(stored)) {
            val injected = injectPaper(stored, stack)
            if (injected != null) {
                be.setStoredItem(injected)
                stack.shrink(1)
                val count = (injected.get(ModDataComponents.MAGIC_BOOK_ATTRS.get()) ?: emptyList<Attr>()).size
                player.displayClientMessage(
                    Component.translatable("colorist.magic_table.injected", count, Colorist.MAX_ATTRS)
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                    true,
                )
                level.playSound(
                    null, pos, SoundEvents.PLAYER_LEVELUP,
                    SoundSource.BLOCKS, 0.6f, 1.5f,
                )
                return ItemInteractionResult.SUCCESS
            } else {
                player.displayClientMessage(
                    Component.translatable("colorist.magic_table.full")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                    true,
                )
                return ItemInteractionResult.CONSUME
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    // ------------------------------------------------------------------
    //  Block-state plumbing
    // ------------------------------------------------------------------

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HAS_ITEM)
    }

    @Deprecated("Deprecated in Java")
    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        moved: Boolean,
    ) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? MagicTableBlockEntity)?.let { be ->
                if (be.hasItem()) {
                    popResource(level, pos, be.storedItem.copy())
                    be.clearStoredItem()
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        // No per-tick processing is required for the table itself.
        return null
    }

    // ------------------------------------------------------------------
    //  Helpers — implemented as private members on the block for clarity
    // ------------------------------------------------------------------

    private fun isMagicPaper(stack: ItemStack): Boolean =
        stack.itemHolder.key()?.location()?.path == "magic_paper"

    private fun isMagicBook(stack: ItemStack): Boolean =
        stack.itemHolder.key()?.location()?.path == "magic_book"

    private fun isMagicCrystal(stack: ItemStack): Boolean =
        stack.itemHolder.key()?.location()?.path == "magic_crystal"

    private fun canGainLevel(stack: ItemStack): Boolean = isMagicPaper(stack) || isMagicBook(stack)

    /**
     * Dyes a magic paper using the documented `MERGE_COLOR` formula:
     * `attr.color = MERGE_COLOR(attr.color, dyeColor, 1 / level)`.
     * The attr is then fully recomputed from the new color via [Attr.fromColor].
     */
    private fun dyePaper(paper: ItemStack, dyeColor: String): ItemStack {
        val result = paper.copy()
        val current = result.get(ModDataComponents.MAGIC_PAPER_ATTR.get()) ?: Attr()
        val ratio = if (current.level <= 0.0) 1.0 else 1.0 / current.level
        val merged = ColorSystem.mergeColor(current.color, dyeColor, ratio)
        val newAttr = Attr.fromColor(merged, current.level)
        result.set(ModDataComponents.MAGIC_PAPER_ATTR.get(), newAttr)
        return result
    }

    /** Adds [delta] to the stored item's level — paper or book aggregate. */
    private fun addLevel(stack: ItemStack, delta: Double): ItemStack {
        val result = stack.copy()
        if (isMagicPaper(result)) {
            val attr = result.get(ModDataComponents.MAGIC_PAPER_ATTR.get()) ?: Attr()
            result.set(
                ModDataComponents.MAGIC_PAPER_ATTR.get(),
                attr.addLevel(delta),
            )
        } else if (isMagicBook(result)) {
            val attrs = (result.get(ModDataComponents.MAGIC_BOOK_ATTRS.get()) ?: emptyList()).toMutableList()
            if (attrs.isNotEmpty()) {
                val first = attrs.removeAt(0)
                attrs.add(0, first.addLevel(delta))
                result.set(ModDataComponents.MAGIC_BOOK_ATTRS.get(), attrs.toList())
                result.set(ModDataComponents.MAGIC_BOOK_AGGREGATE.get(), aggregateAttrs(attrs))
            }
        }
        return result
    }

    /**
     * Injects [paperStack] into [bookStack] if there's room. Returns the modified
     * book, or null if the book is full (12 papers max).
     */
    private fun injectPaper(bookStack: ItemStack, paperStack: ItemStack): ItemStack? {
        val current = (bookStack.get(ModDataComponents.MAGIC_BOOK_ATTRS.get()) ?: emptyList()).toMutableList()
        if (current.size >= Colorist.MAX_ATTRS) return null
        val paperAttr = paperStack.get(ModDataComponents.MAGIC_PAPER_ATTR.get()) ?: Attr()
        current.add(paperAttr)
        val result = bookStack.copy()
        result.set(ModDataComponents.MAGIC_BOOK_ATTRS.get(), current.toList())
        result.set(ModDataComponents.MAGIC_BOOK_AGGREGATE.get(), aggregateAttrs(current))
        return result
    }

    /**
     * Spawns a non-gravity, non-pickup item entity above the table to give visual
     * feedback. Port of `global.FLOAT_ITEM` (DOCUMENTATION.md §4.3).
     *
     * The entity is purely cosmetic — the canonical item data lives in the block
     * entity. We use a long pickup delay and disable gravity to mirror the
     * original implementation.
     */
    private fun spawnFloatingItem(level: Level, pos: BlockPos, stack: ItemStack) {
        if (level.isClientSide) return
        val entity = ItemEntity(
            level,
            pos.x + 0.5,
            pos.y + 1.0,
            pos.z + 0.5,
            stack.copy(),
        )
        entity.setNoGravity(true)
        entity.pickupDelay = Int.MAX_VALUE
        entity.age = -32768
        level.addFreshEntity(entity)
    }

    companion object {
        /** Block state property indicating whether the table currently holds an item. */
        val HAS_ITEM: BooleanProperty = BooleanProperty.create("has_item")

        /** 16×12×16 collision shape, matching the documented model footprint. */
        private val SHAPE: VoxelShape = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0)
    }
}
