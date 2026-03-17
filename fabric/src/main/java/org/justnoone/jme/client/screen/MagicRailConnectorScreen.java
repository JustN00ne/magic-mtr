package org.justnoone.jme.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.MagicRailConstants;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.justnoone.jme.rail.MagicRailTiltRegistry;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.PartCondition;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;

public class MagicRailConnectorScreen extends ScreenExtension {

    private static final Identifier RAIL_PREVIEW_TEXTURE = new Identifier("mtr", "textures/block/rail_preview.png");
    private static final int PREVIEW_MASK_COLOR = 0xFF101010;
    private static final int PREVIEW_RAIL_HALF_LENGTH = 18;
    private static final String[] SP1900_VEHICLE_ID_CANDIDATES = {
            "sp1900_hk_trailer",
            "sp1900_hk_cab_1",
            "sp1900_hk_cab_2",
            "sp1900_hk_cab_3",
            "sp1900_trailer",
            "sp1900_cab_1",
            "sp1900_cab_2",
            "sp1900_cab_3"
    };

    private final Hand hand;
    private int speedKmh;
    private String styleId;
    private Rail.Shape shape;
    private int tiltStartDegrees;
    private int tiltMiddleDegrees;
    private int tiltEndDegrees;

    private SliderWidgetExtension speedSlider;
    private SliderWidgetExtension tiltStartSlider;
    private SliderWidgetExtension tiltMiddleSlider;
    private SliderWidgetExtension tiltEndSlider;
    private ButtonWidgetExtension styleButton;
    private ButtonWidgetExtension previewTrainToggleButton;
    private boolean previewTrainVisible;
    private long previewAnimationStartMillis;
    private String previewVehicleId;

    public MagicRailConnectorScreen(Hand hand) {
        this.hand = hand;
        this.speedKmh = getCurrentSpeedFromHeldItem();
        this.styleId = getCurrentStyleFromHeldItem();
        this.shape = getCurrentShapeFromHeldItem();
        this.tiltStartDegrees = getCurrentStartTiltFromHeldItem();
        this.tiltMiddleDegrees = getCurrentMiddleTiltFromHeldItem();
        this.tiltEndDegrees = getCurrentEndTiltFromHeldItem();
        this.previewTrainVisible = true;
        this.previewAnimationStartMillis = System.currentTimeMillis();
    }

    @Override
    protected void init2() {
        final int panelWidth = 368;
        final int panelHeight = 248;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;
        final int leftWidth = 148;
        final int rightX = panelX + leftWidth + 16;
        final int rightWidth = panelWidth - leftWidth - 28;
        final int previewX = panelX + 8;
        final int previewY = panelY + 8;
        final int previewW = leftWidth - 16;

        speedSlider = new SliderWidgetExtension(rightX, panelY + 28, rightWidth, 20, "") {
            @Override
            protected void updateMessage2() {
                this.setMessage2(Text.cast(TextHelper.literal(JmeConfig.formatSpeedLabel(speedKmh))));
            }

            @Override
            protected void applyValue2() {
                speedKmh = fromSliderValue(this.getValueMapped());
                updateMessage2();
            }
        };
        speedSlider.setValueMapped(toSliderValue(speedKmh));
        addChild(new ClickableWidget(speedSlider));

        styleButton = new ButtonWidgetExtension(rightX, panelY + 62, rightWidth, 20, getStyleButtonLabel(), button -> openStyleScreen());
        addChild(new ClickableWidget(styleButton));

        final ButtonWidgetExtension easingButton = new ButtonWidgetExtension(rightX, panelY + 96, rightWidth, 20, getEasingButtonLabel(), button -> {
            shape = MagicRailConstants.nextShape(shape);
            button.setMessage(Text.cast(getEasingButtonLabel()));
        });
        addChild(new ClickableWidget(easingButton));

        tiltStartSlider = createTiltSlider(rightX, panelY + 130, rightWidth, () -> tiltStartDegrees, value -> tiltStartDegrees = value);
        tiltMiddleSlider = createTiltSlider(rightX, panelY + 164, rightWidth, () -> tiltMiddleDegrees, value -> tiltMiddleDegrees = value);
        tiltEndSlider = createTiltSlider(rightX, panelY + 198, rightWidth, () -> tiltEndDegrees, value -> tiltEndDegrees = value);
        addChild(new ClickableWidget(tiltStartSlider));
        addChild(new ClickableWidget(tiltMiddleSlider));
        addChild(new ClickableWidget(tiltEndSlider));

        previewTrainToggleButton = new ButtonWidgetExtension(previewX + previewW - 18, previewY + 4, 14, 14, getPreviewTrainToggleLabel(), button -> {
            previewTrainVisible = !previewTrainVisible;
            button.setMessage(Text.cast(getPreviewTrainToggleLabel()));
            previewAnimationStartMillis = System.currentTimeMillis();
        });
        addChild(new ClickableWidget(previewTrainToggleButton));

        final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(rightX + rightWidth - 86, panelY + panelHeight - 28, 86, 20, TextHelper.literal("Done"), button -> {
            saveNow();
            onClose2();
        });
        addChild(new ClickableWidget(doneButton));
    }

    @Override
    public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
        renderBackground(graphicsHolder);

        final int panelWidth = 368;
        final int panelHeight = 248;
        final int panelX = (width - panelWidth) / 2;
        final int panelY = (height - panelHeight) / 2;
        final int leftWidth = 148;
        final int rightX = panelX + leftWidth + 16;
        final int rightWidth = panelWidth - leftWidth - 28;

        final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xB0101010);

        final int previewX = panelX + 8;
        final int previewY = panelY + 8;
        final int previewW = leftWidth - 16;
        final int previewH = panelHeight - 16;

        guiDrawing.drawRectangle(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF000000);
        guiDrawing.finishDrawingRectangle();

        graphicsHolder.drawText(TextHelper.literal("Preview"), previewX + 6, previewY + 6, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        jme$withScissor(previewX + 2, previewY + 2, previewW - 4, previewH - 4, () -> drawPreview3D(graphicsHolder, previewX + 8, previewY + 24, previewW - 16, previewH - 32, delta));
        guiDrawing.beginDrawingRectangle();
        guiDrawing.drawRectangle(panelX, panelY, previewX, panelY + panelHeight, PREVIEW_MASK_COLOR);
        guiDrawing.drawRectangle(previewX + previewW, panelY, panelX + panelWidth, panelY + panelHeight, PREVIEW_MASK_COLOR);
        guiDrawing.drawRectangle(previewX, panelY, previewX + previewW, previewY, PREVIEW_MASK_COLOR);
        guiDrawing.drawRectangle(previewX, previewY + previewH, previewX + previewW, panelY + panelHeight, PREVIEW_MASK_COLOR);
        guiDrawing.drawRectangle(previewX, previewY, previewX + previewW, previewY + 1, 0xFF2B2B2B);
        guiDrawing.drawRectangle(previewX, previewY + previewH - 1, previewX + previewW, previewY + previewH, 0xFF2B2B2B);
        guiDrawing.drawRectangle(previewX, previewY, previewX + 1, previewY + previewH, 0xFF2B2B2B);
        guiDrawing.drawRectangle(previewX + previewW - 1, previewY, previewX + previewW, previewY + previewH, 0xFF2B2B2B);
        guiDrawing.finishDrawingRectangle();

        graphicsHolder.drawText(TextHelper.literal(JmeConfig.formatSpeedLabel(speedKmh)), previewX + 10, previewY + 14, MagicRailSpeedColor.colorForSpeed(speedKmh), true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal(MagicRailConstants.getStyleLabel(styleId)), previewX + 10, previewY + 26, 0xDDDDDD, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal(MagicRailConstants.getShapeLabel(shape)), previewX + 10, previewY + 38, 0xDDDDDD, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt " + tiltStartDegrees + "/" + tiltMiddleDegrees + "/" + tiltEndDegrees), previewX + 10, previewY + 50, 0xB0B0B0, true, GraphicsHolder.getDefaultLight());

        graphicsHolder.drawText(TextHelper.literal("Rail Connector"), rightX, panelY + 8, 0xFFFFFF, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Speed"), rightX, panelY + 16, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Rail Model"), rightX, panelY + 50, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Easing"), rightX, panelY + 84, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt Start"), rightX, panelY + 118, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt Middle"), rightX, panelY + 152, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());
        graphicsHolder.drawText(TextHelper.literal("Tilt End"), rightX, panelY + 186, 0xA0A0A0, true, GraphicsHolder.getDefaultLight());

        super.render(graphicsHolder, mouseX, mouseY, delta);

        // Redraw only the preview frame after widgets. Avoid masking the right panel widgets/sliders.
        final GuiDrawing postGuiDrawing = new GuiDrawing(graphicsHolder);
        postGuiDrawing.beginDrawingRectangle();
        postGuiDrawing.drawRectangle(previewX, previewY, previewX + previewW, previewY + 1, 0xFF2B2B2B);
        postGuiDrawing.drawRectangle(previewX, previewY + previewH - 1, previewX + previewW, previewY + previewH, 0xFF2B2B2B);
        postGuiDrawing.drawRectangle(previewX, previewY, previewX + 1, previewY + previewH, 0xFF2B2B2B);
        postGuiDrawing.drawRectangle(previewX + previewW - 1, previewY, previewX + previewW, previewY + previewH, 0xFF2B2B2B);
        postGuiDrawing.finishDrawingRectangle();
    }

    private void drawPreview3D(GraphicsHolder graphicsHolder, int x, int y, int width, int height, float delta) {
        graphicsHolder.push();
        graphicsHolder.translate(x + width / 2F, y + height / 2F + 16F, 220F);
        final float previewScale = Math.max(12F, Math.min(16F, Math.min(width, height) * 0.13F));
        graphicsHolder.scale(previewScale, -previewScale, previewScale);
        graphicsHolder.rotateXDegrees(31F);
        graphicsHolder.rotateYDegrees(36F);

        final Rail previewRail = createPreviewRail();
        if (previewRail != null) {
            final int speedColor = MagicRailSpeedColor.colorForSpeed(speedKmh);
            final boolean renderedModel = drawPreviewRailModels(graphicsHolder, previewRail, GraphicsHolder.getDefaultLight());
            if (!renderedModel) {
                drawPreviewRailColorSurface(graphicsHolder, previewRail, GraphicsHolder.getDefaultLight(), speedColor);
            }
            drawPreviewTrainModel(graphicsHolder, previewRail, GraphicsHolder.getDefaultLight(), speedColor);
        }

        graphicsHolder.pop();
    }

    private Rail createPreviewRail() {
        try {
            final ObjectArrayList<String> styles = new ObjectArrayList<>();
            styles.add(styleId);
            return Rail.newRail(
                    new Position(-PREVIEW_RAIL_HALF_LENGTH, 0, 0), Angle.E,
                    new Position(PREVIEW_RAIL_HALF_LENGTH, 0, 0), Angle.E,
                    shape, 0,
                    styles,
                    speedKmh, speedKmh,
                    false, false, false, true, true, TransportMode.TRAIN
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean drawPreviewRailModels(GraphicsHolder graphicsHolder, Rail rail, int light) {
        final RailResource railResource = resolvePreviewRailResource();
        if (railResource == null) {
            return false;
        }

        final boolean flip = styleId != null && styleId.endsWith("_2");
        final OptimizedModelWrapper optimizedModel = railResource.getOptimizedModel();
        if (optimizedModel == null) {
            return false;
        }

        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double differenceX = x3 - x1;
            final double differenceZ = z3 - z1;
            final double yaw = Math.atan2(differenceZ, differenceX);
            final double pitch = Math.atan2(y2 - y1, Math.sqrt(differenceX * differenceX + differenceZ * differenceZ));
            final double tiltDegrees = getSignedPreviewTiltForSlice(x1, z1, x2, z2, x3, z3, x4, z4);
            final StoredMatrixTransformations transform = new StoredMatrixTransformations((x1 + x3) / 2, (y1 + y2) / 2 + railResource.getModelYOffset(), (z1 + z3) / 2);
            transform.add(holder -> {
                holder.rotateYRadians((float) (Math.PI / 2 - yaw + (flip ? Math.PI : 0)));
                holder.rotateXRadians((float) (Math.PI - pitch * (flip ? -1 : 1)));
                holder.rotateZDegrees((float) tiltDegrees);
            });
            transform.transform(graphicsHolder, Vector3d.getZeroMapped());
            CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(optimizedModel, graphicsHolder, light);
            graphicsHolder.pop();
        }, Math.max(0.35, railResource.getRepeatInterval()), 0, 0);
        CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(false);
        CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(true);
        return true;
    }

    private RailResource resolvePreviewRailResource() {
        final String requestedStyle = styleId == null || styleId.isEmpty() ? MagicRailConstants.DEFAULT_STYLE : styleId;
        final String normalizedStyle = RailResource.getIdWithoutDirection(requestedStyle);
        final ObjectArrayList<String> candidates = new ObjectArrayList<>();
        jme$addStyleCandidate(candidates, requestedStyle);
        jme$addStyleCandidate(candidates, normalizedStyle);
        if (MagicRailConstants.DEFAULT_STYLE.equals(normalizedStyle)) {
            // Prefer modeled default rails in preview to avoid fallback surface artifacts.
            jme$addStyleCandidate(candidates, CustomResourceLoader.DEFAULT_RAIL_3D_ID);
            jme$addStyleCandidate(candidates, CustomResourceLoader.DEFAULT_RAIL_3D_SIDING_ID);
        }
        jme$addStyleCandidate(candidates, MagicRailConstants.DEFAULT_STYLE);

        for (String candidate : candidates) {
            final RailResource[] resolvedRail = {null};
            CustomResourceLoader.getRailById(candidate, railResource -> resolvedRail[0] = railResource);
            if (resolvedRail[0] != null) {
                return resolvedRail[0];
            }
        }
        return null;
    }

    private static void jme$addStyleCandidate(ObjectArrayList<String> candidates, String value) {
        if (value != null && !value.isEmpty() && !candidates.contains(value)) {
            candidates.add(value);
        }
    }

    private void drawPreviewRailColorSurface(GraphicsHolder graphicsHolder, Rail rail, int light, int speedColor) {
        graphicsHolder.createVertexConsumer(RenderLayer.getEntityCutoutNoCull(RAIL_PREVIEW_TEXTURE));
        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final float textureOffset = ((int) (x1 + z1) % 4) * 0.25F;
            final double signedTiltDegrees = getSignedPreviewTiltForSlice(x1, z1, x2, z2, x3, z3, x4, z4);
            final double startYOffset = getBankYOffset(x1, z1, x2, z2, signedTiltDegrees);
            final double endYOffset = getBankYOffset(x3, z3, x4, z4, signedTiltDegrees);
            IDrawing.drawTexture(
                    graphicsHolder,
                    x1, y1 + 0.065625 + startYOffset, z1,
                    x2, y1 + 0.065625 - startYOffset, z2,
                    x3, y2 + 0.065625 + endYOffset, z3,
                    x4, y2 + 0.065625 - endYOffset, z4,
                    Vector3d.getZeroMapped(),
                    0, 0.1875F + textureOffset, 1, 0.3125F + textureOffset,
                    Direction.getUpMapped(), speedColor, light
            );
            IDrawing.drawTexture(
                    graphicsHolder,
                    x2, y1 + 0.065625 - startYOffset, z2,
                    x1, y1 + 0.065625 + startYOffset, z1,
                    x4, y2 + 0.065625 - endYOffset, z4,
                    x3, y2 + 0.065625 + endYOffset, z3,
                    Vector3d.getZeroMapped(),
                    0, 0.1875F + textureOffset, 1, 0.3125F + textureOffset,
                    Direction.getUpMapped(), speedColor, light
            );
        }, 0.5, -0.2F, 0.2F);
    }

    private void drawPreviewTrainModel(GraphicsHolder graphicsHolder, Rail rail, int light, int color) {
        if (!previewTrainVisible) {
            return;
        }

        final double railLength = rail.railMath.getLength();
        if (railLength < 0.01) {
            return;
        }

        final double speedMetersPerSecond = Math.max(0.1, speedKmh / 3.6);
        final double elapsedSeconds = (System.currentTimeMillis() - previewAnimationStartMillis) / 1000D;
        final double distance = (elapsedSeconds * speedMetersPerSecond) % railLength;
        final double sampleDistance = Math.max(0.05, Math.min(0.35, railLength / 48));

        final Vector position = rail.railMath.getPosition(distance, false);
        final Vector positionBehind = rail.railMath.getPosition(Math.max(0, distance - sampleDistance), false);
        final Vector positionAhead = rail.railMath.getPosition(Math.min(railLength, distance + sampleDistance), false);
        final double differenceX = positionAhead.x() - positionBehind.x();
        final double differenceY = positionAhead.y() - positionBehind.y();
        final double differenceZ = positionAhead.z() - positionBehind.z();

        if (differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ < 1.0E-6) {
            return;
        }

        final double yaw = Math.atan2(differenceZ, differenceX);
        final double pitch = Math.atan2(differenceY, Math.sqrt(differenceX * differenceX + differenceZ * differenceZ));
        final double progress = clamp01(distance / railLength);
        final double tiltDegrees = getPreviewTiltForProgress(progress);
        final VehicleResource vehicleResource = getPreviewVehicleResource();
        if (vehicleResource == null) {
            drawFallbackTrainMarker(graphicsHolder, position, yaw, pitch, tiltDegrees, light, color);
            return;
        }

        final VehicleResourceCache vehicleResourceCache = vehicleResource.getCachedVehicleResource(0, 1, false);
        if (vehicleResourceCache == null || vehicleResourceCache.optimizedModelsDoorsClosed == null || vehicleResourceCache.optimizedModelsDoorsClosed.isEmpty()) {
            drawFallbackTrainMarker(graphicsHolder, position, yaw, pitch, tiltDegrees, light, color);
            return;
        }

        final float vehicleScale = 0.13F;
        graphicsHolder.push();
        graphicsHolder.translate(position.x(), position.y() + 0.16, position.z());
        graphicsHolder.rotateYRadians((float) (Math.PI / 2 - yaw));
        graphicsHolder.rotateXRadians((float) (Math.PI - pitch));
        graphicsHolder.rotateZDegrees((float) tiltDegrees);
        graphicsHolder.scale(vehicleScale, vehicleScale, vehicleScale);

        if (!drawPreviewVehicleCache(graphicsHolder, vehicleResourceCache, light)) {
            graphicsHolder.pop();
            drawFallbackTrainMarker(graphicsHolder, position, yaw, pitch, tiltDegrees, light, color);
            return;
        }

        graphicsHolder.pop();
    }

    private boolean drawPreviewVehicleCache(GraphicsHolder graphicsHolder, VehicleResourceCache vehicleResourceCache, int light) {
        boolean rendered = false;
        rendered |= queuePreviewVehiclePart(vehicleResourceCache, PartCondition.NORMAL, graphicsHolder, light);
        rendered |= queuePreviewVehiclePart(vehicleResourceCache, PartCondition.AT_DEPOT, graphicsHolder, light);
        rendered |= queuePreviewVehiclePart(vehicleResourceCache, PartCondition.DOORS_CLOSED, graphicsHolder, light);
        if (!rendered) {
            for (OptimizedModelWrapper wrapper : vehicleResourceCache.optimizedModelsDoorsClosed.values()) {
                if (wrapper != null) {
                    CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(wrapper, graphicsHolder, light);
                    rendered = true;
                }
            }
        }
        if (rendered) {
            CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(false);
            CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(true);
        }
        return rendered;
    }

    private boolean queuePreviewVehiclePart(VehicleResourceCache vehicleResourceCache, PartCondition partCondition, GraphicsHolder graphicsHolder, int light) {
        final OptimizedModelWrapper wrapper = vehicleResourceCache.optimizedModelsDoorsClosed.get(partCondition);
        if (wrapper == null) {
            return false;
        }
        CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(wrapper, graphicsHolder, light);
        return true;
    }

    private VehicleResource getPreviewVehicleResource() {
        final String resolvedVehicleId = getPreviewVehicleId();
        if (resolvedVehicleId == null || resolvedVehicleId.isEmpty()) {
            return null;
        }

        final VehicleResource[] vehicleResource = {null};
        CustomResourceLoader.getVehicleById(TransportMode.TRAIN, resolvedVehicleId, resourcePair -> vehicleResource[0] = resourcePair.left());
        return vehicleResource[0];
    }

    private String getPreviewVehicleId() {
        if (previewVehicleId != null && !previewVehicleId.isEmpty()) {
            return previewVehicleId;
        }

        final Object2ObjectAVLTreeMap<String, Object2ObjectAVLTreeMap<String, ObjectArrayList<String>>> vehicleTags = CustomResourceLoader.getVehicleTags(TransportMode.TRAIN);
        if (vehicleTags != null) {
            final Object2ObjectAVLTreeMap<String, ObjectArrayList<String>> familyTags = vehicleTags.get("family");
            if (familyTags != null) {
                final ObjectArrayList<String> sp1900Family = familyTags.get("sp1900");
                if (sp1900Family != null && !sp1900Family.isEmpty()) {
                    previewVehicleId = sp1900Family.get(0);
                    return previewVehicleId;
                }
            }
        }

        for (String candidate : SP1900_VEHICLE_ID_CANDIDATES) {
            final String[] matchedId = {null};
            CustomResourceLoader.getVehicleById(TransportMode.TRAIN, candidate, resourcePair -> {
                if (resourcePair != null && resourcePair.left() != null) {
                    matchedId[0] = resourcePair.left().getId();
                }
            });
            if (matchedId[0] != null) {
                previewVehicleId = matchedId[0];
                return previewVehicleId;
            }
        }

        final String[] fallbackId = {null};
        CustomResourceLoader.getVehicleByIndex(TransportMode.TRAIN, 0, vehicleResource -> fallbackId[0] = vehicleResource.getId());
        previewVehicleId = fallbackId[0];
        return previewVehicleId;
    }

    private void drawFallbackTrainMarker(GraphicsHolder graphicsHolder, Vector position, double yaw, double pitch, double tiltDegrees, int light, int color) {
        graphicsHolder.createVertexConsumer(RenderLayer.getEntityCutoutNoCull(RAIL_PREVIEW_TEXTURE));
        graphicsHolder.push();
        graphicsHolder.translate(position.x(), position.y() + 0.14, position.z());
        graphicsHolder.rotateYRadians((float) (Math.PI / 2 - yaw));
        graphicsHolder.rotateXRadians((float) (Math.PI - pitch));
        graphicsHolder.rotateZDegrees((float) tiltDegrees);

        final double halfWidth = 0.12;
        final double halfLength = 0.42;
        final double halfHeight = 0.07;
        final int sideColor = (color & 0xFFFEFEFE) >> 1;

        IDrawing.drawTexture(
                graphicsHolder,
                -halfWidth, halfHeight, -halfLength,
                halfWidth, halfHeight, -halfLength,
                -halfWidth, halfHeight, halfLength,
                halfWidth, halfHeight, halfLength,
                Vector3d.getZeroMapped(),
                0, 0, 1, 1,
                Direction.getUpMapped(), color, light
        );

        IDrawing.drawTexture(
                graphicsHolder,
                -halfWidth, -halfHeight, -halfLength,
                -halfWidth, halfHeight, -halfLength,
                -halfWidth, -halfHeight, halfLength,
                -halfWidth, halfHeight, halfLength,
                Vector3d.getZeroMapped(),
                0, 0, 1, 1,
                Direction.getUpMapped(), sideColor, light
        );
        IDrawing.drawTexture(
                graphicsHolder,
                halfWidth, halfHeight, -halfLength,
                halfWidth, -halfHeight, -halfLength,
                halfWidth, halfHeight, halfLength,
                halfWidth, -halfHeight, halfLength,
                Vector3d.getZeroMapped(),
                0, 0, 1, 1,
                Direction.getUpMapped(), sideColor, light
        );
        IDrawing.drawTexture(
                graphicsHolder,
                -halfWidth, -halfHeight, halfLength,
                halfWidth, -halfHeight, halfLength,
                -halfWidth, halfHeight, halfLength,
                halfWidth, halfHeight, halfLength,
                Vector3d.getZeroMapped(),
                0, 0, 1, 1,
                Direction.getUpMapped(), sideColor, light
        );

        graphicsHolder.pop();
    }

    private void openStyleScreen() {
        if (client != null) {
            org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(new org.mtr.mapping.holder.Screen(MagicRailModelSelectionScreen.create(this, styleId, selectedStyleId -> {
                styleId = selectedStyleId;
                if (styleButton != null) {
                    styleButton.setMessage2(Text.cast(getStyleButtonLabel()));
                }
            })));
        }
    }

    private void saveNow() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return;
        }

        final ItemStack stack = player.getStackInHand(hand);
        if (!MagicRailConstants.isUniversalConnector(stack)) {
            return;
        }

        MagicRailConstants.setSpeedOnStack(stack, speedKmh);
        MagicRailConstants.setStyleOnStack(stack, styleId);
        MagicRailConstants.setShapeOnStack(stack, shape);
        MagicRailConstants.setStartTiltOnStack(stack, tiltStartDegrees);
        MagicRailConstants.setMiddleTiltOnStack(stack, tiltMiddleDegrees);
        MagicRailConstants.setEndTiltOnStack(stack, tiltEndDegrees);

        final PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(speedKmh);
        buf.writeVarInt(hand.ordinal());
        buf.writeString(styleId == null ? MagicRailConstants.DEFAULT_STYLE : styleId);
        buf.writeString((shape == null ? MagicRailConstants.DEFAULT_SHAPE : shape).name());
        buf.writeVarInt(tiltStartDegrees);
        buf.writeVarInt(tiltMiddleDegrees);
        buf.writeVarInt(tiltEndDegrees);
        MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_SPEED_PACKET_ID, buf);
    }

    private int getCurrentSpeedFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_SPEED_KMH;
        }

        final ItemStack stack = player.getStackInHand(hand);
        return MagicRailConstants.getSpeedFromStack(stack);
    }

    private String getCurrentStyleFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_STYLE;
        }
        return MagicRailConstants.getStyleFromStack(player.getStackInHand(hand));
    }

    private Rail.Shape getCurrentShapeFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_SHAPE;
        }
        return MagicRailConstants.getShapeFromStack(player.getStackInHand(hand));
    }

    private int getCurrentStartTiltFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_TILT_DEGREES;
        }
        return MagicRailConstants.getStartTiltFromStack(player.getStackInHand(hand));
    }

    private int getCurrentMiddleTiltFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_TILT_DEGREES;
        }
        return MagicRailConstants.getMiddleTiltFromStack(player.getStackInHand(hand));
    }

    private int getCurrentEndTiltFromHeldItem() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final ClientPlayerEntity player = client.getPlayerMapped();
        if (player == null) {
            return MagicRailConstants.DEFAULT_TILT_DEGREES;
        }
        return MagicRailConstants.getEndTiltFromStack(player.getStackInHand(hand));
    }

    private org.mtr.mapping.holder.MutableText getStyleButtonLabel() {
        return TextHelper.literal("Style: " + MagicRailConstants.getStyleLabel(styleId));
    }

    private org.mtr.mapping.holder.MutableText getEasingButtonLabel() {
        return TextHelper.literal("Easing: " + MagicRailConstants.getShapeLabel(shape));
    }

    private org.mtr.mapping.holder.MutableText getPreviewTrainToggleLabel() {
        return TextHelper.literal(previewTrainVisible ? "||" : ">");
    }

    private static int fromSliderValue(double sliderValue) {
        final int value = MagicRailConstants.MIN_SPEED_KMH + (int) Math.round(sliderValue * (MagicRailConstants.MAX_SPEED_KMH - MagicRailConstants.MIN_SPEED_KMH));
        return MagicRailConstants.clampToStep(value);
    }

    private static double toSliderValue(int speed) {
        final int clamped = MagicRailConstants.clampToStep(speed);
        return (clamped - MagicRailConstants.MIN_SPEED_KMH) / (double) (MagicRailConstants.MAX_SPEED_KMH - MagicRailConstants.MIN_SPEED_KMH);
    }

    private SliderWidgetExtension createTiltSlider(int x, int y, int width, TiltGetter getter, TiltSetter setter) {
        final SliderWidgetExtension slider = new SliderWidgetExtension(x, y, width, 20, "") {
            @Override
            protected void updateMessage2() {
                this.setMessage2(Text.cast(TextHelper.literal(getter.get() + " degrees")));
            }

            @Override
            protected void applyValue2() {
                setter.set(fromTiltSliderValue(this.getValueMapped()));
                updateMessage2();
            }
        };
        slider.setValueMapped(toTiltSliderValue(getter.get()));
        return slider;
    }

    private static int fromTiltSliderValue(double sliderValue) {
        final int range = MagicRailConstants.MAX_TILT_DEGREES - MagicRailConstants.MIN_TILT_DEGREES;
        final int raw = MagicRailConstants.MIN_TILT_DEGREES + (int) Math.round(sliderValue * range);
        return MagicRailConstants.clampTiltDegrees(raw);
    }

    private static double toTiltSliderValue(int tiltDegrees) {
        final int clamped = MagicRailConstants.clampTiltDegrees(tiltDegrees);
        return (clamped - MagicRailConstants.MIN_TILT_DEGREES) / (double) (MagicRailConstants.MAX_TILT_DEGREES - MagicRailConstants.MIN_TILT_DEGREES);
    }

    private double getPreviewTiltForSlice(double x1, double z1, double x3, double z3) {
        final double centerX = (x1 + x3) / 2;
        final double progress = clamp01((centerX + PREVIEW_RAIL_HALF_LENGTH) / (PREVIEW_RAIL_HALF_LENGTH * 2D));
        return getPreviewTiltForProgress(progress);
    }

    private double getSignedPreviewTiltForSlice(double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4) {
        final double tiltDegrees = getPreviewTiltForSlice(x1, z1, x3, z3);
        return tiltDegrees;
    }

    private double getPreviewTiltForProgress(double progress) {
        return MagicRailTiltRegistry.interpolateDegrees(new MagicRailTiltRegistry.TiltSettings(tiltStartDegrees, tiltMiddleDegrees, tiltEndDegrees), progress);
    }

    private static double getBankYOffset(double xLeft, double zLeft, double xRight, double zRight, double tiltDegrees) {
        final double halfWidth = Math.sqrt((xRight - xLeft) * (xRight - xLeft) + (zRight - zLeft) * (zRight - zLeft)) / 2;
        return Math.tan(Math.toRadians(tiltDegrees)) * halfWidth;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static void jme$withScissor(int x, int y, int width, int height, Runnable runnable) {
        if (width <= 0 || height <= 0) {
            runnable.run();
            return;
        }

        final net.minecraft.client.MinecraftClient rawClient = net.minecraft.client.MinecraftClient.getInstance();
        if (rawClient == null || rawClient.getWindow() == null) {
            runnable.run();
            return;
        }

        final net.minecraft.client.util.Window window = rawClient.getWindow();
        final double scaleFactor = window.getScaleFactor();
        final int framebufferWidth = window.getFramebufferWidth();
        final int framebufferHeight = window.getFramebufferHeight();

        int scissorX = (int) Math.floor(x * scaleFactor);
        int scissorY = (int) Math.floor(framebufferHeight - (y + height) * scaleFactor);
        int scissorWidth = Math.max(1, (int) Math.ceil(width * scaleFactor));
        int scissorHeight = Math.max(1, (int) Math.ceil(height * scaleFactor));

        if (scissorX < 0) {
            scissorWidth += scissorX;
            scissorX = 0;
        }
        if (scissorY < 0) {
            scissorHeight += scissorY;
            scissorY = 0;
        }
        if (scissorX + scissorWidth > framebufferWidth) {
            scissorWidth = framebufferWidth - scissorX;
        }
        if (scissorY + scissorHeight > framebufferHeight) {
            scissorHeight = framebufferHeight - scissorY;
        }
        if (scissorWidth <= 0 || scissorHeight <= 0) {
            return;
        }

        RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        try {
            runnable.run();
        } finally {
            RenderSystem.disableScissor();
        }
    }

    @FunctionalInterface
    private interface TiltGetter {
        int get();
    }

    @FunctionalInterface
    private interface TiltSetter {
        void set(int value);
    }

    @Override
    public void onClose2() {
        saveNow();
        if (client != null) {
            super.onClose2();
        }
    }
}
