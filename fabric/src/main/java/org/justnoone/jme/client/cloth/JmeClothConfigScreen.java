package org.justnoone.jme.client.cloth;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.justnoone.jme.client.MagicRailTiltClient;
import org.justnoone.jme.config.JmeConfig;
import org.mtr.mapping.mapper.TextHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cloth Config implementation (works across all supported Minecraft versions).
 *
 * <p>Note: Cloth Config is still an optional dependency; this class is loaded reflectively.
 */
public final class JmeClothConfigScreen {

    private JmeClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        final State state = new State();

        final ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(literal("MAGIC"))
                .setSavingRunnable(() -> {
                    final boolean tiltChanged = state.cameraTiltEnabled != JmeConfig.cameraTiltEnabled()
                            || Math.abs(state.cameraTiltStrength - JmeConfig.cameraTiltStrength()) > 1.0e-6;
                    state.apply();
                    if (tiltChanged) {
                        MagicRailTiltClient.clearSmoothingCache();
                    }
                });

        final ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        buildGeneralCategory(builder, entryBuilder, state);
        buildDashboardCategory(builder, entryBuilder, state);
        buildSystemMapCategory(builder, entryBuilder, state);
        buildTrackColorsCategory(builder, entryBuilder, state);

        return builder.build();
    }

    private static void buildGeneralCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("General"));

        category.addEntry(entryBuilder.startBooleanToggle(literal("Use mph"), state.useMph)
                .setDefaultValue(false)
                .setTooltip(literal("Display speeds in miles per hour instead of km/h."))
                .setSaveConsumer(value -> state.useMph = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("In-world Speed Text"), state.inWorldSpeedTextEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("Render speed labels on rails in-world (can reduce FPS on large networks)."))
                .setSaveConsumer(value -> state.inWorldSpeedTextEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Camera Tilt"), state.cameraTiltEnabled)
                .setDefaultValue(true)
                .setTooltip(literal("Enable MAGIC's vehicle camera tilt smoothing."))
                .setSaveConsumer(value -> state.cameraTiltEnabled = value)
                .build());

        category.addEntry(entryBuilder.startDoubleField(literal("Camera Tilt Strength"), state.cameraTiltStrength)
                .setDefaultValue(1D)
                .setMin(0D)
                .setMax(2D)
                .setTooltip(literal("How strongly the camera tilt effect is applied (0.0 to 2.0)."))
                .setSaveConsumer(value -> state.cameraTiltStrength = value)
                .build());
    }

    private static void buildDashboardCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("Dashboard"));

        category.addEntry(entryBuilder.startEnumSelector(literal("Route List Layout"), JmeConfig.DashboardRouteListMode.class, state.dashboardRouteListMode)
                .setDefaultValue(JmeConfig.DashboardRouteListMode.FOLDERS)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setSaveConsumer(value -> state.dashboardRouteListMode = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Auto-save Dashboard Map"), state.dashboardMapAutoSaveEnabled)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.dashboardMapAutoSaveEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Alternative Platforms"), state.alternativePlatformsEnabled)
                .setDefaultValue(true)
                .setTooltip(literal("Dynamic platform rerouting (can be CPU-heavy on large networks)."))
                .setSaveConsumer(value -> state.alternativePlatformsEnabled = value)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(literal("Rail Overlay Mode"), JmeConfig.DashboardRailOverlayMode.class, state.dashboardRailOverlayMode)
                .setDefaultValue(JmeConfig.DashboardRailOverlayMode.ALL)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setSaveConsumer(value -> state.dashboardRailOverlayMode = value)
                .build());

        category.addEntry(entryBuilder.startIntSlider(literal("Overlay Cull Max (Per Cell)"), state.dashboardRailCullMaxPerCell, 1, 64)
                .setDefaultValue(8)
                .setTooltip(literal("Only used when Rail Overlay Mode is CULL."))
                .setSaveConsumer(value -> state.dashboardRailCullMaxPerCell = value)
                .build());
    }

    private static void buildSystemMapCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("System Map"));

        category.addEntry(entryBuilder.startBooleanToggle(literal("Hide Player"), state.systemMapHidePlayer)
                .setDefaultValue(false)
                .setTooltip(literal("Hide the player/clients layer on the System Map (port 8888)."))
                .setSaveConsumer(value -> state.systemMapHidePlayer = value)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(literal("Language Display"), JmeConfig.SystemMapLanguageDisplay.class, state.systemMapLanguageDisplay)
                .setDefaultValue(JmeConfig.SystemMapLanguageDisplay.NORMAL)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setTooltip(literal("Filters route/station names on the System Map."))
                .setSaveConsumer(value -> state.systemMapLanguageDisplay = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Base Rails"), state.systemMapOverlayShowBaseRails)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowBaseRails = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Details"), state.systemMapOverlayShowDetails)
                .setDefaultValue(false)
                .setTooltip(literal("Show speed coloring, arrows, labels and other details on the overlay."))
                .setSaveConsumer(value -> state.systemMapOverlayShowDetails = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Signals"), state.systemMapOverlayShowSignals)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowSignals = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Show Vehicles"), state.systemMapOverlayShowVehicles)
                .setDefaultValue(true)
                .setSaveConsumer(value -> state.systemMapOverlayShowVehicles = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Respect Route Filters"), state.systemMapOverlayRespectRouteFilters)
                .setDefaultValue(false)
                .setTooltip(literal("Only render rails that belong to visible routes."))
                .setSaveConsumer(value -> state.systemMapOverlayRespectRouteFilters = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Overlay Cache"), state.systemMapOverlayCacheEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("Keep rails/vehicles visible on the System Map even after chunks unload."))
                .setSaveConsumer(value -> state.systemMapOverlayCacheEnabled = value)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(literal("Persist Overlay Cache"), state.systemMapOverlayCachePersistEnabled)
                .setDefaultValue(false)
                .setTooltip(literal("Persist the System Map overlay cache to disk under config/MAGIC/map."))
                .setSaveConsumer(value -> state.systemMapOverlayCachePersistEnabled = value)
                .build());
    }

    private static void buildTrackColorsCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, State state) {
        final ConfigCategory category = builder.getOrCreateCategory(literal("Track Colors"));

        category.addEntry(entryBuilder.startEnumSelector(literal("Track Color Mode"), JmeConfig.TrackColorMode.class, state.trackColorMode)
                .setDefaultValue(JmeConfig.TrackColorMode.OPEN_RAILWAY_MAP)
                .setEnumNameProvider(JmeClothConfigScreen::prettyEnumValue)
                .setTooltip(
                        literal("Controls how MAGIC colors rails by speed (System Map and BlueMap speed layer)."),
                        literal("CUSTOM_GRADIENT can be edited using the list below.")
                )
                .setSaveConsumer(value -> state.trackColorMode = value)
                .build());

        category.addEntry(entryBuilder.startTextDescription(literal("Custom Gradient"))
                .setColor(0xA0A0A0)
                .build());

        category.addEntry(entryBuilder.startTextDescription(literal("Format: \"speed=#RRGGBB\" (example: \"200=#ff0000\"). Clear the list to reset to default."))
                .setColor(0xA0A0A0)
                .build());

        category.addEntry(entryBuilder.startStrList(literal("Custom Gradient Stops"), encodeGradientStops(state.trackColorCustomGradientStops))
                .setDefaultValue(encodeGradientStops(defaultCustomStops()))
                .setExpanded(false)
                .setTooltip(literal("Only used when Track Color Mode is CUSTOM_GRADIENT."))
                .setSaveConsumer(lines -> state.trackColorCustomGradientStops = decodeGradientStops(lines))
                .build());
    }

    private static Text literal(String value) {
        return TextHelper.literal(value).data;
    }

    private static Text prettyEnumValue(Enum<?> value) {
        if (value == null) {
            return literal("Unknown");
        }
        final String raw = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        final String pretty = raw.isEmpty()
                ? value.name()
                : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        return literal(pretty);
    }

    private static JmeConfig.TrackColorStop[] defaultCustomStops() {
        // Must match JmeConfig's defaultTrackColorCustomGradientStops().
        return new JmeConfig.TrackColorStop[]{
                new JmeConfig.TrackColorStop(5, 0xFF102A8A),
                new JmeConfig.TrackColorStop(100, 0xFF25C977),
                new JmeConfig.TrackColorStop(180, 0xFFD9E344),
                new JmeConfig.TrackColorStop(220, 0xFFFFE028),
                new JmeConfig.TrackColorStop(300, 0xFFEF3A26),
                new JmeConfig.TrackColorStop(400, 0xFFB42AE6)
        };
    }

    private static List<String> encodeGradientStops(JmeConfig.TrackColorStop[] stops) {
        final List<String> out = new ArrayList<>();
        if (stops == null) {
            return out;
        }
        for (final JmeConfig.TrackColorStop stop : stops) {
            if (stop == null) {
                continue;
            }
            out.add(stop.speedKmh + "=#" + String.format(Locale.ROOT, "%06X", stop.colorArgb & 0xFFFFFF));
        }
        return out;
    }

    private static JmeConfig.TrackColorStop[] decodeGradientStops(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new JmeConfig.TrackColorStop[0];
        }

        final List<JmeConfig.TrackColorStop> out = new ArrayList<>();
        for (final String raw : lines) {
            if (raw == null) {
                continue;
            }

            final String normalized = raw.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            final String[] parts = normalized.split("[=,:]", 2);
            if (parts.length != 2) {
                continue;
            }

            final int speed;
            try {
                speed = Integer.parseInt(parts[0].trim());
            } catch (Exception ignored) {
                continue;
            }

            final int rgb = parseRgb(parts[1].trim());
            if (rgb < 0) {
                continue;
            }

            out.add(new JmeConfig.TrackColorStop(speed, 0xFF000000 | (rgb & 0xFFFFFF)));
        }

        if (out.isEmpty()) {
            return new JmeConfig.TrackColorStop[0];
        }

        return out.toArray(new JmeConfig.TrackColorStop[0]);
    }

    private static int parseRgb(String raw) {
        if (raw == null) {
            return -1;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return -1;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        // Allow AARRGGBB but ignore alpha.
        if (s.length() == 8) {
            s = s.substring(2);
        }
        if (s.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static final class State {
        private boolean useMph = JmeConfig.useMph();
        private boolean inWorldSpeedTextEnabled = JmeConfig.inWorldSpeedTextEnabled();
        private boolean cameraTiltEnabled = JmeConfig.cameraTiltEnabled();
        private double cameraTiltStrength = JmeConfig.cameraTiltStrength();

        private JmeConfig.DashboardRouteListMode dashboardRouteListMode = JmeConfig.dashboardRouteListMode();
        private boolean dashboardMapAutoSaveEnabled = JmeConfig.dashboardMapAutoSaveEnabled();
        private JmeConfig.DashboardRailOverlayMode dashboardRailOverlayMode = JmeConfig.dashboardRailOverlayMode();
        private int dashboardRailCullMaxPerCell = JmeConfig.dashboardRailOverlayCullMaxPerCell();
        private boolean alternativePlatformsEnabled = JmeConfig.alternativePlatformsEnabled();

        private boolean systemMapOverlayCacheEnabled = JmeConfig.systemMapOverlayCacheEnabled();
        private boolean systemMapOverlayCachePersistEnabled = JmeConfig.systemMapOverlayCachePersistEnabled();
        private JmeConfig.SystemMapLanguageDisplay systemMapLanguageDisplay = JmeConfig.systemMapLanguageDisplay();
        private boolean systemMapHidePlayer = JmeConfig.systemMapHidePlayer();
        private boolean systemMapOverlayShowBaseRails = JmeConfig.systemMapOverlayShowBaseRails();
        private boolean systemMapOverlayShowDetails = JmeConfig.systemMapOverlayShowDetails();
        private boolean systemMapOverlayShowSignals = JmeConfig.systemMapOverlayShowSignals();
        private boolean systemMapOverlayShowVehicles = JmeConfig.systemMapOverlayShowVehicles();
        private boolean systemMapOverlayRespectRouteFilters = JmeConfig.systemMapOverlayRespectRouteFilters();

        private JmeConfig.TrackColorMode trackColorMode = JmeConfig.trackColorMode();
        private JmeConfig.TrackColorStop[] trackColorCustomGradientStops = JmeConfig.trackColorCustomGradientStops();

        private void apply() {
            JmeConfig.setUseMph(useMph);
            JmeConfig.setInWorldSpeedTextEnabled(inWorldSpeedTextEnabled);
            JmeConfig.setCameraTiltEnabled(cameraTiltEnabled);
            JmeConfig.setCameraTiltStrength(cameraTiltStrength);

            JmeConfig.setDashboardRouteListMode(dashboardRouteListMode);
            JmeConfig.setDashboardMapAutoSaveEnabled(dashboardMapAutoSaveEnabled);
            JmeConfig.setDashboardRailOverlayMode(dashboardRailOverlayMode);
            JmeConfig.setDashboardRailOverlayCullMaxPerCell(dashboardRailCullMaxPerCell);
            JmeConfig.setAlternativePlatformsEnabled(alternativePlatformsEnabled);

            JmeConfig.setSystemMapOverlayCacheEnabled(systemMapOverlayCacheEnabled);
            JmeConfig.setSystemMapOverlayCachePersistEnabled(systemMapOverlayCachePersistEnabled);
            JmeConfig.setSystemMapLanguageDisplay(systemMapLanguageDisplay);
            JmeConfig.setSystemMapHidePlayer(systemMapHidePlayer);
            JmeConfig.setSystemMapOverlayShowBaseRails(systemMapOverlayShowBaseRails);
            JmeConfig.setSystemMapOverlayShowDetails(systemMapOverlayShowDetails);
            JmeConfig.setSystemMapOverlayShowSignals(systemMapOverlayShowSignals);
            JmeConfig.setSystemMapOverlayShowVehicles(systemMapOverlayShowVehicles);
            JmeConfig.setSystemMapOverlayRespectRouteFilters(systemMapOverlayRespectRouteFilters);

            JmeConfig.setTrackColorMode(trackColorMode);
            JmeConfig.setTrackColorCustomGradientStops(trackColorCustomGradientStops);

            JmeConfig.save();
        }
    }
}
