package org.justnoone.jme.mixin;

import org.mtr.mapping.holder.Identifier;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(ItemRailModifier.class)
public class ItemRailModifierMixin {

    private static final ThreadLocal<Integer> JME_MAGIC_SPEED_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> JME_MAGIC_STYLE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Rail.Shape> JME_MAGIC_SHAPE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> JME_MAGIC_TILT_START_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> JME_MAGIC_TILT_MIDDLE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> JME_MAGIC_TILT_END_CONTEXT = new ThreadLocal<>();

    @Inject(method = "onConnect", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$cacheMagicConnectorSpeed(
            World world,
            ItemStack stack,
            TransportMode transportMode,
            BlockState stateStart,
            BlockState stateEnd,
            BlockPos posStart,
            BlockPos posEnd,
            Angle facingStart,
            Angle facingEnd,
            ServerPlayerEntity player,
            CallbackInfo ci
    ) {
        final boolean isUniversal = MagicRailConstants.isUniversalConnector(stack);
        
        if (isUniversal) {
            JME_MAGIC_SPEED_CONTEXT.set(MagicRailConstants.getSpeedFromStack(stack));
            JME_MAGIC_STYLE_CONTEXT.set(MagicRailConstants.getStyleFromStack(stack));
            JME_MAGIC_SHAPE_CONTEXT.set(MagicRailConstants.getShapeFromStack(stack));
            JME_MAGIC_TILT_START_CONTEXT.set(MagicRailConstants.getStartTiltFromStack(stack));
            JME_MAGIC_TILT_MIDDLE_CONTEXT.set(MagicRailConstants.getMiddleTiltFromStack(stack));
            JME_MAGIC_TILT_END_CONTEXT.set(MagicRailConstants.getEndTiltFromStack(stack));
        } else {
            JME_MAGIC_SPEED_CONTEXT.remove();
            JME_MAGIC_STYLE_CONTEXT.remove();
            JME_MAGIC_SHAPE_CONTEXT.remove();
            JME_MAGIC_TILT_START_CONTEXT.remove();
            JME_MAGIC_TILT_MIDDLE_CONTEXT.remove();
            JME_MAGIC_TILT_END_CONTEXT.remove();
        }
    }

    @Inject(method = "onConnect", at = @At("TAIL"), remap = false)
    private void jme$clearMagicConnectorSpeed(
            World world,
            ItemStack stack,
            TransportMode transportMode,
            BlockState stateStart,
            BlockState stateEnd,
            BlockPos posStart,
            BlockPos posEnd,
            Angle facingStart,
            Angle facingEnd,
            ServerPlayerEntity player,
            CallbackInfo ci
    ) {
        JME_MAGIC_SPEED_CONTEXT.remove();
        JME_MAGIC_STYLE_CONTEXT.remove();
        JME_MAGIC_SHAPE_CONTEXT.remove();
        JME_MAGIC_TILT_START_CONTEXT.remove();
        JME_MAGIC_TILT_MIDDLE_CONTEXT.remove();
        JME_MAGIC_TILT_END_CONTEXT.remove();
    }

    @Inject(method = "createRail", at = @At("RETURN"), cancellable = true, remap = false)
    private void jme$applySpeedFromConnectorNbt(
            UUID uuid,
            TransportMode transportMode,
            BlockState stateStart,
            BlockState stateEnd,
            BlockPos posStart,
            BlockPos posEnd,
            Angle facingStart,
            Angle facingEnd,
            CallbackInfoReturnable<Rail> cir
    ) {
        final Integer configuredSpeed = JME_MAGIC_SPEED_CONTEXT.get();
        final String configuredStyle = JME_MAGIC_STYLE_CONTEXT.get();
        final Rail.Shape configuredShape = JME_MAGIC_SHAPE_CONTEXT.get();
        final Integer configuredTiltStart = JME_MAGIC_TILT_START_CONTEXT.get();
        final Integer configuredTiltMiddle = JME_MAGIC_TILT_MIDDLE_CONTEXT.get();
        final Integer configuredTiltEnd = JME_MAGIC_TILT_END_CONTEXT.get();
        final Rail rail = cir.getReturnValue();
        if (configuredSpeed == null || rail == null) {
            return;
        }

        if (rail.isPlatform() || rail.isSiding() || rail.canTurnBack()) {
            return;
        }

        final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
        final ObjectArrayList<String> styles = new ObjectArrayList<>(accessor.jme$getStyles());
        if (configuredStyle != null && !configuredStyle.isEmpty() && !MagicRailConstants.DEFAULT_STYLE.equals(configuredStyle)) {
            styles.clear();
            styles.add(MagicRailConstants.toPlacedStyleId(configuredStyle));
        }

        // Preserve one-way rails correctly.
        // Rail.getSpeedLimitKilometersPerHour(reversed) is mapped through Rail.reversePositions (canonical ordering),
        // but Rail.newRail expects raw speedLimit1/speedLimit2 in schema order. Using the mapped getter here can
        // swap which direction is blocked depending on coordinate ordering, causing the "sometimes reversed" bug.
        final long speedA = accessor.jme$getSpeedLimit1() <= 0 ? 0 : configuredSpeed;
        final long speedB = accessor.jme$getSpeedLimit2() <= 0 ? 0 : configuredSpeed;

        final Rail updatedRail = Rail.newRail(
                accessor.jme$getPosition1(), accessor.jme$getAngle1(),
                accessor.jme$getPosition2(), accessor.jme$getAngle2(),
                configuredShape == null ? accessor.jme$getShape() : configuredShape, accessor.jme$getVerticalRadius(),
                styles,
                speedA, speedB,
                rail.isPlatform(), rail.isSiding(), rail.canAccelerate(), rail.canConnectRemotely(), accessor.jme$getCanHaveSignal(), accessor.jme$getTransportMode()
        );

        if (configuredTiltStart != null && configuredTiltMiddle != null && configuredTiltEnd != null) {
            MagicRailTiltRegistry.setTiltAbsolute(updatedRail.getHexId(), configuredTiltStart, configuredTiltMiddle, configuredTiltEnd);
        } else {
            MagicRailTiltRegistry.removeTilt(updatedRail.getHexId());
        }

        cir.setReturnValue(updatedRail);
    }

    private static int jme$getSpeedFromConnectorId(Identifier itemId) {
        final String[] split = itemId.getPath().split("_");
        for (String part : split) {
            try {
                return MagicRailConstants.clampToStep(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // Skip non-numeric segments like rail/connector/one/way.
            }
        }
        return MagicRailConstants.DEFAULT_SPEED_KMH;
    }
}
