package org.justnoone.jme.mixin;

import net.minecraft.text.Text;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MTR formats vehicle speed as "Speed: %s m/s (%s km/h)" via {@code gui.mtr.vehicle_speed}.
 * When MAGIC's "Use mph" is enabled, convert the km/h placeholder to mph and swap the unit label.
 */
@Mixin(value = TranslationProvider.TranslationHolder.class, remap = false)
public abstract class TranslationHolderVehicleSpeedMixin {

    @Unique
    private static final String JME_VEHICLE_SPEED_KEY = "gui.mtr.vehicle_speed";

    @Shadow
    @Final
    public String key;

    @Inject(method = "getMutableText", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$patchVehicleSpeedMutableText(Object[] args, CallbackInfoReturnable<MutableText> cir) {
        final MutableText patched = jme$buildPatchedVehicleSpeedText(args);
        if (patched != null) {
            cir.setReturnValue(patched);
        }
    }

    @Inject(method = "getText", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$patchVehicleSpeedText(Object[] args, CallbackInfoReturnable<org.mtr.mapping.holder.Text> cir) {
        final MutableText patched = jme$buildPatchedVehicleSpeedText(args);
        if (patched == null) {
            return;
        }
        try {
            cir.setReturnValue(new org.mtr.mapping.holder.Text((Text) patched.data));
        } catch (Exception ignored) {
        }
    }

    @Unique
    private MutableText jme$buildPatchedVehicleSpeedText(Object[] args) {
        if (!JmeConfig.useMph()) {
            return null;
        }
        if (key == null || !JME_VEHICLE_SPEED_KEY.equals(key)) {
            return null;
        }
        if (args == null || args.length < 2) {
            return null;
        }

        try {
            final Object mphValue = jme$toMphValue(args[1], args[0]);
            if (mphValue == null) {
                return null;
            }

            final Object[] patchedArgs = args.clone();
            patchedArgs[1] = mphValue;

            // Translate using the current language, then swap the unit suffix to mph.
            final MutableText translated = TextHelper.translatable(key, patchedArgs);
            final String translatedString = translated == null ? "" : translated.getString();
            if (translatedString.isEmpty()) {
                return null;
            }

            final String replaced = jme$replaceUnitSuffix(translatedString);
            if (replaced.equals(translatedString)) {
                return null;
            }

            return TextHelper.literal(replaced);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static Object jme$toMphValue(Object kmhArg, Object mpsArg) {
        final Double kmh = jme$parseNumber(kmhArg);
        if (kmh != null) {
            final double mph = kmh * 0.621371D;
            if (kmhArg instanceof Integer || kmhArg instanceof Long || kmhArg instanceof Short || kmhArg instanceof Byte) {
                return (int) Math.round(mph);
            }
            return jme$round(mph, 1);
        }

        final Double mps = jme$parseNumber(mpsArg);
        if (mps != null) {
            // m/s to mph
            final double mph = mps * 2.2369362920544D;
            return jme$round(mph, 1);
        }

        return null;
    }

    @Unique
    private static Double jme$parseNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        try {
            final String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Unique
    private static double jme$round(double value, int decimals) {
        final int places = Math.max(0, decimals);
        double scale = 1;
        for (int i = 0; i < places; i++) {
            scale *= 10;
        }
        return Math.round(value * scale) / scale;
    }

    @Unique
    private static String jme$replaceUnitSuffix(String text) {
        String updated = text;
        updated = updated.replace("km/h", "mph");
        updated = updated.replace("kmh", "mph");
        updated = updated.replace("км/ч", "mph");
        return updated;
    }
}
