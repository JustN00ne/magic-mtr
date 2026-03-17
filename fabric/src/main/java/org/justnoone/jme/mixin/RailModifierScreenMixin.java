package org.justnoone.jme.mixin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.RailModifierScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

@Mixin(value = RailModifierScreen.class, remap = false)
public abstract class RailModifierScreenMixin implements IGui {

    @Shadow
    @Final
    private Rail rail;

    @Shadow
    @Final
    private int buttonsWidth;

    @Shadow
    @Final
    private int xStart;

    @Unique
    private ButtonWidgetExtension jme$tiltToggleButton;
    @Unique
    private ClickableWidget jme$tiltToggleButtonWidget;
    @Unique
    private SliderWidgetExtension jme$tiltStartSlider;
    @Unique
    private SliderWidgetExtension jme$tiltMiddleSlider;
    @Unique
    private SliderWidgetExtension jme$tiltEndSlider;
    @Unique
    private ClickableWidget jme$tiltStartSliderWidget;
    @Unique
    private ClickableWidget jme$tiltMiddleSliderWidget;
    @Unique
    private ClickableWidget jme$tiltEndSliderWidget;
    @Unique
    private int jme$tiltStartDegrees;
    @Unique
    private int jme$tiltMiddleDegrees;
    @Unique
    private int jme$tiltEndDegrees;
    @Unique
    private boolean jme$suppressSync;
    @Unique
    private boolean jme$tiltExpanded;
    @Unique
    private int jme$tiltSliderBaseY;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$initTiltState(String railId, CallbackInfo ci) {
        jme$tiltExpanded = false;
        if (rail == null) {
            jme$tiltStartDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
            jme$tiltMiddleDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
            jme$tiltEndDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
            return;
        }

        final MagicRailTiltRegistry.TiltSettings settings = MagicRailTiltRegistry.getTiltAbsolute(rail.getHexId());
        if (settings == null) {
            jme$tiltStartDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
            jme$tiltMiddleDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
            jme$tiltEndDegrees = MagicRailConstants.DEFAULT_TILT_DEGREES;
        } else {
            jme$tiltStartDegrees = settings.startDegrees;
            jme$tiltMiddleDegrees = settings.middleDegrees;
            jme$tiltEndDegrees = settings.endDegrees;
        }
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$addTiltControls(CallbackInfo ci) {
        final int sectionY = SQUARE_SIZE * 2 + 4;
        jme$tiltSliderBaseY = sectionY + SQUARE_SIZE + 4;

        jme$tiltToggleButton = new ButtonWidgetExtension(
                xStart,
                sectionY,
                buttonsWidth,
                SQUARE_SIZE,
                TextHelper.literal("Tilt \u25b6"),
                button -> {
                    jme$tiltExpanded = !jme$tiltExpanded;
                    jme$refreshTiltSectionVisibility();
                }
        );

        jme$tiltStartSlider = jme$createTiltSlider(
                xStart,
                jme$tiltSliderBaseY,
                buttonsWidth,
                () -> jme$tiltStartDegrees,
                value -> jme$tiltStartDegrees = value
        );
        jme$tiltMiddleSlider = jme$createTiltSlider(
                xStart,
                jme$tiltSliderBaseY + SQUARE_SIZE + 2,
                buttonsWidth,
                () -> jme$tiltMiddleDegrees,
                value -> jme$tiltMiddleDegrees = value
        );
        jme$tiltEndSlider = jme$createTiltSlider(
                xStart,
                jme$tiltSliderBaseY + (SQUARE_SIZE + 2) * 2,
                buttonsWidth,
                () -> jme$tiltEndDegrees,
                value -> jme$tiltEndDegrees = value
        );

        jme$tiltToggleButtonWidget = new ClickableWidget(jme$tiltToggleButton);
        jme$tiltStartSliderWidget = new ClickableWidget(jme$tiltStartSlider);
        jme$tiltMiddleSliderWidget = new ClickableWidget(jme$tiltMiddleSlider);
        jme$tiltEndSliderWidget = new ClickableWidget(jme$tiltEndSlider);

        jme$addChild(jme$tiltToggleButtonWidget);
        jme$addChild(jme$tiltStartSliderWidget);
        jme$addChild(jme$tiltMiddleSliderWidget);
        jme$addChild(jme$tiltEndSliderWidget);

        jme$suppressSync = true;
        jme$tiltStartSlider.setValueMapped(jme$toTiltSliderValue(jme$tiltStartDegrees));
        jme$tiltMiddleSlider.setValueMapped(jme$toTiltSliderValue(jme$tiltMiddleDegrees));
        jme$tiltEndSlider.setValueMapped(jme$toTiltSliderValue(jme$tiltEndDegrees));
        jme$suppressSync = false;

        jme$refreshTiltSectionVisibility();
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderTiltLabels(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!jme$tiltExpanded || jme$tiltStartSlider == null || jme$tiltMiddleSlider == null || jme$tiltEndSlider == null) {
            return;
        }

        final int textX = xStart + TEXT_PADDING;
        graphicsHolder.drawText(TextHelper.literal("Tilt Start"), textX, jme$tiltSliderBaseY - 10, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt Middle"), textX, jme$tiltSliderBaseY + SQUARE_SIZE - 8, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt End"), textX, jme$tiltSliderBaseY + SQUARE_SIZE * 2 - 6, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
    }

    @Unique
    private void jme$refreshTiltSectionVisibility() {
        jme$updateToggleLabel();
        jme$setControlVisible(jme$tiltStartSliderWidget, jme$tiltExpanded);
        jme$setControlVisible(jme$tiltMiddleSliderWidget, jme$tiltExpanded);
        jme$setControlVisible(jme$tiltEndSliderWidget, jme$tiltExpanded);
    }

    @Unique
    private void jme$updateToggleLabel() {
        if (jme$tiltToggleButton == null) {
            return;
        }
        jme$tiltToggleButton.setMessage2(Text.cast(TextHelper.literal(jme$tiltExpanded ? "Tilt \u25bc" : "Tilt \u25b6")));
    }

    @Unique
    private static void jme$setControlVisible(ClickableWidget widget, boolean visible) {
        if (widget == null) {
            return;
        }
        widget.setVisibleMapped(visible);
        widget.setActiveMapped(visible);
    }

    @Unique
    private SliderWidgetExtension jme$createTiltSlider(int x, int y, int width, IntSupplier getter, IntConsumer setter) {
        return new SliderWidgetExtension(x, y, width, SQUARE_SIZE, "") {
            @Override
            protected void updateMessage2() {
                this.setMessage2(Text.cast(TextHelper.literal(getter.getAsInt() + " deg")));
            }

            @Override
            protected void applyValue2() {
                setter.accept(jme$fromTiltSliderValue(this.getValueMapped()));
                updateMessage2();
                jme$syncTilt();
            }
        };
    }

    @Unique
    private void jme$syncTilt() {
        if (jme$suppressSync || rail == null) {
            return;
        }

        final String canonicalRailId = MagicRailTiltRegistry.normalizeRailId(rail.getHexId());
        MagicRailTiltRegistry.setTiltAbsolute(canonicalRailId, jme$tiltStartDegrees, jme$tiltMiddleDegrees, jme$tiltEndDegrees);

        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(canonicalRailId);
        buf.writeVarInt(jme$tiltStartDegrees);
        buf.writeVarInt(jme$tiltMiddleDegrees);
        buf.writeVarInt(jme$tiltEndDegrees);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_RAIL_TILT_PACKET_ID, buf);
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
            // Keep the base screen functional when widget reflection fails.
        }
    }

    @Unique
    private static int jme$fromTiltSliderValue(double sliderValue) {
        final int range = MagicRailConstants.MAX_TILT_DEGREES - MagicRailConstants.MIN_TILT_DEGREES;
        final int raw = MagicRailConstants.MIN_TILT_DEGREES + (int) Math.round(sliderValue * range);
        return MagicRailConstants.clampTiltDegrees(raw);
    }

    @Unique
    private static double jme$toTiltSliderValue(int tiltDegrees) {
        final int clamped = MagicRailConstants.clampTiltDegrees(tiltDegrees);
        return (clamped - MagicRailConstants.MIN_TILT_DEGREES) / (double) (MagicRailConstants.MAX_TILT_DEGREES - MagicRailConstants.MIN_TILT_DEGREES);
    }
}
