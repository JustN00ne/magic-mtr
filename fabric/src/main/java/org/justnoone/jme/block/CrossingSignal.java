package org.justnoone.jme.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.IntegerProperty;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.World;

/**
 * The flashing lamps and bell of a level crossing, shared by the crossing lights and the controller (which is drawn as
 * a crossing too). While on, the two lamps light alternately and the bell rings; while off, both lamps are dark.
 */
public final class CrossingSignal {

    /**
     * Which lamp is lit: {@link #LAMP_OFF}, or 1 / 2 for the two lamps (textures {@code level_crossing_o1} / {@code _o2}).
     */
    public static final IntegerProperty LAMP = IntegerProperty.of("lamp", 0, 2);
    static final int LAMP_OFF = 0;
    private static final Property<Integer> LAMP_PROPERTY = Property.cast(LAMP);
    private static final int FLASH_INTERVAL_TICKS = 10;
    // A volume above 1 only extends the range (16 blocks per unit), so the bell is heard up to 24 blocks away.
    private static final float BELL_VOLUME = 1.5F;
    private static final float BELL_PITCH = 1.0F;
    // Players this close are told to stop a looping bell when the crossing switches off (beyond the bell's range).
    private static final double STOP_SOUND_RANGE = 48;

    /**
     * The bell sounds a level crossing can use ({@code assets/jme/sounds/*.ogg}), picked with sneak + right-click with
     * MTR's brush. Each is played again every {@code intervalTicks} while the crossing is on: right after a single
     * ring has faded out, or at the end of a sound that is already a loop of rings.
     */
    private static final Bell[] BELLS = {
            new Bell("railroad_crossing", 17, false),
            new Bell("crossing_bell_2", 20, false),
            new Bell("crossing_bell_3", 6, false),
            new Bell("crossing_bell_4", 14, false),
            // British alarm: 4 tone cycles stretched to exactly 36 ticks, with a short faded overlap at the join.
            new Bell("crossing_bell_5", 36, true),
    };

    private int ticksOn;

    /**
     * Registers the bell sounds; must run during mod initialization.
     */
    public static void registerSounds() {
        for (final Bell bell : BELLS) {
            org.justnoone.jme.compat.McCompat.registerSoundEvent(bell.id, bell.sound);
        }
    }

    public static int getBellCount() {
        return BELLS.length;
    }

    static int clampBell(int bell) {
        return bell >= 0 && bell < BELLS.length ? bell : 0;
    }

    /**
     * Called every server tick: rings the bell while on and shows the lamp for this tick.
     */
    void tick(World world, BlockPos pos, BlockState state, boolean on, int bellIndex) {
        final Bell bell = BELLS[clampBell(bellIndex)];
        final int lamp;
        if (on) {
            if (ticksOn % bell.intervalTicks == 0) {
                play(world, pos, bell);
            }
            lamp = (ticksOn / FLASH_INTERVAL_TICKS) % 2 == 0 ? 1 : 2;
            ticksOn++;
        } else {
            if (ticksOn > 0 && bell.loop) {
                stop(world, pos, bell);
            }
            ticksOn = 0;
            lamp = LAMP_OFF;
        }

        if (state.contains(LAMP_PROPERTY) && state.get(LAMP_PROPERTY) != lamp) {
            // Flag 3 syncs the state to clients, which then draw the matching lamp model.
            world.setBlockState(pos, state.with(LAMP_PROPERTY, lamp), 3);
        }
    }

    /**
     * Switches from one bell to another: stops the old one if it is a loop, then rings the new one once (also as a
     * preview while the crossing is off) and restarts the ringing rhythm.
     */
    void changeBell(World world, BlockPos pos, int oldBell, int newBell) {
        final Bell old = BELLS[clampBell(oldBell)];
        if (old.loop) {
            stop(world, pos, old);
        }
        play(world, pos, BELLS[clampBell(newBell)]);
        if (ticksOn > 0) {
            ticksOn = 1;
        }
    }

    static BlockState withLampOff(BlockState state) {
        return state.with(LAMP_PROPERTY, LAMP_OFF);
    }

    private static void play(World world, BlockPos pos, Bell bell) {
        world.data.playSound((net.minecraft.entity.player.PlayerEntity) null, pos.data, bell.sound, net.minecraft.sound.SoundCategory.BLOCKS, BELL_VOLUME, BELL_PITCH);
    }

    private static void stop(World world, BlockPos pos, Bell bell) {
        if (!(world.data instanceof net.minecraft.server.world.ServerWorld)) {
            return;
        }
        final net.minecraft.server.world.ServerWorld serverWorld = (net.minecraft.server.world.ServerWorld) world.data;
        final net.minecraft.network.packet.s2c.play.StopSoundS2CPacket packet = new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(bell.id, net.minecraft.sound.SoundCategory.BLOCKS);
        for (final net.minecraft.server.network.ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= STOP_SOUND_RANGE * STOP_SOUND_RANGE) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    private static final class Bell {

        private final net.minecraft.util.Identifier id;
        private final net.minecraft.sound.SoundEvent sound;
        private final int intervalTicks;
        private final boolean loop;

        private Bell(String name, int intervalTicks, boolean loop) {
            id = new net.minecraft.util.Identifier("jme", name);
            sound = org.justnoone.jme.compat.McCompat.createSoundEvent(id);
            this.intervalTicks = intervalTicks;
            this.loop = loop;
        }
    }
}
