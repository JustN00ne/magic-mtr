package org.justnoone.jme.mixin;

import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.TextFormatting;
import org.mtr.mapping.holder.TooltipContext;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ItemRailModifier.class)
public class ItemRailModifierTooltipMixin {

    @Inject(method = "addTooltips", at = @At("TAIL"), remap = false)
    private void jme$appendMagicSpeedTooltip(ItemStack stack, World world, List<MutableText> tooltip, TooltipContext options, CallbackInfo ci) {
        if (!MagicRailConstants.isUniversalConnector(stack)) {
            return;
        }

        final int speedKmh = MagicRailConstants.getSpeedFromStack(stack);
        final String style = MagicRailConstants.getStyleLabel(MagicRailConstants.getStyleFromStack(stack));
        final String easing = MagicRailConstants.getShapeLabel(MagicRailConstants.getShapeFromStack(stack));
        final int tiltStart = MagicRailConstants.getStartTiltFromStack(stack);
        final int tiltMiddle = MagicRailConstants.getMiddleTiltFromStack(stack);
        final int tiltEnd = MagicRailConstants.getEndTiltFromStack(stack);

        String speedLabel;
        try {
            speedLabel = JmeConfig.formatSpeedLabel(speedKmh);
        } catch (Throwable ignored) {
            // If config class can't be loaded for any reason, don't crash the client while building search tooltips.
            speedLabel = speedKmh + " km/h";
        }

        tooltip.add(TextHelper.literal("MAGIC speed: " + speedLabel).formatted(TextFormatting.GRAY));
        tooltip.add(TextHelper.literal("MAGIC style: " + style).formatted(TextFormatting.GRAY));
        tooltip.add(TextHelper.literal("MAGIC easing: " + easing).formatted(TextFormatting.GRAY));
        tooltip.add(TextHelper.literal("MAGIC tilt: " + tiltStart + "/" + tiltMiddle + "/" + tiltEnd + " deg").formatted(TextFormatting.GRAY));
    }
}
