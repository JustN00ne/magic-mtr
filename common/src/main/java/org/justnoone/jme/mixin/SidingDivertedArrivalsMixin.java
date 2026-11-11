package org.justnoone.jme.mixin;

import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.DivertedArrivals;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A train sent to an alternative platform does not arrive at its scheduled platform: its next arrival is dropped from
 * that platform's board (it is listed at the platform it really uses, see {@link DivertedArrivals}).
 * <p>
 * Done per siding because only here an arrival can be tied to one train (departure indices repeat across sidings).
 */
@Mixin(value = Siding.class, remap = false)
public abstract class SidingDivertedArrivalsMixin {

    @Unique
    private static final ThreadLocal<int[]> jme$firstAddedIndex = ThreadLocal.withInitial(() -> new int[1]);

    @Inject(method = "getArrivals", at = @At("HEAD"), remap = false)
    private void jme$rememberListSize(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
        jme$firstAddedIndex.get()[0] = arrivalResponseList.size();
    }

    @Inject(method = "getArrivals", at = @At("TAIL"), remap = false)
    private void jme$dropDivertedArrivals(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList, CallbackInfo ci) {
        final int firstAddedIndex = jme$firstAddedIndex.get()[0];
        if (!AlternativePlatformRegistry.isEnabled() || platform == null || DivertedArrivals.isLookingUp() || firstAddedIndex >= arrivalResponseList.size()) {
            return;
        }

        final Siding siding = (Siding) (Object) this;
        for (final Vehicle vehicle : ((SidingVehiclesAccessor) this).jme$getVehicles()) {
            final DivertedArrivals.Diversion diversion = DivertedArrivals.getDiversion(siding.area, vehicle);
            if (diversion == null || diversion.scheduledPlatform.getId() != platform.getId()) {
                continue;
            }
            final ArrivalResponse next = DivertedArrivals.findNext(arrivalResponseList.subList(firstAddedIndex, arrivalResponseList.size()), diversion);
            if (next != null) {
                arrivalResponseList.remove(next);
            }
        }
    }
}
