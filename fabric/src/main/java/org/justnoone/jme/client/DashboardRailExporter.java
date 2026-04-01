package org.justnoone.jme.client;

import org.justnoone.jme.config.MagicConfigPaths;
import org.justnoone.jme.rail.MagicRailSpeedColor;
import org.justnoone.jme.mixin.WidgetMapAccessor;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.mod.data.RailType;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.screen.WidgetMap;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class DashboardRailExporter {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DashboardRailExporter() {
    }

    public static Path exportRailsPngViewport(WidgetMap widgetMap) throws IOException {
        final ExportViewport viewport = ExportViewport.fromWidgetMap(widgetMap);
        if (viewport == null) {
            return null;
        }

        final BufferedImage image = new BufferedImage(viewport.width, viewport.height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            final double thickness = Math.max(1.35D, Math.min(4.5D, 1.45D + viewport.scale * 0.035D));
            g2d.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            final LinkedHashMap<String, Rail> railsById = collectRailsById();
            for (final Rail rail : railsById.values()) {
                drawRailViewport(g2d, viewport, rail);
            }
        } finally {
            g2d.dispose();
        }

        final String timestamp = LocalDateTime.now().format(TS_FORMAT);
        final Path output = MagicConfigPaths.resolveExportFile(String.format("rails-%s-viewport.png", timestamp));
        ImageIO.write(image, "png", output.toFile());
        return output;
    }

    public static Path exportRailsSvgViewport(WidgetMap widgetMap) throws IOException {
        final ExportViewport viewport = ExportViewport.fromWidgetMap(widgetMap);
        if (viewport == null) {
            return null;
        }

        final double lineWidth = Math.max(1.35D, Math.min(4.5D, 1.45D + viewport.scale * 0.035D));

        final StringBuilder svg = new StringBuilder(256 * 1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(viewport.width).append("\" height=\"").append(viewport.height).append("\" viewBox=\"0 0 ").append(viewport.width).append(" ").append(viewport.height).append("\">");
        svg.append("<g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">");

        final LinkedHashMap<String, Rail> railsById = collectRailsById();
        for (final Rail rail : railsById.values()) {
            final List<double[]> polyline = buildRailPolyline(rail);
            if (polyline.size() < 2) {
                continue;
            }

            final String d = buildViewportPathD(viewport, polyline);
            if (d.isEmpty()) {
                continue;
            }

            final String stroke = toSvgColor(getRailOverlayArgb(rail));
            svg.append("<path d=\"").append(d).append("\" stroke=\"").append(stroke).append("\" stroke-opacity=\"0.95\" stroke-width=\"").append(roundSvgNumber(lineWidth)).append("\"/>");
        }

        svg.append("</g></svg>");

        final String timestamp = LocalDateTime.now().format(TS_FORMAT);
        final Path output = MagicConfigPaths.resolveExportFile(String.format("rails-%s-viewport.svg", timestamp));
        Files.write(output, svg.toString().getBytes(StandardCharsets.UTF_8));
        return output;
    }

    public static Path exportRailsSvgFull() throws IOException {
        final LinkedHashMap<String, Rail> railsById = collectRailsById();
        if (railsById.isEmpty()) {
            return null;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        final List<RailPolyline> polylines = new ArrayList<>();
        for (final Rail rail : railsById.values()) {
            final List<double[]> polyline = buildRailPolyline(rail);
            if (polyline.size() < 2) {
                continue;
            }

            for (final double[] point : polyline) {
                minX = Math.min(minX, point[0]);
                minZ = Math.min(minZ, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxZ = Math.max(maxZ, point[1]);
            }

            polylines.add(new RailPolyline(getRailOverlayArgb(rail), polyline));
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
            return null;
        }

        final double padding = 1.5D;
        minX -= padding;
        minZ -= padding;
        maxX += padding;
        maxZ += padding;

        final double width = Math.max(1D, maxX - minX);
        final double height = Math.max(1D, maxZ - minZ);

        final StringBuilder svg = new StringBuilder(512 * 1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
                .append(roundSvgNumber(minX)).append(" ")
                .append(roundSvgNumber(minZ)).append(" ")
                .append(roundSvgNumber(width)).append(" ")
                .append(roundSvgNumber(height)).append("\">");
        svg.append("<g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">");

        final double lineWidth = 0.45D;
        for (final RailPolyline railPolyline : polylines) {
            final String d = buildWorldPathD(railPolyline.points);
            if (d.isEmpty()) {
                continue;
            }
            svg.append("<path d=\"").append(d)
                    .append("\" stroke=\"").append(toSvgColor(railPolyline.argb))
                    .append("\" stroke-opacity=\"0.9\" stroke-width=\"").append(roundSvgNumber(lineWidth)).append("\"/>");
        }

        svg.append("</g></svg>");

        final String timestamp = LocalDateTime.now().format(TS_FORMAT);
        final Path output = MagicConfigPaths.resolveExportFile(String.format("rails-%s-full.svg", timestamp));
        Files.write(output, svg.toString().getBytes(StandardCharsets.UTF_8));
        return output;
    }

    private static void drawRailViewport(Graphics2D g2d, ExportViewport viewport, Rail rail) {
        if (rail == null) {
            return;
        }

        final List<double[]> polyline = buildRailPolyline(rail);
        if (polyline.size() < 2) {
            return;
        }

        final Path2D.Double path = new Path2D.Double();
        boolean started = false;
        for (final double[] point : polyline) {
            final double sx = viewport.worldToScreenX(point[0]);
            final double sy = viewport.worldToScreenY(point[1]);
            if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                continue;
            }
            if (!started) {
                path.moveTo(sx, sy);
                started = true;
            } else {
                path.lineTo(sx, sy);
            }
        }

        if (!started) {
            return;
        }

        g2d.setColor(new Color(getRailOverlayArgb(rail), true));
        g2d.draw(path);
    }

    private static String buildViewportPathD(ExportViewport viewport, List<double[]> polyline) {
        final StringBuilder d = new StringBuilder(polyline.size() * 12);
        boolean started = false;
        for (final double[] point : polyline) {
            final double sx = viewport.worldToScreenX(point[0]);
            final double sy = viewport.worldToScreenY(point[1]);
            if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                continue;
            }

            final String x = roundSvgNumber(sx);
            final String y = roundSvgNumber(sy);
            if (!started) {
                d.append("M").append(x).append(" ").append(y);
                started = true;
            } else {
                d.append(" L").append(x).append(" ").append(y);
            }
        }
        return d.toString();
    }

    private static String buildWorldPathD(List<double[]> polyline) {
        final StringBuilder d = new StringBuilder(polyline.size() * 12);
        boolean started = false;
        for (final double[] point : polyline) {
            if (point == null) {
                continue;
            }
            final String x = roundSvgNumber(point[0]);
            final String y = roundSvgNumber(point[1]);
            if (!started) {
                d.append("M").append(x).append(" ").append(y);
                started = true;
            } else {
                d.append(" L").append(x).append(" ").append(y);
            }
        }
        return d.toString();
    }

    private static List<double[]> buildRailPolyline(Rail rail) {
        final List<double[]> points = new ArrayList<>();
        if (rail == null) {
            return points;
        }

        rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            final double centerXPrevious = (x1 + x2) * 0.5D;
            final double centerZPrevious = (z1 + z2) * 0.5D;
            final double centerXCurrent = (x3 + x4) * 0.5D;
            final double centerZCurrent = (z3 + z4) * 0.5D;
            appendPolylinePoint(points, centerXPrevious, centerZPrevious);
            appendPolylinePoint(points, centerXCurrent, centerZCurrent);
        }, 1, 0, 0);

        // Fallback to endpoints if the rail math didn't provide enough samples.
        if (points.size() < 2) {
            final Position[] positions = parseRailPositions(rail.getHexId());
            final Position p1 = positions[0];
            final Position p2 = positions[1];
            if (p1 != null && p2 != null) {
                points.clear();
                points.add(new double[]{p1.getX() + 0.5D, p1.getZ() + 0.5D});
                points.add(new double[]{p2.getX() + 0.5D, p2.getZ() + 0.5D});
            }
        }

        return points;
    }

    private static Position[] parseRailPositions(String railId) {
        try {
            if (railId == null) {
                return new Position[]{null, null};
            }
            final String[] split = railId.split("-");
            if (split.length != 6) {
                return new Position[]{null, null};
            }
            return new Position[]{
                    new Position(Long.parseUnsignedLong(split[0], 16), Long.parseUnsignedLong(split[1], 16), Long.parseUnsignedLong(split[2], 16)),
                    new Position(Long.parseUnsignedLong(split[3], 16), Long.parseUnsignedLong(split[4], 16), Long.parseUnsignedLong(split[5], 16))
            };
        } catch (Exception ignored) {
            return new Position[]{null, null};
        }
    }

    private static void appendPolylinePoint(List<double[]> points, double x, double z) {
        if (points.isEmpty()) {
            points.add(new double[]{x, z});
            return;
        }
        final double[] previous = points.get(points.size() - 1);
        if (Math.hypot(previous[0] - x, previous[1] - z) > 0.08D) {
            points.add(new double[]{x, z});
        }
    }

    private static int getRailSpeedArgb(Rail rail) {
        final long speedForward = rail.getSpeedLimitKilometersPerHour(false);
        final long speedReverse = rail.getSpeedLimitKilometersPerHour(true);
        final long resolvedSpeed = Math.max(speedForward, speedReverse);
        final int clampedSpeed = (int) Math.max(1L, Math.min(400L, resolvedSpeed <= 0 ? 1 : resolvedSpeed));
        return MagicRailSpeedColor.colorForSpeed(clampedSpeed);
    }

    private static int getRailOverlayArgb(Rail rail) {
        if (rail == null) {
            return 0xFF3F8BFF;
        }
        if (rail.isPlatform()) {
            return RailType.PLATFORM.color;
        }
        if (rail.isSiding()) {
            return RailType.SIDING.color;
        }
        if (rail.canTurnBack()) {
            return RailType.TURN_BACK.color;
        }
        return getRailSpeedArgb(rail);
    }

    private static String toSvgColor(int argb) {
        final int rgb = argb & 0xFFFFFF;
        return String.format("#%06X", rgb);
    }

    private static String roundSvgNumber(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static LinkedHashMap<String, Rail> collectRailsById() {
        final LinkedHashMap<String, Rail> railsById = new LinkedHashMap<>();
        appendKnownRails(railsById, MinecraftClientData.getDashboardInstance());
        appendKnownRails(railsById, MinecraftClientData.getInstance());
        return railsById;
    }

    private static void appendKnownRails(LinkedHashMap<String, Rail> railsById, MinecraftClientData railData) {
        if (railData == null) {
            return;
        }

        railData.rails.forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
        railData.railIdMap.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail));
        railData.positionsToRail.values().forEach(edges -> edges.values().forEach(rail -> railsById.putIfAbsent(rail.getHexId(), rail)));
    }

    private static final class ExportViewport {
        private final int width;
        private final int height;
        private final double scale;
        private final double centerX;
        private final double centerY;

        private ExportViewport(int width, int height, double scale, double centerX, double centerY) {
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.centerX = centerX;
            this.centerY = centerY;
        }

        static ExportViewport fromWidgetMap(WidgetMap widgetMap) {
            if (widgetMap == null) {
                return null;
            }

            final int width = widgetMap.getWidth2();
            final int height = widgetMap.getHeight2();
            if (width <= 0 || height <= 0) {
                return null;
            }

            final WidgetMapAccessor accessor = (WidgetMapAccessor) (Object) widgetMap;
            final double scale = accessor.jme$getScale();
            final double centerX = accessor.jme$getCenterX();
            final double centerY = accessor.jme$getCenterY();
            if (!Double.isFinite(scale) || !Double.isFinite(centerX) || !Double.isFinite(centerY) || Math.abs(scale) < 1.0e-6) {
                return null;
            }

            return new ExportViewport(width, height, scale, centerX, centerY);
        }

        double worldToScreenX(double worldX) {
            return (worldX - centerX) * scale + width / 2D;
        }

        double worldToScreenY(double worldZ) {
            return (worldZ - centerY) * scale + height / 2D;
        }
    }

    private static final class RailPolyline {
        private final int argb;
        private final List<double[]> points;

        private RailPolyline(int argb, List<double[]> points) {
            this.argb = argb;
            this.points = points;
        }
    }
}
