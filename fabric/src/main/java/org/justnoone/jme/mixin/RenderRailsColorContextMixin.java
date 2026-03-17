package org.justnoone.jme.mixin;

import net.minecraft.client.MinecraftClient;
import org.justnoone.jme.client.MagicRailRenderContext;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(RenderRails.class)
public class RenderRailsColorContextMixin {

    private static Field jme$hasColorField;

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void jme$pushHeldSpeedColorForPreview(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        if (!jme$hasColor(renderState) || !jme$isDraggingState(renderState)) {
            MagicRailRenderContext.clear();
            return;
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            MagicRailRenderContext.clear();
            return;
        }

        final ItemStack mainHand = new ItemStack(client.player.getMainHandStack());
        final ItemStack offHand = new ItemStack(client.player.getOffHandStack());
        final ItemStack connector = MagicRailConstants.isUniversalConnector(mainHand) ? mainHand : (MagicRailConstants.isUniversalConnector(offHand) ? offHand : ItemStack.getEmptyMapped());
        if (connector.isEmpty()) {
            MagicRailRenderContext.clear();
            return;
        }

        MagicRailRenderContext.pushOverrideSpeed(MagicRailConstants.getSpeedFromStack(connector));
    }

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false
    )
    private static void jme$clearHeldSpeedColorPreview(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        MagicRailRenderContext.clear();
    }

    private static boolean jme$hasColor(Object renderState) {
        if (renderState == null) {
            return false;
        }

        try {
            Field field = jme$hasColorField;
            if (field == null || field.getDeclaringClass() != renderState.getClass()) {
                field = renderState.getClass().getDeclaredField("hasColor");
                field.setAccessible(true);
                jme$hasColorField = field;
            }
            return field.getBoolean(renderState);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean jme$isDraggingState(Object renderState) {
        if (!(renderState instanceof Enum<?>)) {
            return false;
        }
        final Enum<?> enumState = (Enum<?>) renderState;
        return "FLASHING".equals(enumState.name());
    }
}
