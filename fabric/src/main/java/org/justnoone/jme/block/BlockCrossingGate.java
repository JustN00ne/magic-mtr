package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.BlockView;
import org.mtr.mapping.holder.BooleanProperty;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.ItemPlacementContext;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mapping.mapper.BlockHelper;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;

import java.util.List;

/**
 * A crossing gate (barrier). Linked to a crossing controller (a level crossing) with the Crossing Linker's object mode,
 * it lowers its arm when the controller's timeline closes the gates and raises it again when the crossing opens. There
 * is a left and a right gate, whose arm swings to the left or to the right as seen from the road in front of it, so
 * both sides of the road (and either driving side) can be closed. It also lowers while it receives a redstone signal,
 * and emits a redstone signal of 15 while the controller has it closed (eg to stop road vehicles). The arm's length
 * (4 to 11 blocks) and its bell are set in its screen: right-click it with MTR's brush.
 */
public class BlockCrossingGate extends BlockExtension implements BlockWithEntity {

    /**
     * Set while the controller has the gate closed; the gate then emits a redstone signal.
     */
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    /**
     * Set while the arm is (going) down: closed by the controller or by a redstone signal. Drawn by the client.
     */
    public static final BooleanProperty LOWERED = BooleanProperty.of("lowered");
    private static final Property<Boolean> POWERED_PROPERTY = Property.cast(POWERED);
    private static final Property<Boolean> LOWERED_PROPERTY = Property.cast(LOWERED);
    // The same facing and diagonal properties as the level crossing, so gates line up with the crossings.
    private static final Property<net.minecraft.util.math.Direction> FACING_PROPERTY = Property.cast(BlockCrossingLights.FACING);
    private static final Property<Boolean> DIAGONAL_PROPERTY = Property.cast(BlockCrossingLights.DIAGONAL);
    private static final int REDSTONE_CHECK_TICKS = 4;
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 11;
    public static final int DEFAULT_LENGTH = 6;
    /**
     * The housing is about 1.6 blocks tall.
     */
    private static final double LINK_OUTLINE_HEIGHT = 1.6;

    private final boolean right;

    /**
     * @param right whether the arm swings to the right (seen from the front) instead of the left
     */
    public BlockCrossingGate(boolean right) {
        super(BlockHelper.createBlockSettings(true, true).nonOpaque());
        this.right = right;
    }

    public boolean isRight() {
        return right;
    }

    @Override
    public BlockState getPlacementState2(ItemPlacementContext context) {
        // The front faces the player, snapped to 45° steps of the player's yaw (0 = south, clockwise), like the level
        // crossing. Boolean properties default to their first value (true), so both are set explicitly.
        final int step = Math.floorMod(Math.round((context.getPlayerYaw() + 180) / 45F), 8);
        final net.minecraft.util.math.Direction front = net.minecraft.util.math.Direction.fromRotation(step / 2 * 90);
        return super.getPlacementState2(context).with(FACING_PROPERTY, front).with(DIAGONAL_PROPERTY, step % 2 == 1).with(POWERED_PROPERTY, false).with(LOWERED_PROPERTY, false);
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        properties.add(POWERED);
        properties.add(LOWERED);
        properties.add(BlockCrossingLights.FACING);
        properties.add(BlockCrossingLights.DIAGONAL);
    }

    @Override
    public boolean emitsRedstonePower2(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower2(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED_PROPERTY) ? 15 : 0;
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    public static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    public static boolean isLowered(net.minecraft.block.BlockState state) {
        final net.minecraft.state.property.BooleanProperty lowered = LOWERED.data;
        return state.contains(lowered) && state.get(lowered);
    }

    public static class BlockEntity extends BlockEntityExtension implements CrossingTicker.Ticking, CrossingObject {

        /**
         * Set by the controller this gate is linked to.
         */
        private boolean closed;
        /**
         * Set while the gate receives a vanilla redstone signal. Like the level crossing's, this never switches the
         * redstone output on, so two gates next to each other cannot latch each other closed.
         */
        private boolean redstoneActive;
        private int redstoneCheckCounter;
        private int length = DEFAULT_LENGTH;
        /**
         * Gates are silent by default: the level crossings next to them already ring.
         */
        private int bell = CrossingSignal.BELL_NONE;
        private final CrossingSignal signal = new CrossingSignal();
        /**
         * Client only: where the arm is, animated while drawing.
         */
        private final org.justnoone.jme.util.CrossingGateMotion motion = new org.justnoone.jme.util.CrossingGateMotion();
        private boolean motionStarted;
        private long lastRenderNanos;

        public BlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.CROSSING_GATE_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            super.readCompoundTag(compoundTag);
            CrossingTicker.track(this);
            closed = compoundTag.getBoolean("jme_gate_closed");
            redstoneActive = compoundTag.getBoolean("jme_gate_redstone_active");
            length = compoundTag.data.contains("jme_gate_length") ? clampLength(compoundTag.getInt("jme_gate_length")) : DEFAULT_LENGTH;
            bell = compoundTag.data.contains("jme_gate_bell") ? CrossingSignal.clampBell(compoundTag.getInt("jme_gate_bell")) : CrossingSignal.BELL_NONE;
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            super.writeCompoundTag(compoundTag);
            compoundTag.putBoolean("jme_gate_closed", closed);
            compoundTag.putBoolean("jme_gate_redstone_active", redstoneActive);
            compoundTag.putInt("jme_gate_length", length);
            compoundTag.putInt("jme_gate_bell", bell);
        }

        @Override
        public void blockEntityTick() {
            CrossingTicker.onTick(this);
        }

        @Override
        public void crossingTick(World world) {

            if (++redstoneCheckCounter >= REDSTONE_CHECK_TICKS) {
                redstoneCheckCounter = 0;
                final boolean powered = world.data.isReceivingRedstonePower(getPos2().data);
                if (powered != redstoneActive) {
                    redstoneActive = powered;
                    markDirty2();
                }
            }

            final boolean lowered = closed || redstoneActive;
            BlockState state = getCachedState2();
            if (state.contains(POWERED_PROPERTY) && state.contains(LOWERED_PROPERTY) && (state.get(POWERED_PROPERTY) != closed || state.get(LOWERED_PROPERTY) != lowered)) {
                // Flag 3 notifies neighbours (redstone output) and syncs the state to clients, which animate the arm.
                state = state.with(POWERED_PROPERTY, closed).with(LOWERED_PROPERTY, lowered);
                world.setBlockState(getPos2(), state, 3);
            }
            signal.tick(world, getPos2(), state, lowered, bell);
        }

        /**
         * Applies the settings chosen in the gate's screen; a new bell rings once.
         */
        void configure(int newLength, int newBell) {
            newBell = CrossingSignal.clampBell(newBell);
            final World world = getWorld2();
            if (world != null && newBell != bell) {
                signal.changeBell(world, getPos2(), bell, newBell);
            }
            bell = newBell;
            length = clampLength(newLength);
            markDirty2();
        }

        public int getLength() {
            return length;
        }

        public int getBell() {
            return bell;
        }

        public boolean isRight() {
            final BlockState state = getCachedState2();
            return state != null && state.getBlock().data instanceof BlockCrossingGate && ((BlockCrossingGate) state.getBlock().data).isRight();
        }

        /**
         * Client only: advances the arm's animation to now.
         *
         * @return how far the arm is lowered (0 = up, 1 = down)
         */
        public float updateArm(boolean lowered) {
            final long now = System.nanoTime();
            if (!motionStarted) {
                // A gate coming into view is drawn where it is, not animated from the top.
                motionStarted = true;
                motion.snap(lowered);
            } else {
                motion.update(lowered, Math.min(1, (now - lastRenderNanos) / 1E9F));
            }
            lastRenderNanos = now;
            return motion.getFraction();
        }

        @Override
        public void setCrossingState(boolean lightsOn, boolean gatesDown) {
            setCrossingActive(gatesDown);
        }

        @Override
        public void setCrossingActive(boolean active) {
            if (closed != active) {
                closed = active;
                markDirty2();
            }
        }

        @Override
        public String getCrossingObjectName() {
            return isRight() ? "Crossing gate (right)" : "Crossing gate (left)";
        }

        @Override
        public double getLinkOutlineHeight() {
            return LINK_OUTLINE_HEIGHT;
        }
    }
}
