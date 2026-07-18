package com.colorist.registry

import com.colorist.Colorist
import com.colorist.block.MagicTableBlock
import com.colorist.blockentity.MagicTableBlockEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Deferred register for [BlockEntityType]s. Only the magic table needs a block
 * entity in the core port; the magic crystal ore is a plain block.
 */
object ModBlockEntities {

    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Colorist.MOD_ID)

    val MAGIC_TABLE: DeferredHolder<BlockEntityType<*>, BlockEntityType<MagicTableBlockEntity>> =
        BLOCK_ENTITIES.register("magic_table") {
            BlockEntityType.Builder.of(
                ::MagicTableBlockEntity,
                ModBlocks.MAGIC_TABLE.get(),
            ).build(null)
        }
}
