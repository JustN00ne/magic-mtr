package org.justnoone.jme.mixin;

import org.mtr.core.data.Depot;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Siding;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.EditDepotScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(value = EditDepotScreen.class, remap = false)
public abstract class EditDepotScreenTrainLengthMixin implements IGui {

    @Shadow
    @Final
    private int rightPanelsX;

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderTrainLength(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        final String label = jme$getTrainLengthLabel();
        if (label == null) {
            return;
        }
        graphicsHolder.drawText(TextHelper.literal(label), rightPanelsX, SQUARE_SIZE * 11 + 4, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
    }

    @Unique
    private String jme$getTrainLengthLabel() {
        final NameColorDataBase data = ((EditNameColorScreenBaseAccessor) this).jme$getData();
        if (!(data instanceof Depot)) {
            return null;
        }

        double minLength = Double.POSITIVE_INFINITY;
        double maxLength = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (final Object savedRail : ((Depot) data).savedRails) {
            if (savedRail instanceof Siding) {
                final double length = Siding.getTotalVehicleLength(((Siding) savedRail).getVehicleCars());
                if (Double.isFinite(length) && length > 0) {
                    minLength = Math.min(minLength, length);
                    maxLength = Math.max(maxLength, length);
                    count++;
                }
            }
        }

        if (count == 0) {
            return "Train length: none";
        }
        if (Math.abs(maxLength - minLength) < 0.05D) {
            return "Train length: " + jme$formatBlocks(maxLength) + " blocks";
        }
        return "Train length: " + jme$formatBlocks(minLength) + "-" + jme$formatBlocks(maxLength) + " blocks";
    }

    @Unique
    private String jme$formatBlocks(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
