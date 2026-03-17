package org.justnoone.jme.rail;

import org.justnoone.jme.mixin.RailSchemaAccessor;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemStack;

public final class BrushRailProfile {

    private static final String TAG_HAS_PROFILE = "jme_brush_has_profile";

    public final int speedKmh;
    public final String style;
    public final Rail.Shape shape;
    public final int tiltStart;
    public final int tiltMiddle;
    public final int tiltEnd;

    public BrushRailProfile(int speedKmh, String style, Rail.Shape shape, int tiltStart, int tiltMiddle, int tiltEnd) {
        this.speedKmh = MagicRailConstants.clampToStep(speedKmh);
        this.style = normalizeStyle(style);
        this.shape = shape == null ? MagicRailConstants.DEFAULT_SHAPE : shape;
        this.tiltStart = MagicRailConstants.clampTiltDegrees(tiltStart);
        this.tiltMiddle = MagicRailConstants.clampTiltDegrees(tiltMiddle);
        this.tiltEnd = MagicRailConstants.clampTiltDegrees(tiltEnd);
    }

    public static BrushRailProfile fromRail(Rail rail) {
        if (rail == null) {
            return null;
        }

        final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
        final MagicRailTiltRegistry.TiltSettings tiltSettings = MagicRailTiltRegistry.getTiltAbsolute(rail.getHexId());
        final int copiedSpeed = resolveCopiedSpeed(rail);
        final String copiedStyle = resolveStyle(accessor);
        final Rail.Shape copiedShape = accessor.jme$getShape();

        if (tiltSettings == null) {
            return new BrushRailProfile(
                    copiedSpeed,
                    copiedStyle,
                    copiedShape,
                    MagicRailConstants.DEFAULT_TILT_DEGREES,
                    MagicRailConstants.DEFAULT_TILT_DEGREES,
                    MagicRailConstants.DEFAULT_TILT_DEGREES
            );
        }

        return new BrushRailProfile(
                copiedSpeed,
                copiedStyle,
                copiedShape,
                tiltSettings.startDegrees,
                tiltSettings.middleDegrees,
                tiltSettings.endDegrees
        );
    }

    public static boolean hasProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        final CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.getBoolean(TAG_HAS_PROFILE);
    }

    public static BrushRailProfile fromStack(ItemStack stack) {
        if (!hasProfile(stack)) {
            return null;
        }

        return new BrushRailProfile(
                MagicRailConstants.getSpeedFromStack(stack),
                MagicRailConstants.getStyleFromStack(stack),
                MagicRailConstants.getShapeFromStack(stack),
                MagicRailConstants.getStartTiltFromStack(stack),
                MagicRailConstants.getMiddleTiltFromStack(stack),
                MagicRailConstants.getEndTiltFromStack(stack)
        );
    }

    public static void writeToStack(ItemStack stack, BrushRailProfile profile) {
        if (stack == null || stack.isEmpty() || profile == null) {
            return;
        }

        MagicRailConstants.setSpeedOnStack(stack, profile.speedKmh);
        MagicRailConstants.setStyleOnStack(stack, profile.style);
        MagicRailConstants.setShapeOnStack(stack, profile.shape);
        MagicRailConstants.setStartTiltOnStack(stack, profile.tiltStart);
        MagicRailConstants.setMiddleTiltOnStack(stack, profile.tiltMiddle);
        MagicRailConstants.setEndTiltOnStack(stack, profile.tiltEnd);
        stack.getOrCreateTag().putBoolean(TAG_HAS_PROFILE, true);
    }

    public static Rail applyToRail(Rail rail, BrushRailProfile profile) {
        if (rail == null || profile == null) {
            return null;
        }

        final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
        final ObjectArrayList<String> styles = new ObjectArrayList<>();
        final String placedStyleId = MagicRailConstants.toPlacedStyleId(profile.style);
        if (!placedStyleId.isEmpty()) {
            styles.add(placedStyleId);
        }

        final long newSpeedA = rail.getSpeedLimitKilometersPerHour(false) == 0 ? 0 : profile.speedKmh;
        final long newSpeedB = rail.getSpeedLimitKilometersPerHour(true) == 0 ? 0 : profile.speedKmh;

        Rail updatedRail = Rail.newRail(
                accessor.jme$getPosition1(),
                accessor.jme$getAngle1(),
                accessor.jme$getPosition2(),
                accessor.jme$getAngle2(),
                profile.shape,
                accessor.jme$getVerticalRadius(),
                styles,
                newSpeedA,
                newSpeedB,
                rail.isPlatform(),
                rail.isSiding(),
                rail.canAccelerate(),
                rail.canConnectRemotely(),
                accessor.jme$getCanHaveSignal(),
                accessor.jme$getTransportMode()
        );

        if (updatedRail == null || !updatedRail.isValid()) {
            updatedRail = Rail.newRail(
                    accessor.jme$getPosition1(),
                    accessor.jme$getAngle1(),
                    accessor.jme$getPosition2(),
                    accessor.jme$getAngle2(),
                    accessor.jme$getShape(),
                    accessor.jme$getVerticalRadius(),
                    styles,
                    newSpeedA,
                    newSpeedB,
                    rail.isPlatform(),
                    rail.isSiding(),
                    rail.canAccelerate(),
                    rail.canConnectRemotely(),
                    accessor.jme$getCanHaveSignal(),
                    accessor.jme$getTransportMode()
            );
        }

        if (updatedRail == null || !updatedRail.isValid()) {
            return null;
        }

        updatedRail.copySignalColors(rail);
        MagicRailTiltRegistry.setTiltAbsolute(updatedRail.getHexId(), profile.tiltStart, profile.tiltMiddle, profile.tiltEnd);
        return updatedRail;
    }

    private static int resolveCopiedSpeed(Rail rail) {
        final long speedA = rail.getSpeedLimitKilometersPerHour(false);
        final long speedB = rail.getSpeedLimitKilometersPerHour(true);
        final long chosenSpeed = Math.max(speedA, speedB);
        if (chosenSpeed <= 0) {
            return MagicRailConstants.DEFAULT_SPEED_KMH;
        }
        return MagicRailConstants.clampToStep((int) chosenSpeed);
    }

    private static String resolveStyle(RailSchemaAccessor accessor) {
        if (accessor == null || accessor.jme$getStyles() == null || accessor.jme$getStyles().isEmpty()) {
            return MagicRailConstants.DEFAULT_STYLE;
        }

        final String rawStyle = accessor.jme$getStyles().get(0);
        return normalizeStyle(rawStyle);
    }

    private static String normalizeStyle(String style) {
        return style == null || style.isEmpty() ? MagicRailConstants.DEFAULT_STYLE : style;
    }
}
