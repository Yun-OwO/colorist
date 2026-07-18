package com.colorist.client

import com.colorist.Colorist
import com.colorist.core.Attr
import com.colorist.core.ColorSystem
import com.colorist.core.TooltipFormatter
import com.colorist.core.ValueCounter
import com.colorist.core.aggregateAttrs
import com.colorist.item.MagicBookItem
import com.colorist.registry.ModDataComponents
import com.colorist.registry.ModItems
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

/**
 * Port of `client_scripts/tooltip.js` — renders the magic-paper and magic-book
 * tooltips described in DOCUMENTATION.md §10.3.
 *
 *  Magic paper tooltip (Shift toggles detail):
 *    等级: <level>           (colored with paper color)
 *    虹彩: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍
 *    阴阳: ▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍▍
 *    按住shift查看详情
 *    [Shift]:
 *      朱赤 / 碧青 / 苍蓝 / 阴 / 阳
 *      消耗 / 攻击 / 生命 / 暴击率 / 暴击伤害
 *
 *  Magic book tooltip (similar, plus 数量: N/12 line).
 */
object TooltipHandler {

    fun onTooltip(event: ItemTooltipEvent) {
        val stack: ItemStack = event.itemStack
        val key = stack.itemHolder.key()?.location()?.path ?: return

        when (key) {
            "magic_paper" -> renderPaperTooltip(stack, event)
            "magic_book" -> renderBookTooltip(stack, event)
            else -> return
        }
    }

    // ------------------------------------------------------------------

    private fun renderPaperTooltip(stack: ItemStack, event: ItemTooltipEvent) {
        val attr = stack.get(ModDataComponents.MAGIC_PAPER_ATTR.get()) ?: Attr.EMPTY
        val lines = event.toolTip
        val shift = net.minecraft.client.gui.screens.Screen.hasShiftDown()

        // 等级 line — colored with paper color
        lines += levelLine(attr)
        // 虹彩 line
        lines += Component.translatable("colorist.tooltip.rainbow")
            .append(": ").append(TooltipFormatter.rainbowBar(attr))
        // 阴阳 line
        lines += Component.translatable("colorist.tooltip.yin_yang")
            .append(": ").append(TooltipFormatter.yinYangBar(attr))

        if (!shift) {
            lines += Component.translatable("colorist.tooltip.hold_shift")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        } else {
            appendDetailedAttrs(lines, attr)
        }
    }

    private fun renderBookTooltip(stack: ItemStack, event: ItemTooltipEvent) {
        val attrs = stack.get(ModDataComponents.MAGIC_BOOK_ATTRS.get()) ?: emptyList()
        val aggregate = stack.get(ModDataComponents.MAGIC_BOOK_AGGREGATE.get())
            ?: aggregateAttrs(attrs)
        val lines = event.toolTip
        val shift = net.minecraft.client.gui.screens.Screen.hasShiftDown()

        lines += levelLine(aggregate)
        lines += Component.translatable("colorist.tooltip.rainbow")
            .append(": ").append(TooltipFormatter.rainbowBar(aggregate))
        lines += Component.translatable("colorist.tooltip.yin_yang")
            .append(": ").append(TooltipFormatter.yinYangBar(aggregate))
        lines += Component.translatable("colorist.tooltip.count", attrs.size, Colorist.MAX_ATTRS)
            .withStyle(ChatFormatting.GRAY)

        if (!shift) {
            lines += Component.translatable("colorist.tooltip.hold_shift")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        } else {
            appendDetailedAttrs(lines, aggregate)
            lines += Component.translatable("colorist.tooltip.count", attrs.size, Colorist.MAX_ATTRS)
                .withStyle(ChatFormatting.GRAY)
        }
    }

    /**
     * "等级: <level>" rendered with the attr's color (DOCUMENTATION.md §10.3).
     */
    private fun levelLine(attr: Attr): MutableComponent {
        val colorStyle: Style = colorStyleFromHex(attr.color)
        val levelStr = String.format("%.2f", attr.level)
        return Component.translatable("colorist.tooltip.level")
            .append(": ")
            .append(Component.literal(levelStr).withStyle(colorStyle))
    }

    /**
     * Appends the detailed attribute block shown when Shift is held:
     *   朱赤 / 碧青 / 苍蓝 / 阴 / 阳
     *   消耗 / 攻击 / 生命 / 暴击率 / 暴击伤害
     */
    private fun appendDetailedAttrs(lines: MutableList<Component>, attr: Attr) {
        val value = ValueCounter.count(attr)
        lines += Component.translatable("colorist.tooltip.r", attr.r).withStyle(ChatFormatting.RED)
        lines += Component.translatable("colorist.tooltip.g", attr.g).withStyle(ChatFormatting.GREEN)
        lines += Component.translatable("colorist.tooltip.b", attr.b).withStyle(ChatFormatting.BLUE)
        lines += Component.translatable("colorist.tooltip.yin", attr.darkness).withStyle(ChatFormatting.DARK_GRAY)
        lines += Component.translatable("colorist.tooltip.yang", attr.brightness).withStyle(ChatFormatting.WHITE)
        lines += Component.translatable("colorist.tooltip.cost", String.format("%.4f", value.cost)).withStyle(ChatFormatting.AQUA)
        lines += Component.translatable("colorist.tooltip.atk", String.format("+%.2f", value.atk)).withStyle(ChatFormatting.RED)
        lines += Component.translatable("colorist.tooltip.hp", String.format("+%.2f", value.hp)).withStyle(ChatFormatting.GREEN)
        lines += Component.translatable("colorist.tooltip.crit_rate", String.format("+%.4f", value.br)).withStyle(ChatFormatting.YELLOW)
        lines += Component.translatable("colorist.tooltip.crit_dmg", String.format("+%.4f", value.bd)).withStyle(ChatFormatting.LIGHT_PURPLE)
    }

    private fun colorStyleFromHex(hex: String): Style {
        val rgb = ColorSystem.hexToRgb(hex) ?: return Style.EMPTY
        val packed = ((rgb.first and 0xFF) shl 16) or ((rgb.second and 0xFF) shl 8) or (rgb.third and 0xFF)
        return Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(packed))
    }

    @Suppress("unused")
    private const val MOD_ID = Colorist.MOD_ID
}
