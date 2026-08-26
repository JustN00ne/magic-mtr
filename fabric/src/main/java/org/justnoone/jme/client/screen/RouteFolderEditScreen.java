package org.justnoone.jme.client.screen;

import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;

public class RouteFolderEditScreen extends ScreenExtension {

    public interface SaveHandler {
        void onSave(String name, int iconColor);
    }

    private final Screen parent;
    private final String title;
    private final SaveHandler saveHandler;
    private final String initialName;
    private final int initialColor;

    private TextFieldWidgetExtension nameField;
    private TextFieldWidgetExtension colorField;

    public RouteFolderEditScreen(ScreenExtension parent, String title, String initialName, int initialColor, SaveHandler saveHandler) {
        this(new Screen(parent), title, initialName, initialColor, saveHandler);
    }

    public RouteFolderEditScreen(Screen parent, String title, String initialName, int initialColor, SaveHandler saveHandler) {
        this.parent = parent;
        this.title = title == null || title.isEmpty() ? "Folder" : title;
        this.saveHandler = saveHandler;
        this.initialName = initialName == null ? "" : initialName;
        this.initialColor = initialColor;
    }

    @Override
    protected void init2() {
        final int panelWidth = 300;
        final int panelHeight = 176;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;

        nameField = new TextFieldWidgetExtension(panelX + 12, panelY + 38, panelWidth - 24, 20, 64, TextCase.DEFAULT, null, "");
        nameField.setText2(initialName);
        addChild(new ClickableWidget(nameField));

        colorField = new TextFieldWidgetExtension(panelX + 12, panelY + 84, panelWidth - 24, 20, 7, TextCase.DEFAULT, "[^0-9a-fA-F#]", "");
        colorField.setText2(jme$formatColor(initialColor));
        addChild(new ClickableWidget(colorField));

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(panelX + panelWidth - 80, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Done"), button -> {
            if (saveHandler != null) {
                saveHandler.onSave(jme$sanitizeName(nameField.getText2()), jme$parseColor(colorField.getText2(), initialColor));
            }
            onClose2();
        });
        addChild(new ClickableWidget(doneButton));

        final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(panelX + panelWidth - 156, panelY + panelHeight - 26, 68, 20, TextHelper.literal("Cancel"), button -> onClose2());
        addChild(new ClickableWidget(cancelButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        final int panelWidth = 300;
        final int panelHeight = 176;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;

        MagicUiStyle.drawPanel(graphicsHolder, panelX, panelY, panelWidth, panelHeight);

        graphicsHolder.drawText(TextHelper.literal(title), panelX + 12, panelY + 12, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Name"), panelX + 12, panelY + 28, 0xC0C0C0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Icon Color (#RRGGBB, optional)"), panelX + 12, panelY + 74, 0xC0C0C0, true, GraphicsHolder.getDefaultLight());

        final int previewColor = jme$parseColor(colorField == null ? "" : colorField.getText2(), initialColor);
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(panelX + panelWidth - 36, panelY + 84, panelX + panelWidth - 16, panelY + 104, 0xFF000000 | (previewColor >= 0 ? previewColor : 0x666666));
        guiDrawing.finishDrawingRectangle();

        super.render(graphicsHolder, mouseX, mouseY, delta);
    }

    @Override
    public void onClose2() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(parent);
        }
    }

    private static String jme$sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Folder";
        }
        return name.trim();
    }

    private static String jme$formatColor(int color) {
        if (color < 0) {
            return "";
        }
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static int jme$parseColor(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return -1;
        }

        String normalized = raw.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return -1;
        }

        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return fallback < 0 ? -1 : (fallback & 0xFFFFFF);
        }
    }
}
