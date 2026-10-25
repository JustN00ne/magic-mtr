package org.justnoone.jme.compat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;

/**
 * The version-specific part of the axle counter entity: its spawn packet (this copy: 1.19 to 1.19.2, the vanilla one).
 */
public abstract class AxleCounterEntityBase extends Entity {

    protected AxleCounterEntityBase(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public Packet<?> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    /**
     * Players can hit (break) and click (link) the counter.
     */
    @Override
    public boolean canHit() {
        return true;
    }
}
