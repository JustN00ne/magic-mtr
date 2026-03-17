package org.justnoone.jme.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.text.Text;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mod.client.MinecraftClientData;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class MagicClientCommands {

    private static final String CLIENT_COMMAND_CALLBACK = "net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback";

    private MagicClientCommands() {
    }

    public static void register() {
        try {
            final Class<?> callbackClass = Class.forName(CLIENT_COMMAND_CALLBACK);
            final Object event = callbackClass.getField("EVENT").get(null);
            final Method registerMethod = event.getClass().getMethod("register", callbackClass);

            final Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                if (!"register".equals(method.getName()) || args == null || args.length < 1 || !(args[0] instanceof CommandDispatcher)) {
                    return null;
                }

                @SuppressWarnings("unchecked") final CommandDispatcher<Object> dispatcher = (CommandDispatcher<Object>) args[0];
                dispatcher.register(jme$buildRootCommand());
                return null;
            });

            registerMethod.invoke(event, callback);
        } catch (ClassNotFoundException ignored) {
            // Client command API v2 is not available on this Minecraft/Fabric API version.
        } catch (Throwable throwable) {
            System.err.println("[MAGIC] Failed to register client commands: " + throwable.getMessage());
        }
    }

    private static LiteralArgumentBuilder<Object> jme$buildRootCommand() {
        final LiteralArgumentBuilder<Object> root = LiteralArgumentBuilder.literal("magic");
        final LiteralArgumentBuilder<Object> settings = LiteralArgumentBuilder.literal("settings");

        settings.then(LiteralArgumentBuilder.literal("resources")
                .then(LiteralArgumentBuilder.literal("reload")
                        .executes(context -> jme$reloadResources(context.getSource()))));

        settings.then(LiteralArgumentBuilder.literal("functions")
                .then(LiteralArgumentBuilder.literal("reloadtilt")
                        .executes(context -> jme$reloadTilt(context.getSource())))
                .then(LiteralArgumentBuilder.literal("reloadstate")
                        .executes(context -> jme$reloadState(context.getSource()))));

        settings.then(LiteralArgumentBuilder.literal("rails")
                .then(LiteralArgumentBuilder.literal("syncPositionsOnDash")
                        .executes(context -> jme$syncDashboardRailPositions(context.getSource())))
                .then(LiteralArgumentBuilder.literal("syncpositionsondash")
                        .executes(context -> jme$syncDashboardRailPositions(context.getSource()))));

        root.then(settings);
        return root;
    }

    private static int jme$reloadResources(Object source) {
        try {
            MagicReloadHooks.reloadResourcesAndState();
            jme$sendFeedback(source, "[MAGIC] Reloading resources and MAGIC state.");
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            jme$sendFeedback(source, "[MAGIC] Failed to reload resources: " + e.getMessage());
            return 0;
        }
    }

    private static int jme$reloadTilt(Object source) {
        MagicReloadHooks.reloadTiltState();
        jme$sendFeedback(source, "[MAGIC] Reloaded camera tilt settings.");
        return Command.SINGLE_SUCCESS;
    }

    private static int jme$reloadState(Object source) {
        MagicReloadHooks.reloadState();
        jme$sendFeedback(source, "[MAGIC] Reloaded MAGIC state from disk.");
        return Command.SINGLE_SUCCESS;
    }

    private static int jme$syncDashboardRailPositions(Object source) {
        final MinecraftClientData dashboardData = MinecraftClientData.getDashboardInstance();
        final MinecraftClientData liveData = MinecraftClientData.getInstance();
        if (dashboardData == null && liveData == null) {
            jme$sendFeedback(source, "[MAGIC] Dashboard data is unavailable.");
            return 0;
        }

        if (liveData != null) {
            liveData.sync();
        }

        if (dashboardData == null) {
            jme$sendFeedback(source, "[MAGIC] Synced live rail positions for " + liveData.rails.size() + " rails and " + liveData.positionsToRail.size() + " graph nodes.");
            return Command.SINGLE_SUCCESS;
        }

        if (liveData != null && (!liveData.rails.isEmpty() || !liveData.positionsToRail.isEmpty())) {
            jme$copyClientData(liveData, dashboardData);
        }
        dashboardData.sync();
        final int liveRailCount = liveData == null ? 0 : liveData.rails.size();
        final int dashboardRailCount = dashboardData.rails.size();
        final int liveGraphNodeCount = liveData == null ? 0 : liveData.positionsToRail.size();
        final int dashboardGraphNodeCount = dashboardData.positionsToRail.size();
        jme$sendFeedback(
                source,
                "[MAGIC] Synced dashboard rail positions for " + dashboardRailCount +
                        " rails and " + dashboardGraphNodeCount +
                        " graph nodes (live: " + liveRailCount +
                        " rails, " + liveGraphNodeCount + " nodes)."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void jme$copyClientData(MinecraftClientData from, MinecraftClientData to) {
        if (from == null || to == null) {
            return;
        }

        // Merge, don't replace: dashboard data may already contain additional rails
        // that are not present in live cache at this exact tick.
        to.stations.addAll(from.stations);
        to.platforms.addAll(from.platforms);
        to.sidings.addAll(from.sidings);
        to.routes.addAll(from.routes);
        to.depots.addAll(from.depots);
        to.lifts.addAll(from.lifts);
        to.rails.addAll(from.rails);
        to.simplifiedRoutes.addAll(from.simplifiedRoutes);

        to.stationIdMap.putAll(from.stationIdMap);
        to.platformIdMap.putAll(from.platformIdMap);
        to.sidingIdMap.putAll(from.sidingIdMap);
        to.routeIdMap.putAll(from.routeIdMap);
        to.depotIdMap.putAll(from.depotIdMap);
        to.liftIdMap.putAll(from.liftIdMap);
        to.railIdMap.putAll(from.railIdMap);
        to.runwaysInbound.putAll(from.runwaysInbound);
        to.runwaysOutbound.addAll(from.runwaysOutbound);
        to.platformIdToPosition.putAll(from.platformIdToPosition);

        from.positionsToRail.forEach((position, edges) -> {
            final Object2ObjectOpenHashMap<Position, Rail> targetEdges = to.positionsToRail.computeIfAbsent(position, ignored -> new Object2ObjectOpenHashMap<>());
            targetEdges.putAll(edges);
        });

        to.simplifiedRouteIdMap.putAll(from.simplifiedRouteIdMap);
        to.vehicles.addAll(from.vehicles);
        to.vehicleIdToPersistentVehicleData.putAll(from.vehicleIdToPersistentVehicleData);
        to.liftWrapperList.putAll(from.liftWrapperList);
        to.railWrapperList.putAll(from.railWrapperList);
        to.railIdToPreBlockedSignalColors.putAll(from.railIdToPreBlockedSignalColors);
        to.railIdToCurrentlyBlockedSignalColors.putAll(from.railIdToCurrentlyBlockedSignalColors);
        to.blockedRailIds.addAll(from.blockedRailIds);
        to.railActions.addAll(from.railActions);
    }

    private static void jme$sendFeedback(Object source, String message) {
        final Text text = Text.of(message);
        if (source != null) {
            try {
                final Method sendFeedbackMethod = source.getClass().getMethod("sendFeedback", Text.class);
                sendFeedbackMethod.invoke(source, text);
                return;
            } catch (Exception ignored) {
            }
        }
        System.out.println(message);
    }
}
