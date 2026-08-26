package org.justnoone.jme.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.justnoone.jme.config.MagicConfigReloader;
import org.justnoone.jme.rail.WaypointNodeCreator;
import org.justnoone.jme.rail.WaypointRegistry;
import org.mtr.core.data.Data;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.mapping.holder.World;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

public final class MagicServerCommands {

    private static final String COMMAND_CALLBACK_V2 = "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback";
    private static final String COMMAND_CALLBACK_V1 = "net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback";

    private MagicServerCommands() {
    }

    public static void register() {
        if (registerWithCallback(COMMAND_CALLBACK_V2)) {
            return;
        }
        registerWithCallback(COMMAND_CALLBACK_V1);
    }

    private static boolean registerWithCallback(String callbackClassName) {
        try {
            final Class<?> callbackClass = Class.forName(callbackClassName);
            final Object event = callbackClass.getField("EVENT").get(null);
            final Method registerMethod = findEventRegisterMethod(event, callbackClass);
            if (registerMethod == null) {
                throw new NoSuchMethodException(event.getClass().getName() + ".register(...)");
            }

            final Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                if (!"register".equals(method.getName()) || args == null || args.length < 1 || !(args[0] instanceof CommandDispatcher)) {
                    return null;
                }

                @SuppressWarnings("unchecked") final CommandDispatcher<ServerCommandSource> dispatcher = (CommandDispatcher<ServerCommandSource>) args[0];
                dispatcher.register(buildRootCommand());
                return null;
            });

            registerMethod.invoke(event, callback);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable throwable) {
            System.err.println("[MAGIC] Failed to register server commands: " + throwable.getMessage());
            return false;
        }
    }

    private static Method findEventRegisterMethod(Object event, Class<?> callbackClass) {
        if (event == null || callbackClass == null) {
            return null;
        }

        for (final Method method : event.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(callbackClass)) {
                return method;
            }
        }

        for (final Method method : event.getClass().getDeclaredMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(callbackClass)) {
                method.setAccessible(true);
                return method;
            }
        }

        return null;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRootCommand() {
        return CommandManager.literal("magic")
                .then(CommandManager.literal("waypoint")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> addWaypoint(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(context -> removeWaypoint(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("list")
                                .executes(context -> listWaypoints(context.getSource())))
                )
                .then(CommandManager.literal("debug")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("config")
                                .then(CommandManager.literal("reload")
                                        .executes(context -> reloadConfig(context.getSource())))
                                .then(CommandManager.literal("status")
                                        .executes(context -> status(context.getSource()))))
                        .then(CommandManager.literal("reload")
                                .then(CommandManager.literal("all")
                                        .executes(context -> reloadAll(context.getSource())))));
    }

    private static int reloadConfig(ServerCommandSource source) {
        final MagicConfigReloader.ReloadResult result = MagicConfigReloader.reloadMainConfigFromDisk();
        sendFeedback(source, "[MAGIC] Reloaded magic.json: " + result.toDebugString());
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadAll(ServerCommandSource source) {
        final MagicConfigReloader.ReloadResult result = MagicConfigReloader.reloadAllFromDisk();
        sendFeedback(source, "[MAGIC] Reloaded all MAGIC configs: " + result.toDebugString());
        return Command.SINGLE_SUCCESS;
    }

    private static int addWaypoint(ServerCommandSource source, String name) {
        final net.minecraft.server.world.ServerWorld serverWorld = source.getWorld();
        final World wrappedWorld = new World(serverWorld);
        final Data data = WaypointNodeCreator.resolveSimulator(wrappedWorld);
        if (data == null) {
            sendFeedback(source, "[MAGIC] Could not resolve MTR data for this world.");
            return 0;
        }

        final int px = (int) Math.floor(source.getPosition().getX());
        final int py = (int) Math.floor(source.getPosition().getY());
        final int pz = (int) Math.floor(source.getPosition().getZ());
        final Position pos = new Position(px, py, pz);
        final Platform platform = WaypointNodeCreator.createWaypointPlatform(data, name, pos);
        if (platform != null) {
            WaypointRegistry.register(platform.getId(), name, 0x5555FF, pos);
            sendFeedback(source, "[MAGIC] Waypoint '" + name + "' created (platform id=" + platform.getId() + ") at " + px + ", " + py + ", " + pz + " [dwellTime=0]");
            return Command.SINGLE_SUCCESS;
        }
        sendFeedback(source, "[MAGIC] Failed to create waypoint '" + name + "'.");
        return 0;
    }

    private static int removeWaypoint(ServerCommandSource source, String name) {
        final net.minecraft.server.world.ServerWorld serverWorld = source.getWorld();
        final World wrappedWorld = new World(serverWorld);
        final Data data = WaypointNodeCreator.resolveSimulator(wrappedWorld);
        if (data == null) {
            sendFeedback(source, "[MAGIC] Could not resolve MTR data for this world.");
            return 0;
        }

        final WaypointRegistry.Waypoint wpEntry = WaypointRegistry.getAllAsList().stream()
                .filter(wp -> wp.name.equals(name))
                .findFirst().orElse(null);
        if (wpEntry != null) {
            WaypointNodeCreator.removeWaypointPlatform(data, wpEntry.id);
            WaypointRegistry.unregister(wpEntry.id);
            sendFeedback(source, "[MAGIC] Waypoint '" + name + "' removed.");
            return Command.SINGLE_SUCCESS;
        }

        for (final Platform p : data.platformIdMap.values()) {
            if (p != null && name.equals(p.getName()) && p.getDwellTime() == 0) {
                WaypointNodeCreator.removeWaypointPlatform(data, p.getId());
                sendFeedback(source, "[MAGIC] Waypoint '" + name + "' removed (platform id=" + p.getId() + ").");
                return Command.SINGLE_SUCCESS;
            }
        }

        sendFeedback(source, "[MAGIC] Waypoint not found: " + name);
        return 0;
    }

    private static int listWaypoints(ServerCommandSource source) {
        final net.minecraft.server.world.ServerWorld serverWorld = source.getWorld();
        final World wrappedWorld = new World(serverWorld);
        final Data data = WaypointNodeCreator.resolveSimulator(wrappedWorld);

        int count = 0;
        if (data != null) {
            for (final Platform p : data.platformIdMap.values()) {
                if (p != null && p.getDwellTime() == 0) {
                    final Position pos = WaypointNodeCreator.getPlatformPosition(p);
                    final String posStr = pos != null
                            ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                            : "unknown";
                    sendFeedback(source, "  - " + p.getName() + " (platform id=" + p.getId() + ") @ " + posStr);
                    count++;
                }
            }
        }
        if (count == 0) {
            sendFeedback(source, "[MAGIC] No waypoint platforms found.");
        } else {
            sendFeedback(source, "[MAGIC] Waypoint platforms (" + count + "):");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int status(ServerCommandSource source) {
        final MagicConfigReloader.ReloadResult result = MagicConfigReloader.current();
        sendFeedback(source, "[MAGIC] Current config: " + result.toDebugString());
        return Command.SINGLE_SUCCESS;
    }

    private static void sendFeedback(ServerCommandSource source, String message) {
        final Text text = Text.of(message);
        try {
            final Method sendFeedbackTextMethod = source.getClass().getMethod("sendFeedback", Text.class, boolean.class);
            sendFeedbackTextMethod.invoke(source, text, false);
            return;
        } catch (Exception ignored) {
        }

        try {
            final Method sendFeedbackSupplierMethod = source.getClass().getMethod("sendFeedback", Supplier.class, boolean.class);
            sendFeedbackSupplierMethod.invoke(source, (Supplier<Text>) () -> text, false);
        } catch (Exception ignored) {
        }
    }
}
