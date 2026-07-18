package com.colorist.core

import com.colorist.Colorist
import com.mojang.serialization.Codec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Holds the codec/stream-codec pairs used by [com.colorist.registry.ModDataComponents]
 * to register the actual [net.minecraft.core.component.DataComponentType] instances.
 *
 * We keep this separate from the registry so the codec declarations can be referenced
 * by other parts of the codebase (e.g. block-entity serialization) without depending on
 * the registry bootstrap order.
 *
 * Original NBT shape (DOCUMENTATION.md §3.3 / §6.3 / §13):
 *   magic_paper: { attr: {...} }
 *   magic_book : { attrs: [...], attr: {...}, hasHpBonus: bool, usingMagic: bool }
 */
object ColoristDataComponents {

    /**
     * Strongly-typed component specifications. The companion object below lists one
     * instance per component.
     */
    data class Spec<T>(
        val name: String,
        val codec: Codec<T>,
        val streamCodec: StreamCodec<in RegistryFriendlyByteBuf, T>,
    )

    val MAGIC_PAPER_ATTR = Spec(
        name = "magic_paper_attr",
        codec = Attr.CODEC,
        streamCodec = Attr.STREAM_CODEC,
    )

    val MAGIC_BOOK_ATTRS = Spec<List<Attr>>(
        name = "magic_book_attrs",
        codec = Codec.list(Attr.CODEC),
        streamCodec = Attr.STREAM_CODEC.apply(ByteBufCodecs.list()),
    )

    val MAGIC_BOOK_AGGREGATE = Spec(
        name = "magic_book_aggregate",
        codec = Attr.CODEC,
        streamCodec = Attr.STREAM_CODEC,
    )

    val MAGIC_BOOK_HP_BONUS = Spec(
        name = "magic_book_hp_bonus",
        codec = Codec.BOOL,
        streamCodec = ByteBufCodecs.BOOL,
    )

    val MAGIC_BOOK_CASTING = Spec(
        name = "magic_book_casting",
        codec = Codec.BOOL,
        streamCodec = ByteBufCodecs.BOOL,
    )

    val all: List<Spec<*>> = listOf(
        MAGIC_PAPER_ATTR,
        MAGIC_BOOK_ATTRS,
        MAGIC_BOOK_AGGREGATE,
        MAGIC_BOOK_HP_BONUS,
        MAGIC_BOOK_CASTING,
    )

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID
}
