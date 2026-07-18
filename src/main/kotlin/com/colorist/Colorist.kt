package com.colorist

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.runForDist
import com.colorist.client.ClientSetup
import com.colorist.event.BlockBreakEventHandler
import com.colorist.event.InventoryEventHandler
import com.colorist.event.MagicBookServerEventHandler
import com.colorist.network.ModPayloads
import com.colorist.registry.ModBlockEntities
import com.colorist.registry.ModBlocks
import com.colorist.registry.ModCreativeTabs
import com.colorist.registry.ModDataComponents
import com.colorist.registry.ModItems
import com.colorist.registry.ModRecipeSerializers

/**
 * Colorist — a Kotlin/NeoForge 1.21.1 native port of the original KubeJS Colorist mod.
 *
 * Core design: every Minecraft dye color is quantified to RGB and further translated
 * into RPG-like attributes (r/g/b/yin/yang/level). The "color is power" concept is
 * preserved verbatim from the source mod. GUI components and gameplay extensions
 * described in the documentation are intentionally excluded from this port.
 */
@Mod(Colorist.MOD_ID)
object Colorist {
    const val MOD_ID = "colorist"

    /** Maximum number of magic papers a single magic book can hold. (lib.js: global.MAX_ATTRS) */
    const val MAX_ATTRS = 12

    /** Total length of attribute progress bars rendered in tooltips. (lib.js: global.PROG_LENGTH) */
    const val PROG_LENGTH = 18

    /** Range of the magic book's ray attack, in blocks. */
    const val RAY_RANGE = 10.0

    /** Duration of the magic book's ray attack, in ticks. */
    const val RAY_DURATION_TICKS = 10

    /** Maximum durability the magic book displays. (item.js: maxDamage(1000)) */
    const val MAGIC_BOOK_MAX_DURABILITY = 1000

    /** Level bonus applied per magic crystal injected into a magic paper. */
    const val CRYSTAL_LEVEL_BONUS = 5

    /**
     * Kotlin for Forge distinguishes between the mod event bus (registration-time events)
     * and the NeoForge event bus (gameplay events).
     */
    @Suppress("unused")
    constructor() {
        val modBus: IEventBus = MOD_BUS

        // Registry deferred registers — must run before any gameplay code.
        ModDataComponents.DATA_COMPONENTS.register(modBus)
        ModItems.ITEMS.register(modBus)
        ModBlocks.BLOCKS.register(modBus)
        ModBlockEntities.BLOCK_ENTITIES.register(modBus)
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus)
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus)
        // Network payloads are registered via @EventBusSubscriber on ModPayloads.

        // Client / dist-specific setup.
        runForDist(
            clientTarget = { ClientSetup.onClientConstruct() },
            serverTarget = { },
        )

        // Gameplay event handlers (NeoForge bus).
        // We register each handler explicitly via `addListener` rather than relying on
        // `@EventBusSubscriber`, so the wiring is visible in one place. (The `@SubscribeEvent`
        // annotations on the individual handler methods are decorative — they only fire when
        // their owning class is itself an `@EventBusSubscriber`, which these are not.)
        val neoBus = NeoForge.EVENT_BUS
        neoBus.addListener(MagicBookServerEventHandler::onRightClick)
        neoBus.addListener(MagicBookServerEventHandler::onServerTick)
        neoBus.addListener(BlockBreakEventHandler::onBlockBroken)
        neoBus.addListener(InventoryEventHandler::onServerTick)
        neoBus.addListener(InventoryEventHandler::onPlayerRespawn)
        neoBus.addListener(InventoryEventHandler::onPlayerLogout)

        com.colorist.core.ColorLog.info("Colorist core systems initialized.")
    }
}
