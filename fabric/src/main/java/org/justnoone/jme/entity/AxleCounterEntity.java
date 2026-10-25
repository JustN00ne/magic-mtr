package org.justnoone.jme.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.justnoone.jme.block.BlockAxleCounter;
import org.justnoone.jme.block.CrossingLinks;
import org.justnoone.jme.compat.AxleCounterEntityBase;
import org.justnoone.jme.item.ModItems;
import org.mtr.core.data.Rail;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.mapper.TextHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * An axle counter attached to an existing MTR rail. It is not a block and does not change the rail: it stores the rail's
 * id and its distance along the rail, sits on the rail (turned along it), and detects trains passing over that exact
 * point. Crossing controllers link to it by UUID as their start or end node.
 * <p>
 * If the rail is removed, the counter drops as an item; if the rail is reshaped, the counter follows it. Hitting it
 * (left click) breaks it.
 */
public class AxleCounterEntity extends AxleCounterEntityBase {

    private static final int DETECT_TICKS = 3;
    private static final int RAIL_CHECK_TICKS = 40;
    /**
     * The rail must be missing this many checks in a row before the counter is dropped, so a counter is never dropped
     * while MTR is still loading the dimension's rails.
     */
    private static final int MISSING_RAIL_CHECKS = 3;

    private String railId = "";
    private double railProgress;
    private boolean facingForward = true;
    private boolean detecting;
    private int missingRailChecks;
    /**
     * The controllers this counter is linked to, so they can be told when it is removed.
     */
    private final Set<Long> controllers = new HashSet<>();

    public AxleCounterEntity(EntityType<?> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
    }

    /**
     * Creates a counter on a rail point; the caller spawns it.
     */
    static AxleCounterEntity create(World world, Rail rail, double railProgress, boolean facingForward) {
        final AxleCounterEntity counter = new AxleCounterEntity(ModEntities.AXLE_COUNTER, world);
        counter.railId = rail.getHexId();
        counter.railProgress = railProgress;
        counter.facingForward = facingForward;
        RailAttachment.applyPose(counter, rail, railProgress, facingForward);
        return counter;
    }

    /**
     * Places a counter on the MTR rail the player is aiming at (server side), facing the player's look direction along
     * the rail.
     *
     * @return whether a counter was placed (the caller then uses up the item)
     */
    public static boolean tryPlace(net.minecraft.server.world.ServerWorld world, PlayerEntity player, double reach) {
        final Simulator simulator = BlockAxleCounter.getSimulator(new org.mtr.mapping.holder.World(world));
        if (simulator == null) {
            player.sendMessage(TextHelper.literal("§cNo MTR rails in this dimension.").data, true);
            return false;
        }
        final net.minecraft.util.math.Vec3d eyePos = player.getCameraPosVec(1);
        final net.minecraft.util.math.Vec3d lookVec = player.getRotationVec(1);
        final double[] eye = {eyePos.x, eyePos.y, eyePos.z};
        final double[] look = {lookVec.x, lookVec.y, lookVec.z};
        final RailAttachment.Target target = RailAttachment.findTarget(simulator, eye, look, reach);
        if (target == null) {
            player.sendMessage(TextHelper.literal("§eAim at an MTR rail to place the axle counter on it.").data, true);
            return false;
        }

        final AxleCounterEntity counter = create(world, target.rail, target.railProgress, RailAttachment.facesForward(target.rail, target.railProgress, look));
        final boolean occupied = !world.getEntitiesByClass(AxleCounterEntity.class, counter.getBoundingBox().expand(2), other -> other.railId.equals(counter.railId) && Math.abs(other.railProgress - counter.railProgress) < 0.75).isEmpty();
        if (occupied) {
            player.sendMessage(TextHelper.literal("§eThere is already an axle counter here.").data, true);
            return false;
        }
        world.spawnEntity(counter);
        world.playSound(null, counter.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_METAL_PLACE, net.minecraft.sound.SoundCategory.BLOCKS, 1, 1);
        player.sendMessage(TextHelper.literal("§aAxle counter placed on the rail. Link it with the Crossing Linker (Start Node / End Node mode).").data, true);
        return true;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        final World world = getEntityWorld();
        if (world.isClient()) {
            return;
        }
        final int stagger = (int) Math.floorMod(getUuid().getLeastSignificantBits(), (long) RAIL_CHECK_TICKS);
        final int tick = age++ + stagger;
        if (tick % DETECT_TICKS != 0 && tick % RAIL_CHECK_TICKS != 0) {
            return;
        }

        final Simulator simulator = BlockAxleCounter.getSimulator(new org.mtr.mapping.holder.World(world));
        if (simulator == null) {
            detecting = false;
            return;
        }
        final Rail rail = RailAttachment.getRail(simulator, railId);

        if (tick % RAIL_CHECK_TICKS == 0) {
            if (rail == null) {
                boolean hasRails;
                try {
                    hasRails = !simulator.rails.isEmpty();
                } catch (RuntimeException ignored) {
                    hasRails = false;
                }
                if (hasRails && ++missingRailChecks >= MISSING_RAIL_CHECKS) {
                    breakCounter(true);
                    return;
                }
            } else {
                missingRailChecks = 0;
                followRail(rail);
            }
        }

        if (tick % DETECT_TICKS == 0) {
            detecting = rail != null && BlockAxleCounter.isRailPointOccupied(simulator, rail, railProgress);
        }
    }

    /**
     * Keeps the counter on its rail if the rail was reshaped (same nodes, new curve).
     */
    private void followRail(Rail rail) {
        final double oldX = getX(), oldY = getY(), oldZ = getZ();
        final float oldYaw = getYaw();
        if (railProgress > rail.railMath.getLength()) {
            railProgress = rail.railMath.getLength();
        }
        RailAttachment.applyPose(this, rail, railProgress, facingForward);
        if (squaredDistanceTo(oldX, oldY, oldZ) < 1E-6 && Math.abs(getYaw() - oldYaw) < 1E-3) {
            setPosition(oldX, oldY, oldZ);
        }
    }

    public boolean isDetecting() {
        return detecting;
    }

    public String getRailId() {
        return railId;
    }

    public double getRailProgress() {
        return railProgress;
    }

    public void addController(BlockPos controllerPos) {
        controllers.add(controllerPos.asLong());
    }

    public void removeController(BlockPos controllerPos) {
        controllers.remove(controllerPos.asLong());
    }

    /**
     * Removes the counter, unlinking it from its controllers, and drops it as an item if {@code drop}.
     */
    public void breakCounter(boolean drop) {
        final World world = getEntityWorld();
        if (world.isClient() || isRemoved()) {
            return;
        }
        CrossingLinks.onCounterRemoved(world, getUuid(), controllers);
        if (drop) {
            dropStack(new ItemStack(ModItems.AXLE_COUNTER.data));
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getEntityWorld().isClient() || isRemoved()) {
            return false;
        }
        // Only players break counters (not explosions, mobs or fire), like item frames.
        if (source.getAttacker() instanceof PlayerEntity) {
            breakCounter(!((PlayerEntity) source.getAttacker()).isCreative());
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.AXLE_COUNTER.data);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        railId = nbt.getString("jme_rail_id");
        railProgress = nbt.getDouble("jme_rail_progress");
        facingForward = !nbt.contains("jme_facing_forward") || nbt.getBoolean("jme_facing_forward");
        controllers.clear();
        for (final long controller : nbt.getLongArray("jme_controllers")) {
            controllers.add(controller);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("jme_rail_id", railId);
        nbt.putDouble("jme_rail_progress", railProgress);
        nbt.putBoolean("jme_facing_forward", facingForward);
        nbt.putLongArray("jme_controllers", controllers.stream().mapToLong(Long::longValue).toArray());
    }
}
