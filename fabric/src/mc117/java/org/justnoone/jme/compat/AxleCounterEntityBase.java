package org.justnoone.jme.compat;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.justnoone.jme.Jme;

/**
 * The version-specific part of the axle counter entity: its spawn packet (this copy: 1.17 and 1.18). Before 1.19 the
 * vanilla spawn packet only spawns vanilla entity types on the client, so the counter sends its own, which
 * {@link McClientCompat#registerAxleCounter()} handles.
 */
public abstract class AxleCounterEntityBase extends Entity {

    public static final Identifier SPAWN_PACKET_ID = new Identifier(Jme.MOD_ID, "axle_counter_spawn");

    protected AxleCounterEntityBase(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public Packet<?> createSpawnPacket() {
        final PacketByteBuf packetByteBuf = PacketByteBufs.create();
        packetByteBuf.writeVarInt(getId());
        packetByteBuf.writeUuid(getUuid());
        packetByteBuf.writeDouble(getX());
        packetByteBuf.writeDouble(getY());
        packetByteBuf.writeDouble(getZ());
        packetByteBuf.writeFloat(getYaw());
        packetByteBuf.writeFloat(getPitch());
        return ServerPlayNetworking.createS2CPacket(SPAWN_PACKET_ID, packetByteBuf);
    }

    /**
     * Players can hit (break) and click (link) the counter.
     */
    @Override
    public boolean collides() {
        return true;
    }
}
