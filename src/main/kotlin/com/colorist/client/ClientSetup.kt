package com.colorist.client

import com.colorist.Colorist
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Central registration of all client-side listeners.
 *
 * The port excludes GUI screens (per the user's instructions), but the client
 * still needs to:
 *  - Tick the magic-book particle VFX ([MagicBookClientHandler.tick])
 *  - Render custom item tooltips ([TooltipHandler])
 */
@EventBusSubscriber(modid = Colorist.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object ClientSetup {

    /**
     * Hook called from [Colorist] main constructor via `runForDist` to perform any
     * eager client-side init. We don't register anything here; all client listeners
     * are wired up via the [EventBusSubscriber] annotations on this object and
     * [TooltipHandler].
     */
    @JvmStatic
    fun onClientConstruct() {
        // No-op — kept as a hook for future client setup.
    }

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        // Reserved for future client setup that must run after registry freeze.
    }

    /**
     * Drives per-client-tick work — currently just particle emission for active casts.
     */
    @EventBusSubscriber(modid = Colorist.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
    object GameBus {
        @SubscribeEvent
        fun onClientTick(event: ClientTickEvent.Post) {
            MagicBookClientHandler.tick()
        }

        @SubscribeEvent
        fun onItemTooltip(event: ItemTooltipEvent) {
            TooltipHandler.onTooltip(event)
        }
    }
}
