package org.justnoone.jme.mixin;

import org.justnoone.jme.client.DashboardRailExporter;
import org.justnoone.jme.client.ui.OverlayClickHandler;
import org.mtr.core.Main;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.nio.file.Path;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenMagicMenuMixin implements IGui, OverlayClickHandler {

    @Unique
    private static final Identifier JME_MAGIC_ICON_TEXTURE = new Identifier("jme", "textures/item/magic_icon.png");

    // Hidden for the main release UI.
    @Unique
    private static final boolean JME_ENABLE_MAGIC_DASHBOARD_BUTTON = true;

    @Unique
    private ButtonWidgetExtension jme$magicMenuButton;
    @Unique
    private boolean jme$magicMenuButtonAdded;
    @Unique
    private boolean jme$magicMenuOpen;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jme$createMagicMenuButton(org.mtr.core.data.TransportMode transportMode, CallbackInfo ci) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            jme$magicMenuButton = null;
            jme$magicMenuButtonAdded = false;
            jme$magicMenuOpen = false;
            return;
        }

        jme$magicMenuButton = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(""), button -> jme$magicMenuOpen = !jme$magicMenuOpen);
        jme$magicMenuButtonAdded = false;
        jme$magicMenuOpen = false;
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initMagicMenuButton(CallbackInfo ci) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return;
        }

        if (jme$magicMenuButton == null) {
            return;
        }

        final WidgetMap widgetMap = ((DashboardScreenAccessor) this).jme$getWidgetMap();
        if (widgetMap == null) {
            return;
        }

        final int margin = 6;
        final int x = widgetMap.getX2() + widgetMap.getWidth2() - SQUARE_SIZE - margin;
        final int y = widgetMap.getY2() + margin;
        IDrawing.setPositionAndWidth(jme$magicMenuButton, x, y, SQUARE_SIZE);

        if (!jme$magicMenuButtonAdded) {
            jme$addChild(new ClickableWidget(jme$magicMenuButton));
            jme$magicMenuButtonAdded = true;
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderMagicMenu(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return;
        }

        if (jme$magicMenuButton == null) {
            return;
        }

        jme$drawMagicLogo(graphicsHolder);

        if (!jme$magicMenuOpen) {
            return;
        }

        final int panelWidth = 210;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int rows = 3;

        final int panelX = jme$magicMenuButton.getX2() + SQUARE_SIZE - panelWidth;
        final int panelY = jme$magicMenuButton.getY2() + SQUARE_SIZE + 4;
        final int panelHeight = rows * (rowHeight + gap) - gap;

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 700);

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();

        // Shadow + panel.
        guiDrawing.drawRectangle(panelX + 1, panelY + 1, panelX + panelWidth + 1, panelY + panelHeight + padding * 2 + 1, 0x66000000);
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xCC101014);

        // Border.
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY + panelHeight + padding * 2 - 1, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + panelHeight + padding * 2, 0xFF2B2B2B);
        guiDrawing.drawRectangle(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight + padding * 2, 0xFF2B2B2B);

        for (int i = 0; i < rows; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final boolean hovered = mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                guiDrawing.drawRectangle(panelX + 2, rowY, panelX + panelWidth - 2, rowY + rowHeight, 0x33FFFFFF);
            }
        }

        guiDrawing.finishDrawingRectangle();

        final String[] labels = new String[]{
                "Export PNG (viewport)",
                "Export SVG (viewport)",
                "Export SVG (all rails)",
        };

        for (int i = 0; i < labels.length; i++) {
            final int rowY = panelY + padding + i * (rowHeight + gap);
            final int textX = panelX + 10;
            final int textY = rowY + (rowHeight - TEXT_HEIGHT) / 2;
            graphicsHolder.drawText(TextHelper.literal(labels[i]), textX, textY, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
        }

        graphicsHolder.pop();
    }

    @Unique
    private void jme$drawMagicLogo(GraphicsHolder graphicsHolder) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return;
        }

        if (jme$magicMenuButton == null) {
            return;
        }

        final int iconSize = 14;
        final int iconX1 = jme$magicMenuButton.getX2() + (SQUARE_SIZE - iconSize) / 2;
        final int iconY1 = jme$magicMenuButton.getY2() + (SQUARE_SIZE - iconSize) / 2;
        final int iconX2 = iconX1 + iconSize;
        final int iconY2 = iconY1 + iconSize;

        graphicsHolder.push();
        graphicsHolder.translate(0, 0, 650);
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingTexture(JME_MAGIC_ICON_TEXTURE);
        IGui.drawTexture(guiDrawing, iconX1, iconY1, iconX2, iconY2, 0F, 0F, 1F, 1F);
        guiDrawing.finishDrawingTexture();
        graphicsHolder.pop();
    }

    @Unique
    @Override
    public boolean jme$handleOverlayClick(double mouseX, double mouseY, int button) {
        if (!JME_ENABLE_MAGIC_DASHBOARD_BUTTON) {
            return false;
        }

        if (jme$magicMenuButton == null) {
            return false;
        }

        // Toggle the menu button ourselves so the underlying WidgetMap can't steal the click.
        if (jme$isInMagicMenuButtonBounds(mouseX, mouseY)) {
            jme$magicMenuOpen = !jme$magicMenuOpen;
            return true;
        }

        if (!jme$magicMenuOpen) {
            return false;
        }

        final int panelWidth = 210;
        final int rowHeight = SQUARE_SIZE;
        final int gap = 2;
        final int padding = 6;
        final int rows = 3;
        final int panelX = jme$magicMenuButton.getX2() + SQUARE_SIZE - panelWidth;
        final int panelY = jme$magicMenuButton.getY2() + SQUARE_SIZE + 4;
        final int panelHeight = rows * (rowHeight + gap) - gap;
        final int panelX1 = panelX;
        final int panelY1 = panelY;
        final int panelX2 = panelX + panelWidth;
        final int panelY2 = panelY + panelHeight + padding * 2;

        // Clicking outside closes and consumes to avoid accidental map interactions.
        if (mouseX < panelX1 || mouseX > panelX2 || mouseY < panelY1 || mouseY > panelY2) {
            jme$magicMenuOpen = false;
            return true;
        }

        final double relativeY = mouseY - (panelY + padding);
        final int stride = rowHeight + gap;
        final int index = (int) Math.floor(relativeY / stride);
        final int inside = (int) Math.floor(relativeY - index * (double) stride);
        if (index < 0 || index >= rows || inside < 0 || inside >= rowHeight) {
            // Clicked the panel padding or a gap; consume.
            return true;
        }

        final WidgetMap widgetMap = ((DashboardScreenAccessor) this).jme$getWidgetMap();
        if (widgetMap != null) {
            try {
                final Path exported;
                if (index == 0) {
                    exported = DashboardRailExporter.exportRailsPngViewport(widgetMap);
                } else if (index == 1) {
                    exported = DashboardRailExporter.exportRailsSvgViewport(widgetMap);
                } else {
                    exported = DashboardRailExporter.exportRailsSvgFull();
                }
                if (exported != null) {
                    Main.LOGGER.info("[MAGIC] Exported rails to {}", exported);
                    jme$sendClientMessage("MAGIC: Exported rails to " + exported);
                } else {
                    jme$sendClientMessage("MAGIC: Export failed (map/rails not ready)");
                }
            } catch (Throwable throwable) {
                Main.LOGGER.warn("[MAGIC] Failed exporting rails", throwable);
                jme$sendClientMessage("MAGIC: Export failed (" + throwable.getClass().getSimpleName() + ")");
            }
        }

        jme$magicMenuOpen = false;
        return true;
    }

    @Unique
    private static boolean jme$isInButtonBounds(ButtonWidgetExtension button, double mouseX, double mouseY) {
        if (button == null) {
            return false;
        }
        final int x1 = button.getX2();
        final int y1 = button.getY2();
        final int x2 = x1 + button.getWidth2();
        final int y2 = y1 + button.getHeight2();
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Unique
    private boolean jme$isInMagicMenuButtonBounds(double mouseX, double mouseY) {
        final int x1 = jme$magicMenuButton.getX2();
        final int y1 = jme$magicMenuButton.getY2();
        final int x2 = x1 + SQUARE_SIZE;
        final int y2 = y1 + SQUARE_SIZE;
        return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
    }

    @Unique
    private static void jme$sendClientMessage(String message) {
        try {
            final MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            final ClientPlayerEntity player = client.getPlayerMapped();
            if (player == null) {
                return;
            }
            player.sendMessage(Text.cast(TextHelper.literal(message)), false);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private void jme$addChild(ClickableWidget clickableWidget) {
        try {
            Method addChildMethod = null;
            Class<?> targetClass = this.getClass();
            while (targetClass != null && addChildMethod == null) {
                for (final Method method : targetClass.getDeclaredMethods()) {
                    if ("addChild".equals(method.getName()) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(clickableWidget.getClass())) {
                        addChildMethod = method;
                        break;
                    }
                }
                targetClass = targetClass.getSuperclass();
            }

            if (addChildMethod != null) {
                addChildMethod.setAccessible(true);
                addChildMethod.invoke(this, clickableWidget);
            }
        } catch (Exception ignored) {
        }
    }
}
