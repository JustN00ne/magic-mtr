package org.justnoone.jme.mixin;

import org.justnoone.jme.client.data.SidingSpeedSliderFileStore;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.core.data.Siding;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.tool.Utilities;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.CheckboxWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.SavedRailScreenBase;
import org.mtr.mod.screen.SidingScreen;
import org.mtr.mod.screen.WidgetShorterSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntFunction;

@Mixin(value = SidingScreen.class, remap = false)
public abstract class SidingScreenMixin {

    /**
     * When the UI slider shows "No limit" (at 300 km/h), we actually store a high cap in the siding
     * rather than the vanilla 300 km/h. This keeps compatibility with MTR internals while making
     * "No limit" effectively unrestricted for normal gameplay.
     */
    private static final int JME_NO_LIMIT_CAP_KMH = 1000;
    private static final int JME_MAX_MANUAL_SPEED_SLIDER_KMH = 300;
    private static final int JME_DEFAULT_MANUAL_SPEED_SLIDER_KMH = 300;

    @Shadow
    private WidgetShorterSlider sliderDecelerationConstant;
    @Shadow
    private WidgetShorterSlider sliderDelayedVehicleSpeedIncreasePercentage;
    @Shadow
    private WidgetShorterSlider sliderDelayedVehicleReduceDwellTimePercentage;
    @Shadow
    private CheckboxWidgetExtension buttonEarlyVehicleIncreaseDwellTime;
    @Shadow
    private CheckboxWidgetExtension buttonIsManual;
    @Shadow
    private WidgetShorterSlider sliderMaxManualSpeed;
    @Inject(method = "init2", at = @At("TAIL"))
    private void jme$moveMaxSpeedSlider(CallbackInfo ci) {
        final SavedRailScreenBase<?, ?> base = (SavedRailScreenBase<?, ?>) (Object) this;
        final Object savedRailBase = ((SavedRailScreenBaseAccessor) base).jme$getSavedRailBase();
        if (savedRailBase instanceof Siding) {
            final Siding siding = (Siding) savedRailBase;
            final String sidingKey = Long.toString(siding.getId());
            final double maxManualSpeed = siding.getMaxManualSpeed();
            // MTR stores speed in meters per millisecond; km/h = m/ms * 3600.
            final int kmhFromSiding = maxManualSpeed > 0
                    ? Math.max(1, (int) Math.round(maxManualSpeed * 3600D))
                    : JME_DEFAULT_MANUAL_SPEED_SLIDER_KMH;
            final int sliderFromSiding = kmhFromSiding >= JME_NO_LIMIT_CAP_KMH ? JME_MAX_MANUAL_SPEED_SLIDER_KMH : Math.min(kmhFromSiding, JME_MAX_MANUAL_SPEED_SLIDER_KMH);
            final Integer cachedKmh = SidingSpeedSliderFileStore.get(sidingKey);
            sliderMaxManualSpeed.setValue(cachedKmh == null ? sliderFromSiding : Math.max(1, Math.min(JME_MAX_MANUAL_SPEED_SLIDER_KMH, cachedKmh)));
        }

        sliderMaxManualSpeed.setY2(108);
        sliderDecelerationConstant.setY2(128);
        sliderDelayedVehicleSpeedIncreasePercentage.setY2(148);
        sliderDelayedVehicleReduceDwellTimePercentage.setY2(168);
        buttonEarlyVehicleIncreaseDwellTime.setY2(188);
        buttonIsManual.setY2(208);
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/screen/WidgetShorterSlider;<init>(IIILjava/util/function/IntFunction;Ljava/util/function/IntConsumer;)V",
                    ordinal = 4
            ),
            index = 2
    )
    private int jme$setMaxSpeedSliderRange(int maxValue) {
        return JME_MAX_MANUAL_SPEED_SLIDER_KMH;
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/screen/WidgetShorterSlider;<init>(IIILjava/util/function/IntFunction;Ljava/util/function/IntConsumer;)V",
                    ordinal = 4
            ),
            index = 3
    )
    private IntFunction<String> jme$setContinuousSpeedFormatter(IntFunction<String> originalFormatter) {
        return value -> {
            final int kmh = Math.max(1, Math.min(JME_MAX_MANUAL_SPEED_SLIDER_KMH, value));
            if (kmh >= JME_MAX_MANUAL_SPEED_SLIDER_KMH) {
                return new Text(TextHelper.translatable("gui.jme.no_limit").data).getString();
            }
            return JmeConfig.formatSpeedLabel(kmh);
        };
    }

    @Redirect(
            method = "setButtons",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mapping/mapper/CheckboxWidgetExtension;isChecked2()Z",
                    ordinal = 0
            )
    )
    private boolean jme$alwaysShowMaxSpeedSlider(CheckboxWidgetExtension checkbox) {
        return true;
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mapping/mapper/CheckboxWidgetExtension;isChecked2()Z",
                    ordinal = 0
            )
    )
    private boolean jme$alwaysShowMaxSpeedLabel(CheckboxWidgetExtension checkbox) {
        return true;
    }

    @ModifyArg(
            method = "onClose2",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;setMaxManualSpeed(D)V"
            ),
            index = 0
    )
    private double jme$saveContinuousSpeedDirectly(double originalValue) {
        final int sliderKmh = Math.max(1, Math.min(JME_MAX_MANUAL_SPEED_SLIDER_KMH, sliderMaxManualSpeed.getIntValue()));
        if (sliderKmh >= JME_MAX_MANUAL_SPEED_SLIDER_KMH) {
            return Utilities.kilometersPerHourToMetersPerMillisecond(JME_NO_LIMIT_CAP_KMH);
        }
        return Utilities.kilometersPerHourToMetersPerMillisecond(sliderKmh);
    }

    @Inject(method = "tick2", at = @At("TAIL"))
    private void jme$cacheSpeedEveryTick(CallbackInfo ci) {
        final SavedRailScreenBase<?, ?> base = (SavedRailScreenBase<?, ?>) (Object) this;
        final Object savedRailBase = ((SavedRailScreenBaseAccessor) base).jme$getSavedRailBase();
        if (savedRailBase instanceof Siding) {
            final Siding siding = (Siding) savedRailBase;
            SidingSpeedSliderFileStore.set(Long.toString(siding.getId()), sliderMaxManualSpeed.getIntValue());
        }
    }

    @Inject(method = "onClose2", at = @At("TAIL"))
    private void jme$forcePersistAfterClose(CallbackInfo ci) {
        final SavedRailScreenBase<?, ?> base = (SavedRailScreenBase<?, ?>) (Object) this;
        final Object savedRailBase = ((SavedRailScreenBaseAccessor) base).jme$getSavedRailBase();
        if (savedRailBase instanceof Siding) {
            final Siding siding = (Siding) savedRailBase;
            final int sliderKmh = Math.max(1, Math.min(JME_MAX_MANUAL_SPEED_SLIDER_KMH, sliderMaxManualSpeed.getIntValue()));
            siding.setMaxManualSpeed(sliderKmh >= JME_MAX_MANUAL_SPEED_SLIDER_KMH
                    ? Utilities.kilometersPerHourToMetersPerMillisecond(JME_NO_LIMIT_CAP_KMH)
                    : Utilities.kilometersPerHourToMetersPerMillisecond(sliderKmh));
            SidingSpeedSliderFileStore.set(Long.toString(siding.getId()), sliderKmh);
            InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));
        }
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 114))
    private int jme$moveDecelerationLabelDown(int original) {
        return 134;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 134))
    private int jme$moveDelayedIncreaseLabelDown(int original) {
        return 154;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 154))
    private int jme$moveDelayedReduceLabelDown(int original) {
        return 174;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 214))
    private int jme$moveMaxManualSpeedLabelUp(int original) {
        // Align the "Maximum Drive Speed" label with the relocated slider.
        return 114;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 234))
    private int jme$moveManualToAutomaticLabelDown(int original) {
        return 254;
    }

}
