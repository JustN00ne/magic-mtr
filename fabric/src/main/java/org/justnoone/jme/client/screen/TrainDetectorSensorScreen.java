package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.block.BlockTrainDetector;
import org.justnoone.jme.client.TrainDetectorPreviewState;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.mixin.WidgetMapAccessor;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.screen.TrainSensorScreenBase;
import org.mtr.mod.screen.WidgetMap;
import org.mtr.mod.screen.WorldMap;

import java.util.Collections;
import java.util.List;

public class TrainDetectorSensorScreen extends TrainSensorScreenBase {

    private static final int DEFAULT_START_DISTANCE_BLOCKS = 0;
    private static final int DEFAULT_END_DISTANCE_BLOCKS = 128;
    private static final int MIN_DISTANCE_BLOCKS = 0;
    private static final int MAX_DISTANCE_BLOCKS = 8192;
    private static final long PREVIEW_DEBOUNCE_MILLIS = 150;

    private static final int PREVIEW_ORANGE_RGB = 0xFF6A00;

    private final int startDistance;
    private final int endDistance;

    private WidgetMap mapWidget;

    private int lastPreviewStart = Integer.MIN_VALUE;
    private int lastPreviewEnd = Integer.MIN_VALUE;
    private long lastPreviewComputeMillis;
    private boolean previewDirty = true;

    public TrainDetectorSensorScreen(BlockPos blockPos, BlockTrainDetector.BlockEntity blockEntity) {
        super(
                blockPos,
                true,
                new ObjectObjectImmutablePair<>(
                        new TextFieldWidgetExtension(0, 0, 0, SQUARE_SIZE, 5, TextCase.DEFAULT, "[^\\d]", null),
                        TextHelper.literal("Start Distance (Blocks)")
                ),
                new ObjectObjectImmutablePair<>(
                        new TextFieldWidgetExtension(0, 0, 0, SQUARE_SIZE, 5, TextCase.DEFAULT, "[^\\d]", null),
                        TextHelper.literal("End Distance (Blocks)")
                )
        );

        startDistance = blockEntity == null ? DEFAULT_START_DISTANCE_BLOCKS : clampDistance(blockEntity.getStartDistance());
        endDistance = blockEntity == null ? DEFAULT_END_DISTANCE_BLOCKS : clampDistance(blockEntity.getEndDistance());
    }

    @Override
    protected void init2() {
        super.init2();

        textFields[0].setText2(String.valueOf(startDistance));
        textFields[1].setText2(String.valueOf(Math.max(startDistance, endDistance)));

        final int mapW = Math.min(360, Math.max(220, width / 3));
        final int mapH = Math.min(240, Math.max(160, height / 4));
        final int mapX = width - mapW - SQUARE_SIZE;
        final int mapY = Math.max(SQUARE_SIZE * 4, SQUARE_SIZE * 2 + TEXT_HEIGHT + TEXT_PADDING * 2 + TEXT_FIELD_PADDING * 2);

        mapWidget = new WidgetMap(
                TransportMode.TRAIN,
                (topLeft, bottomRight) -> {
                },
                () -> {
                },
                routeId -> {
                },
                savedRail -> {
                },
                (mouseX, mouseY) -> false
        );
        mapWidget.setShowStations(false);
        mapWidget.setMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
        mapWidget.setPositionAndSize(mapX, mapY, mapW, mapH);
        addChild(new ClickableWidget(mapWidget));

        previewDirty = true;
        TrainDetectorPreviewState.clear();
    }

    @Override
    public void tick2() {
        super.tick2();
        jme$updatePreviewIfNeeded();
    }

    @Override
    public void onClose2() {
        TrainDetectorPreviewState.clear();
        super.onClose2();
    }

    @Override
    protected void renderAdditional(GraphicsHolder graphicsHolder) {
        if (mapWidget == null) {
            return;
        }

        final MutableText title = TextHelper.literal("Preview");
        final int titleX = mapWidget.getX2();
        final int titleY = mapWidget.getY2() - TEXT_HEIGHT - 2;
        graphicsHolder.drawText(title, titleX, titleY, 0xFFFFFFFF, false, GraphicsHolder.getDefaultLight());

        final MutableText legend = TextHelper.literal("Neon orange: active range");
        graphicsHolder.drawText(legend, titleX, titleY + TEXT_HEIGHT, (0xFF << 24) | (PREVIEW_ORANGE_RGB & 0xFFFFFF), false, GraphicsHolder.getDefaultLight());
    }

    @Override
    protected void sendUpdate(BlockPos blockPos, LongAVLTreeSet filterRouteIds, boolean stoppedOnly, boolean movingOnly) {
        super.sendUpdate(blockPos, filterRouteIds, stoppedOnly, movingOnly);

        int parsedStart = startDistance;
        try {
            parsedStart = Integer.parseInt(textFields[0].getText2());
        } catch (Exception ignored) {
        }

        int parsedEnd = endDistance;
        try {
            parsedEnd = Integer.parseInt(textFields[1].getText2());
        } catch (Exception ignored) {
        }

        int clampedStart = clampDistance(parsedStart);
        int clampedEnd = clampDistance(parsedEnd);
        if (clampedEnd < clampedStart) {
            final int tmp = clampedStart;
            clampedStart = clampedEnd;
            clampedEnd = tmp;
        }

        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeBlockPos(new net.minecraft.util.math.BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        packet.writeVarInt(0); // v2 sentinel
        packet.writeVarInt(clampedStart);
        packet.writeVarInt(clampedEnd);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_TRAIN_DETECTOR_RANGE_PACKET_ID, packet);
    }

    private void jme$updatePreviewIfNeeded() {
        if (mapWidget == null || textFields == null || textFields.length < 2) {
            return;
        }

        int parsedStart = startDistance;
        try {
            parsedStart = Integer.parseInt(textFields[0].getText2());
        } catch (Exception ignored) {
        }

        int parsedEnd = endDistance;
        try {
            parsedEnd = Integer.parseInt(textFields[1].getText2());
        } catch (Exception ignored) {
        }

        int start = clampDistance(parsedStart);
        int end = clampDistance(parsedEnd);
        if (end < start) {
            final int tmp = start;
            start = end;
            end = tmp;
        }

        if (!previewDirty && start == lastPreviewStart && end == lastPreviewEnd) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (!previewDirty && now - lastPreviewComputeMillis < PREVIEW_DEBOUNCE_MILLIS) {
            return;
        }

        previewDirty = false;
        lastPreviewStart = start;
        lastPreviewEnd = end;
        lastPreviewComputeMillis = now;

        final MinecraftClientData railData = jme$getPreferredRailData();
        if (railData == null || railData.rails.isEmpty()) {
            TrainDetectorPreviewState.setPreviewEdges(Collections.emptyList());
            return;
        }

        final List<Object[]> previewEdges = BlockTrainDetector.computePreviewEdges(railData, blockPos, start, end);
        TrainDetectorPreviewState.setPreviewEdges(previewEdges);
        jme$fitMapToEdges(previewEdges);
    }

    private void jme$fitMapToEdges(List<Object[]> edges) {
        if (mapWidget == null) {
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        if (edges != null) {
            for (final Object[] edge : edges) {
                if (edge == null || edge.length < 3) {
                    continue;
                }
                final Rail rail = edge[2] instanceof Rail ? (Rail) edge[2] : null;
                if (rail != null && rail.railMath != null) {
                    minX = Math.min(minX, rail.railMath.minX);
                    maxX = Math.max(maxX, rail.railMath.maxX);
                    minZ = Math.min(minZ, rail.railMath.minZ);
                    maxZ = Math.max(maxZ, rail.railMath.maxZ);
                } else {
                    final Position p1 = edge[0] instanceof Position ? (Position) edge[0] : null;
                    final Position p2 = edge[1] instanceof Position ? (Position) edge[1] : null;
                    if (p1 != null) {
                        minX = Math.min(minX, p1.getX());
                        maxX = Math.max(maxX, p1.getX());
                        minZ = Math.min(minZ, p1.getZ());
                        maxZ = Math.max(maxZ, p1.getZ());
                    }
                    if (p2 != null) {
                        minX = Math.min(minX, p2.getX());
                        maxX = Math.max(maxX, p2.getX());
                        minZ = Math.min(minZ, p2.getZ());
                        maxZ = Math.max(maxZ, p2.getZ());
                    }
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
            // Fallback to detector position if we have nothing to render yet.
            minX = blockPos.getX() - 8;
            maxX = blockPos.getX() + 8;
            minZ = blockPos.getZ() - 8;
            maxZ = blockPos.getZ() + 8;
        }

        final double centerX = (minX + maxX) * 0.5D + 0.5D;
        final double centerZ = (minZ + maxZ) * 0.5D + 0.5D;

        final double worldWidth = Math.max(1, maxX - minX);
        final double worldHeight = Math.max(1, maxZ - minZ);

        final double scaleX = mapWidget.getWidth2() / worldWidth;
        final double scaleY = mapWidget.getHeight2() / worldHeight;
        double scale = Math.min(scaleX, scaleY) * 0.85D;
        scale = Math.max(0.02D, Math.min(8D, scale));

        final WidgetMapAccessor accessor = (WidgetMapAccessor) (Object) mapWidget;
        accessor.jme$setCenterX(centerX);
        accessor.jme$setCenterY(centerZ);
        accessor.jme$setScale(scale);
    }

    private static MinecraftClientData jme$getPreferredRailData() {
        final MinecraftClientData dashboardData = MinecraftClientData.getDashboardInstance();
        if (dashboardData != null && !dashboardData.rails.isEmpty() && !dashboardData.positionsToRail.isEmpty()) {
            return dashboardData;
        }
        final MinecraftClientData liveData = MinecraftClientData.getInstance();
        if (liveData != null && !liveData.rails.isEmpty() && !liveData.positionsToRail.isEmpty()) {
            return liveData;
        }
        return dashboardData;
    }

    private static int clampDistance(int distanceBlocks) {
        return Math.max(MIN_DISTANCE_BLOCKS, Math.min(MAX_DISTANCE_BLOCKS, distanceBlocks));
    }
}

