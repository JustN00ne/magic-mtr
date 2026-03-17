package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * When a vehicle is diverted to an alternative platform, the physical path contains the
 * alternative platform ID, but {@link VehicleExtraData#setRoutePlatformInfo} will still map
 * the stopIndex back to the route's primary platform. This keeps the simulation correct but
 * makes the UI always show the primary platform.
 *
 * Patch the platform IDs to match the actual platform in the path, but only when that platform
 * is a configured alternative for the route + primary platform at that stop.
 */
@Mixin(value = VehicleExtraData.class, remap = false)
public abstract class VehicleExtraDataAlternativePlatformMixin {

    @Unique
    private static volatile Field jme$thisPlatformIdField;

    @Inject(method = "setRoutePlatformInfo", at = @At("TAIL"), remap = false, require = 0)
    private void jme$applyAlternativePlatformToRouteInfo(Depot depot, int currentIndex, CallbackInfo ci) {
        if (!AlternativePlatformRegistry.isEnabled()) {
            return;
        }
        if (depot == null) {
            return;
        }

        final VehicleExtraData self = (VehicleExtraData) (Object) this;
        if (currentIndex < 0 || currentIndex >= self.immutablePath.size()) {
            return;
        }

        final PathData pathData = self.immutablePath.get(currentIndex);
        if (pathData == null || pathData.getDwellTime() <= 0) {
            return;
        }

        final long actualPlatformId = pathData.getSavedRailBaseId();
        if (actualPlatformId == 0) {
            return;
        }

        final long routeId = self.getThisRouteId();
        final long scheduledPrimaryPlatformId = self.getThisPlatformId();
        if (routeId == 0 || scheduledPrimaryPlatformId == 0) {
            return;
        }

        if (actualPlatformId == scheduledPrimaryPlatformId) {
            return;
        }

        // Only override when the actual platform is an explicitly configured alternative.
        if (!AlternativePlatformRegistry.getAlternatives(routeId, scheduledPrimaryPlatformId).contains(actualPlatformId)) {
            return;
        }

        jme$setThisPlatformId(self, actualPlatformId);
    }

    @Unique
    private static void jme$setThisPlatformId(VehicleExtraData vehicleExtraData, long platformId) {
        if (vehicleExtraData == null || platformId == 0) {
            return;
        }

        try {
            Field field = jme$thisPlatformIdField;
            if (field == null) {
                field = jme$findField(vehicleExtraData.getClass(), "thisPlatformId");
                if (field == null) {
                    return;
                }
                field.setAccessible(true);
                jme$thisPlatformIdField = field;
            }
            field.setLong(vehicleExtraData, platformId);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static Field jme$findField(Class<?> clazz, String fieldName) {
        Class<?> check = clazz;
        while (check != null) {
            try {
                return check.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                check = check.getSuperclass();
            }
        }
        return null;
    }
}
