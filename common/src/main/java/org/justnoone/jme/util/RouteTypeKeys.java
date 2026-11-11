package org.justnoone.jme.util;

import org.mtr.core.data.RouteType;

import java.util.Locale;

/**
 * The train service types offered by MAGIC's route type selector and how they map onto MTR's {@link RouteType}.
 * <p>
 * MTR only knows NORMAL, LIGHT_RAIL and HIGH_SPEED. The extra types (metro, bus, tram, S-Bahn) are stored by MAGIC as a
 * per-route override while the MTR route keeps a compatible base type.
 */
public final class RouteTypeKeys {

    public static final String TRAIN_NORMAL = "train_normal";
    public static final String TRAIN_LIGHT_RAIL = "train_light_rail";
    public static final String TRAIN_HIGH_SPEED = "train_high_speed";
    public static final String TRAIN_METRO = "train_metro";
    public static final String TRAIN_BUS = "train_bus";
    public static final String TRAIN_TRAM = "train_tram";
    // Shown as "Commuter Rail"; the key is kept for compatibility with existing saves.
    public static final String TRAIN_SBAHN = "train_sbahn";
    public static final String TRAIN_REGIONAL = "train_regional";
    public static final String TRAIN_INTERCITY = "train_intercity";
    public static final String TRAIN_TROLLEYBUS = "train_trolleybus";
    public static final String TRAIN_FREIGHT = "train_freight";

    /**
     * Order of the entries in the selector dropdown.
     */
    public static final String[] TRAIN_ROUTE_TYPE_ORDER = {
            TRAIN_NORMAL,
            TRAIN_METRO,
            TRAIN_SBAHN,
            TRAIN_REGIONAL,
            TRAIN_INTERCITY,
            TRAIN_HIGH_SPEED,
            TRAIN_LIGHT_RAIL,
            TRAIN_TRAM,
            TRAIN_BUS,
            TRAIN_TROLLEYBUS,
            TRAIN_FREIGHT
    };

    /**
     * Display label of each type (same style as MTR's own labels, eg "Light Rail"), index-aligned with
     * {@link #TRAIN_ROUTE_TYPE_ORDER}.
     */
    private static final String[] TRAIN_ROUTE_TYPE_LABELS = {
            "Train",
            "Metro",
            "Commuter Rail",
            "Regional Rail",
            "Intercity",
            "High-Speed Rail",
            "Light Rail",
            "Tram",
            "Bus",
            "Trolleybus",
            "Freight"
    };

    /**
     * Material Icons ligature of each type, index-aligned with {@link #TRAIN_ROUTE_TYPE_ORDER}. The System Map renders
     * these with the classic "Material Icons" font, so only names that exist in that font may be used; anything else
     * is drawn as the raw (wide) ligature text.
     */
    private static final String[] TRAIN_ROUTE_TYPE_ICONS = {
            "directions_railway",
            "subway",
            "directions_subway",
            "directions_transit",
            "directions_transit_filled",
            "train",
            "tram",
            "tram",
            "directions_bus",
            "electric_bolt",
            "local_shipping"
    };

    public static String getLabel(String trainRouteTypeKey) {
        final int index = indexOf(trainRouteTypeKey);
        return index < 0 ? TRAIN_ROUTE_TYPE_LABELS[0] : TRAIN_ROUTE_TYPE_LABELS[index];
    }

    public static String getIcon(String trainRouteTypeKey) {
        final int index = indexOf(trainRouteTypeKey);
        return index < 0 ? TRAIN_ROUTE_TYPE_ICONS[0] : TRAIN_ROUTE_TYPE_ICONS[index];
    }

    public static int indexOf(String trainRouteTypeKey) {
        for (int i = 0; i < TRAIN_ROUTE_TYPE_ORDER.length; i++) {
            if (TRAIN_ROUTE_TYPE_ORDER[i].equals(trainRouteTypeKey)) {
                return i;
            }
        }
        return -1;
    }

    private RouteTypeKeys() {
    }

    public static String normalizeRouteId(String routeId) {
        if (routeId == null) {
            return "";
        }
        return routeId.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * Same output as {@code String.format("%016X", routeId)}, without the formatter overhead (used on hot paths).
     */
    public static String toRouteIdHex(long routeId) {
        final String hex = Long.toHexString(routeId).toUpperCase(Locale.ENGLISH);
        if (hex.length() >= 16) {
            return hex;
        }
        final StringBuilder stringBuilder = new StringBuilder(16);
        for (int i = hex.length(); i < 16; i++) {
            stringBuilder.append('0');
        }
        return stringBuilder.append(hex).toString();
    }

    /**
     * @return the canonical key, or an empty string for unknown values (meaning "no override")
     */
    public static String normalizeTrainType(String routeType) {
        final String normalized = routeType == null ? "" : routeType.trim().toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "normal":
            case TRAIN_NORMAL:
                return TRAIN_NORMAL;
            case "light_rail":
            case "lightrail":
            case TRAIN_LIGHT_RAIL:
                return TRAIN_LIGHT_RAIL;
            case "high_speed":
            case "highspeed":
            case TRAIN_HIGH_SPEED:
                return TRAIN_HIGH_SPEED;
            case "metro":
            case TRAIN_METRO:
                return TRAIN_METRO;
            case "bus":
            case TRAIN_BUS:
                return TRAIN_BUS;
            case "tram":
            case TRAIN_TRAM:
                return TRAIN_TRAM;
            case "sbahn":
            case "s_bahn":
            case "commuter":
            case "commuter_rail":
            case TRAIN_SBAHN:
                return TRAIN_SBAHN;
            case "regional":
            case "regional_rail":
            case "regio":
            case TRAIN_REGIONAL:
                return TRAIN_REGIONAL;
            case "intercity":
            case "inter_city":
            case TRAIN_INTERCITY:
                return TRAIN_INTERCITY;
            case "trolleybus":
            case "trolley":
            case "trolley_bus":
            case TRAIN_TROLLEYBUS:
                return TRAIN_TROLLEYBUS;
            // Ferry used to be offered for trains (boats are MTR's own transport mode); old saves show as Train.
            case "ferry":
            case "train_ferry":
                return "";
            case "freight":
            case "cargo":
            case "goods":
            case TRAIN_FREIGHT:
                return TRAIN_FREIGHT;
            default:
                return "";
        }
    }

    /**
     * @return whether MTR can store this type natively (no MAGIC override needed)
     */
    public static boolean isBaseTrainKey(String trainRouteTypeKey) {
        return trainRouteTypeKey == null
                || trainRouteTypeKey.isEmpty()
                || TRAIN_NORMAL.equals(trainRouteTypeKey)
                || TRAIN_LIGHT_RAIL.equals(trainRouteTypeKey)
                || TRAIN_HIGH_SPEED.equals(trainRouteTypeKey);
    }

    /**
     * @return the MTR route type stored on the route for this key (extended types fall back to NORMAL)
     */
    public static RouteType getBaseRouteType(String trainRouteTypeKey) {
        if (TRAIN_LIGHT_RAIL.equals(trainRouteTypeKey)) {
            return RouteType.LIGHT_RAIL;
        } else if (TRAIN_HIGH_SPEED.equals(trainRouteTypeKey)) {
            return RouteType.HIGH_SPEED;
        } else {
            return RouteType.NORMAL;
        }
    }

    /**
     * @param override the stored MAGIC override for the route, or empty
     * @return the key shown as selected: overrides only apply while the route still uses a base MTR type
     */
    public static String getEffectiveKey(RouteType routeType, String override) {
        final String baseKey = fromRouteType(routeType);
        if (!isBaseTrainKey(baseKey)) {
            return baseKey;
        }
        final String normalizedOverride = normalizeTrainType(override);
        return normalizedOverride.isEmpty() ? baseKey : normalizedOverride;
    }

    /**
     * The route type key used by MTR's web map ({@code <transport mode>_<route type>}, eg {@code boat_light_rail}),
     * with MAGIC's extended train types applied (eg {@code train_metro}).
     *
     * @param transportModeName MTR transport mode name (eg {@code TRAIN}, {@code CABLE_CAR})
     * @param override          the stored MAGIC override for the route, or empty (only used for trains)
     */
    public static String getWebTypeKey(String transportModeName, RouteType routeType, String override) {
        final String mode = transportModeName == null || transportModeName.isEmpty() ? "train" : transportModeName.toLowerCase(Locale.ENGLISH);
        if ("train".equals(mode)) {
            return getEffectiveKey(routeType, override);
        }
        return mode + "_" + (routeType == null ? "normal" : routeType.name().toLowerCase(Locale.ENGLISH));
    }

    public static String fromRouteType(RouteType routeType) {
        if (routeType == null) {
            return TRAIN_NORMAL;
        }
        // Use name() so this also works with RouteType enums that have additional values.
        switch (routeType.name()) {
            case "LIGHT_RAIL":
                return TRAIN_LIGHT_RAIL;
            case "HIGH_SPEED":
                return TRAIN_HIGH_SPEED;
            case "METRO":
                return TRAIN_METRO;
            case "BUS":
                return TRAIN_BUS;
            case "TRAM":
                return TRAIN_TRAM;
            case "SBAHN":
                return TRAIN_SBAHN;
            case "REGIONAL":
                return TRAIN_REGIONAL;
            case "INTERCITY":
                return TRAIN_INTERCITY;
            case "TROLLEYBUS":
                return TRAIN_TROLLEYBUS;
            case "FREIGHT":
                return TRAIN_FREIGHT;
            default:
                return TRAIN_NORMAL;
        }
    }

    /**
     * Maps a click inside the dropdown list to an entry.
     *
     * @param relativeY click position relative to the top of the first row
     * @return the entry index, or -1 if the click hit a gap or was outside the rows
     */
    public static int rowAt(double relativeY, int rowHeight, int gap, int rowCount) {
        if (relativeY < 0 || rowHeight <= 0) {
            return -1;
        }
        final int stride = rowHeight + Math.max(0, gap);
        final int index = (int) Math.floor(relativeY / stride);
        final double inside = relativeY - index * (double) stride;
        return index < rowCount && inside < rowHeight ? index : -1;
    }
}
