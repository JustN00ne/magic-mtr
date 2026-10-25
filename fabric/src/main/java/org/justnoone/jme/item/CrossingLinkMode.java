package org.justnoone.jme.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.justnoone.jme.compat.McCompat;

/**
 * The Crossing Linker's modes, stored on the item (so its icon shows the mode). Sneak + right-click cycles them.
 */
public enum CrossingLinkMode {
    INACTIVE("Inactive", 0xAAAAAA),
    START_NODE("Start Node", 0x33DD33),
    END_NODE("End Node", 0xEE3333),
    OBJECT("Object Linking", 0x3388FF);

    private static final String NBT_MODE = "jme_link_mode";

    public final String displayName;
    /**
     * RGB colour of the outline and lines shown for this mode.
     */
    public final int color;

    CrossingLinkMode(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public CrossingLinkMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean linksCounters() {
        return this == START_NODE || this == END_NODE;
    }

    public static CrossingLinkMode get(ItemStack stack) {
        final NbtCompound nbt = McCompat.getNbt(stack);
        if (nbt == null) {
            return INACTIVE;
        }
        final int index = nbt.getInt(NBT_MODE);
        return index >= 0 && index < values().length ? values()[index] : INACTIVE;
    }

    public static void set(ItemStack stack, CrossingLinkMode mode) {
        McCompat.getOrCreateNbt(stack).putInt(NBT_MODE, mode.ordinal());
    }
}
