package org.justnoone.jme.rail;

import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.Registry;

public final class MagicRailConstants {

    public static final Identifier UNIVERSAL_CONNECTOR_ID = new Identifier("jme", "magic_rail_connector");
    public static final Identifier SET_SPEED_PACKET_ID = new Identifier("jme", "set_magic_rail_speed");
    public static final Identifier SET_RAIL_TILT_PACKET_ID = new Identifier("jme", "set_magic_rail_tilt");
    public static final Identifier SET_ALTERNATIVE_PLATFORM_PACKET_ID = new Identifier("jme", "set_alternative_platform");
    public static final Identifier SET_DEPOT_CANCELLATION_PACKET_ID = new Identifier("jme", "set_depot_cancellation");
    public static final Identifier SET_TRAIN_DETECTOR_RANGE_PACKET_ID = new Identifier("jme", "set_train_detector_range");
    public static final Identifier SET_ROUTE_TYPE_OVERRIDE_PACKET_ID = new Identifier("jme", "set_route_type_override");
    public static final Identifier SET_BRUSH_PROFILE_PACKET_ID = new Identifier("jme", "set_brush_profile");
    public static final Identifier APPLY_BRUSH_PROFILE_PACKET_ID = new Identifier("jme", "apply_brush_profile");

    public static final int DEFAULT_SPEED_KMH = 80;
    public static final int MIN_SPEED_KMH = 1;
    public static final int MAX_SPEED_KMH = 400;
    public static final int SPEED_STEP = 10;

    public static final String DEFAULT_STYLE = "default";
    public static final Rail.Shape DEFAULT_SHAPE = Rail.Shape.QUADRATIC;
    public static final int DEFAULT_TILT_DEGREES = 0;
    public static final int MIN_TILT_DEGREES = -45;
    public static final int MAX_TILT_DEGREES = 45;

    private MagicRailConstants() {
    }

    public static int clampToStep(int speed) {
        if (speed <= MIN_SPEED_KMH) return MIN_SPEED_KMH;
        if (speed >= MAX_SPEED_KMH) return MAX_SPEED_KMH;
        return Math.round(speed / (float) SPEED_STEP) * SPEED_STEP;
    }

    public static int clampTiltDegrees(int degrees) {
        return Math.max(MIN_TILT_DEGREES, Math.min(MAX_TILT_DEGREES, degrees));
    }

    public static boolean isUniversalConnector(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Since we can't easily get the ID in common without direct access,
        // and we can't get direct access due to mapping issues, we check the translation key.
        final String translationKey = stack.getItem().getTranslationKey();
        if (translationKey.contains("magic_rail_connector")) {
            return true;
        }

        // Backward compatibility: legacy MAGIC used MTR's rail_connector_300 as the universal connector.
        // Keep treating it as universal only if it carries MAGIC NBT.
        if (translationKey.contains("rail_connector_300")) {
            final CompoundTag nbt = stack.getTag();
            return nbt != null && (nbt.contains("jme_speed", 3) || nbt.contains("jme_style", 8) || nbt.contains("jme_shape", 8) || nbt.contains("jme_tilt_start", 3) || nbt.contains("jme_tilt_middle", 3) || nbt.contains("jme_tilt_end", 3));
        }

        return false;
    }

    public static Item getUniversalConnectorItem() {
        // This method should be overridden in platform-specific code
        // Return null as fallback
        return null;
    }

    public static int getSpeedFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_SPEED_KMH;
        }
        final CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains("jme_speed", 3)) {
            return DEFAULT_SPEED_KMH;
        }
        return clampToStep(nbt.getInt("jme_speed"));
    }

    public static void setSpeedOnStack(ItemStack stack, int speedKmh) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt("jme_speed", clampToStep(speedKmh));
    }

    public static String getStyleFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_STYLE;
        }
        final CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains("jme_style", 8)) {
            return DEFAULT_STYLE;
        }
        return nbt.getString("jme_style");
    }

    public static void setStyleOnStack(ItemStack stack, String styleId) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final CompoundTag nbt = stack.getOrCreateTag();
        nbt.putString("jme_style", styleId == null ? DEFAULT_STYLE : styleId);
    }

    public static Rail.Shape getShapeFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_SHAPE;
        }
        final CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains("jme_shape", 8)) {
            return DEFAULT_SHAPE;
        }
        try {
            return Rail.Shape.valueOf(nbt.getString("jme_shape"));
        } catch (Exception ignored) {
            return DEFAULT_SHAPE;
        }
    }

    public static void setShapeOnStack(ItemStack stack, Rail.Shape shape) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final CompoundTag nbt = stack.getOrCreateTag();
        nbt.putString("jme_shape", (shape == null ? DEFAULT_SHAPE : shape).name());
    }

    public static int getStartTiltFromStack(ItemStack stack) {
        return getTiltFromStack(stack, "jme_tilt_start");
    }

    public static int getMiddleTiltFromStack(ItemStack stack) {
        return getTiltFromStack(stack, "jme_tilt_middle");
    }

    public static int getEndTiltFromStack(ItemStack stack) {
        return getTiltFromStack(stack, "jme_tilt_end");
    }

    public static void setStartTiltOnStack(ItemStack stack, int tiltDegrees) {
        setTiltOnStack(stack, "jme_tilt_start", tiltDegrees);
    }

    public static void setMiddleTiltOnStack(ItemStack stack, int tiltDegrees) {
        setTiltOnStack(stack, "jme_tilt_middle", tiltDegrees);
    }

    public static void setEndTiltOnStack(ItemStack stack, int tiltDegrees) {
        setTiltOnStack(stack, "jme_tilt_end", tiltDegrees);
    }

    public static String getStyleLabel(String styleId) {
        if (styleId == null || styleId.isEmpty() || styleId.equals("default")) {
            return "Standard MTR";
        }
        if (styleId.equals("default_2")) {
            return "Standard MTR (Flipped)";
        }
        return styleId;
    }

    public static String getShapeLabel(Rail.Shape shape) {
        if (shape == null) return "Unknown";
        if (shape == Rail.Shape.QUADRATIC) return "Quadratic (Default)";
        if (shape == Rail.Shape.TWO_RADII) return "Two Radii";
        if (shape == Rail.Shape.CABLE) return "Cable";
        return shape.name();
    }

    public static Rail.Shape nextShape(Rail.Shape current) {
        if (current == null) return DEFAULT_SHAPE;
        final Rail.Shape[] values = Rail.Shape.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    public static String toPlacedStyleId(String styleId) {
        if (styleId == null || styleId.isEmpty() || styleId.equals("default")) {
            return "";
        }
        return styleId;
    }

    public static Hand decodeHand(int handOrdinal) {
        final Hand[] values = Hand.values();
        if (handOrdinal < 0 || handOrdinal >= values.length) {
            return Hand.MAIN_HAND;
        }
        return values[handOrdinal];
    }

    private static int getTiltFromStack(ItemStack stack, String nbtKey) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_TILT_DEGREES;
        }
        final CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(nbtKey, 3)) {
            return DEFAULT_TILT_DEGREES;
        }
        return clampTiltDegrees(nbt.getInt(nbtKey));
    }

    private static void setTiltOnStack(ItemStack stack, String nbtKey, int tiltDegrees) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(nbtKey, clampTiltDegrees(tiltDegrees));
    }
}
