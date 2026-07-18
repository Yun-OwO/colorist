package com.colorist.recipe

import com.colorist.core.Attr
import com.colorist.core.ColorSystem
import com.colorist.registry.ModDataComponents
import com.mojang.serialization.MapCodec
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.level.Level

/**
 * "Wash magic paper" recipe — DOCUMENTATION.md §7.5:
 *
 *   magic_crystal + magic_paper (any color/level) + white_dye → magic_paper
 *   (level preserved, color reset to #FFFFFF)
 *
 * The original KubeJS code used `.modifyResult` to retain the input paper's `level`
 * while resetting the output's `attr.color`. NeoForge 1.21.1 has no equivalent
 * callback; we wrap a vanilla [ShapelessRecipe] (composition over inheritance)
 * and override only [assemble] to perform the same transformation.
 *
 * The corresponding JSON lives in `data/colorist/recipe/wash_magic_paper.json`
 * and references this serializer via `"type": "colorist:wash_magic_paper"`.
 */
class WashMagicPaperRecipe(
    /** The underlying vanilla shapeless recipe that defines matching & default result. */
    val delegate: ShapelessRecipe,
) : CraftingRecipe by delegate {

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        val output = delegate.assemble(input, registries).copy()
        // Find the input paper stack and copy its level into the output.
        var paperLevel = 0.0
        for (i in 0 until input.size()) {
            val stack = input.getItem(i)
            if (stack.itemHolder.key()?.location()?.path == "magic_paper") {
                val attr = stack.get(ModDataComponents.MAGIC_PAPER_ATTR.get()) ?: Attr.EMPTY
                paperLevel = attr.level
                break
            }
        }
        val freshAttr = Attr.fromColor(ColorSystem.DEFAULT_PAPER_COLOR, paperLevel)
        output.set(ModDataComponents.MAGIC_PAPER_ATTR.get(), freshAttr)
        return output
    }

    @Suppress("UNCHECKED_CAST")
    override fun getSerializer(): RecipeSerializer<WashMagicPaperRecipe> =
        com.colorist.registry.ModRecipeSerializers.WASH_MAGIC_PAPER.get() as RecipeSerializer<WashMagicPaperRecipe>

    /**
     * Custom serializer — wraps the vanilla [ShapelessRecipe.Serializer] codec and
     * network stream, then maps results into [WashMagicPaperRecipe] instances.
     */
    class Serializer : RecipeSerializer<WashMagicPaperRecipe> {

        private val vanilla: ShapelessRecipe.Serializer = ShapelessRecipe.Serializer()

        override fun codec(): MapCodec<WashMagicPaperRecipe> = vanilla.codec().xmap(
            { sr -> WashMagicPaperRecipe(sr) },
            { recipe -> recipe.delegate },
        )

        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, WashMagicPaperRecipe> {
            val inner = vanilla.streamCodec()
            return StreamCodec.of(
                { buf, recipe -> inner.encode(buf, recipe.delegate) },
                { buf -> WashMagicPaperRecipe(inner.decode(buf)) },
            )
        }
    }
}
