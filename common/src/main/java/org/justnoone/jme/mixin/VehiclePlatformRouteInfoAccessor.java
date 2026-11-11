package org.justnoone.jme.mixin;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleExtraData.VehiclePlatformRouteInfo.class, remap = false)
public interface VehiclePlatformRouteInfoAccessor {

    @Accessor("previousPlatform")
    Platform jme$getPreviousPlatform();

    @Accessor("thisPlatform")
    Platform jme$getThisPlatform();

    @Accessor("nextPlatform")
    Platform jme$getNextPlatform();

    @Accessor("previousRoute")
    Route jme$getPreviousRoute();

    @Accessor("thisRoute")
    Route jme$getThisRoute();

    @Accessor("nextRoute")
    Route jme$getNextRoute();

    @Accessor("platformIndexInRoute")
    int jme$getPlatformIndexInRoute();
}

