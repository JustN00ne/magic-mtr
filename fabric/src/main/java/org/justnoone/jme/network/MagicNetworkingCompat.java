package org.justnoone.jme.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.mtr.mapping.holder.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public final class MagicNetworkingCompat {

    public interface ServerPacketReceiver {
        void receive(Object server, ServerPlayerEntity player, PacketByteBuf packetByteBuf);
    }

    public interface ClientPacketReceiver {
        void receive(Object client, PacketByteBuf packetByteBuf);
    }

    private static final Logger LOGGER = Logger.getLogger("MAGIC");
    private static volatile boolean warnedMissingClientSend;
    private static volatile boolean warnedMissingServerRegister;
    private static volatile boolean warnedMissingServerSend;
    private static volatile boolean warnedMissingClientRegister;
    private static volatile Method cachedClientSendMethod;
    private static volatile Method cachedServerRegisterMethod;
    private static volatile Method cachedServerSendMethod;
    private static volatile Method cachedClientRegisterMethod;

    private MagicNetworkingCompat() {
    }

    public static void registerServerReceiver(Identifier packetId, ServerPacketReceiver receiver) {
        if (packetId == null || receiver == null) {
            return;
        }

        final Method registerMethod = getServerRegisterMethod();
        if (registerMethod == null) {
            warnMissingServerRegister();
            return;
        }

        final Class<?> handlerType = registerMethod.getParameterTypes()[1];
        final Object receiverProxy = Proxy.newProxyInstance(
                MagicNetworkingCompat.class.getClassLoader(),
                new Class<?>[]{handlerType},
                (proxy, method, args) -> {
                    if (args == null || args.length < 4) {
                        return null;
                    }

                    final Object server = args[0];
                    final Object player = args[1];
                    final Object packet = args[3];
                    if (player instanceof ServerPlayerEntity && packet instanceof PacketByteBuf) {
                        receiver.receive(server, (ServerPlayerEntity) player, (PacketByteBuf) packet);
                    }
                    return null;
                }
        );

        try {
            registerMethod.invoke(null, MagicPacketIdHelper.toMinecraftId(packetId), receiverProxy);
        } catch (Exception e) {
            LOGGER.warning("Failed to register server receiver for " + packetId.data + ": " + e.getClass().getSimpleName());
        }
    }

    public static void sendToServer(Identifier packetId, PacketByteBuf packetByteBuf) {
        if (packetId == null || packetByteBuf == null) {
            return;
        }

        final Method sendMethod = getClientSendMethod();
        if (sendMethod == null) {
            warnMissingClientSend();
            return;
        }

        try {
            sendMethod.invoke(null, MagicPacketIdHelper.toMinecraftId(packetId), packetByteBuf);
        } catch (Exception e) {
            LOGGER.warning("Failed to send client packet " + packetId.data + ": " + e.getClass().getSimpleName());
        }
    }

    public static void sendToPlayer(ServerPlayerEntity player, Identifier packetId, PacketByteBuf packetByteBuf) {
        if (player == null || packetId == null || packetByteBuf == null) {
            return;
        }

        final Method sendMethod = getServerSendMethod();
        if (sendMethod == null) {
            if (!warnedMissingServerSend) {
                warnedMissingServerSend = true;
                LOGGER.warning("Server packet send API is not compatible with this version; skipping packet send");
            }
            return;
        }

        try {
            sendMethod.invoke(null, player, MagicPacketIdHelper.toMinecraftId(packetId), packetByteBuf);
        } catch (Exception e) {
            LOGGER.warning("Failed to send server packet " + packetId.data + ": " + e.getClass().getSimpleName());
        }
    }

    public static void registerClientReceiver(Identifier packetId, ClientPacketReceiver receiver) {
        if (packetId == null || receiver == null) {
            return;
        }

        final Method registerMethod = getClientRegisterMethod();
        if (registerMethod == null) {
            if (!warnedMissingClientRegister) {
                warnedMissingClientRegister = true;
                LOGGER.warning("Client packet register API is not compatible with this version; skipping packet registration");
            }
            return;
        }

        final Class<?> handlerType = registerMethod.getParameterTypes()[1];
        final Object receiverProxy = Proxy.newProxyInstance(
                MagicNetworkingCompat.class.getClassLoader(),
                new Class<?>[]{handlerType},
                (proxy, method, args) -> {
                    // PlayChannelHandler#receive(MinecraftClient, ClientPlayNetworkHandler, PacketByteBuf, PacketSender)
                    if (method.getDeclaringClass() == Object.class || args == null || args.length < 3) {
                        return null;
                    }
                    if (args[2] instanceof PacketByteBuf) {
                        receiver.receive(args[0], (PacketByteBuf) args[2]);
                    }
                    return null;
                }
        );

        try {
            registerMethod.invoke(null, MagicPacketIdHelper.toMinecraftId(packetId), receiverProxy);
        } catch (Exception e) {
            LOGGER.warning("Failed to register client receiver for " + packetId.data + ": " + e.getClass().getSimpleName());
        }
    }

    public static void executeOnServer(Object server, Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (server == null) {
            runnable.run();
            return;
        }

        try {
            final Method executeMethod = server.getClass().getMethod("execute", Runnable.class);
            executeMethod.invoke(server, runnable);
        } catch (Exception ignored) {
            runnable.run();
        }
    }

    private static Method getClientSendMethod() {
        Method method = cachedClientSendMethod;
        if (method != null) {
            return method;
        }

        try {
            for (final Method declaredMethod : net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.class.getMethods()) {
                if (!Modifier.isStatic(declaredMethod.getModifiers()) || !"send".equals(declaredMethod.getName()) || declaredMethod.getParameterCount() != 2) {
                    continue;
                }

                final Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
                if (parameterTypes[0] == net.minecraft.util.Identifier.class && PacketByteBuf.class.isAssignableFrom(parameterTypes[1])) {
                    cachedClientSendMethod = declaredMethod;
                    return declaredMethod;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Method getServerRegisterMethod() {
        Method method = cachedServerRegisterMethod;
        if (method != null) {
            return method;
        }

        try {
            for (final Method declaredMethod : net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class.getMethods()) {
                if (!Modifier.isStatic(declaredMethod.getModifiers()) || !"registerGlobalReceiver".equals(declaredMethod.getName()) || declaredMethod.getParameterCount() != 2) {
                    continue;
                }

                final Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
                if (parameterTypes[0] == net.minecraft.util.Identifier.class) {
                    cachedServerRegisterMethod = declaredMethod;
                    return declaredMethod;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Method getServerSendMethod() {
        Method method = cachedServerSendMethod;
        if (method != null) {
            return method;
        }

        try {
            for (final Method declaredMethod : net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class.getMethods()) {
                if (!Modifier.isStatic(declaredMethod.getModifiers()) || !"send".equals(declaredMethod.getName()) || declaredMethod.getParameterCount() != 3) {
                    continue;
                }

                final Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
                if (parameterTypes[0] == ServerPlayerEntity.class && parameterTypes[1] == net.minecraft.util.Identifier.class && PacketByteBuf.class.isAssignableFrom(parameterTypes[2])) {
                    cachedServerSendMethod = declaredMethod;
                    return declaredMethod;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Method getClientRegisterMethod() {
        Method method = cachedClientRegisterMethod;
        if (method != null) {
            return method;
        }

        try {
            for (final Method declaredMethod : net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.class.getMethods()) {
                if (!Modifier.isStatic(declaredMethod.getModifiers()) || !"registerGlobalReceiver".equals(declaredMethod.getName()) || declaredMethod.getParameterCount() != 2) {
                    continue;
                }

                if (declaredMethod.getParameterTypes()[0] == net.minecraft.util.Identifier.class) {
                    cachedClientRegisterMethod = declaredMethod;
                    return declaredMethod;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static void warnMissingClientSend() {
        if (!warnedMissingClientSend) {
            warnedMissingClientSend = true;
            LOGGER.warning("Client packet send API is not compatible with this version; skipping packet send");
        }
    }

    private static void warnMissingServerRegister() {
        if (!warnedMissingServerRegister) {
            warnedMissingServerRegister = true;
            LOGGER.warning("Server packet register API is not compatible with this version; skipping packet registration");
        }
    }
}
