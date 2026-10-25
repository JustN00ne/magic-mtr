package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.CronTimetableRegistry;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.util.CronExpression;
import org.justnoone.jme.util.CronTimetables;
import org.mtr.core.data.Depot;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Edits a depot's cron timetable. Opened from MTR's depot screen.
 */
public class CronTimetableScreen extends ScreenExtension {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 236;
    private static final int PREVIEW_COUNT = 4;
    private static final DateTimeFormatter PREVIEW_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);

    private final Screen parent;
    private final Depot depot;
    private final boolean hadEntry;
    private boolean enabled;
    private boolean realTime;
    private String expressionText;
    private String zoneText;

    private TextFieldWidgetExtension expressionField;
    private TextFieldWidgetExtension zoneField;
    private ButtonWidgetExtension enabledButton;
    private ButtonWidgetExtension clockButton;
    private ButtonWidgetExtension saveButton;

    private String cachedKey;
    private String cachedStatus = "";
    private boolean cachedValid;
    private String[] cachedPreview = new String[0];

    public CronTimetableScreen(ScreenExtension parent, Depot depot) {
        this.parent = new Screen(parent);
        this.depot = depot;
        final CronTimetableRegistry.Entry entry = CronTimetableRegistry.get(depot.getId());
        hadEntry = entry != null;
        enabled = entry == null || entry.enabled;
        expressionText = entry == null ? "*/30 5-23 * * *" : entry.expression;
        zoneText = entry == null ? "" : entry.zoneId;
        realTime = depot.getUseRealTime();
    }

    @Override
    protected void init2() {
        final int panelX = (width - PANEL_WIDTH) / 2;
        final int panelY = (height - PANEL_HEIGHT) / 2;
        final int innerWidth = PANEL_WIDTH - 24;

        expressionField = new TextFieldWidgetExtension(panelX + 12, panelY + 38, innerWidth, 20, 128, TextCase.DEFAULT, null, "minute hour day month weekday");
        expressionField.setText2(expressionText);
        addChild(new ClickableWidget(expressionField));

        zoneField = new TextFieldWidgetExtension(panelX + 12, panelY + 82, innerWidth / 2 - 2, 20, 64, TextCase.DEFAULT, null, ZoneId.systemDefault().getId());
        zoneField.setText2(zoneText);
        addChild(new ClickableWidget(zoneField));

        clockButton = new ButtonWidgetExtension(panelX + 12 + innerWidth / 2 + 2, panelY + 82, innerWidth / 2 - 2, 20, getClockLabel(), button -> {
            realTime = !realTime;
            clockButton.setMessage2(Text.cast(getClockLabel()));
            cachedKey = null;
        });
        addChild(new ClickableWidget(clockButton));

        enabledButton = new ButtonWidgetExtension(panelX + 12, panelY + PANEL_HEIGHT - 26, 76, 20, getEnabledLabel(), button -> {
            enabled = !enabled;
            enabledButton.setMessage2(Text.cast(getEnabledLabel()));
        });
        addChild(new ClickableWidget(enabledButton));

        final ButtonWidgetExtension removeButton = new ButtonWidgetExtension(panelX + 92, panelY + PANEL_HEIGHT - 26, 76, 20, TextHelper.literal("Remove"), button -> send(true));
        removeButton.active = hadEntry;
        addChild(new ClickableWidget(removeButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + PANEL_WIDTH - 160, panelY + PANEL_HEIGHT - 26, 72, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));

        saveButton = new ButtonWidgetExtension(panelX + PANEL_WIDTH - 84, panelY + PANEL_HEIGHT - 26, 72, 20, TextHelper.literal("Save"), button -> send(false));
        addChild(new ClickableWidget(saveButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);
        final int panelX = (width - PANEL_WIDTH) / 2;
        final int panelY = (height - PANEL_HEIGHT) / 2;
        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        expressionText = expressionField.getText2();
        zoneText = zoneField.getText2();
        updateStatus();
        saveButton.active = cachedValid;

        final int light = GraphicsHolder.getDefaultLight();
        graphicsHolder.drawText(TextHelper.literal("Cron Timetable - " + depot.getName()), panelX + 12, panelY + 10, 0xFFFFFF, true, light);
        graphicsHolder.drawText(TextHelper.literal("Expression: minute hour day month weekday"), panelX + 12, panelY + 26, 0xA0A0A0, true, light);
        graphicsHolder.drawText(TextHelper.literal("Time zone (blank = server)"), panelX + 12, panelY + 70, 0xA0A0A0, true, light);
        graphicsHolder.drawText(TextHelper.literal(cachedStatus), panelX + 12, panelY + 110, cachedValid ? 0x55FF55 : 0xFF5555, true, light);
        for (int i = 0; i < cachedPreview.length; i++) {
            graphicsHolder.drawText(TextHelper.literal(cachedPreview[i]), panelX + 20, panelY + 122 + i * 10, 0xE0E0E0, true, light);
        }
        graphicsHolder.drawText(TextHelper.literal("Examples: */30 5-23 * * 1-5 | 0,15,30,45 * * * *"), panelX + 12, panelY + 168, 0x808080, true, light);
        graphicsHolder.drawText(TextHelper.literal("0 0 25 12 * | @hourly. Replaces this depot's MTR schedule."), panelX + 12, panelY + 178, 0x808080, true, light);
        if (!enabled) {
            graphicsHolder.drawText(TextHelper.literal("Disabled: the depot keeps its MTR schedule."), panelX + 12, panelY + 192, 0xFFAA00, true, light);
        }

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public void onClose2() {
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
    }

    private void updateStatus() {
        final String key = expressionText + "\n" + zoneText + "\n" + realTime;
        if (key.equals(cachedKey)) {
            return;
        }
        cachedKey = key;

        final CronExpression cron;
        try {
            cron = CronExpression.parse(expressionText);
        } catch (IllegalArgumentException e) {
            setStatus(false, e.getMessage(), new String[0]);
            return;
        }
        final String zoneError = CronTimetables.validateZone(zoneText);
        if (zoneError != null) {
            setStatus(false, zoneError, new String[0]);
            return;
        }

        final int perDay = cron.getMinutesOfDay().length;
        if (perDay == 0) {
            setStatus(false, "This expression never departs", new String[0]);
            return;
        }

        if (realTime) {
            final ZoneId zone = CronTimetables.resolveZone(zoneText);
            final List<ZonedDateTime> next = CronTimetables.preview(cron, zone, System.currentTimeMillis(), PREVIEW_COUNT);
            if (next.isEmpty()) {
                setStatus(false, "This expression never departs (check the day and month)", new String[0]);
                return;
            }
            final String[] preview = new String[next.size()];
            for (int i = 0; i < next.size(); i++) {
                preview[i] = PREVIEW_FORMAT.format(next.get(i)) + " " + zone.getId();
            }
            setStatus(true, "Valid, up to " + perDay + " departures a day. Next:", preview);
        } else {
            final int[] minutesOfDay = cron.getMinutesOfDay();
            final String[] preview = new String[Math.min(PREVIEW_COUNT, minutesOfDay.length)];
            for (int i = 0; i < preview.length; i++) {
                preview[i] = String.format(Locale.ROOT, "%02d:%02d (Minecraft time)", minutesOfDay[i] / 60, minutesOfDay[i] % 60);
            }
            setStatus(true, perDay + " departures a Minecraft day" + (cron.hasDateRestriction() ? " (day fields ignored)" : "") + ". First:", preview);
        }
    }

    private void setStatus(boolean valid, String status, String[] preview) {
        cachedValid = valid;
        cachedStatus = status;
        cachedPreview = preview;
    }

    private void send(boolean remove) {
        if (!remove) {
            // Keep MTR's client-side copy of the depot in step, so closing MTR's depot screen (which uploads the depot)
            // does not switch the clock back.
            depot.setUseRealTime(realTime);
        }
        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeLong(depot.getId());
        packet.writeBoolean(remove);
        packet.writeString(expressionText.trim());
        packet.writeString(zoneText.trim());
        packet.writeBoolean(enabled);
        packet.writeBoolean(realTime);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_CRON_TIMETABLE_PACKET_ID, packet);
        onClose2();
    }

    private org.mtr.mapping.holder.MutableText getEnabledLabel() {
        return TextHelper.literal(enabled ? "Enabled" : "Disabled");
    }

    private org.mtr.mapping.holder.MutableText getClockLabel() {
        return TextHelper.literal(realTime ? "Clock: Real world" : "Clock: Minecraft");
    }
}
