package org.justnoone.jme.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.generated.data.RailSchema;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RailSchema.class)
public interface RailSchemaAccessor {

    @Accessor(value = "position1", remap = false)
    Position jme$getPosition1();

    @Accessor(value = "angle1", remap = false)
    Angle jme$getAngle1();

    @Accessor(value = "position2", remap = false)
    Position jme$getPosition2();

    @Accessor(value = "angle2", remap = false)
    Angle jme$getAngle2();

    @Accessor(value = "shape", remap = false)
    Rail.Shape jme$getShape();

    @Accessor(value = "verticalRadius", remap = false)
    double jme$getVerticalRadius();

    @Accessor(value = "styles", remap = false)
    ObjectArrayList<String> jme$getStyles();

    @Accessor(value = "speedLimit1", remap = false)
    long jme$getSpeedLimit1();

    @Accessor(value = "speedLimit2", remap = false)
    long jme$getSpeedLimit2();

    @Accessor(value = "canHaveSignal", remap = false)
    boolean jme$getCanHaveSignal();

    @Accessor(value = "transportMode", remap = false)
    TransportMode jme$getTransportMode();
}
