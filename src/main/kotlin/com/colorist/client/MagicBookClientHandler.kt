package com.colorist.client

import com.colorist.Colorist
import com.colorist.network.CritPayload
import com.colorist.network.MagicStartPayload
import com.colorist.network.MagicStopPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Client-side receiver for Colorist's network payloads (DOCUMENTATION.md §10, §13.1).
 *
 * Responsibilities:
 *  - On [MagicStartPayload] — begin emitting the multi-particle ray trail along
 *    the player's look vector. Particles advance per-tick following the formula
 *    `progress += Math.random() / 50 + 0.05` (DOCUMENTATION.md §10.1).
 *  - On [MagicStopPayload] — halt particle emission.
 *  - On [CritPayload] — play the lightning-bolt impact sound if [CritPayload.crit]
 *    is true (DOCUMENTATION.md §10.2).
 */
object MagicBookClientHandler {

    /** Per-player cast state. Keyed by player UUID. */
    private data class CastFx(
        val playerId: UUID,
        val r: Float,
        val g: Float,
        val b: Float,
        var progress: Double,
    )

    private val activeFx: MutableMap<UUID, CastFx> = ConcurrentHashMap()

    // ------------------------------------------------------------------
    //  Payload handlers — invoked from ModPayloads registrar
    // ------------------------------------------------------------------

    fun handleMagicStart(payload: MagicStartPayload, context: IPayloadContext) {
        val player = safePlayer(context) ?: return
        activeFx[player.uuid] = CastFx(player.uuid, payload.r, payload.g, payload.b, 0.0)
    }

    fun handleMagicStop(context: IPayloadContext) {
        val player = safePlayer(context) ?: return
        activeFx.remove(player.uuid)
    }

    fun handleCrit(payload: CritPayload, context: IPayloadContext) {
        val player = safePlayer(context) ?: return
        val level = player.level() as? ClientLevel ?: return
        if (payload.crit) {
            level.playLocalSound(
                player.x, player.y, player.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS,
                0.8f,
                (1.4f + Random.nextFloat() * 0.2f).coerceIn(0.5f, 2.0f),
                false,
            )
        }
    }

    // ------------------------------------------------------------------
    //  Per-tick particle emission — wired up by ClientSetup
    // ------------------------------------------------------------------

    /**
     * Called every client tick to advance particle emission for any active cast.
     * Emits the documented particle types (DOCUMENTATION.md §10.1):
     *  - dripping_obsidian_tear, electric_spark, enchant — every tick
     *  - sonic_boom, dripping_dripstone_lava, dripping_dripstone_water — alternating
     */
    fun tick() {
        if (activeFx.isEmpty()) return
        val mc = Minecraft.getInstance()
        val level = mc.level as? ClientLevel ?: return
        val alternating = (level.gameTime / 2) % 2 == 0L

        val toRemove = mutableListOf<UUID>()
        for ((uuid, fx) in activeFx) {
            val player: Player? = level.getPlayerByUUID(uuid)
            if (player == null) {
                toRemove.add(uuid); continue
            }
            val eye = player.eyePosition
            val look = player.lookAngle

            // Advance progress 0.05-0.07 per tick, exactly as documented.
            fx.progress += Random.nextDouble() / 50.0 + 0.05
            if (fx.progress > 1.0) fx.progress = 0.0 // loop until stop payload arrives

            val dist = fx.progress * Colorist.RAY_RANGE
            val x = eye.x + look.x * dist
            val y = eye.y + look.y * dist
            val z = eye.z + look.z * dist

            // Always-on particles.
            level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y, z, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0, 0.0, 0.0)

            // Alternating particles.
            if (alternating) {
                level.addParticle(ParticleTypes.SONIC_BOOM, x, y, z, 0.0, 0.0, 0.0)
                level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, x, y, z, 0.0, 0.0, 0.0)
            } else {
                level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_WATER, x, y, z, 0.0, 0.0, 0.0)
            }
        }
        toRemove.forEach { activeFx.remove(it) }
    }

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID

    /**
     * Returns the player from [context] or null if no player is available
     * (e.g. during a non-player-bound payload). Wraps the NeoForge API which
     * throws when no player context is set.
     */
    private fun safePlayer(context: IPayloadContext): Player? = try {
        context.player()
    } catch (e: IllegalStateException) {
        null
    }
}
