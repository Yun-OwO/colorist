package com.colorist.registry

import com.colorist.Colorist
import com.colorist.recipe.WashMagicPaperRecipe
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.crafting.RecipeSerializer
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Deferred register for custom [RecipeSerializer]s.
 *
 * The core port has exactly one custom recipe: [WashMagicPaperRecipe], which
 * preserves a paper's `level` while resetting its color to `#FFFFFF`
 * (DOCUMENTATION.md §7.5). All other recipes are JSON data files in
 * `data/colorist/recipe/`.
 */
object ModRecipeSerializers {

    val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, Colorist.MOD_ID)

    val WASH_MAGIC_PAPER: DeferredHolder<RecipeSerializer<*>, WashMagicPaperRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("wash_magic_paper") { WashMagicPaperRecipe.Serializer() }
}
