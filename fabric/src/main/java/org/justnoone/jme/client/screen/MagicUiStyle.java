package org.justnoone.jme.client.screen;

import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;

public final class MagicUiStyle {

    private MagicUiStyle() {
    }

    public static void drawPanel(GraphicsHolder graphicsHolder, int panelX, int panelY, int panelWidth, int panelHeight) {
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xB0101010);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 22, 0x60000000);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2B2B2B);
        guiDrawing.finishDrawingRectangle();
    }
}
