package com.colorist.event

import com.colorist.Colorist
import com.colorist.core.Attr
import com.colorist.core.ValueCounter
import com.colorist.item.MagicBookItem
import com.colorist.registry.ModDataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

/**
 * Port of `server_scripts/inventory.js` — grants the magic book's HP bonus to
 * the carrying player.
 *
 * Behavior (DOCUMENTATION.md §6.4):
 *  - When a magic book enters the player's inventory → add `hp` from the book's
 *    aggregate attr to the player's `max_health` base value.
 *  - When the book leaves → remove the previously granted bonus.
 *  - On respawn → reset bonus state and recompute.
 *  - Uses a per-stack boolean component `MAGIC_BOOK_HP_BONUS` to prevent the
 *    same book from granting its bonus twice.
 *
 * KubeJS exposed `PlayerEvents.inventoryChanged` for the original mod; NeoForge
 * 1.21.1 has no exact equivalent, so we poll inventories on [ServerTickEvent.Post]
 * every 10 ticks (~0.5 s). The polling cost is trivial: O(player count × inventory
 * size × constant) per pass.
 */
object InventoryEventHandler {

    /** Recompute interval (in server ticks). 10 = twice per second. */
    private const val RECOMPUTE_INTERVAL_TICKS = 10

    /** Player UUID → currently applied HP bonus (sum of book HP values). */
    private val appliedBonus: MutableMap<UUID, Double> = mutableMapOf()

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (event.server.tickCount % RECOMPUTE_INTERVAL_TICKS != 0L) return
        for (player in event.server.playerList.players) {
            recomputeHpBonus(player)
        }
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? Player ?: return
        if (player.level().isClientSide) return
        // On respawn the player's attributes are reset; clear our records.
        appliedBonus.remove(player.uuid)
        // Also clear the per-stack flags so the next recomputation re-applies cleanly.
        player.inventory.allItems.forEach { stack -> clearBonusFlag(stack) }
        recomputeHpBonus(player)
    }

    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? Player ?: return
        if (player.level().isClientSide) return
        // Strip the bonus on logout so the attribute base value is preserved
        // correctly across sessions.
        val current = appliedBonus.remove(player.uuid) ?: 0.0
        if (current != 0.0) {
            val attr: AttributeInstance = player.getAttribute(Attributes.MAX_HEALTH) ?: return
            attr.baseValue = (attr.baseValue - current).coerceAtLeast(2.0)
            if (player.health > player.maxHealth) {
                player.health = player.maxHealth
            }
        }
        // Clear the per-stack flags so re-login triggers a fresh grant.
        player.inventory.allItems.forEach { stack -> clearBonusFlag(stack) }
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    /**
     * Walks the player's inventory and ensures the player's MAX_HEALTH attribute
     * reflects exactly the sum of HP values of all magic books currently held.
     *
     * Strategy:
     *  1. Compute the target bonus = Σ book.aggregate.hp for each MagicBookItem
     *     currently in the inventory.
     *  2. Compare against [appliedBonus][player.uuid] (last applied amount).
     *  3. If different, adjust the attribute by the delta.
     *  4. Stamp each book with `MAGIC_BOOK_HP_BONUS = true` so a partial removal
     *     is detected on the next pass.
     */
    private fun recomputeHpBonus(player: Player) {
        val attr: AttributeInstance = player.getAttribute(Attributes.MAX_HEALTH) ?: return

        var targetBonus = 0.0
        for (stack in player.inventory.allItems) {
            if (stack.isEmpty) continue
            if (stack.item !is MagicBookItem) continue
            val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
            targetBonus += ValueCounter.count(aggregate).hp
            // Mark this book as currently contributing.
            stack.set(ModDataComponents.MAGIC_BOOK_HP_BONUS.get(), true)
        }

        val current = appliedBonus[player.uuid] ?: 0.0
        val delta = targetBonus - current
        if (delta != 0.0) {
            attr.baseValue = (attr.baseValue + delta).coerceAtLeast(2.0)
            appliedBonus[player.uuid] = targetBonus
            if (player.health > player.maxHealth) {
                player.health = player.maxHealth
            }
        }
    }

    private fun clearBonusFlag(stack: ItemStack) {
        if (stack.item is MagicBookItem) {
            stack.set(ModDataComponents.MAGIC_BOOK_HP_BONUS.get(), false)
        }
    }

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID
}
