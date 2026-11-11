package org.justnoone.jme.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CuttingBuilderItem extends Item {

    public CuttingBuilderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking() && !world.isClient) {
            cycleWidth(stack, user);
            return TypedActionResult.success(stack);
        }
        return super.use(world, user, hand);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        ItemStack stack = context.getStack();

        if (world.isClient) return ActionResult.SUCCESS;

        if (player.isSneaking()) {
            cycleWidth(stack, player);
            return ActionResult.SUCCESS;
        }

        int width = getWidth(stack);
        Direction facing = player.getHorizontalFacing();
        // Perpendicular direction to extend width. 
        // If facing North (Z-), perpendicular is East (X+).
        Direction perpendicular = facing.rotateYClockwise();

        int range = (width - 1) / 2;
        boolean error = false;

        // Validation Pass
        // Check center column first, then outwards? Or just check all columns.
        // We check if ANY column exceeds height limit.
        for (int i = -range; i <= range; i++) {
            BlockPos colStart = pos.offset(perpendicular, i);
            BlockPos checkPos = colStart.down();
            int depth = 0;
            boolean hitGround = false;

            while (depth <= 16) {
                if (checkPos.getY() < world.getBottomY()) break;

                BlockState state = world.getBlockState(checkPos);
                // "Solid block which is ground (not leaves wood or any of those)"
                // We use tag checks for LEAVES and LOGS.
                // We also check isSolidBlock.
                if (state.isSolidBlock(world, checkPos) 
                        && !state.isIn(BlockTags.LEAVES) 
                        && !state.isIn(BlockTags.LOGS)
                        && !state.isAir()) {
                    hitGround = true;
                    break;
                }
                depth++;
                checkPos = checkPos.down();
            }

            if (depth > 16 || !hitGround) {
                error = true;
                break;
            }
        }

        if (error) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Height limit exceeded!").formatted(Formatting.RED)));
            }
            return ActionResult.FAIL;
        }

        // Build Pass
        for (int i = -range; i <= range; i++) {
            BlockPos colStart = pos.offset(perpendicular, i);
            BlockPos buildPos = colStart.down();
            int depth = 0;

            while (depth <= 16) {
                BlockState state = world.getBlockState(buildPos);
                 if (state.isSolidBlock(world, buildPos) 
                        && !state.isIn(BlockTags.LEAVES) 
                        && !state.isIn(BlockTags.LOGS)
                        && !state.isAir()) {
                    break; // Hit ground
                }
                
                world.setBlockState(buildPos, Blocks.STONE.getDefaultState());
                depth++;
                buildPos = buildPos.down();
            }
        }

        return ActionResult.SUCCESS;
    }

    private void cycleWidth(ItemStack stack, PlayerEntity player) {
        NbtCompound nbt = stack.getOrCreateNbt();
        int current = nbt.getInt("Width");
        if (current < 3) current = 3;
        
        int next = current + 2;
        if (next > 11) next = 3;
        
        nbt.putInt("Width", next);
        player.sendMessage(Text.literal("Set width to: " + next), true);
    }

    private int getWidth(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        int w = nbt.getInt("Width");
        return w < 3 ? 3 : w;
    }
}
