package org.justnoone.jme.mixin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.PlatformStopPositionRegistry;
import org.mtr.core.data.Platform;
import org.mtr.core.data.SavedRailBase;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.PlatformScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = PlatformScreen.class, remap = false)
public abstract class PlatformScreenStopPositionMixin implements IGui {

    @Unique
    private ButtonWidgetExtension jme$stopPositionButton;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initStopPositionButton(CallbackInfo ci) {
        final Platform platform = jme$getPlatform();
        if (platform == null) {
            return;
        }

        if (jme$stopPositionButton == null) {
            jme$stopPositionButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, jme$getStopPositionText(platform), button -> jme$cycleStopPosition());
        }

        final int[] bounds = jme$getButtonBounds();
        IDrawing.setPositionAndWidth(jme$stopPositionButton, bounds[0], bounds[1], bounds[2]);
        jme$stopPositionButton.setMessage2(Text.cast(jme$getStopPositionText(platform)));
        jme$addChild(new ClickableWidget(jme$stopPositionButton));
    }

    @Unique
    private void jme$cycleStopPosition() {
        final Platform platform = jme$getPlatform();
        if (platform == null) {
            return;
        }

        final PlatformStopPositionRegistry.StopPosition stopPosition = PlatformStopPositionRegistry.cycle(platform.getId());
        if (jme$stopPositionButton != null) {
            jme$stopPositionButton.setMessage2(Text.cast(jme$getStopPositionText(platform)));
        }

        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeLong(platform.getId());
        packet.writeString(stopPosition.getSerializedId());
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_PLATFORM_STOP_POSITION_PACKET_ID, packet);
    }

    @Unique
    private org.mtr.mapping.holder.MutableText jme$getStopPositionText(Platform platform) {
        final PlatformStopPositionRegistry.StopPosition stopPosition = PlatformStopPositionRegistry.get(platform.getId());
        return TextHelper.literal("Stop Position: " + stopPosition.getDisplayName());
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 90), remap = false)
    private int jme$moveRoutesAtPlatformLabelDown(int original) {
        return original + SQUARE_SIZE;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 80), remap = false)
    private int jme$moveRoutesAtPlatformRowsDown(int original) {
        return original + SQUARE_SIZE;
    }

    @Unique
    private Platform jme$getPlatform() {
        final SavedRailBase<?, ?> savedRailBase = ((SavedRailScreenBaseAccessor) this).jme$getSavedRailBase();
        return savedRailBase instanceof Platform ? (Platform) savedRailBase : null;
    }

    @Unique
    private int[] jme$getButtonBounds() {
        final Object slider = jme$getFieldValue("sliderDwellTimeSec");
        if (slider != null) {
            try {
                final int x = (Integer) slider.getClass().getMethod("getX2").invoke(slider);
                final int y = (Integer) slider.getClass().getMethod("getY2").invoke(slider) + SQUARE_SIZE + 4;
                final int width = (Integer) slider.getClass().getMethod("getWidth2").invoke(slider);
                return new int[]{x, y, Math.max(SQUARE_SIZE * 4, width)};
            } catch (Exception ignored) {
            }
        }
        return new int[]{SQUARE_SIZE, SQUARE_SIZE * 8, SQUARE_SIZE * 6};
    }

    @Unique
    private Object jme$getFieldValue(String fieldName) {
        Class<?> targetClass = this.getClass();
        while (targetClass != null) {
            try {
                final java.lang.reflect.Field field = targetClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(this);
            } catch (Exception ignored) {
                targetClass = targetClass.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private void jme$addChild(ClickableWidget clickableWidget) {
        try {
            Method addChildMethod = null;
            Class<?> targetClass = this.getClass();
            while (targetClass != null && addChildMethod == null) {
                for (final Method method : targetClass.getDeclaredMethods()) {
                    if ("addChild".equals(method.getName()) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(clickableWidget.getClass())) {
                        addChildMethod = method;
                        break;
                    }
                }
                targetClass = targetClass.getSuperclass();
            }

            if (addChildMethod != null) {
                addChildMethod.setAccessible(true);
                addChildMethod.invoke(this, clickableWidget);
            }
        } catch (Exception ignored) {
        }
    }
}
