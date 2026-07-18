package com.colorist.registry

import com.colorist.Colorist
import com.colorist.core.Attr
import com.colorist.core.ColoristDataComponents.Spec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Deferred register for Colorist's custom [DataComponentType]s.
 *
 * NeoForge 1.21.1 ships [DeferredRegister.registerComponentType], which wraps the
 * usual registration call with a builder lambda that wires up the codec and stream
 * codec at the same time — exactly what we need.
 *
 * Each public field here is a `DeferredHolder` resolving to the corresponding
 * `DataComponentType<T>`. Call `MAGIC_PAPER_ATTR.get()` to fetch the live type at
 * runtime.
 */
object ModDataComponents {

    val DATA_COMPONENTS: DeferredRegister<DataComponentType<*>> =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Colorist.MOD_ID)

    val MAGIC_PAPER_ATTR: DeferredHolder<DataComponentType<*>, DataComponentType<Attr>> =
        register(com.colorist.core.ColoristDataComponents.MAGIC_PAPER_ATTR)

    val MAGIC_BOOK_ATTRS: DeferredHolder<DataComponentType<*>, DataComponentType<List<Attr>>> =
        register(com.colorist.core.ColoristDataComponents.MAGIC_BOOK_ATTRS)

    val MAGIC_BOOK_AGGREGATE: DeferredHolder<DataComponentType<*>, DataComponentType<Attr>> =
        register(com.colorist.core.ColoristDataComponents.MAGIC_BOOK_AGGREGATE)

    val MAGIC_BOOK_HP_BONUS: DeferredHolder<DataComponentType<*>, DataComponentType<Boolean>> =
        register(com.colorist.core.ColoristDataComponents.MAGIC_BOOK_HP_BONUS)

    val MAGIC_BOOK_CASTING: DeferredHolder<DataComponentType<*>, DataComponentType<Boolean>> =
        register(com.colorist.core.ColoristDataComponents.MAGIC_BOOK_CASTING)

    private fun <T> register(spec: Spec<T>): DeferredHolder<DataComponentType<*>, DataComponentType<T>> =
        DATA_COMPONENTS.registerComponentType(spec.name) { builder ->
            builder.persistent(spec.codec).networkSynchronized(spec.streamCodec)
        }
}
