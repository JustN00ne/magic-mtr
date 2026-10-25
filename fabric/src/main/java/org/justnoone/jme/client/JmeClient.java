package org.justnoone.jme.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.justnoone.jme.Jme;
import org.justnoone.jme.block.CrossingLinkerHandler;
import org.justnoone.jme.client.render.ClientCrossingLinks;
import org.justnoone.jme.client.render.CrossingLinkRenderer;
import org.justnoone.jme.client.render.ObjBlockModel;
import org.justnoone.jme.compat.McClientCompat;
import org.justnoone.jme.item.CrossingLinkMode;
import org.justnoone.jme.client.render.RenderCrossing;
import org.justnoone.jme.client.render.RenderObjBlock;
import org.justnoone.jme.item.ModItems;
import org.mtr.mapping.registry.Registry;
import org.mtr.mapping.registry.RegistryClient;
import org.mtr.mod.item.ItemBlockClickingBase;
import net.minecraft.client.MinecraftClient;
import org.justnoone.jme.block.BlockAxleCounter;
import org.justnoone.jme.block.ModBlocks;
import org.justnoone.jme.config.RouteTypeOverrideConfig;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.network.MagicRouteStateSync;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.CronTimetableRegistry;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.rail.MagicRailConstants;

public class JmeClient {
    public static void initClient() {
        MagicRailClientHooks.register();
        MagicClientCommands.register();

        if (ModBlocks.AXLE_COUNTER_BLOCK != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.AXLE_COUNTER_BLOCK.get().data, RenderLayer.getCutout());
        }
        if (ModBlocks.CROSSING_CONTROLLER_BLOCK != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CROSSING_CONTROLLER_BLOCK.get().data, RenderLayer.getCutout());
        }
        if (ModBlocks.CROSSING_LIGHTS_BLOCK != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CROSSING_LIGHTS_BLOCK.get().data, RenderLayer.getCutout());
        }
        if (ModBlocks.TRAIN_DETECTOR != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRAIN_DETECTOR.get().data, RenderLayer.getCutout());
        }
        if (ModBlocks.WMTA_ON_BLOCK != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WMTA_ON_BLOCK.get().data, RenderLayer.getCutout());
        }
        if (ModBlocks.WMTA_ON_SLAB_BLOCK != null) {
            BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WMTA_ON_SLAB_BLOCK.get().data, RenderLayer.getCutout());
        }

        registerRouteStateSync();
        registerObjBlockRenderers();
    }

    private static void registerObjBlockRenderers() {
        final RegistryClient registryClient = new RegistryClient(new Registry());
        // The whole level crossing is drawn from its Blockbench model; the placed blocks' JSON models are empty.
        if (ModBlocks.CROSSING_LIGHTS_BLOCK_ENTITY != null) {
            registryClient.registerBlockEntityRenderer(ModBlocks.CROSSING_LIGHTS_BLOCK_ENTITY, RenderCrossing::new);
        }
        if (ModBlocks.CROSSING_CONTROLLER_BLOCK_ENTITY != null) {
            registryClient.registerBlockEntityRenderer(ModBlocks.CROSSING_CONTROLLER_BLOCK_ENTITY, RenderCrossing::new);
        }
        if (ModBlocks.AXLE_COUNTER_BLOCK_ENTITY != null) {
            registryClient.registerBlockEntityRenderer(ModBlocks.AXLE_COUNTER_BLOCK_ENTITY, argument -> new RenderObjBlock<>(argument, ObjBlockModel.AXLE_COUNTER, 1, BlockAxleCounter.FACING, false));
        }
        registerConnectorIconPredicates(registryClient);
        registerCrossingLinking(registryClient);
        registryClient.init();

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier("jme", "obj_block_models");
            }

            @Override
            public void reload(ResourceManager manager) {
                ObjBlockModel.invalidateAll();
            }
        });
    }

    /**
     * Switches the MAGIC connector icons to their "selected" models (the {@code mtr:selected} overrides in the item
     * models) while a first node is stored on the stack, like MTR does for its own rail connectors. Without this the
     * overrides never matched and the selected icons were never shown.
     */
    private static void registerConnectorIconPredicates(RegistryClient registryClient) {
        final org.mtr.mapping.holder.Identifier selectedPredicate = new org.mtr.mapping.holder.Identifier("mtr", "selected");
        final RegistryClient.ModelPredicateProvider hasFirstNode = (stack, clientWorld, entity) -> stack.getOrCreateTag().contains(ItemBlockClickingBase.TAG_POS) ? 1 : 0;
        if (ModItems.MAGIC_RAIL_CONNECTOR_ENTRY != null) {
            registryClient.registerItemModelPredicate(ModItems.MAGIC_RAIL_CONNECTOR_ENTRY, selectedPredicate, hasFirstNode);
        }
        if (ModItems.MAGIC_RAIL_CONNECTOR_ONE_WAY_ENTRY != null) {
            registryClient.registerItemModelPredicate(ModItems.MAGIC_RAIL_CONNECTOR_ONE_WAY_ENTRY, selectedPredicate, hasFirstNode);
        }
    }

    /**
     * Axle counters on rails, the Crossing Linker's mode icons, its selection (sent by the server) and the linking /
     * MTR brush connection rendering.
     */
    private static void registerCrossingLinking(RegistryClient registryClient) {
        McClientCompat.registerAxleCounter();
        if (ModItems.CROSSING_LINKER_ENTRY != null) {
            registryClient.registerItemModelPredicate(ModItems.CROSSING_LINKER_ENTRY, new org.mtr.mapping.holder.Identifier(Jme.MOD_ID, "link_mode"), // Predicate values are clamped to 0..1, so the modes are 0, 1/3, 2/3 and 1 (thresholds in crossing_linker.json).
                    (stack, clientWorld, entity) -> CrossingLinkMode.get(stack.data).ordinal() / 3F);
        }
        MagicNetworkingCompat.registerClientReceiver(CrossingLinkerHandler.SESSION_PACKET_ID, (client, packetByteBuf) -> {
            final net.minecraft.util.math.BlockPos pos = packetByteBuf.readBoolean() ? net.minecraft.util.math.BlockPos.fromLong(packetByteBuf.readLong()) : null;
            MinecraftClient.getInstance().execute(() -> ClientCrossingLinks.setSelectedController(pos));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientCrossingLinks::clear));
        CrossingLinkRenderer.register();
    }

    private static void registerRouteStateSync() {
        MagicNetworkingCompat.registerClientReceiver(MagicRailConstants.SYNC_ROUTE_STATE_PACKET_ID, (client, packetByteBuf) -> {
            final Runnable apply = MagicRouteStateSync.readSnapshot(packetByteBuf);
            final MinecraftClient minecraftClient = MinecraftClient.getInstance();
            minecraftClient.execute(() -> {
                // In singleplayer the integrated server shares these registries and owns the files; on a remote
                // server the snapshot must not overwrite this machine's own config files.
                final boolean remoteServer = !minecraftClient.isIntegratedServerRunning();
                RouteTypeOverrideConfig.setPersistenceEnabled(!remoteServer);
                AlternativePlatformRegistry.setPersistenceEnabled(!remoteServer);
                DepotCancellationRegistry.setPersistenceEnabled(!remoteServer);
                CronTimetableRegistry.setPersistenceEnabled(!remoteServer);
                apply.run();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            RouteTypeOverrideConfig.setPersistenceEnabled(true);
            AlternativePlatformRegistry.setPersistenceEnabled(true);
            DepotCancellationRegistry.setPersistenceEnabled(true);
            CronTimetableRegistry.setPersistenceEnabled(true);
            RouteTypeOverrideConfig.reload();
            AlternativePlatformRegistry.reloadFromDisk();
            DepotCancellationRegistry.reloadFromDisk();
            CronTimetableRegistry.reloadFromDisk();
        }));
    }
}
