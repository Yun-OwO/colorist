package com.colorist.registry

import com.colorist.Colorist
import com.colorist.core.Attr
import com.colorist.core.ColorSystem
import com.colorist.item.MagicBookItem
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Port of `startup_scripts/item.js` — registers every Colorist item via
 * NeoForge's [DeferredRegister].
 *
 * Item list (DOCUMENTATION.md §3.1):
 *  - rainbow_dye      (basic, stack 64)
 *  - grayscale_dye    (basic, stack 64)
 *  - bleak_dye        (basic, stack 64)
 *  - soil_dye         (basic, stack 64)
 *  - magic_book       (maxDamage 1000, stack 1)
 *  - magic_paper      (stack 1)
 *  - magic_crystal    (stack 64)
 *
 * The original `global.ITEM(e, id, maxStack, type)` helper pushed each id into
 * `global.ALL_ITEMS`; we mirror that here via [ALL_ITEMS].
 */
object ModItems {

    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Colorist.MOD_ID)

    // --- Dyes -----------------------------------------------------------

    val RAINBOW_DYE: DeferredHolder<Item, Item> =
        ITEMS.register("rainbow_dye") { basicItem(64) }
    val GRAYSCALE_DYE: DeferredHolder<Item, Item> =
        ITEMS.register("grayscale_dye") { basicItem(64) }
    val BLEAK_DYE: DeferredHolder<Item, Item> =
        ITEMS.register("bleak_dye") { basicItem(64) }
    val SOIL_DYE: DeferredHolder<Item, Item> =
        ITEMS.register("soil_dye") { basicItem(64) }

    // --- Materials ------------------------------------------------------

    val MAGIC_CRYSTAL: DeferredHolder<Item, Item> =
        ITEMS.register("magic_crystal") { basicItem(64) }

    // --- Magic paper ----------------------------------------------------

    /**
     * Magic paper carries a single [Attr] component, persisted via
     * [ModDataComponents.MAGIC_PAPER_ATTR].
     *
     * Stack size 1 — each paper is a unique colored artifact.
     */
    val MAGIC_PAPER: DeferredHolder<Item, Item> =
        ITEMS.register("magic_paper") {
            basicItem(1)
        }

    // --- Magic book -----------------------------------------------------

    /**
     * The magic book is the central weapon. It uses [MagicBookItem] for the dynamic
     * durability bar and right-click cast action, and stacks only to 1.
     */
    val MAGIC_BOOK: DeferredHolder<Item, MagicBookItem> =
        ITEMS.register("magic_book") {
            MagicBookItem(
                Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .component(
                        ModDataComponents.MAGIC_BOOK_ATTRS.get(),
                        emptyList<Attr>(),
                    )
                    .component(
                        ModDataComponents.MAGIC_BOOK_AGGREGATE.get(),
                        Attr.EMPTY,
                    )
                    .component(
                        ModDataComponents.MAGIC_BOOK_HP_BONUS.get(),
                        false,
                    )
                    .component(
                        ModDataComponents.MAGIC_BOOK_CASTING.get(),
                        false,
                    )
            )
        }

    // ------------------------------------------------------------------

    /**
     * Vanilla-style basic item with a specified max stack size. Matches
     * `e.create(id, "basic").maxStackSize(N)` from the original mod.
     */
    private fun basicItem(maxStack: Int): Item =
        Item(Item.Properties().stacksTo(maxStack))

    /**
     * Mirrors `global.ALL_ITEMS` from `lib.js`. Populated lazily on first access.
     */
    val ALL_ITEMS: List<String> = listOf(
        "rainbow_dye",
        "grayscale_dye",
        "bleak_dye",
        "soil_dye",
        "magic_book",
        "magic_paper",
        "magic_crystal",
    )

    /**
     * Convenience: returns true if [stack] is one of the four special colorist dyes
     * (rainbow / grayscale / bleak / soil).
     */
    fun isSpecialDye(stack: net.minecraft.world.item.ItemStack): Boolean {
        val holder = stack.itemHolder
        val key = holder.key() ?: return false
        return key.location().namespace == Colorist.MOD_ID && key.location().path in SPECIAL_DYE_IDS
    }

    private val SPECIAL_DYE_IDS: Set<String> = setOf(
        "rainbow_dye", "grayscale_dye", "bleak_dye", "soil_dye",
    )

    /**
     * Returns the hex color of a Colorist special dye, if [stack] is one.
     * Mirrors the lookup performed in `global.MAGIC_TABLE` for special dyes.
     */
    fun specialDyeColor(stack: net.minecraft.world.item.ItemStack): String? {
        val key = stack.itemHolder.key()?.location()?.path ?: return null
        return when (key) {
            "rainbow_dye"   -> "#FF00FF" // arbitrary vivid representation; rainbow has no single hex
            "grayscale_dye" -> "#7F7F7F"
            "bleak_dye"     -> "#8B7E6B"
            "soil_dye"      -> ColorSystem.DYE_COLORS["soil_dye"]
            else -> null
        }
    }
}
