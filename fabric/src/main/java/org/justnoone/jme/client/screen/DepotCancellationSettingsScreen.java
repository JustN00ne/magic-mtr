package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.DepotCancellationRegistry;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.SliderWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;

public class DepotCancellationSettingsScreen extends ScreenExtension {

    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 24 * 60;

    private final Screen parent;
    private final long depotId;
    private boolean enabled;
    private int thresholdMinutes;
    private DepotCancellationRegistry.Action action;

    private SliderWidgetExtension thresholdSlider;
    private ButtonWidgetExtension enabledButton;
    private ButtonWidgetExtension actionButton;

    public DepotCancellationSettingsScreen(ScreenExtension parent, long depotId) {
        this(new Screen(parent), depotId);
    }

    public DepotCancellationSettingsScreen(Screen parent, long depotId) {
        this.parent = parent;
        this.depotId = depotId;
        final DepotCancellationRegistry.Settings settings = DepotCancellationRegistry.get(depotId);
        this.enabled = settings.enabled;
        this.thresholdMinutes = jme$clampMinutes(settings.thresholdMinutes);
        this.action = settings.action;
    }

    @Override
    protected void init2() {
        final int panelWidth = 320;
        final int panelHeight = 172;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;

        enabledButton = new ButtonWidgetExtension(panelX + 12, panelY + 28, panelWidth - 24, 20, jme$getEnabledLabel(), button -> {
            enabled = !enabled;
            if (enabledButton != null) {
                enabledButton.setMessage2(Text.cast(jme$getEnabledLabel()));
            }
        });
        addChild(new ClickableWidget(enabledButton));

        thresholdSlider = new SliderWidgetExtension(panelX + 12, panelY + 68, panelWidth - 24, 20, "") {
            @Override
            protected void updateMessage2() {
                this.setMessage2(Text.cast(TextHelper.literal("Delay Threshold: " + thresholdMinutes + " min")));
            }

            @Override
            protected void applyValue2() {
                thresholdMinutes = jme$fromSliderValue(this.getValueMapped());
                updateMessage2();
            }
        };
        thresholdSlider.setValueMapped(jme$toSliderValue(thresholdMinutes));
        addChild(new ClickableWidget(thresholdSlider));

        actionButton = new ButtonWidgetExtension(panelX + 12, panelY + 102, panelWidth - 24, 20, jme$getActionLabel(), button -> {
            action = action == DepotCancellationRegistry.Action.DESPAWN ? DepotCancellationRegistry.Action.RETURN_TO_DEPOT : DepotCancellationRegistry.Action.DESPAWN;
            if (actionButton != null) {
                actionButton.setMessage2(Text.cast(jme$getActionLabel()));
            }
        });
        addChild(new ClickableWidget(actionButton));

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(panelX + panelWidth - 80, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Done"), button -> jme$saveAndClose());
        addChild(new ClickableWidget(doneButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + panelWidth - 156, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        final int panelWidth = 320;
        final int panelHeight = 172;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;

        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, panelWidth, panelHeight);

        graphicsHolder.drawText(TextHelper.literal("Cancellations"), panelX + 12, panelY + 12, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Cancel delayed trains for this depot"), panelX + 12, panelY + 46, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Action decides whether trains despawn instantly or wait until depot"), panelX + 12, panelY + 130, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public void onClose2() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
        }
    }

    private void jme$saveAndClose() {
        final DepotCancellationRegistry.Settings settings = new DepotCancellationRegistry.Settings(enabled, thresholdMinutes, action);
        DepotCancellationRegistry.set(depotId, settings);

        final PacketByteBuf packet = PacketByteBufs.create();
        packet.writeLong(depotId);
        packet.writeBoolean(enabled);
        packet.writeVarInt(jme$clampMinutes(thresholdMinutes));
        packet.writeString(action.getSerializedId());
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_DEPOT_CANCELLATION_PACKET_ID, packet);
        onClose2();
    }

    private org.mtr.mapping.holder.MutableText jme$getEnabledLabel() {
        return TextHelper.literal("Enabled: " + (enabled ? "ON" : "OFF"));
    }

    private org.mtr.mapping.holder.MutableText jme$getActionLabel() {
        final String actionText = action == DepotCancellationRegistry.Action.RETURN_TO_DEPOT ? "Return To Depot" : "Despawn";
        return TextHelper.literal("Action: " + actionText);
    }

    private static int jme$fromSliderValue(double value) {
        final int resolved = MIN_MINUTES + (int) Math.round(value * (MAX_MINUTES - MIN_MINUTES));
        return jme$clampMinutes(resolved);
    }

    private static double jme$toSliderValue(int minutes) {
        final int clamped = jme$clampMinutes(minutes);
        return (clamped - MIN_MINUTES) / (double) (MAX_MINUTES - MIN_MINUTES);
    }

    private static int jme$clampMinutes(int minutes) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }
}
