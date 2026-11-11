package org.justnoone.jme.client.screen;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.justnoone.jme.block.BlockCrossingGate;
import org.justnoone.jme.block.CrossingConfigPackets;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;

/**
 * A crossing gate's screen, opened by right-clicking it with MTR's brush: the arm's length (how far it reaches across
 * the road, from the gate's block) and its bell sound, or none. A plan view shows the gate and its arm over the road.
 */
public class CrossingGateScreen extends CrossingScreenBase {

    private static final int CELL = 14;
    private static final int GATE_COLOR = 0xFF707070;
    private static final int ARM_WHITE = 0xFFF0F0F0;
    private static final int ARM_RED = 0xFFC92D2D;

    private final boolean right;
    private int length;
    private final int[] bell;

    private CrossingGateScreen(BlockPos pos, boolean right, int length, int bell) {
        super(pos, right ? "Crossing Gate (right)" : "Crossing Gate (left)", new String[0], 0);
        this.right = right;
        this.length = BlockCrossingGate.clampLength(length);
        this.bell = new int[]{bell};
    }

    /**
     * Opens the screen of the gate at the position, with its settings as last synced to this client.
     */
    public static void open(BlockPos pos) {
        final BlockEntity blockEntity = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getBlockEntity(pos);
        if (blockEntity instanceof BlockCrossingGate.BlockEntity) {
            final BlockCrossingGate.BlockEntity gate = (BlockCrossingGate.BlockEntity) blockEntity;
            open(new CrossingGateScreen(pos.toImmutable(), gate.isRight(), gate.getLength(), gate.getBell()));
        }
    }

    @Override
    protected void initContent() {
        final int top = TITLE_HEIGHT + 10;
        final SliderWidgetExtension slider = new SliderWidgetExtension(MARGIN, top + 18, 240, BUTTON_HEIGHT, "") {
            @Override
            protected void updateMessage2() {
                setMessage2(Text.cast(TextHelper.literal(lengthLabel())));
            }

            @Override
            protected void applyValue2() {
                length = BlockCrossingGate.MIN_LENGTH + (int) Math.round(getValueMapped() * (BlockCrossingGate.MAX_LENGTH - BlockCrossingGate.MIN_LENGTH));
                updateMessage2();
            }
        };
        slider.setValueMapped((length - BlockCrossingGate.MIN_LENGTH) / (double) (BlockCrossingGate.MAX_LENGTH - BlockCrossingGate.MIN_LENGTH));
        // The slider does not always update its message after a value is set from code.
        slider.setMessage2(Text.cast(TextHelper.literal(lengthLabel())));
        addChild(new ClickableWidget(slider));

        addBellChooser(MARGIN, top + 18 + BUTTON_HEIGHT + 30 + 18, width - MARGIN * 2, bell, value -> {
        });
    }

    @Override
    protected void renderContent(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final int top = TITLE_HEIGHT + 10;
        heading(graphicsHolder, "Arm length", MARGIN, top, width - MARGIN * 2);
        text(graphicsHolder, "How far the arm reaches across the road, from the gate's block (" + BlockCrossingGate.MIN_LENGTH + " to " + BlockCrossingGate.MAX_LENGTH + " blocks).", MARGIN + 252, top + 24, TEXT_DIM);
        heading(graphicsHolder, "Bell sound", MARGIN, top + 18 + BUTTON_HEIGHT + 30, width - MARGIN * 2);
        text(graphicsHolder, "Gates are silent by default, as the level crossings next to them ring.", MARGIN, top + 18 + BUTTON_HEIGHT + 30 + 18 + 3 * (BUTTON_HEIGHT + 4) + 4, TEXT_DIM);

        // Plan view: the gate's block and its lowered arm over the road, seen from above with the road in front.
        final int planTop = top + 18 + BUTTON_HEIGHT + 30 + 18 + 3 * (BUTTON_HEIGHT + 4) + 24;
        heading(graphicsHolder, "Seen from above, from the road in front of the gate", MARGIN, planTop, width - MARGIN * 2);
        final int cells = BlockCrossingGate.MAX_LENGTH + 1;
        final int planWidth = cells * CELL;
        final int planLeft = (width - planWidth) / 2;
        final int rowY = planTop + 20;
        for (int i = 0; i < cells; i++) {
            rectangle(graphicsHolder, planLeft + i * CELL, rowY, planLeft + (i + 1) * CELL - 1, rowY + CELL - 1, 0x30FFFFFF);
        }
        // A left gate stands at the right end and its arm reaches left, as seen from the road; a right gate the other way.
        final int gateCell = right ? 0 : cells - 1;
        rectangle(graphicsHolder, planLeft + gateCell * CELL + 2, rowY + 2, planLeft + (gateCell + 1) * CELL - 3, rowY + CELL - 3, GATE_COLOR);
        final int centreX = planLeft + gateCell * CELL + CELL / 2;
        final int armEnd = centreX + (right ? 1 : -1) * length * CELL;
        final int armLeft = Math.min(centreX, armEnd), armRight = Math.max(centreX, armEnd);
        for (int x = armLeft; x < armRight; x += CELL / 2) {
            final int stripe = Math.abs(x - centreX) / (CELL / 2);
            rectangle(graphicsHolder, x, rowY + CELL / 2 - 2, Math.min(armRight, x + CELL / 2), rowY + CELL / 2 + 2, stripe % 2 == 0 ? ARM_RED : ARM_WHITE);
        }
        final String caption = length + " blocks";
        text(graphicsHolder, caption, (armLeft + armRight) / 2 - GraphicsHolder.getTextWidth(caption) / 2, rowY + CELL + 4, TEXT);
        final String road = "road in front of the gate";
        text(graphicsHolder, road, width / 2 - GraphicsHolder.getTextWidth(road) / 2, rowY + CELL + 18, TEXT_DIM);
    }

    @Override
    protected void save() {
        send(CrossingConfigPackets.gate(pos, length, bell[0]));
    }

    private String lengthLabel() {
        return "Arm length: " + length + " blocks";
    }
}
