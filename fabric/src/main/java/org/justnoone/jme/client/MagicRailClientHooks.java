package org.justnoone.jme.client;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.justnoone.jme.client.screen.MagicRailConnectorScreen;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.mapping.holder.Hand;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.Screen;

public final class MagicRailClientHooks {

    private MagicRailClientHooks() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            final net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (!player.isSneaking() || !MagicRailConstants.isUniversalConnector(new ItemStack(stack))) {
                return TypedActionResult.pass(stack);
            }

            if (world.isClient) {
                org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new MagicRailConnectorScreen(Hand.convert(hand))));
            }
            return TypedActionResult.success(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            final net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (!player.isSneaking() || !MagicRailConstants.isUniversalConnector(new ItemStack(stack))) {
                return ActionResult.PASS;
            }

            if (world.isClient) {
                org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new MagicRailConnectorScreen(Hand.convert(hand))));
            }
            return ActionResult.SUCCESS;
        });
    }
}
