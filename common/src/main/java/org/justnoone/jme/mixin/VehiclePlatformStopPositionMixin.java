package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.PlatformStopPositionRegistry;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = Vehicle.class, remap = false)
public abstract class VehiclePlatformStopPositionMixin {

    @Shadow
    public VehicleExtraData vehicleExtraData;

    @Unique
    private double jme$stoppedPlatformProgressOverride = Double.NaN;
    @Unique
    private static volatile Field jme$railProgressField;

    @Redirect(
            method = "getPathStoppingPoint()D",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getEndDistance()D", ordinal = 0),
            require = 0
    )
    private double jme$getAdjustedPassedPlatformPoint(PathData pathData) {
        final double adjustedStoppingPoint = jme$adjustStoppingPoint(pathData);
        return adjustedStoppingPoint == pathData.getEndDistance() ? adjustedStoppingPoint : adjustedStoppingPoint - 1.0e-4D;
    }

    @Redirect(
            method = "getPathStoppingPoint()D",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getEndDistance()D", ordinal = 1),
            require = 0
    )
    private double jme$getAdjustedPathStoppingPoint(PathData pathData) {
        return jme$adjustStoppingPoint(pathData);
    }

    @Redirect(
            method = "setNextStoppingIndex()V",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/tool/Utilities;getIndexFromConditionalList(Ljava/util/List;D)I"),
            require = 0
    )
    private int jme$getNextStoppingSearchIndex(List<PathData> path, double railProgress) {
        final int pathIndex = org.mtr.core.tool.Utilities.getIndexFromConditionalList(path, railProgress);
        if (path == null || pathIndex < 0 || pathIndex >= path.size()) {
            return pathIndex;
        }

        final PathData pathData = path.get(pathIndex);
        if (pathData == null || PlatformStopPositionRegistry.get(pathData.getSavedRailBaseId()) == PlatformStopPositionRegistry.StopPosition.END) {
            return pathIndex;
        }

        final double adjustedStoppingPoint = jme$adjustStoppingPoint(pathData);
        if (Math.abs(railProgress - adjustedStoppingPoint) < 1.0e-3D) {
            return Math.min(pathIndex + 1, path.size());
        }
        return pathIndex;
    }

    @ModifyArg(
            method = "simulate(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/libraries/it/unimi/dsi/fastutil/longs/Long2LongAVLTreeMap;)V",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Vehicle;simulateStopped(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;I)V"),
            index = 2,
            require = 0
    )
    private int jme$useNextSegmentForAdjustedPlatformStop(int pathIndex) {
        jme$stoppedPlatformProgressOverride = Double.NaN;
        if (vehicleExtraData == null || vehicleExtraData.immutablePath == null || pathIndex < 0 || pathIndex >= vehicleExtraData.immutablePath.size()) {
            return pathIndex;
        }

        final PathData pathData = vehicleExtraData.immutablePath.get(pathIndex);
        if (pathData == null || PlatformStopPositionRegistry.get(pathData.getSavedRailBaseId()) == PlatformStopPositionRegistry.StopPosition.END) {
            return pathIndex;
        }

        final double adjustedStoppingPoint = jme$adjustStoppingPoint(pathData);
        if (pathIndex + 1 < vehicleExtraData.immutablePath.size() && Math.abs(jme$getRailProgress() - adjustedStoppingPoint) < 1.0e-4D) {
            jme$stoppedPlatformProgressOverride = adjustedStoppingPoint;
            return pathIndex + 1;
        }
        return pathIndex;
    }

    @Redirect(
            method = "simulateStopped(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;I)V",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getStartDistance()D", ordinal = 0),
            require = 0
    )
    private double jme$getManualStoppedPlatformProgress(PathData pathData) {
        return jme$getStoppedPlatformProgress(pathData);
    }

    @Redirect(
            method = "simulateStopped(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;I)V",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getStartDistance()D", ordinal = 2),
            require = 0
    )
    private double jme$getAutomaticStoppedPlatformProgress(PathData pathData) {
        return jme$getStoppedPlatformProgress(pathData);
    }

    @Redirect(
            method = "simulateStopped(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;I)V",
            at = @At(value = "FIELD", target = "Lorg/mtr/core/data/Vehicle;railProgress:D", opcode = Opcodes.PUTFIELD),
            require = 0
    )
    private void jme$setStoppedRailProgress(Vehicle vehicle, double value) {
        if (Double.isFinite(jme$stoppedPlatformProgressOverride)) {
            return;
        }
        jme$setRailProgress(value);
    }

    @Inject(
            method = "simulateStopped(JLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;I)V",
            at = @At("TAIL"),
            require = 0
    )
    private void jme$clearStoppedPlatformProgressOverride(long millisElapsed, ObjectArrayList<?> trains, int pathIndex, CallbackInfo ci) {
        jme$stoppedPlatformProgressOverride = Double.NaN;
    }

    private double jme$adjustStoppingPoint(PathData pathData) {
        final double defaultStoppingPoint = pathData.getEndDistance();
        final double trainLength = vehicleExtraData == null ? 0D : vehicleExtraData.getTotalVehicleLength();
        return PlatformStopPositionRegistry.adjustStoppingPoint(pathData, defaultStoppingPoint, trainLength);
    }

    private double jme$getStoppedPlatformProgress(PathData pathData) {
        if (Double.isFinite(jme$stoppedPlatformProgressOverride)) {
            return jme$stoppedPlatformProgressOverride;
        }
        final PlatformStopPositionRegistry.StopPosition stopPosition = PlatformStopPositionRegistry.get(pathData.getSavedRailBaseId());
        if (stopPosition == PlatformStopPositionRegistry.StopPosition.END) {
            return pathData.getStartDistance();
        }
        return jme$adjustStoppingPoint(pathData);
    }

    @Unique
    private double jme$getRailProgress() {
        try {
            return jme$getRailProgressField().getDouble(this);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    @Unique
    private void jme$setRailProgress(double value) {
        try {
            jme$getRailProgressField().setDouble(this, value);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static Field jme$getRailProgressField() throws NoSuchFieldException {
        Field field = jme$railProgressField;
        if (field != null) {
            return field;
        }

        Class<?> targetClass = Vehicle.class;
        while (targetClass != null) {
            try {
                field = targetClass.getDeclaredField("railProgress");
                field.setAccessible(true);
                jme$railProgressField = field;
                return field;
            } catch (NoSuchFieldException ignored) {
                targetClass = targetClass.getSuperclass();
            }
        }
        throw new NoSuchFieldException("railProgress");
    }
}
