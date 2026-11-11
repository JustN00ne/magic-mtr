package org.justnoone.jme.client.screen;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.justnoone.jme.block.BlockCrossingLights;
import org.justnoone.jme.block.BlockRailroadCrossingController;
import org.justnoone.jme.block.CrossingConfigPackets;
import org.justnoone.jme.block.CrossingControl;
import org.justnoone.jme.block.CrossingObject;
import org.justnoone.jme.block.CrossingSignal;
import org.justnoone.jme.util.CrossingGateMotion;
import org.justnoone.jme.util.CrossingTimeline;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The crossing controller's screen, opened by right-clicking a level crossing (or legacy controller) with MTR's brush:
 * <ul>
 *     <li>Components: everything the controller controls, with its coordinates (its start and end axle counters and
 *     the level crossings and gates linked to it), each of which can be unlinked;</li>
 *     <li>Timeline and controls: whether the crossing works automatically or is closed by hand, and the timeline that
 *     sets when the lights and the gates switch after a train is detected and after the crossing clears (drag the
 *     markers, or use the buttons);</li>
 *     <li>Lights (level crossings only, also opened directly with sneak + right-click): the bell sound, or none, and how
 *     fast the lamps flash.</li>
 * </ul>
 * The edited settings are kept while switching tabs and sent when saved.
 */
public class CrossingControllerScreen extends CrossingScreenBase {

    public static final int TAB_COMPONENTS = 0;
    public static final int TAB_TIMELINE = 1;
    public static final int TAB_LIGHTS = 2;

    private static final int ROW_HEIGHT = 12;
    private static final int LANE_HEIGHT = 26;
    private static final int LIGHTS_COLOR = 0xFFFFD040;
    private static final int GATES_COLOR = 0xFFE04848;
    private static final int UNLINK_COLOR = 0xFF6060;
    private static final int GATE_MOTION_TICKS = (int) (CrossingGateMotion.DURATION_SECONDS * CrossingTimeline.TICKS_PER_SECOND);
    private static final String[] EVENT_NAMES = {"Lights switch on", "Gates start lowering", "Gates start rising", "Lights switch off"};
    private static final String[] EVENT_SHORT_NAMES = {"Lights on", "Gates down", "Gates up", "Lights off"};
    private static final int[] STEPS = {-10, -1, 1, 10};

    private final Draft draft;
    private final int tab;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    /**
     * The timeline event being dragged, or -1.
     */
    private int dragging = -1;
    private ButtonWidgetExtension modeButton;

    private CrossingControllerScreen(Draft draft, int tab) {
        super(draft.pos, draft.title, draft.lights ? new String[]{"Components", "Timeline & Controls", "Lights"} : new String[]{"Components", "Timeline & Controls"}, tab);
        this.draft = draft;
        this.tab = tab;
    }

    /**
     * Opens the screen of the controller at the position, with its settings as last synced to this client.
     */
    public static void open(BlockPos pos, int tab) {
        final BlockEntity blockEntity = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getBlockEntity(pos);
        if (!(blockEntity instanceof CrossingControl.Holder)) {
            return;
        }
        final String title = blockEntity instanceof BlockCrossingLights.BlockEntity ? "Level Crossing (controller)" : blockEntity instanceof BlockRailroadCrossingController.BlockEntity ? "Railroad Crossing Controller" : "Level Crossing Controller (Legacy)";
        final Draft draft = new Draft(pos.toImmutable(), title, blockEntity instanceof BlockCrossingLights.BlockEntity);
        final CrossingControl control = ((CrossingControl.Holder) blockEntity).getCrossingControl();
        final CrossingTimeline timeline = control.getTimeline();
        draft.manualClosed = control.isManualClosed();
        draft.delays[0] = timeline.getLightsOnDelay();
        draft.delays[1] = timeline.getGatesLowerDelay();
        draft.delays[2] = timeline.getGatesRaiseDelay();
        draft.delays[3] = timeline.getLightsOffDelay();
        if (draft.lights) {
            draft.bell[0] = ((BlockCrossingLights.BlockEntity) blockEntity).getBell();
            draft.flashRate = ((BlockCrossingLights.BlockEntity) blockEntity).getFlashRate();
        }
        open(new CrossingControllerScreen(draft, draft.lights || tab != TAB_LIGHTS ? tab : TAB_COMPONENTS));
    }

    @Override
    protected void switchTab(int newTab) {
        open(new CrossingControllerScreen(draft, newTab));
    }

    @Override
    protected void initContent() {
        if (tab == TAB_TIMELINE) {
            initTimeline();
        } else if (tab == TAB_LIGHTS) {
            initLights();
        }
    }

    @Override
    protected void renderContent(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        if (tab == TAB_COMPONENTS) {
            renderComponents(graphicsHolder, mouseX, mouseY);
        } else if (tab == TAB_TIMELINE) {
            renderTimeline(graphicsHolder);
        } else {
            renderLights(graphicsHolder);
        }
    }

    @Override
    protected void save() {
        send(CrossingConfigPackets.controller(pos, draft.manualClosed, draft.delays[0], draft.delays[1], draft.delays[2], draft.delays[3]));
        if (draft.lights) {
            send(CrossingConfigPackets.lights(pos, draft.bell[0], draft.flashRate));
        }
    }

    // Components

    private void renderComponents(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final CrossingControl control = getControl();
        final int top = TITLE_HEIGHT + 10;
        if (control == null) {
            text(graphicsHolder, "This controller is no longer loaded.", MARGIN, top, TEXT_DIM);
            return;
        }

        final String state = control.isManualClosed() ? "§cClosed by hand" : control.isTrainDetected() ? "§6Train detected (" + control.getTrainsInside() + " in the section)" : "§aClear";
        text(graphicsHolder, "State: " + state + "§r   Lights: " + (control.isLightsOn() ? "§eon" : "§7off") + "§r   Gates: " + (control.isGatesDown() ? "§cdown" : "§7up"), MARGIN, top, TEXT);

        buildRows(control);
        final int listTop = top + 18;
        final int listBottom = height - MARGIN - BUTTON_HEIGHT - 8;
        final int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        final int unlinkX = width - MARGIN - GraphicsHolder.getTextWidth("[Unlink]");
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            final Row row = rows.get(scroll + i);
            final int y = listTop + i * ROW_HEIGHT;
            if (row.role < 0) {
                heading(graphicsHolder, row.text, MARGIN, y + 1, width - MARGIN * 2);
            } else {
                text(graphicsHolder, row.text, MARGIN + 8, y + 2, TEXT);
                final boolean hover = mouseX >= unlinkX && mouseY >= y && mouseY < y + ROW_HEIGHT;
                text(graphicsHolder, "[Unlink]", unlinkX, y + 2, hover ? 0xFFA0A0 : UNLINK_COLOR);
            }
        }
        if (rows.size() > visibleRows) {
            final String more = "Scroll for more (" + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + " of " + rows.size() + ")";
            text(graphicsHolder, more, MARGIN, height - MARGIN - 14, TEXT_DIM);
        }
        text(graphicsHolder, "Link more with the Crossing Linker. Axle counters link at any distance.", MARGIN, height - MARGIN - 4 - (rows.size() > visibleRows ? 0 : 10), TEXT_DIM);
    }

    private void buildRows(CrossingControl control) {
        rows.clear();
        final List<CrossingControl.CounterLink> starts = control.getStarts();
        final List<CrossingControl.CounterLink> ends = control.getEnds();
        rows.add(new Row("Start nodes (axle counters): " + starts.size(), -1, 0, 0, 0));
        addCounterRows(starts, CrossingConfigPackets.ROLE_START);
        rows.add(new Row("End nodes (axle counters): " + ends.size(), -1, 0, 0, 0));
        addCounterRows(ends, CrossingConfigPackets.ROLE_END);
        final List<org.mtr.mapping.holder.BlockPos> objects = control.getObjects();
        rows.add(new Row("Controlled level crossings and gates: " + objects.size(), -1, 0, 0, 0));
        for (final org.mtr.mapping.holder.BlockPos objectPos : objects) {
            final BlockEntity blockEntity = MinecraftClient.getInstance().world.isChunkLoaded(objectPos.getX() >> 4, objectPos.getZ() >> 4) ? MinecraftClient.getInstance().world.getBlockEntity(objectPos.data) : null;
            final String name = blockEntity instanceof CrossingObject ? ((CrossingObject) blockEntity).getCrossingObjectName() : "§7Not loaded";
            rows.add(new Row(name + "§r  at " + objectPos.getX() + ", " + objectPos.getY() + ", " + objectPos.getZ() + distance(objectPos.getX() + 0.5, objectPos.getY(), objectPos.getZ() + 0.5), CrossingConfigPackets.ROLE_OBJECT, 0, objectPos.asLong(), 0));
        }
        final List<org.mtr.mapping.holder.BlockPos> detectors = control.getDetectors();
        rows.add(new Row("Train detectors (close the crossing while powered): " + detectors.size() + (control.isDetectorActive() ? "§e  - powered" : ""), -1, 0, 0, 0));
        for (int i = 0; i < detectors.size(); i++) {
            final org.mtr.mapping.holder.BlockPos detectorPos = detectors.get(i);
            rows.add(new Row("§6Train detector§r  at " + detectorPos.getX() + ", " + detectorPos.getY() + ", " + detectorPos.getZ() + distance(detectorPos.getX() + 0.5, detectorPos.getY(), detectorPos.getZ() + 0.5), CrossingConfigPackets.ROLE_DETECTOR, i, detectorPos.asLong(), 0));
        }
    }

    private void addCounterRows(List<CrossingControl.CounterLink> links, int role) {
        for (int i = 0; i < links.size(); i++) {
            final CrossingControl.CounterLink link = links.get(i);
            final UUID id = link.getEntityId();
            final String name = (role == CrossingConfigPackets.ROLE_START ? "§2" : "§4") + "Axle counter" + (link.isLegacyBlock() ? " (legacy block)" : "") + (link.getDirection() == 0 ? "" : "§7 (one direction)");
            rows.add(new Row(name + "§r  at " + (int) Math.floor(link.getX()) + ", " + (int) Math.floor(link.getY()) + ", " + (int) Math.floor(link.getZ()) + distance(link.getX(), link.getY(), link.getZ()), role, i, id == null ? 0 : id.getMostSignificantBits(), id == null ? 0 : id.getLeastSignificantBits()));
        }
    }

    private String distance(double x, double y, double z) {
        final double dx = x - pos.getX() - 0.5, dy = y - pos.getY(), dz = z - pos.getZ() - 0.5;
        return "§7  (" + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " blocks away)";
    }

    @Override
    public boolean mouseClicked2(double mouseX, double mouseY, int button) {
        if (tab == TAB_COMPONENTS && button == 0) {
            final int listTop = TITLE_HEIGHT + 28;
            final int unlinkX = width - MARGIN - GraphicsHolder.getTextWidth("[Unlink]");
            final int index = scroll + (int) Math.floor((mouseY - listTop) / ROW_HEIGHT);
            if (mouseX >= unlinkX && mouseY >= listTop && index >= 0 && index < rows.size() && rows.get(index).role >= 0 && mouseY < height - MARGIN - BUTTON_HEIGHT - 8) {
                final Row row = rows.get(index);
                send(CrossingConfigPackets.unlink(pos, row.role, row.index, row.identity1, row.identity2));
                return true;
            }
        }
        if (tab == TAB_TIMELINE && button == 0) {
            final int event = findMarker(mouseX, mouseY);
            if (event >= 0) {
                dragging = event;
                return true;
            }
        }
        return super.mouseClicked2(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged2(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging >= 0) {
            draft.delays[dragging] = CrossingTimeline.clampDelay((int) Math.round((mouseX - graphLeft()) / ticksToPixelsExact(1)));
            return true;
        }
        return super.mouseDragged2(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased2(double mouseX, double mouseY, int button) {
        dragging = -1;
        return super.mouseReleased2(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
        if (tab == TAB_COMPONENTS) {
            scroll -= (int) Math.signum(amount) * 3;
            return true;
        }
        return super.mouseScrolled2(mouseX, mouseY, amount);
    }

    // Timeline and controls

    private void initTimeline() {
        final int top = TITLE_HEIGHT + 10;
        modeButton = addButton(MARGIN, top, 220, modeLabel(), () -> {
            draft.manualClosed = !draft.manualClosed;
            setLabel(modeButton, modeLabel());
        });
        addButton(MARGIN + 224, top, 120, "Reset timeline", () -> {
            draft.delays[0] = CrossingTimeline.DEFAULT_LIGHTS_ON_DELAY;
            draft.delays[1] = CrossingTimeline.DEFAULT_GATES_LOWER_DELAY;
            draft.delays[2] = CrossingTimeline.DEFAULT_GATES_RAISE_DELAY;
            draft.delays[3] = CrossingTimeline.DEFAULT_LIGHTS_OFF_DELAY;
        });

        final int rowsTop = laneTop(1) + LANE_HEIGHT + 34;
        for (int event = 0; event < 4; event++) {
            final int y = rowsTop + event * (BUTTON_HEIGHT + 4);
            int x = MARGIN + 150;
            for (final int step : STEPS) {
                final int eventIndex = event;
                final String label = (step > 0 ? "+" : "-") + (Math.abs(step) == 1 ? "0.05" : "0.5");
                addButton(x, y, 40, label, () -> draft.delays[eventIndex] = CrossingTimeline.clampDelay(draft.delays[eventIndex] + step));
                // Leave room for the value between the minus and plus buttons.
                x += step == -1 ? 44 + 60 : 44;
            }
        }
    }

    private void renderTimeline(GraphicsHolder graphicsHolder) {
        final int top = TITLE_HEIGHT + 10;
        text(graphicsHolder, draft.manualClosed ? "The crossing stays closed until set back to automatic." : "The axle counters close and open the crossing.", MARGIN + 350, top + 6, TEXT_DIM);

        heading(graphicsHolder, "Timeline (drag the markers)", MARGIN, top + 30, width - MARGIN * 2);
        final int left = graphLeft(), right = graphRight();
        for (int lane = 0; lane < 2; lane++) {
            final int y = laneTop(lane);
            text(graphicsHolder, lane == 0 ? "Train detected" : "Crossing clear", MARGIN, y + LANE_HEIGHT / 2 - 4, TEXT);
            rectangle(graphicsHolder, left, y, right, y + LANE_HEIGHT, 0x30FFFFFF);
            final int lightsEvent = lane == 0 ? 0 : 3;
            final int gatesEvent = lane == 0 ? 1 : 2;
            // The gates take a few seconds to move: shown as a bar after their marker.
            final int gatesX = timeX(draft.delays[gatesEvent]);
            rectangle(graphicsHolder, gatesX, y + LANE_HEIGHT - 8, Math.min(right, timeX(draft.delays[gatesEvent] + GATE_MOTION_TICKS)), y + LANE_HEIGHT - 3, (GATES_COLOR & 0xFFFFFF) | 0x70000000);
            marker(graphicsHolder, draft.delays[lightsEvent], y, LIGHTS_COLOR, EVENT_SHORT_NAMES[lightsEvent], 2);
            marker(graphicsHolder, draft.delays[gatesEvent], y, GATES_COLOR, EVENT_SHORT_NAMES[gatesEvent], 11);
        }
        // Seconds scale under the lanes.
        final int axisY = laneTop(1) + LANE_HEIGHT + 2;
        final int labelEvery = ticksToPixels(CrossingTimeline.TICKS_PER_SECOND) >= 24 ? 1 : 5;
        for (int second = 0; second * CrossingTimeline.TICKS_PER_SECOND <= CrossingTimeline.MAX_DELAY_TICKS; second++) {
            final int x = timeX(second * CrossingTimeline.TICKS_PER_SECOND);
            rectangle(graphicsHolder, x, axisY, x + 1, axisY + (second % labelEvery == 0 ? 5 : 3), 0x80FFFFFF);
            if (second % labelEvery == 0) {
                final String label = second + "s";
                text(graphicsHolder, label, x - GraphicsHolder.getTextWidth(label) / 2, axisY + 7, TEXT_DIM);
            }
        }

        final int rowsTop = laneTop(1) + LANE_HEIGHT + 34;
        for (int event = 0; event < 4; event++) {
            final int y = rowsTop + event * (BUTTON_HEIGHT + 4);
            text(graphicsHolder, EVENT_NAMES[event], MARGIN, y + 6, event == 0 || event == 3 ? LIGHTS_COLOR & 0xFFFFFF : GATES_COLOR & 0xFFFFFF);
            final String value = seconds(draft.delays[event]);
            text(graphicsHolder, value, MARGIN + 150 + 88 + 30 - GraphicsHolder.getTextWidth(value) / 2, y + 6, TEXT);
            text(graphicsHolder, event < 2 ? "after a train is detected" : "after the crossing clears", MARGIN + 150 + 88 + 60 + 88 + 8, y + 6, TEXT_DIM);
        }
        text(graphicsHolder, "Gates take " + (int) CrossingGateMotion.DURATION_SECONDS + " s to lower or rise. Hold the markers to drag them in 0.05 s steps.", MARGIN, rowsTop + 4 * (BUTTON_HEIGHT + 4) + 4, TEXT_DIM);
    }

    private void marker(GraphicsHolder graphicsHolder, int ticks, int laneY, int color, String label, int labelOffset) {
        final int x = timeX(ticks);
        rectangle(graphicsHolder, x - 1, laneY - 2, x + 2, laneY + LANE_HEIGHT + 2, color);
        rectangle(graphicsHolder, x - 3, laneY - 4, x + 4, laneY + 2, color);
        text(graphicsHolder, label + " " + seconds(ticks), x + 5, laneY + labelOffset - 1, color & 0xFFFFFF);
    }

    private int findMarker(double mouseX, double mouseY) {
        int best = -1;
        double bestDistance = 6;
        for (int lane = 0; lane < 2; lane++) {
            final int y = laneTop(lane);
            if (mouseY < y - 5 || mouseY > y + LANE_HEIGHT + 3) {
                continue;
            }
            for (final int event : lane == 0 ? new int[]{0, 1} : new int[]{3, 2}) {
                final double distance = Math.abs(mouseX - timeX(draft.delays[event]));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = event;
                }
            }
        }
        return best;
    }

    private int laneTop(int lane) {
        return TITLE_HEIGHT + 10 + 48 + lane * (LANE_HEIGHT + 10);
    }

    private int graphLeft() {
        return MARGIN + 90;
    }

    private int graphRight() {
        return width - MARGIN - 60;
    }

    private double ticksToPixelsExact(int ticks) {
        return ticks * (graphRight() - graphLeft()) / (double) CrossingTimeline.MAX_DELAY_TICKS;
    }

    private int ticksToPixels(int ticks) {
        return (int) Math.round(ticksToPixelsExact(ticks));
    }

    private int timeX(int ticks) {
        return graphLeft() + ticksToPixels(Math.min(ticks, CrossingTimeline.MAX_DELAY_TICKS));
    }

    private String modeLabel() {
        return draft.manualClosed ? "Mode: §cClosed by hand" : "Mode: §aAutomatic (axle counters)";
    }

    // Lights

    private ButtonWidgetExtension flashButton;

    private void initLights() {
        final int top = TITLE_HEIGHT + 10;
        addBellChooser(MARGIN, top + 18, width - MARGIN * 2, draft.bell, bell -> {
        });
        flashButton = addButton(MARGIN, top + 18 + 2 * (BUTTON_HEIGHT + 4) + 40, 160, flashLabel(), () -> {
            draft.flashRate = (draft.flashRate + 1) % CrossingSignal.getFlashRateCount();
            setLabel(flashButton, flashLabel());
        });
    }

    private void renderLights(GraphicsHolder graphicsHolder) {
        final int top = TITLE_HEIGHT + 10;
        heading(graphicsHolder, "Bell sound", MARGIN, top, width - MARGIN * 2);
        heading(graphicsHolder, "Lamps", MARGIN, top + 18 + 2 * (BUTTON_HEIGHT + 4) + 26, width - MARGIN * 2);
        text(graphicsHolder, "\"No sound\" keeps a level crossing silent, eg one only used to link sections.", MARGIN, top + 18 + 3 * (BUTTON_HEIGHT + 4) + 46, TEXT_DIM);
        text(graphicsHolder, "These settings are this level crossing's own; the linked ones keep theirs.", MARGIN, top + 18 + 3 * (BUTTON_HEIGHT + 4) + 58, TEXT_DIM);
    }

    private String flashLabel() {
        return "Flash rate: " + CrossingSignal.getFlashRateName(draft.flashRate);
    }

    private CrossingControl getControl() {
        final BlockEntity blockEntity = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getBlockEntity(pos);
        return blockEntity instanceof CrossingControl.Holder ? ((CrossingControl.Holder) blockEntity).getCrossingControl() : null;
    }

    /**
     * The settings being edited, kept while switching tabs.
     */
    private static final class Draft {

        private final BlockPos pos;
        private final String title;
        private final boolean lights;
        private boolean manualClosed;
        private final int[] delays = new int[4];
        private final int[] bell = {0};
        private int flashRate = CrossingSignal.DEFAULT_FLASH_RATE;

        private Draft(BlockPos pos, String title, boolean lights) {
            this.pos = pos;
            this.title = title;
            this.lights = lights;
        }
    }

    /**
     * A line of the components list: a heading ({@code role} -1) or something linked, which can be unlinked.
     */
    private static final class Row {

        private final String text;
        private final int role;
        private final int index;
        private final long identity1;
        private final long identity2;

        private Row(String text, int role, int index, long identity1, long identity2) {
            this.text = text;
            this.role = role;
            this.index = index;
            this.identity1 = identity1;
            this.identity2 = identity2;
        }
    }
}
