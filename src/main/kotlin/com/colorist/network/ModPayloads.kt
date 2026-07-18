package com.colorist.network

import com.colorist.Colorist
import com.colorist.client.MagicBookClientHandler
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Port of KubeJS's `player.sendData(...)` calls (DOCUMENTATION.md §13.1):
 *
 * | Original channel              | Payload class           | Direction  |
 * |--------------------------------|-------------------------|------------|
 * | `colorist:magic_start` {r,g,b}| [MagicStartPayload]     | S → C      |
 * | `colorist:magic_stop`   {}    | [MagicStopPayload]      | S → C      |
 * | `colorist:crit`         {crit}| [CritPayload]           | S → C      |
 *
 * Each payload uses NeoForge 1.21.1's [CustomPacketPayload] / [StreamCodec]
 * mechanism, bound via a [PayloadRegistrar] in [onRegisterPayloads].
 *
 * Note: payloads do NOT require a `DeferredRegister` — they are registered purely
 * through [RegisterPayloadHandlersEvent]. The [Colorist] main class therefore only
 * needs to register this class as a mod-bus subscriber (handled by the
 * [EventBusSubscriber] annotation below).
 */
@EventBusSubscriber(modid = Colorist.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object ModPayloads {

    @SubscribeEvent
    fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = PayloadRegistrar(Colorist.MOD_ID)
            .versioned("1.0.0")
            .optional()

        registrar.playToClient(
            MagicStartPayload.TYPE,
            MagicStartPayload.STREAM_CODEC,
            { payload, context -> MagicBookClientHandler.handleMagicStart(payload, context) },
        )

        registrar.playToClient(
            MagicStopPayload.TYPE,
            MagicStopPayload.STREAM_CODEC,
            { _, context -> MagicBookClientHandler.handleMagicStop(context) },
        )

        registrar.playToClient(
            CritPayload.TYPE,
            CritPayload.STREAM_CODEC,
            { payload, context -> MagicBookClientHandler.handleCrit(payload, context) },
        )
    }
}

// ----------------------------------------------------------------------
//  Payloads
// ----------------------------------------------------------------------

/**
 * Server → Client: start the casting VFX. [r]/[g]/[b] are normalized 0..1
 * values representing the book's aggregate color.
 */
data class MagicStartPayload(
    val r: Float,
    val g: Float,
    val b: Float,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MagicStartPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Colorist.MOD_ID, "magic_start"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MagicStartPayload> =
            StreamCodec.composite(
                ByteBufCodecs.FLOAT, MagicStartPayload::r,
                ByteBufCodecs.FLOAT, MagicStartPayload::g,
                ByteBufCodecs.FLOAT, MagicStartPayload::b,
                ::MagicStartPayload,
            )
    }
}

/** Server → Client: stop the casting VFX. */
class MagicStopPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MagicStopPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Colorist.MOD_ID, "magic_stop"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MagicStopPayload> =
            StreamCodec.unit(MagicStopPayload())

        @JvmStatic
        fun instance(): MagicStopPayload = MagicStopPayload()
    }
}

/**
 * Server → Client: notify crit (or non-crit) so the client can trigger the
 * corresponding sound/flash.
 */
data class CritPayload(
    val crit: Boolean,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<CritPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(Colorist.MOD_ID, "crit"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CritPayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, CritPayload::crit,
                ::CritPayload,
            )
    }
}
