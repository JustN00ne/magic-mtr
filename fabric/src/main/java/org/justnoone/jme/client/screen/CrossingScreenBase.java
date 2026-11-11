package org.justnoone.jme.client.screen;

import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.block.CrossingConfigPackets;
import org.justnoone.jme.block.CrossingSignal;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;

import java.util.function.IntConsumer;

/**
 * The look shared by the crossing screens (opened by right-clicking crossing equipment with MTR's brush): a slightly
 * transparent layer over the whole screen, so the crossing stays visible behind it, a title bar with optional tabs, and
 * Save / Cancel buttons. The game keeps running while it is open, so a gate's arm can be watched while it is set up.
 */
public abstract class CrossingScreenBase extends ScreenExtension {

    protected static final int BACKGROUND = 0xB0101418;
    protected static final int BAR = 0xD0000000;
    protected static final int SECTION = 0x40FFFFFF;
    protected static final int ACCENT = 0xFFE0B020;
    protected static final int TEXT = 0xFFFFFF;
    protected static final int TEXT_DIM = 0xA0A0A0;
    protected static final int TITLE_HEIGHT = 28;
    protected static final int MARGIN = 16;
    protected static final int BUTTON_HEIGHT = 20;

    protected final net.minecraft.util.math.BlockPos pos;
    private final String title;
    private final String[] tabs;
    private final int selectedTab;

    protected CrossingScreenBase(net.minecraft.util.math.BlockPos pos, String title, String[] tabs, int selectedTab) {
        this.pos = pos;
        this.title = title;
        this.tabs = tabs;
        this.selectedTab = selectedTab;
    }

    @Override
    protected void init2() {
        int x = MARGIN + GraphicsHolder.getTextWidth(title) + 24;
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            final int tabWidth = GraphicsHolder.getTextWidth(tabs[i]) + 20;
            addButton(x, 4, tabWidth, (i == selectedTab ? "§e§n" : "") + tabs[i], () -> {
                if (tab != selectedTab) {
                    switchTab(tab);
                }
            });
            x += tabWidth + 4;
        }
        addButton(width - MARGIN - 80, height - MARGIN - BUTTON_HEIGHT, 80, "Save", () -> {
            save();
            onClose2();
        });
        addButton(width - MARGIN - 164, height - MARGIN - BUTTON_HEIGHT, 80, "Cancel", this::onClose2);
        initContent();
    }

    /**
     * Adds the widgets of the screen (or of the selected tab).
     */
    protected abstract void initContent();

    /**
     * Draws the screen (or the selected tab) under its widgets.
     */
    protected abstract void renderContent(GraphicsHolder graphicsHolder, int mouseX, int mouseY);

    /**
     * Sends the settings to the server.
     */
    protected abstract void save();

    /**
     * Opens another tab of the screen, keeping the edited settings.
     */
    protected void switchTab(int tab) {
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        rectangle(graphicsHolder, 0, 0, width, height, BACKGROUND);
        rectangle(graphicsHolder, 0, 0, width, TITLE_HEIGHT, BAR);
        rectangle(graphicsHolder, 0, TITLE_HEIGHT - 1, width, TITLE_HEIGHT, ACCENT);
        graphicsHolder.drawText(title, MARGIN, 10, TEXT, true, GraphicsHolder.getDefaultLight());
        final String location = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        graphicsHolder.drawText(location, width - MARGIN - GraphicsHolder.getTextWidth(location), 10, TEXT_DIM, true, GraphicsHolder.getDefaultLight());
        renderContent(graphicsHolder, mouseX, mouseY);
        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen2() {
        return false;
    }

    protected ButtonWidgetExtension addButton(int x, int y, int buttonWidth, String label, Runnable onPress) {
        final ButtonWidgetExtension button = new ButtonWidgetExtension(x, y, buttonWidth, BUTTON_HEIGHT, TextHelper.literal(label), pressed -> onPress.run());
        addChild(new ClickableWidget(button));
        return button;
    }

    protected static void setLabel(ButtonWidgetExtension button, String label) {
        button.setMessage2(Text.cast(TextHelper.literal(label)));
    }

    protected static void open(ScreenExtension screen) {
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new org.mtr.mapping.holder.Screen(screen));
    }

    protected static void send(PacketByteBuf packet) {
        MagicNetworkingCompat.sendToServer(CrossingConfigPackets.PACKET_ID, packet);
    }

    protected static void rectangle(GraphicsHolder graphicsHolder, int x1, int y1, int x2, int y2, int color) {
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(x1, y1, x2, y2, color);
        guiDrawing.finishDrawingRectangle();
    }

    protected static void text(GraphicsHolder graphicsHolder, String text, int x, int y, int color) {
        graphicsHolder.drawText(text, x, y, color, true, GraphicsHolder.getDefaultLight());
    }

    /**
     * A heading with a thin line under it.
     */
    protected void heading(GraphicsHolder graphicsHolder, String text, int x, int y, int lineWidth) {
        text(graphicsHolder, text, x, y, ACCENT & 0xFFFFFF);
        rectangle(graphicsHolder, x, y + 10, x + lineWidth, y + 11, SECTION);
    }

    /**
     * Adds a row of buttons to choose a bell sound (or none), with a button that rings the chosen bell once at the
     * block.
     *
     * @return the height used
     */
    protected int addBellChooser(int x, int y, int maxWidth, int[] bell, IntConsumer onChange) {
        final int count = CrossingSignal.getBellCount() + 1;
        final ButtonWidgetExtension[] buttons = new ButtonWidgetExtension[count];
        int bx = x, by = y;
        for (int i = 0; i < count; i++) {
            final int value = i == 0 ? CrossingSignal.BELL_NONE : i - 1;
            final String name = CrossingSignal.getBellName(value);
            final int buttonWidth = GraphicsHolder.getTextWidth(name) + 24;
            if (bx + buttonWidth > x + maxWidth && bx > x) {
                bx = x;
                by += BUTTON_HEIGHT + 4;
            }
            buttons[i] = addButton(bx, by, buttonWidth, bellLabel(value, bell[0]), () -> {
                bell[0] = value;
                for (int j = 0; j < count; j++) {
                    final int other = j == 0 ? CrossingSignal.BELL_NONE : j - 1;
                    setLabel(buttons[j], bellLabel(other, bell[0]));
                }
                onChange.accept(value);
            });
            bx += buttonWidth + 4;
        }
        by += BUTTON_HEIGHT + 4;
        addButton(x, by, 120, "Preview sound", () -> send(CrossingConfigPackets.previewBell(pos, bell[0])));
        return by + BUTTON_HEIGHT - y;
    }

    private static String bellLabel(int value, int selected) {
        return (value == selected ? "§a● " : "") + CrossingSignal.getBellName(value);
    }

    protected static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.2f s", ticks / 20F);
    }
}
