package com.colorist.registry

import com.colorist.Colorist
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * A single creative tab aggregating every Colorist item, with the magic book as
 * the icon. DOCUMENTATION.md §12.1 notes that the original KubeJS config used
 * `minecraft:purple_dye` as the tab icon; here we use the magic book to better
 * reflect the mod's identity (and to avoid overriding KubeJS's tab).
 */
object ModCreativeTabs {

    val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Colorist.MOD_ID)

    val COLORIST_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> =
        CREATIVE_MODE_TABS.register("colorist_tab") {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.colorist"))
                .icon { ItemStack(ModItems.MAGIC_BOOK.get()) }
                .displayItems { params, output ->
                    output.accept(ModItems.RAINBOW_DYE.get())
                    output.accept(ModItems.GRAYSCALE_DYE.get())
                    output.accept(ModItems.BLEAK_DYE.get())
                    output.accept(ModItems.SOIL_DYE.get())
                    output.accept(ModItems.MAGIC_CRYSTAL.get())
                    output.accept(ModItems.MAGIC_PAPER.get())
                    output.accept(ModItems.MAGIC_BOOK.get())
                    output.accept(ModBlocks.MAGIC_TABLE_ITEM.get())
                    output.accept(ModBlocks.MAGIC_CRYSTAL_ORE_ITEM.get())
                }
                .build()
        }
}
