package org.justnoone.jme.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.justnoone.jme.entity.AxleCounterEntity;

/**
 * Places an axle counter on the MTR rail the player is aiming at (it does not place a block). Works whether the aim
 * hits a block (eg the ground or a rail node under the rail) or nothing.
 */
public class AxleCounterItem extends Item {

    private static final double REACH = 6;

    public AxleCounterItem() {
        super(new Settings());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        final ItemStack stack = user.getStackInHand(hand);
        place(world, user, stack);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        final PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        place(context.getWorld(), player, context.getStack());
        return ActionResult.success(context.getWorld().isClient());
    }

    private static void place(World world, PlayerEntity player, ItemStack stack) {
        if (world instanceof ServerWorld && AxleCounterEntity.tryPlace((ServerWorld) world, player, REACH) && !player.isCreative()) {
            stack.decrement(1);
        }
    }
}
