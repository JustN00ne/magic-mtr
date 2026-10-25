package org.justnoone.jme.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.justnoone.jme.block.CrossingLinkerHandler;
import org.mtr.mapping.mapper.TextHelper;

/**
 * The Crossing Linker. Its mode ({@link CrossingLinkMode}) is stored on the item and cycled with sneak + right-click;
 * the linking itself (select a crossing controller, then click an axle counter or crossing object) is handled by
 * {@link CrossingLinkerHandler}.
 */
public class CrossingLinkerItem extends Item {

    public CrossingLinkerItem() {
        super(new Settings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        final ItemStack stack = user.getStackInHand(hand);
        if (hand == Hand.MAIN_HAND && user.isSneaking()) {
            cycleMode(world, user, stack);
            return TypedActionResult.success(stack, world.isClient());
        }
        if (!world.isClient() && user instanceof ServerPlayerEntity) {
            CrossingLinkerHandler.onUseInAir((ServerPlayerEntity) user, stack);
        }
        return TypedActionResult.pass(stack);
    }

    @Override
    public Text getName(ItemStack stack) {
        return TextHelper.translatable(getTranslationKey(stack)).data.append(TextHelper.literal(" (" + CrossingLinkMode.get(stack).displayName + ")").data);
    }

    /**
     * Switches to the next mode. Runs on both sides, so the icon changes at once on the client; the server's copy is
     * authoritative. Never changes a connection.
     */
    public static void cycleMode(World world, PlayerEntity player, ItemStack stack) {
        final CrossingLinkMode mode = CrossingLinkMode.get(stack).next();
        CrossingLinkMode.set(stack, mode);
        if (!world.isClient() && player instanceof ServerPlayerEntity) {
            CrossingLinkerHandler.onModeChanged((ServerPlayerEntity) player, mode);
        }
    }
}
