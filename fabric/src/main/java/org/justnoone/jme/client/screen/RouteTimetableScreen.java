package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.mixin.RoutePlatformDataAccessor;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.RouteTimetableRegistry;
import org.justnoone.jme.util.FlexibleTimetables;
import org.justnoone.jme.util.HeadcodePriority;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;

import java.util.List;
import java.util.Locale;

/**
 * Edits a route's settings: its flexible timetable (the departure time of each stop after the depot departure, and the
 * minimum dwell time) and its headcode class, which sets which trains overtake which. Opened from MTR's route screen.
 */
public class RouteTimetableScreen extends ScreenExtension {

    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 116;
    private static final int FOOTER_HEIGHT = 58;
    private static final int FIELD_WIDTH = 84;
    private static final int HEADCODE_BUTTON_WIDTH = 110;

    private final Screen parent;
    private final Route route;
    private final boolean hadEntry;
    private final long[] platformIds;
    private final String[] stopNames;
    private final String[] offsetTexts;
    private String minimumDwellText;
    private int headcodeClass;
    private int page;

    private TextFieldWidgetExtension minimumDwellField;
    private ButtonWidgetExtension headcodeClassButton;
    private TextFieldWidgetExtension[] offsetFields = new TextFieldWidgetExtension[0];
    private ButtonWidgetExtension previousPageButton;
    private ButtonWidgetExtension nextPageButton;
    private ButtonWidgetExtension saveButton;

    private String status = "";
    private boolean valid;

    public RouteTimetableScreen(ScreenExtension parent, Route route) {
        this.parent = new Screen(parent);
        this.route = route;
        final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
        final int count = routePlatforms.size();
        platformIds = new long[count];
        stopNames = new String[count];
        offsetTexts = new String[count];
        final RouteTimetableRegistry.Entry entry = RouteTimetableRegistry.get(route.getId());
        hadEntry = entry != null;
        for (int i = 0; i < count; i++) {
            final RoutePlatformData routePlatform = routePlatforms.get(i);
            final Platform platform = routePlatform.getPlatform();
            platformIds[i] = platform == null ? ((RoutePlatformDataAccessor) (Object) routePlatform).jme$getPlatformId() : platform.getId();
            stopNames[i] = getStopName(platform);
            offsetTexts[i] = entry == null ? "" : FlexibleTimetables.formatDuration(entry.getOffset(i, platformIds[i]));
        }
        minimumDwellText = entry == null || entry.minimumDwellMillis == 0 ? "" : FlexibleTimetables.formatDuration(entry.minimumDwellMillis);
        headcodeClass = entry == null ? HeadcodePriority.NONE : entry.headcodeClass;
    }

    @Override
    protected void init2() {
        final int panelX = getPanelX();
        final int panelY = getPanelY();

        minimumDwellField = new TextFieldWidgetExtension(panelX + PANEL_WIDTH - 12 - FIELD_WIDTH, panelY + 52, FIELD_WIDTH, 20, 12, TextCase.DEFAULT, null, "0:00:30");
        minimumDwellField.setText2(minimumDwellText);
        addChild(new ClickableWidget(minimumDwellField));

        headcodeClassButton = new ButtonWidgetExtension(panelX + PANEL_WIDTH - 12 - HEADCODE_BUTTON_WIDTH, panelY + 76, HEADCODE_BUTTON_WIDTH, 20, TextHelper.literal(HeadcodePriority.describe(headcodeClass)), button -> {
            headcodeClass = HeadcodePriority.next(headcodeClass);
            headcodeClassButton.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal(HeadcodePriority.describe(headcodeClass))));
        });
        addChild(new ClickableWidget(headcodeClassButton));

        offsetFields = new TextFieldWidgetExtension[offsetTexts.length];
        for (int i = 0; i < offsetFields.length; i++) {
            offsetFields[i] = new TextFieldWidgetExtension(panelX + PANEL_WIDTH - 12 - FIELD_WIDTH, 0, FIELD_WIDTH, 20, 12, TextCase.DEFAULT, null, "+H:MM:SS");
            offsetFields[i].setText2(offsetTexts[i]);
            addChild(new ClickableWidget(offsetFields[i]));
        }

        final int footerY = panelY + getPanelHeight() - 26;
        previousPageButton = new ButtonWidgetExtension(panelX + 12, footerY - 24, 20, 20, TextHelper.literal("<"), button -> setPage(page - 1));
        addChild(new ClickableWidget(previousPageButton));
        nextPageButton = new ButtonWidgetExtension(panelX + 112, footerY - 24, 20, 20, TextHelper.literal(">"), button -> setPage(page + 1));
        addChild(new ClickableWidget(nextPageButton));

        final ButtonWidgetExtension removeButton = new ButtonWidgetExtension(panelX + 12, footerY, 76, 20, TextHelper.literal("Remove"), button -> send(true));
        removeButton.active = hadEntry;
        addChild(new ClickableWidget(removeButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + PANEL_WIDTH - 160, footerY, 72, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));

        saveButton = new ButtonWidgetExtension(panelX + PANEL_WIDTH - 84, footerY, 72, 20, TextHelper.literal("Save"), button -> send(false));
        addChild(new ClickableWidget(saveButton));

        setPage(page);
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        final int panelX = getPanelX();
        final int panelY = getPanelY();
        final int panelHeight = getPanelHeight();
        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, PANEL_WIDTH, panelHeight);

        minimumDwellText = minimumDwellField.getText2();
        for (int i = 0; i < offsetFields.length; i++) {
            offsetTexts[i] = offsetFields[i].getText2();
        }
        updateStatus();
        saveButton.active = valid;

        final int light = GraphicsHolder.getDefaultLight();
        graphicsHolder.drawText(TextHelper.literal("Route settings - " + route.getName().replace('|', ' ')), panelX + 12, panelY + 10, 0xFFFFFF, true, light);
        graphicsHolder.drawText(TextHelper.literal("Departure from each stop after the train leaves its depot."), panelX + 12, panelY + 24, 0xA0A0A0, true, light);
        graphicsHolder.drawText(TextHelper.literal("H:MM:SS; blank keeps the platform's dwell time."), panelX + 12, panelY + 36, 0xA0A0A0, true, light);
        graphicsHolder.drawText(TextHelper.literal("Minimum dwell time (all stops, late trains too)"), panelX + 12, panelY + 58, 0xE0E0E0, true, light);
        graphicsHolder.drawText(TextHelper.literal("Headcode class (9 > 1 > 2 > 3)"), panelX + 12, panelY + 82, 0xE0E0E0, true, light);
        graphicsHolder.drawText(TextHelper.literal("Stop"), panelX + 12, panelY + HEADER_HEIGHT - 12, 0x808080, true, light);
        graphicsHolder.drawText(TextHelper.literal("Departs +"), panelX + PANEL_WIDTH - 12 - FIELD_WIDTH, panelY + HEADER_HEIGHT - 12, 0x808080, true, light);

        if (offsetFields.length == 0) {
            graphicsHolder.drawText(TextHelper.literal("This route has no stops yet."), panelX + 12, panelY + HEADER_HEIGHT + 6, 0xFFAA00, true, light);
        }
        final int rowsPerPage = getRowsPerPage();
        final int first = page * rowsPerPage;
        for (int i = first; i < Math.min(offsetFields.length, first + rowsPerPage); i++) {
            final int rowY = panelY + HEADER_HEIGHT + (i - first) * ROW_HEIGHT;
            final String name = (i + 1) + ". " + stopNames[i];
            graphicsHolder.drawText(TextHelper.literal(GraphicsHolder.getTextWidth(name) > PANEL_WIDTH - FIELD_WIDTH - 40 ? trim(name, PANEL_WIDTH - FIELD_WIDTH - 40) : name), panelX + 12, rowY + 6, 0xFFFFFF, true, light);
        }

        final int footerY = panelY + panelHeight - 26;
        graphicsHolder.drawText(TextHelper.literal(String.format(Locale.ROOT, "Page %d/%d", page + 1, getPageCount())), panelX + 40, footerY - 18, 0xA0A0A0, true, light);
        graphicsHolder.drawText(TextHelper.literal(status), panelX + 140, footerY - 18, valid ? 0x55FF55 : 0xFF5555, true, light);

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public void onClose2() {
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
    }

    private void setPage(int newPage) {
        page = Math.max(0, Math.min(getPageCount() - 1, newPage));
        final int rowsPerPage = getRowsPerPage();
        final int first = page * rowsPerPage;
        final int panelY = getPanelY();
        for (int i = 0; i < offsetFields.length; i++) {
            final boolean visible = i >= first && i < first + rowsPerPage;
            offsetFields[i].setVisible2(visible);
            if (visible) {
                offsetFields[i].setY2(panelY + HEADER_HEIGHT + (i - first) * ROW_HEIGHT);
            }
        }
        if (previousPageButton != null) {
            previousPageButton.active = page > 0;
            nextPageButton.active = page < getPageCount() - 1;
        }
    }

    private void updateStatus() {
        final long[] offsets = new long[offsetTexts.length];
        final long minimumDwell;
        try {
            minimumDwell = Math.max(0, FlexibleTimetables.parseDuration(minimumDwellText));
            for (int i = 0; i < offsets.length; i++) {
                try {
                    offsets[i] = FlexibleTimetables.parseDuration(offsetTexts[i]);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Stop " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (IllegalArgumentException e) {
            setStatus(false, e.getMessage());
            return;
        }
        final String error = FlexibleTimetables.validate(offsets, minimumDwell);
        if (error != null) {
            setStatus(false, error);
            return;
        }
        int timed = 0;
        for (final long offset : offsets) {
            if (offset != FlexibleTimetables.NO_OFFSET) {
                timed++;
            }
        }
        setStatus(true, timed + " timed stop" + (timed == 1 ? "" : "s") + (headcodeClass == HeadcodePriority.NONE ? "" : ", class " + headcodeClass));
    }

    private void setStatus(boolean valid, String status) {
        this.valid = valid;
        this.status = GraphicsHolder.getTextWidth(status) > PANEL_WIDTH - 152 ? trim(status, PANEL_WIDTH - 152) : status;
    }

    private void send(boolean remove) {
        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeLong(route.getId());
        packet.writeBoolean(remove);
        if (remove) {
            packet.writeVarLong(0);
            packet.writeVarInt(0);
            packet.writeVarInt(0);
        } else {
            packet.writeVarLong(Math.max(0, FlexibleTimetables.parseDuration(minimumDwellText)));
            packet.writeVarInt(headcodeClass);
            packet.writeVarInt(offsetTexts.length);
            for (int i = 0; i < offsetTexts.length; i++) {
                packet.writeLong(platformIds[i]);
                packet.writeLong(FlexibleTimetables.parseDuration(offsetTexts[i]));
            }
        }
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ROUTE_TIMETABLE_PACKET_ID, packet);
        onClose2();
    }

    private int getRowsPerPage() {
        return Math.max(3, Math.min(12, (height - 24 - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT));
    }

    private int getPageCount() {
        return Math.max(1, (offsetTexts.length + getRowsPerPage() - 1) / getRowsPerPage());
    }

    private int getPanelHeight() {
        return HEADER_HEIGHT + getRowsPerPage() * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    private int getPanelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int getPanelY() {
        return Math.max(4, (height - getPanelHeight()) / 2);
    }

    private static String getStopName(Platform platform) {
        if (platform == null) {
            return "(unknown platform)";
        }
        final String stationName = platform.getStationName();
        final String station = stationName == null || stationName.isEmpty() ? "" : stationName.replace('|', ' ');
        return station.isEmpty() ? "Platform " + platform.getName() : station + " (" + platform.getName() + ")";
    }

    private static String trim(String text, int maxWidth) {
        String result = text;
        while (result.length() > 1 && GraphicsHolder.getTextWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
}
