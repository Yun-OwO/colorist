package com.colorist.item

import com.colorist.Colorist
import com.colorist.core.Attr
import com.colorist.core.ColorSystem
import com.colorist.core.ValueCounter
import com.colorist.network.ModPayloads
import com.colorist.network.MagicStartPayload
import com.colorist.network.MagicStopPayload
import com.colorist.registry.ModDataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The Magic Book — Colorist's signature weapon.
 *
 * Right-click fires a 10-tick ray attack (DOCUMENTATION.md §6.3):
 *   1. The book enters "casting" state for [Colorist.RAY_DURATION_TICKS] ticks.
 *   2. Each tick re-runs a 10-block entity raytrace from the player's eye.
 *   3. On hit, damage is computed from `ValueCounter.count(aggregate).atk`.
 *   4. Crit roll: `Math.random() < br`. On crit, damage multiplied by `1 + bd`.
 *   5. When the cast ends, level of the first paper is reduced by `cost`.
 *
 * The book's "durability" bar is purely cosmetic — it visualizes
 *   `(1 - level / (cost * 100)) * 1000` rather than tracking real damage.
 * The book never actually breaks.
 */
class MagicBookItem(properties: Properties) : Item(properties) {

    // ------------------------------------------------------------------
    //  Dynamic durability display (DOCUMENTATION.md §3.3)
    // ------------------------------------------------------------------

    override fun getMaxDamage(stack: ItemStack): Int = Colorist.MAGIC_BOOK_MAX_DURABILITY

    override fun getDamage(stack: ItemStack): Int {
        val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
        if (aggregate == Attr.EMPTY) return 0 // freshly crafted — full bar
        val cost = ValueCounter.count(aggregate).cost
        if (cost <= 0.0) return 0
        val ratio = (aggregate.level / (cost * 100.0))
            .coerceAtLeast(0.0)
            .coerceAtMost(1.0)
        return ((1.0 - ratio) * Colorist.MAGIC_BOOK_MAX_DURABILITY).toInt()
    }

    override fun isBarVisible(stack: ItemStack): Boolean = true

    override fun getBarColor(stack: ItemStack): Int {
        // Use the aggregate color for the bar — a small visual flourish that ties
        // the displayed bar to the current book color.
        val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
        if (aggregate == Attr.EMPTY) return 0xFFFFFF
        val rgb = ColorSystem.hexToRgb(aggregate.color) ?: return 0xFFFFFF
        return ((rgb.first and 0xFF) shl 16) or ((rgb.second and 0xFF) shl 8) or (rgb.third and 0xFF)
    }

    // ------------------------------------------------------------------
    //  Right-click — initiate the cast
    // ------------------------------------------------------------------

    override fun use(
        level: Level,
        player: Player,
        usedHand: InteractionHand,
    ): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (stack.item !is MagicBookItem) {
            return InteractionResultHolder.pass(stack)
        }

        // Block re-entry while a cast is in progress.
        if (stack.get(ModDataComponents.MAGIC_BOOK_CASTING.get()) == true) {
            return InteractionResultHolder.pass(stack)
        }

        val attrs = stack.get(ModDataComponents.MAGIC_BOOK_ATTRS.get()) ?: emptyList()
        if (attrs.isEmpty()) {
            return InteractionResultHolder.pass(stack)
        }
        val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
        val value = ValueCounter.count(aggregate)

        // Level check: first paper must have enough level to pay the cost.
        val firstPaper = attrs.first()
        if (firstPaper.level < value.cost) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("colorist.magic_book.insufficient_level")
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true,
            )
            return InteractionResultHolder.pass(stack)
        }

        if (level.isClientSide) {
            // Client-side: surface a quick pass; the server drives the actual cast.
            return InteractionResultHolder.success(stack)
        }

        // Server side: enter casting state.
        stack.set(ModDataComponents.MAGIC_BOOK_CASTING.get(), true)

        val serverPlayer = player as? ServerPlayer ?: return InteractionResultHolder.success(stack)
        val r = aggregate.r / 10.0f
        val g = aggregate.g / 10.0f
        val b = aggregate.b / 10.0f
        PacketDistributor.sendToPlayer(serverPlayer, MagicStartPayload(r, g, b))

        // Play start sound: warden.death (DOCUMENTATION.md §10.2).
        level.playSound(
            null,
            serverPlayer.blockPosition(),
            SoundEvents.WARDEN_DEATH,
            SoundSource.PLAYERS,
            0.6f,
            (1.5f + level.random.nextFloat()).coerceIn(0.5f, 2.0f),
        )

        return InteractionResultHolder.success(stack)
    }

    // ------------------------------------------------------------------
    //  Per-tick processing — invoked by MagicBookServerEventHandler
    // ------------------------------------------------------------------

    /**
     * Called once per server tick while the book is in casting state. Performs the
     * raytrace and damage application.
     *
     * Returns true if the cast should continue, false if it should end this tick
     * (because duration elapsed or the book left the player's hand).
     */
    fun processCastTick(
        serverPlayer: ServerPlayer,
        stack: ItemStack,
        tickIndex: Int,
    ): Boolean {
        if (tickIndex >= Colorist.RAY_DURATION_TICKS) {
            endCast(serverPlayer, stack)
            return false
        }
        if (stack.get(ModDataComponents.MAGIC_BOOK_CASTING.get()) != true) {
            return false
        }

        val level = serverPlayer.level()
        val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
        if (aggregate == Attr.EMPTY) {
            endCast(serverPlayer, stack)
            return false
        }
        val value = ValueCounter.count(aggregate)

        // Raytrace from the player's eye along their look vector.
        val hit = rayTraceEntity(level, serverPlayer, Colorist.RAY_RANGE)
        if (hit is EntityHitResult && hit.entity is LivingEntity) {
            val target = hit.entity as LivingEntity
            var damage = value.atk.toFloat()
            val isCrit = level.random.nextDouble() < value.br
            if (isCrit) {
                damage = (damage * (1.0 + value.bd)).toFloat()
                PacketDistributor.sendToPlayer(serverPlayer, com.colorist.network.CritPayload(true))
                level.playSound(
                    null,
                    target.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS,
                    0.8f,
                    (1.4f + level.random.nextFloat() * 0.2f).coerceIn(0.5f, 2.0f),
                )
            } else {
                PacketDistributor.sendToPlayer(serverPlayer, com.colorist.network.CritPayload(false))
                level.playSound(
                    null,
                    target.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS,
                    0.8f,
                    (1.4f + level.random.nextFloat() * 0.2f).coerceIn(0.5f, 2.0f),
                )
            }
            target.hurt(level.damageSources().playerAttack(serverPlayer), damage)
        }

        // Per-tick looping sound: amethyst_block.hit, alternating tick to avoid spam.
        if (tickIndex % 2 == 0) {
            level.playSound(
                null,
                serverPlayer.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_HIT,
                SoundSource.PLAYERS,
                1.0f,
                (1.0f + level.random.nextFloat() * 0.2f).coerceIn(0.5f, 2.0f),
            )
        }
        return true
    }

    /**
     * Ends the cast: clears the casting flag, deducts the level cost from the first
     * paper, refreshes the aggregate, and notifies the client.
     */
    fun endCast(serverPlayer: ServerPlayer, stack: ItemStack) {
        if (stack.get(ModDataComponents.MAGIC_BOOK_CASTING.get()) != true) return
        stack.set(ModDataComponents.MAGIC_BOOK_CASTING.get(), false)

        val attrs = stack.get(ModDataComponents.MAGIC_BOOK_ATTRS.get())?.toMutableList() ?: mutableListOf()
        if (attrs.isNotEmpty()) {
            val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get()) ?: Attr.EMPTY
            val cost = ValueCounter.count(aggregate).cost
            val first = attrs.removeAt(0)
            val newFirst = first.addLevel(-cost)
            attrs.add(0, newFirst)
            stack.set(ModDataComponents.MAGIC_BOOK_ATTRS.get(), attrs.toList())
            stack.set(ModDataComponents.MAGIC_BOOK_AGGREGATE.get(), com.colorist.core.aggregateAttrs(attrs))
        }

        PacketDistributor.sendToPlayer(serverPlayer, MagicStopPayload())
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /**
     * Performs a block-then-entity raytrace from the player's eye position along
     * the look vector, returning the first entity hit (or the block hit if no
     * entity is found). Mirrors KubeJS's `rayTrace(10)` behavior.
     */
    private fun rayTraceEntity(
        level: Level,
        player: Player,
        range: Double,
    ): HitResult {
        val eye = player.eyePosition
        val look = player.lookAngle
        val end = eye.add(look.scale(range))

        // Block clip first — the ray stops at the first solid block.
        val blockClip = level.clip(
            ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        )
        val clippedEnd = if (blockClip.type == HitResult.Type.BLOCK) blockClip.location else end

        // Then entity clip — search the AABB around the ray segment.
        val aabb = AABB(eye, clippedEnd).inflate(1.0)
        var nearestEntity: LivingEntity? = null
        var nearestDist = Double.MAX_VALUE
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, aabb)) {
            if (entity == player) continue
            val entBox = entity.boundingBox.inflate(0.3)
            val intersection = entBox.clip(eye)
            if (intersection.isPresent) {
                val hitPos = intersection.get()
                val dist = eye.distanceToSqr(hitPos)
                if (dist < nearestDist && dist <= eye.distanceToSqr(clippedEnd)) {
                    nearestDist = dist
                    nearestEntity = entity
                }
            }
        }
        return nearestEntity?.let { EntityHitResult(it) } ?: blockClip
    }
}
