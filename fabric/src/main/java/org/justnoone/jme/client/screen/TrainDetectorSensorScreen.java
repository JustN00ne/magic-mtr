package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.block.BlockTrainDetector;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.CheckboxWidgetExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.screen.TrainSensorScreenBase;

public class TrainDetectorSensorScreen extends TrainSensorScreenBase {

    private static final int DEFAULT_NODE_RANGE = 6;
    private static final int MIN_NODE_RANGE = 1;
    private static final int MAX_NODE_RANGE = 64;
    private static final int MIN_SECONDS_OFFSET = -86400;
    private static final int MAX_SECONDS_OFFSET = 86400;

    private final int nodeRange;
    private final int secondsOffset;
    private boolean useSecondsOffset;
    private final CheckboxWidgetExtension useSecondsOffsetCheckbox;

    public TrainDetectorSensorScreen(BlockPos blockPos, BlockTrainDetector.BlockEntity blockEntity) {
        super(
                blockPos,
                true,
                new ObjectObjectImmutablePair<>(
                        new TextFieldWidgetExtension(0, 0, 0, SQUARE_SIZE, 3, TextCase.DEFAULT, "[^\\d]", null),
                        TextHelper.literal("Activation Range (Nodes)")
                ),
                new ObjectObjectImmutablePair<>(
                        new TextFieldWidgetExtension(0, 0, 0, SQUARE_SIZE, 6, TextCase.DEFAULT, "[^\\d-]", null),
                        TextHelper.literal("Activation Offset (Seconds)")
                )
        );
        nodeRange = blockEntity == null ? DEFAULT_NODE_RANGE : jme$clampNodeRange(blockEntity.getNodeRange());
        secondsOffset = blockEntity == null ? 0 : jme$clampSecondsOffset(blockEntity.getSecondsOffset());
        useSecondsOffset = blockEntity != null && blockEntity.getUseSecondsOffset();

        useSecondsOffsetCheckbox = new CheckboxWidgetExtension(0, 0, 0, SQUARE_SIZE, true, checked -> useSecondsOffset = checked);
        useSecondsOffsetCheckbox.setMessage2(new Text(TextHelper.literal("Use Seconds Offset").data));
    }

    @Override
    protected void init2() {
        super.init2();
        textFields[0].setText2(String.valueOf(nodeRange));
        textFields[1].setText2(String.valueOf(secondsOffset));

        // Place the toggle between the text fields row and the stopped/moving checkboxes.
        final int basePartY = SQUARE_SIZE * 3 + TEXT_HEIGHT + TEXT_PADDING * 2 + TEXT_FIELD_PADDING * 2;
        IDrawing.setPositionAndWidth(useSecondsOffsetCheckbox, SQUARE_SIZE, basePartY - SQUARE_SIZE, PANEL_WIDTH);
        useSecondsOffsetCheckbox.setChecked(useSecondsOffset);
        addChild(new ClickableWidget(useSecondsOffsetCheckbox));
    }

    @Override
    protected void sendUpdate(BlockPos blockPos, LongAVLTreeSet filterRouteIds, boolean stoppedOnly, boolean movingOnly) {
        super.sendUpdate(blockPos, filterRouteIds, stoppedOnly, movingOnly);

        int parsedNodeRange = nodeRange;
        try {
            parsedNodeRange = Integer.parseInt(textFields[0].getText2());
        } catch (Exception ignored) {
        }

        int parsedSecondsOffset = secondsOffset;
        try {
            parsedSecondsOffset = Integer.parseInt(textFields[1].getText2());
        } catch (Exception ignored) {
        }

        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeBlockPos(new net.minecraft.util.math.BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        packet.writeVarInt(jme$clampNodeRange(parsedNodeRange));
        packet.writeBoolean(useSecondsOffset);
        packet.writeVarInt(jme$clampSecondsOffset(parsedSecondsOffset));
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_TRAIN_DETECTOR_RANGE_PACKET_ID, packet);
    }

    private static int jme$clampNodeRange(int range) {
        return Math.max(MIN_NODE_RANGE, Math.min(MAX_NODE_RANGE, range));
    }

    private static int jme$clampSecondsOffset(int seconds) {
        return Math.max(MIN_SECONDS_OFFSET, Math.min(MAX_SECONDS_OFFSET, seconds));
    }
}
