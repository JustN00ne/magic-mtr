package org.justnoone.jme.mixin;

import net.minecraft.client.MinecraftClient;
import org.justnoone.jme.client.DashboardRouteFolderStore;
import org.justnoone.jme.client.screen.RouteFolderEditScreen;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenRouteFoldersMixin extends ScreenExtension implements IGui {

    @Shadow
    private Route editingRoute;

    @Shadow
    private int editingRoutePlatformIndex;

    @Shadow
    @Final
    private DashboardList dashboardList;

    @Unique
    private TextFieldWidgetExtension jme$folderRenameField;
    @Unique
    private ButtonWidgetExtension jme$folderRenameDoneButton;
    @Unique
    private ButtonWidgetExtension jme$folderRenameCancelButton;
    @Unique
    private boolean jme$folderRenameActive;
    @Unique
    private DashboardRouteFolderStore.RowMetadata jme$renamingRow;
    @Unique
    private boolean jme$dragActive;
    @Unique
    private int jme$dragFromIndex = -1;
    @Unique
    private int jme$dragToIndex = -1;
    @Unique
    private boolean jme$dragMoved;
    @Unique
    private boolean jme$leftMouseDownLastFrame;
    @Unique
    private double jme$dragVisualProgress;
    @Unique
    private boolean jme$dragReleasePhase;
    @Unique
    private double jme$dragGhostX;
    @Unique
    private double jme$dragGhostY;
    @Unique
    private double jme$dragGhostTargetX;
    @Unique
    private double jme$dragGhostTargetY;
    @Unique
    private String jme$dragGhostLabel = "";
    @Unique
    private int jme$dragGhostColor = ARGB_WHITE;
    @Unique
    private int jme$dropLineY = -1;
    @Unique
    private boolean jme$dragDropAfter;
    @Unique
    private boolean jme$renameWidgetsAdded;
    @Unique
    private boolean jme$rightMouseDownLastFrame;
    @Unique
    private boolean jme$contextMenuVisible;
    @Unique
    private DashboardRouteFolderStore.RowMetadata jme$contextRow;
    @Unique
    private int jme$contextVisibleIndex = -1;
    @Unique
    private ButtonWidgetExtension jme$contextMakeFolderButton;
    @Unique
    private ButtonWidgetExtension jme$contextEditFolderButton;
    @Unique
    private ButtonWidgetExtension jme$contextSortButton;
    @Unique
    private ButtonWidgetExtension jme$contextRemoveButton;
    @Unique
    private ButtonWidgetExtension jme$contextDuplicateButton;
    @Unique
    private boolean jme$contextButtonsAdded;
    @Unique
    private int jme$contextMenuX;
    @Unique
    private int jme$contextMenuY;
    @Unique
    private int jme$contextMenuWidth;
    @Unique
    private int jme$contextMenuHeight;

    protected DashboardScreenRouteFoldersMixin() {
        super();
    }

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$initFolderRenameWidgets(CallbackInfo ci) {
        if (jme$contextMakeFolderButton == null) {
            jme$contextMakeFolderButton = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 4, SQUARE_SIZE, TextHelper.literal("Make Folder"), button -> jme$onContextMakeFolder());
            jme$contextEditFolderButton = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 4, SQUARE_SIZE, TextHelper.literal("Edit Folder"), button -> jme$onContextEditFolder());
            jme$contextSortButton = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 4, SQUARE_SIZE, TextHelper.literal("Sort"), button -> jme$onContextSort());
            jme$contextRemoveButton = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 4, SQUARE_SIZE, TextHelper.literal("Remove"), button -> jme$onContextRemove());
            jme$contextDuplicateButton = new ButtonWidgetExtension(0, 0, SQUARE_SIZE * 4, SQUARE_SIZE, TextHelper.literal("Duplicate"), button -> jme$onContextDuplicate());
        }

        if (!jme$contextButtonsAdded) {
            jme$addChild(new ClickableWidget(jme$contextMakeFolderButton));
            jme$addChild(new ClickableWidget(jme$contextEditFolderButton));
            jme$addChild(new ClickableWidget(jme$contextSortButton));
            jme$addChild(new ClickableWidget(jme$contextRemoveButton));
            jme$addChild(new ClickableWidget(jme$contextDuplicateButton));
            jme$contextButtonsAdded = true;
        }

        jme$hideContextMenu();
        jme$updateFolderRenameVisibility();
    }

    @Inject(method = "toggleButtons", at = @At("TAIL"), remap = false)
    private void jme$toggleFolderRenameWidgets(CallbackInfo ci) {
        jme$hideContextMenu();
        jme$updateFolderRenameVisibility();
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false)
    private void jme$injectFolderRows(CallbackInfo ci) {
        if (editingRoute == null) {
            return;
        }
        final List<DashboardListItem> routeRows = DashboardRouteFolderStore.buildRows(editingRoute);
        dashboardList.setData(new ObjectArrayList<>(routeRows), false, true, true, false, false, true);
    }

    @Inject(method = "onEdit", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$onEditRouteRow(DashboardListItem dashboardListItem, int index, CallbackInfo ci) {
        if (editingRoute == null) {
            return;
        }
        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, index);
        if (rowMetadata == null) {
            return;
        }

        if (rowMetadata.folder) {
            jme$openFolderEditScreen(rowMetadata);
            ci.cancel();
            return;
        }

        if (rowMetadata.platformIndex >= 0) {
            ((DashboardScreenAccessor) this).jme$startEditingRouteDestination(rowMetadata.platformIndex);
            ci.cancel();
        }
    }

    @Inject(method = "onDelete", at = @At("HEAD"), cancellable = true, remap = false)
    private void jme$onDeleteRouteRow(DashboardListItem dashboardListItem, int index, CallbackInfo ci) {
        if (editingRoute == null) {
            return;
        }
        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, index);
        if (rowMetadata == null) {
            return;
        }

        if (rowMetadata.folder) {
            if (jme$removeFolderSafe(index, rowMetadata)) {
                jme$syncRoute();
            }
            ci.cancel();
            return;
        }

        if (rowMetadata.platformIndex >= 0 || rowMetadata.platformId != 0) {
            final int resolvedIndex = jme$resolveRoutePlatformIndex(rowMetadata);
            if (resolvedIndex < 0 || resolvedIndex >= editingRoute.getRoutePlatforms().size()) {
                ci.cancel();
                return;
            }
            if (rowMetadata.platformId != 0) {
                DashboardRouteFolderStore.removePlatformFromFolders(editingRoute, rowMetadata.platformId);
            }
            editingRoute.getRoutePlatforms().remove(resolvedIndex);
            jme$syncRoute();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void jme$updateDragDrop(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (editingRoute == null || jme$folderRenameActive) {
            jme$clearDragState();
            jme$hideContextMenu();
            return;
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            jme$clearDragState();
            jme$hideContextMenu();
            return;
        }

        final boolean leftMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        final boolean rightMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (leftMouseDown && !jme$leftMouseDownLastFrame) {
            if (jme$contextMenuVisible && !jme$isInContextMenuBounds(mouseX, mouseY) && !jme$isInListBounds(mouseX, mouseY)) {
                jme$hideContextMenu();
            }
            if (jme$isInListBounds(mouseX, mouseY)) {
                jme$hideContextMenu();
                jme$startDrag(mouseX, mouseY);
            }
        } else if (leftMouseDown && jme$dragActive) {
            jme$updateDragTarget(mouseX, mouseY);
        } else if (!leftMouseDown && jme$leftMouseDownLastFrame && jme$dragActive) {
            jme$finishDrag();
        }

        if (rightMouseDown && !jme$rightMouseDownLastFrame) {
            if (jme$isInListBounds(mouseX, mouseY)) {
                jme$handleRightClick(mouseX, mouseY);
            } else if (!jme$isInContextMenuBounds(mouseX, mouseY)) {
                jme$hideContextMenu();
            }
        }

        jme$leftMouseDownLastFrame = leftMouseDown;
        jme$rightMouseDownLastFrame = rightMouseDown;
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void jme$renderFolderRenameLabel(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        jme$renderDragAnimation(graphicsHolder, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void jme$openContextMenuOnRightClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || editingRoute == null || jme$folderRenameActive) {
            return;
        }

        if (!jme$isInListBounds(mouseX, mouseY)) {
            jme$hideContextMenu();
            return;
        }

        jme$handleRightClick((int) mouseX, (int) mouseY);
        cir.setReturnValue(true);
    }

    @Inject(method = "stopEditing", at = @At("TAIL"), remap = false)
    private void jme$stopRenameOnStopEditing(CallbackInfo ci) {
        jme$cancelFolderRename();
        jme$clearDragState();
        jme$hideContextMenu();
    }

    @Inject(method = "onClose2", at = @At("TAIL"), remap = false)
    private void jme$stopRenameOnClose(CallbackInfo ci) {
        jme$cancelFolderRename();
        jme$clearDragState();
        jme$hideContextMenu();
    }

    @Unique
    private void jme$startFolderRename(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        jme$renamingRow = rowMetadata;
        jme$folderRenameActive = true;
        jme$folderRenameField.setText2(DashboardRouteFolderStore.getFolderName(editingRoute, rowMetadata));
        jme$updateFolderRenameVisibility();
    }

    @Unique
    private void jme$finishFolderRename() {
        if (!jme$folderRenameActive || editingRoute == null || jme$renamingRow == null) {
            return;
        }
        DashboardRouteFolderStore.renameFolder(editingRoute, jme$renamingRow, jme$folderRenameField.getText2());
        jme$cancelFolderRename();
    }

    @Unique
    private void jme$cancelFolderRename() {
        jme$folderRenameActive = false;
        jme$renamingRow = null;
        jme$updateFolderRenameVisibility();
    }

    @Unique
    private void jme$updateFolderRenameVisibility() {
        if (jme$folderRenameField == null) {
            return;
        }
        final boolean visible = jme$folderRenameActive && editingRoute != null;
        jme$folderRenameField.visible = visible;
        jme$folderRenameDoneButton.visible = visible;
        jme$folderRenameCancelButton.visible = visible;
        jme$folderRenameField.active = visible;
        jme$folderRenameDoneButton.active = visible;
        jme$folderRenameCancelButton.active = visible;
    }

    @Unique
    private void jme$syncRoute() {
        jme$syncRoute(editingRoute);
    }

    @Unique
    private void jme$syncRoute(Route route) {
        if (route == null) {
            return;
        }
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addRoute(route)));
    }

    @Unique
    private void jme$startDrag(double mouseX, double mouseY) {
        if (!jme$isInListBounds(mouseX, mouseY)) {
            return;
        }
        if (mouseX >= dashboardList.x + dashboardList.width - SQUARE_SIZE * 4) {
            return;
        }
        dashboardList.mouseMoved(mouseX, mouseY);
        final int hoverIndex = dashboardList.getHoverItemIndex();
        if (hoverIndex < 0) {
            return;
        }
        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, hoverIndex);
        if (rowMetadata == null || rowMetadata.child) {
            return;
        }
        jme$dragActive = true;
        jme$dragReleasePhase = false;
        jme$dragFromIndex = hoverIndex;
        jme$dragToIndex = hoverIndex;
        jme$dragMoved = false;
        jme$dragGhostTargetX = mouseX;
        jme$dragGhostTargetY = mouseY;
        jme$dragGhostX = mouseX;
        jme$dragGhostY = mouseY;
        jme$dragGhostLabel = jme$getRowLabel(rowMetadata);
        jme$dragGhostColor = jme$getRowColor(rowMetadata);
        jme$dragDropAfter = false;
        jme$updateDropIndicator(mouseY, false);
    }

    @Unique
    private void jme$updateDragTarget(double mouseX, double mouseY) {
        jme$dragGhostTargetX = mouseX;
        jme$dragGhostTargetY = mouseY;
        dashboardList.mouseMoved(mouseX, mouseY);
        int hoverIndex = dashboardList.getHoverItemIndex();
        if (hoverIndex < 0) {
            return;
        }
        final DashboardRouteFolderStore.RowMetadata sourceRow = DashboardRouteFolderStore.getRow(editingRoute, jme$dragFromIndex);
        if (sourceRow == null) {
            return;
        }

        final int listTop = jme$getDashboardListTopY();
        final int localY = Math.max(0, (int) mouseY - listTop);
        final boolean preferAfter = localY % SQUARE_SIZE >= SQUARE_SIZE / 2;
        jme$dragDropAfter = preferAfter;
        jme$updateDropIndicator(mouseY, preferAfter);

        if (sourceRow.folder) {
            hoverIndex = DashboardRouteFolderStore.resolveFolderDropTarget(editingRoute, hoverIndex, preferAfter);
            if (hoverIndex < 0) {
                return;
            }
        }

        final DashboardRouteFolderStore.RowMetadata targetRow = DashboardRouteFolderStore.getRow(editingRoute, hoverIndex);
        if (targetRow == null || targetRow.child) {
            return;
        }

        if (hoverIndex != jme$dragToIndex) {
            jme$dragMoved = true;
            jme$dragToIndex = hoverIndex;
        }
    }

    @Unique
    private void jme$finishDrag() {
        if (editingRoute == null || jme$dragFromIndex < 0) {
            jme$clearDragState();
            return;
        }

        if (jme$dragMoved && jme$dragToIndex >= 0 && jme$dragFromIndex != jme$dragToIndex) {
            if (DashboardRouteFolderStore.handleDrag(editingRoute, jme$dragFromIndex, jme$dragToIndex, jme$dragDropAfter)) {
                jme$syncRoute();
            }
        } else {
            jme$handleRowClick(jme$dragFromIndex);
        }

        jme$dragActive = false;
        jme$dragFromIndex = -1;
        jme$dragToIndex = -1;
        jme$dragMoved = false;
        jme$leftMouseDownLastFrame = false;
        jme$dragReleasePhase = true;
        jme$dragDropAfter = false;
        jme$dropLineY = -1;
    }

    @Unique
    private void jme$handleRowClick(int visibleIndex) {
        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, visibleIndex);
        if (rowMetadata == null) {
            return;
        }

        if (rowMetadata.folder) {
            DashboardRouteFolderStore.toggleFolder(editingRoute, visibleIndex);
            return;
        }

        if (rowMetadata.platformIndex >= 0) {
            ((DashboardScreenAccessor) this).jme$startEditingRouteDestination(rowMetadata.platformIndex);
        }
    }

    @Unique
    private void jme$clearDragState() {
        jme$dragActive = false;
        jme$dragReleasePhase = false;
        jme$dragFromIndex = -1;
        jme$dragToIndex = -1;
        jme$dragMoved = false;
        jme$leftMouseDownLastFrame = false;
        jme$rightMouseDownLastFrame = false;
        jme$dragVisualProgress = 0;
        jme$dragGhostLabel = "";
        jme$dragDropAfter = false;
        jme$dropLineY = -1;
    }

    @Unique
    private void jme$renderDragAnimation(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
        final int listTop = jme$getDashboardListTopY();
        final int visibleRows = jme$getDashboardVisibleRows();
        if (visibleRows > 0) {
            final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
            guiDrawing.beginDrawingRectangle();
            final int separatorX1 = dashboardList.x + 2;
            final int separatorX2 = dashboardList.x + dashboardList.width - 2;
            for (int row = 1; row < visibleRows; row++) {
                final int y = listTop + row * SQUARE_SIZE;
                guiDrawing.drawRectangle(separatorX1, y, separatorX2, y + 1, 0x332C2C2C);
            }
            guiDrawing.finishDrawingRectangle();
        }

        jme$renderContextMenuBox(graphicsHolder);

        if (jme$dragActive) {
            jme$dragGhostTargetX = mouseX;
            jme$dragGhostTargetY = mouseY;
        }

        if (jme$dragActive && jme$dropLineY >= 0) {
            final int lineStartX = dashboardList.x + TEXT_PADDING;
            final int lineEndX = dashboardList.x + dashboardList.width - TEXT_PADDING;
            final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
            guiDrawing.beginDrawingRectangle();
            guiDrawing.drawRectangle(lineStartX, jme$dropLineY - 1, lineEndX, jme$dropLineY + 2, 0xFFE4E4E4);
            guiDrawing.finishDrawingRectangle();
        }

        final double targetProgress = jme$dragActive ? 1D : 0D;
        jme$dragVisualProgress += (targetProgress - jme$dragVisualProgress) * 0.28D;
        if (!jme$dragActive && jme$dragVisualProgress < 0.02D) {
            jme$dragVisualProgress = 0;
            jme$dragReleasePhase = false;
            if (!jme$dragGhostLabel.isEmpty()) {
                jme$dragGhostLabel = "";
            }
            return;
        }

        jme$dragGhostX += (jme$dragGhostTargetX - jme$dragGhostX) * 0.35D;
        jme$dragGhostY += (jme$dragGhostTargetY - jme$dragGhostY) * 0.35D;
        if (jme$dragGhostLabel.isEmpty()) {
            return;
        }

        final double riseOffset = -12D * jme$dragVisualProgress;
        final double sinkOffset = jme$dragReleasePhase ? (1D - jme$dragVisualProgress) * 8D : 0D;
        final double scale = 1D - 0.07D * jme$dragVisualProgress;

        graphicsHolder.push();
        graphicsHolder.translate(jme$dragGhostX + 12D, jme$dragGhostY + riseOffset + sinkOffset, 720D);
        graphicsHolder.scale((float) scale, (float) scale, 1F);

        final int textWidth = GraphicsHolder.getTextWidth(jme$dragGhostLabel);
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        final int alpha = Math.max(0x30, (int) (0xB0 * jme$dragVisualProgress));
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(-6, -11, textWidth + 8, 7, (alpha << 24) | 0x00121212);
        guiDrawing.finishDrawingRectangle();
        graphicsHolder.drawText(jme$dragGhostLabel, -3, -7, jme$dragGhostColor, false, GraphicsHolder.getDefaultLight());

        graphicsHolder.pop();
    }

    @Unique
    private void jme$updateDropIndicator(double mouseY, boolean preferAfter) {
        final int listTop = jme$getDashboardListTopY();
        final int visibleRows = jme$getDashboardVisibleRows();
        if (visibleRows <= 0) {
            jme$dropLineY = -1;
            return;
        }

        final int listBottom = listTop + visibleRows * SQUARE_SIZE;
        final int clampedMouseY = (int) Math.max(listTop, Math.min(listBottom - 1, mouseY));
        final int slot = Math.max(0, Math.min(visibleRows - 1, (clampedMouseY - listTop) / SQUARE_SIZE));
        jme$dropLineY = listTop + slot * SQUARE_SIZE + (preferAfter ? SQUARE_SIZE : 0);
    }

    @Unique
    private int jme$getDashboardListTopY() {
        return dashboardList.y + SQUARE_SIZE + TEXT_FIELD_PADDING;
    }

    @Unique
    private int jme$getDashboardVisibleRows() {
        return Math.max(0, (dashboardList.height - (SQUARE_SIZE + TEXT_FIELD_PADDING)) / SQUARE_SIZE);
    }

    @Unique
    private String jme$getRowLabel(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null) {
            return "";
        }
        if (rowMetadata.folder) {
            return DashboardRouteFolderStore.getFolderName(editingRoute, rowMetadata);
        }
        if (rowMetadata.platformIndex < 0 || rowMetadata.platformIndex >= editingRoute.getRoutePlatforms().size()) {
            return "";
        }

        final Platform platform = editingRoute.getRoutePlatforms().get(rowMetadata.platformIndex).platform;
        if (platform == null) {
            return "";
        }
        if (platform.area == null) {
            return platform.getName();
        }
        return platform.area.getName() + " (" + platform.getName() + ")";
    }

    @Unique
    private int jme$getRowColor(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null) {
            return ARGB_WHITE;
        }
        final int index = rowMetadata.folder ? rowMetadata.startIndex : rowMetadata.platformIndex;
        if (index < 0 || index >= editingRoute.getRoutePlatforms().size()) {
            return ARGB_WHITE;
        }
        final Platform platform = editingRoute.getRoutePlatforms().get(index).platform;
        if (platform == null || platform.area == null) {
            return ARGB_WHITE;
        }
        return (platform.area.getColor() & RGB_WHITE) | ARGB_BLACK;
    }

    @Unique
    private void jme$handleRightClick(int mouseX, int mouseY) {
        if (editingRoute == null) {
            jme$hideContextMenu();
            return;
        }

        dashboardList.mouseMoved(mouseX, mouseY);
        final int hoverIndex = dashboardList.getHoverItemIndex();
        if (hoverIndex < 0) {
            jme$hideContextMenu();
            return;
        }

        final DashboardRouteFolderStore.RowMetadata rowMetadata = DashboardRouteFolderStore.getRow(editingRoute, hoverIndex);
        if (rowMetadata == null) {
            jme$hideContextMenu();
            return;
        }

        jme$showContextMenuForRow(rowMetadata, hoverIndex, mouseX, mouseY);
    }

    @Unique
    private void jme$showContextMenuForRow(DashboardRouteFolderStore.RowMetadata rowMetadata, int visibleIndex, int mouseX, int mouseY) {
        if (rowMetadata == null || jme$contextMakeFolderButton == null) {
            return;
        }

        jme$contextMenuVisible = true;
        jme$contextRow = rowMetadata;
        jme$contextVisibleIndex = visibleIndex;

        final boolean isFolder = rowMetadata.folder;
        final boolean isPlatform = rowMetadata.platformIndex >= 0;
        final boolean showSort = isFolder;
        final boolean showRemove = isPlatform || isFolder;

        final int menuWidth = SQUARE_SIZE * 5;
        final int startX = Math.max(2, Math.min(PANEL_WIDTH - menuWidth - 2, mouseX + 6));
        int startY = Math.max(SQUARE_SIZE + 2, mouseY - 6);

        int rows = 0;
        rows += 1; // make folder
        rows += isFolder ? 1 : 0; // edit folder
        rows += showSort ? 1 : 0; // sort folder
        rows += showRemove ? 1 : 0; // remove
        rows += isPlatform ? 1 : 0; // duplicate
        final int maxY = Math.max(SQUARE_SIZE + 2, height - rows * (SQUARE_SIZE + 1) - 2);
        startY = Math.min(startY, maxY);

        jme$contextMenuX = startX - 2;
        jme$contextMenuY = startY - 2;
        jme$contextMenuWidth = menuWidth + 4;
        jme$contextMenuHeight = rows * (SQUARE_SIZE + 1) + 3;

        int row = 0;
        row = jme$placeContextButton(jme$contextMakeFolderButton, true, TextHelper.literal("Make Folder"), startX, startY, row);
        row = jme$placeContextButton(jme$contextEditFolderButton, isFolder, TextHelper.literal("Edit Folder"), startX, startY, row);
        row = jme$placeContextButton(jme$contextSortButton, showSort, TextHelper.literal("Sort Folder"), startX, startY, row);
        row = jme$placeContextButton(jme$contextRemoveButton, showRemove, TextHelper.literal(isFolder ? "Delete Folder" : "Remove"), startX, startY, row);
        jme$placeContextButton(jme$contextDuplicateButton, isPlatform, TextHelper.literal("Duplicate"), startX, startY, row);
    }

    @Unique
    private int jme$placeContextButton(ButtonWidgetExtension button, boolean visible, org.mtr.mapping.holder.MutableText label, int startX, int startY, int row) {
        if (button == null) {
            return row;
        }
        button.visible = visible;
        button.active = visible;
        if (!visible) {
            return row;
        }
        button.setMessage2(Text.cast(label));
        IDrawing.setPositionAndWidth(button, startX, startY + row * (SQUARE_SIZE + 1), SQUARE_SIZE * 5);
        return row + 1;
    }

    @Unique
    private void jme$hideContextMenu() {
        jme$contextMenuVisible = false;
        jme$contextRow = null;
        jme$contextVisibleIndex = -1;
        jme$contextMenuX = 0;
        jme$contextMenuY = 0;
        jme$contextMenuWidth = 0;
        jme$contextMenuHeight = 0;
        jme$setContextButtonVisible(jme$contextMakeFolderButton, false);
        jme$setContextButtonVisible(jme$contextEditFolderButton, false);
        jme$setContextButtonVisible(jme$contextSortButton, false);
        jme$setContextButtonVisible(jme$contextRemoveButton, false);
        jme$setContextButtonVisible(jme$contextDuplicateButton, false);
    }

    @Unique
    private void jme$setContextButtonVisible(ButtonWidgetExtension button, boolean visible) {
        if (button == null) {
            return;
        }
        button.visible = visible;
        button.active = visible;
    }

    @Unique
    private void jme$onContextMakeFolder() {
        if (editingRoute == null) {
            jme$hideContextMenu();
            return;
        }

        final Route route = editingRoute;
        final DashboardRouteFolderStore.RowMetadata contextRow = jme$contextRow;
        jme$hideContextMenu();
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new RouteFolderEditScreen(new Screen(this), "Create Folder", "", -1, (name, iconColor) -> {
            if (DashboardRouteFolderStore.createFolder(route, contextRow, name, iconColor)) {
                jme$syncRoute(route);
            }
        })));
    }

    @Unique
    private void jme$onContextEditFolder() {
        if (editingRoute == null || jme$contextRow == null || !jme$contextRow.folder) {
            jme$hideContextMenu();
            return;
        }

        final DashboardRouteFolderStore.RowMetadata rowMetadata = jme$contextRow;
        jme$hideContextMenu();
        jme$openFolderEditScreen(rowMetadata);
    }

    @Unique
    private void jme$onContextSort() {
        if (editingRoute == null || jme$contextRow == null) {
            jme$hideContextMenu();
            return;
        }

        boolean changed = false;
        if (jme$contextRow.folder) {
            changed = DashboardRouteFolderStore.sortFolder(editingRoute, jme$contextRow);
        }

        jme$hideContextMenu();
        if (changed) {
            jme$syncRoute();
        }
    }

    @Unique
    private void jme$onContextRemove() {
        if (editingRoute == null || jme$contextRow == null) {
            jme$hideContextMenu();
            return;
        }

        if (jme$contextRow.folder) {
            if (jme$removeFolderSafe(jme$contextVisibleIndex, jme$contextRow)) {
                jme$hideContextMenu();
                jme$syncRoute();
            } else {
                jme$hideContextMenu();
            }
            return;
        }

        if (jme$contextRow.platformIndex < 0 && jme$contextRow.platformId == 0) {
            jme$hideContextMenu();
            return;
        }

        final int resolvedIndex = jme$resolveRoutePlatformIndex(jme$contextRow);
        if (resolvedIndex < 0 || resolvedIndex >= editingRoute.getRoutePlatforms().size()) {
            jme$hideContextMenu();
            return;
        }

        if (jme$contextRow.platformId != 0) {
            DashboardRouteFolderStore.removePlatformFromFolders(editingRoute, jme$contextRow.platformId);
        }
        editingRoute.getRoutePlatforms().remove(resolvedIndex);
        jme$hideContextMenu();
        jme$syncRoute();
    }

    @Unique
    private void jme$onContextDuplicate() {
        if (editingRoute == null || jme$contextRow == null || jme$contextRow.platformIndex < 0 || jme$contextRow.platformIndex >= editingRoute.getRoutePlatforms().size()) {
            jme$hideContextMenu();
            return;
        }

        final RoutePlatformData source = editingRoute.getRoutePlatforms().get(jme$contextRow.platformIndex);
        if (source == null || source.platform == null) {
            jme$hideContextMenu();
            return;
        }

        final RoutePlatformData duplicate = new RoutePlatformData(source.platform.getId());
        duplicate.setCustomDestination(source.getCustomDestination());
        duplicate.writePlatformCache(editingRoute, MinecraftClientData.getDashboardInstance().platformIdMap);
        editingRoute.getRoutePlatforms().add(jme$contextRow.platformIndex + 1, duplicate);

        jme$hideContextMenu();
        jme$syncRoute();
    }

    @Unique
    private void jme$openFolderEditScreen(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null || !rowMetadata.folder) {
            return;
        }

        final Route route = editingRoute;
        final String folderName = DashboardRouteFolderStore.getFolderName(route, rowMetadata);
        final int folderColor = DashboardRouteFolderStore.getFolderColor(route, rowMetadata);
        org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new Screen(new RouteFolderEditScreen(new Screen(this), "Edit Folder", folderName, folderColor, (name, iconColor) -> {
            if (DashboardRouteFolderStore.setFolderAppearance(route, rowMetadata, name, iconColor)) {
                jme$syncRoute(route);
            }
        })));
    }

    @Unique
    private int jme$resolveRoutePlatformIndex(DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null) {
            return -1;
        }

        if (rowMetadata.platformIndex >= 0 && rowMetadata.platformIndex < editingRoute.getRoutePlatforms().size()) {
            final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(rowMetadata.platformIndex);
            if (routePlatformData != null && routePlatformData.platform != null && (rowMetadata.platformId == 0 || routePlatformData.platform.getId() == rowMetadata.platformId)) {
                return rowMetadata.platformIndex;
            }
        }

        if (rowMetadata.platformId != 0) {
            for (int i = 0; i < editingRoute.getRoutePlatforms().size(); i++) {
                final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(i);
                if (routePlatformData != null && routePlatformData.platform != null && routePlatformData.platform.getId() == rowMetadata.platformId) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Unique
    private boolean jme$removeFolderSafe(int visibleIndex, DashboardRouteFolderStore.RowMetadata rowMetadata) {
        if (editingRoute == null || rowMetadata == null || !rowMetadata.folder) {
            return false;
        }

        if (DashboardRouteFolderStore.removeFolder(editingRoute, rowMetadata)) {
            return true;
        }

        if (visibleIndex < 0) {
            return false;
        }

        final DashboardRouteFolderStore.RowMetadata refreshedRow = DashboardRouteFolderStore.getRow(editingRoute, visibleIndex);
        return refreshedRow != null && refreshedRow.folder && DashboardRouteFolderStore.removeFolder(editingRoute, refreshedRow);
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

    @Unique
    private void jme$renderContextMenuBox(GraphicsHolder graphicsHolder) {
        if (!jme$contextMenuVisible || jme$contextMenuWidth <= 0 || jme$contextMenuHeight <= 0) {
            return;
        }
        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(jme$contextMenuX, jme$contextMenuY, jme$contextMenuX + jme$contextMenuWidth, jme$contextMenuY + jme$contextMenuHeight, 0xEE161616);
        guiDrawing.drawRectangle(jme$contextMenuX, jme$contextMenuY, jme$contextMenuX + jme$contextMenuWidth, jme$contextMenuY + 1, 0xFF3A3A3A);
        guiDrawing.drawRectangle(jme$contextMenuX, jme$contextMenuY + jme$contextMenuHeight - 1, jme$contextMenuX + jme$contextMenuWidth, jme$contextMenuY + jme$contextMenuHeight, 0xFF2A2A2A);
        guiDrawing.drawRectangle(jme$contextMenuX, jme$contextMenuY, jme$contextMenuX + 1, jme$contextMenuY + jme$contextMenuHeight, 0xFF3A3A3A);
        guiDrawing.drawRectangle(jme$contextMenuX + jme$contextMenuWidth - 1, jme$contextMenuY, jme$contextMenuX + jme$contextMenuWidth, jme$contextMenuY + jme$contextMenuHeight, 0xFF2A2A2A);
        guiDrawing.finishDrawingRectangle();
    }

    @Unique
    private boolean jme$isInContextMenuBounds(double mouseX, double mouseY) {
        if (!jme$contextMenuVisible || jme$contextMenuWidth <= 0 || jme$contextMenuHeight <= 0) {
            return false;
        }
        return mouseX >= jme$contextMenuX && mouseX < jme$contextMenuX + jme$contextMenuWidth && mouseY >= jme$contextMenuY && mouseY < jme$contextMenuY + jme$contextMenuHeight;
    }

    @Unique
    private boolean jme$isInListBounds(double mouseX, double mouseY) {
        final int listLeft = dashboardList.x;
        final int listTop = jme$getDashboardListTopY();
        final int listRight = dashboardList.x + dashboardList.width;
        final int listBottom = listTop + jme$getDashboardVisibleRows() * SQUARE_SIZE;
        return mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom;
    }
}
