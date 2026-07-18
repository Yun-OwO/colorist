package com.colorist.event

import com.colorist.Colorist
import com.colorist.item.MagicBookItem
import com.colorist.registry.ModDataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * Server-side driver for the magic book's channeled ray attack.
 *
 * The original KubeJS implementation (DOCUMENTATION.md §6.3, §13) used a
 * `ServerEvents.tick` hook to advance every active cast. We mirror that here:
 *  - On right-click, [MagicBookItem.use] sets the casting flag and sends a
 *    `MagicStartPayload`.
 *  - Each server tick, [onServerTick] walks all online players and advances any
 *    in-progress cast via [MagicBookItem.processCastTick].
 *  - When the cast duration elapses, [MagicBookItem.endCast] deducts the level
 *    cost from the first paper and sends a `MagicStopPayload`.
 *
 * Per-player cast progress is stored in a side-table ([castProgress]) keyed by
 * player UUID, so it survives client reconnects but is wiped on server restart
 * (matches the original mod's behavior — `usingMagic` was on the item only).
 */
object MagicBookServerEventHandler {

    private data class CastState(
        val playerUUID: java.util.UUID,
        val hand: InteractionHand,
        var tickIndex: Int,
    )

    private val activeCasts: MutableMap<java.util.UUID, CastState> = mutableMapOf()

    /**
     * Right-click handler — the cast itself is initiated inside [MagicBookItem.use],
     * but we still need to register the cast in [activeCasts] when the item reports
     * it has entered the casting state.
     */
    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickItem) {
        val stack = event.itemStack
        if (stack.item !is MagicBookItem) return
        if (event.entity.level().isClientSide) return
        // Only register if the item just transitioned into casting state on this click.
        // The item's use() already set the casting flag if conditions were met.
        val casting = stack.get(ModDataComponents.MAGIC_BOOK_CASTING.get()) == true
        if (casting && event.entity is ServerPlayer) {
            activeCasts[event.entity.uuid] = CastState(
                playerUUID = event.entity.uuid,
                hand = event.hand,
                tickIndex = 0,
            )
        }
    }

    /**
     * Per-tick cast driver — advances every active cast, calling
     * [MagicBookItem.processCastTick] on the player's held book.
     */
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (activeCasts.isEmpty()) return
        val server = event.server
        val toRemove = mutableListOf<java.util.UUID>()
        for ((uuid, state) in activeCasts) {
            val player = server.playerList.getPlayer(uuid) ?: run {
                toRemove.add(uuid); continue
            }
            val stack: ItemStack = player.getItemInHand(state.hand)
            if (stack.item !is MagicBookItem || stack.get(ModDataComponents.MAGIC_BOOK_CASTING.get()) != true) {
                toRemove.add(uuid); continue
            }
            val book = stack.item as MagicBookItem
            val keepGoing = book.processCastTick(player, stack, state.tickIndex)
            state.tickIndex++
            if (!keepGoing) toRemove.add(uuid)
        }
        toRemove.forEach { activeCasts.remove(it) }
    }

    /** @suppress internal use */
    fun clearCastsFor(playerUUID: java.util.UUID) {
        activeCasts.remove(playerUUID)
    }

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID
}
