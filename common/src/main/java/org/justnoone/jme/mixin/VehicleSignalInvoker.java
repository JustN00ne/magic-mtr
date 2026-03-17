package org.justnoone.jme.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehiclePosition;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

@Mixin(value = Vehicle.class, remap = false)
public interface VehicleSignalInvoker {

    @Invoker("railBlockedDistance")
    double jme$railBlockedDistance(
            int currentIndex,
            double checkRailProgress,
            double checkDistance,
            @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
            boolean reserveRail,
            boolean secondPass
    );

    @Invoker("isCurrentlyManual")
    boolean jme$isCurrentlyManual();

    @Invoker("setNextStoppingIndex")
    void jme$setNextStoppingIndex();
}

