package org.justnoone.jme.mixin;

import org.justnoone.jme.config.JmeConfig;
import org.mtr.core.generated.WebserverResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Mixin(value = WebserverResources.class, remap = false)
public abstract class WebserverResourcesMixin {

    private static final String JME_DEFAULT_MAP_CSS = jme$readClasspathResource("/assets/jme/system_map/jme_system_map.css");
    private static final String JME_DEFAULT_MAP_JS = jme$readClasspathResource("/assets/jme/system_map/jme_system_map.js");
    // Don't depend on minified constructor names like `new eo(...)` which change between builds.
    // The upstream system map only reads `icon` and `text`, so plain object literals are sufficient.
    private static final String JME_ROUTE_TYPE_METRO_VALUE = "{icon:\"subway\",text:\"Metro\"}";
    private static final String JME_ROUTE_TYPE_BUS_VALUE = "{icon:\"directions_bus\",text:\"Bus\"}";
    private static final String JME_ROUTE_TYPE_TRAM_VALUE = "{icon:\"tram\",text:\"Tram\"}";
    // Use directions_railway (Material Icons + Symbols) so S-Bahn stays 1:1 with other mode icons.
    // directions_railway_2 is Symbols-only and falls back to a wide ligature string in Material Icons.
    private static final String JME_ROUTE_TYPE_SBAHN_VALUE = "{icon:\"directions_railway\",text:\"S-Bahn\"}";

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void jme$injectCustomCssAndJs(String resource, CallbackInfoReturnable<String> cir) {
        final String currentContent = cir.getReturnValue();
        if (currentContent == null || currentContent.isEmpty()) {
            return;
        }

        if (jme$looksLikeRouteTypesBundle(resource, currentContent)) {
            cir.setReturnValue(jme$patchRouteTypeEntries(currentContent));
            return;
        }

        if (!jme$shouldInject(resource, currentContent)) {
            return;
        }
        if (currentContent.contains("jme-system-map-custom-css") || currentContent.contains("jme-system-map-custom-js")) {
            return;
        }

        final String mergedCss = jme$merge(JME_DEFAULT_MAP_CSS, JmeConfig.getSystemMapCustomCss());
        final String mergedJs = jme$merge(JME_DEFAULT_MAP_JS, JmeConfig.getSystemMapCustomJs());
        if (mergedCss.isEmpty() && mergedJs.isEmpty()) {
            return;
        }

        String updatedHtml = currentContent;
        if (!mergedCss.isEmpty()) {
            final String styleTag = "<style id=\"jme-system-map-custom-css\">\n" + mergedCss + "\n</style>";
            updatedHtml = updatedHtml.contains("</head>") ? updatedHtml.replace("</head>", styleTag + "</head>") : updatedHtml + styleTag;
        }

        if (!mergedJs.isEmpty()) {
            final String scriptTag = "<script id=\"jme-system-map-custom-js\">\n" + mergedJs + "\n</script>";
            updatedHtml = updatedHtml.contains("</body>") ? updatedHtml.replace("</body>", scriptTag + "</body>") : updatedHtml + scriptTag;
        }

        cir.setReturnValue(updatedHtml);
    }

    private static boolean jme$looksLikeRouteTypesBundle(String resource, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        if (!jme$containsRouteTypeKey(text, "train_normal")
                || !jme$containsRouteTypeKey(text, "train_high_speed")
                || !jme$containsRouteTypeKey(text, "boat_normal")) {
            return false;
        }

        if (resource == null || resource.isEmpty()) {
            return false;
        }

        return jme$normalizeResourcePath(resource).endsWith(".js");
    }

    private static String jme$patchRouteTypeEntries(String bundleText) {
        if (bundleText == null || bundleText.isEmpty()) {
            return bundleText;
        }

        // Normalize any previously injected S-Bahn icon to the square Material Icons glyph.
        String patchedText = bundleText.replace("directions_railway_2", "directions_railway");

        final char quote = jme$detectRouteTypeKeyQuote(patchedText);

        final StringBuilder missingRouteTypes = new StringBuilder();
        if (!jme$containsRouteTypeKey(patchedText, "train_metro")) {
            missingRouteTypes.append(jme$formatRouteTypeEntry("train_metro", JME_ROUTE_TYPE_METRO_VALUE, quote));
        }
        if (!jme$containsRouteTypeKey(patchedText, "train_bus")) {
            missingRouteTypes.append(jme$formatRouteTypeEntry("train_bus", JME_ROUTE_TYPE_BUS_VALUE, quote));
        }
        if (!jme$containsRouteTypeKey(patchedText, "train_tram")) {
            missingRouteTypes.append(jme$formatRouteTypeEntry("train_tram", JME_ROUTE_TYPE_TRAM_VALUE, quote));
        }
        if (!jme$containsRouteTypeKey(patchedText, "train_sbahn")) {
            missingRouteTypes.append(jme$formatRouteTypeEntry("train_sbahn", JME_ROUTE_TYPE_SBAHN_VALUE, quote));
        }

        if (missingRouteTypes.length() == 0) {
            return patchedText;
        }

        int anchorStart = jme$indexOfRouteTypeKey(patchedText, "train_high_speed");
        if (anchorStart < 0) {
            anchorStart = jme$indexOfRouteTypeKey(patchedText, "train_normal");
        }
        if (anchorStart < 0) {
            anchorStart = jme$indexOfRouteTypeKey(patchedText, "boat_normal");
        }
        if (anchorStart < 0) {
            return patchedText;
        }

        final int entrySeparatorIndex = jme$findObjectEntrySeparator(patchedText, anchorStart);
        if (entrySeparatorIndex < 0) {
            return patchedText;
        }

        final int insertIndex = entrySeparatorIndex + 1;
        return patchedText.substring(0, insertIndex) + missingRouteTypes + patchedText.substring(insertIndex);
    }

    private static boolean jme$containsRouteTypeKey(String text, String key) {
        return jme$indexOfRouteTypeKey(text, key) >= 0;
    }

    private static int jme$indexOfRouteTypeKey(String text, String key) {
        if (text == null || text.isEmpty() || key == null || key.isEmpty()) {
            return -1;
        }

        final String raw = key + ":";
        int idx = text.indexOf(raw);
        if (idx >= 0) {
            return idx;
        }

        final String quotedDouble = "\"" + key + "\":";
        idx = text.indexOf(quotedDouble);
        if (idx >= 0) {
            return idx;
        }

        final String quotedSingle = "'" + key + "':";
        return text.indexOf(quotedSingle);
    }

    private static char jme$detectRouteTypeKeyQuote(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (text.contains("\"train_normal\":")) {
            return '"';
        }
        if (text.contains("'train_normal':")) {
            return '\'';
        }
        return 0;
    }

    private static String jme$formatRouteTypeEntry(String key, String value, char quote) {
        if (quote == '"' || quote == '\'') {
            return quote + key + quote + ":" + value + ",";
        }
        return key + ":" + value + ",";
    }

    private static int jme$findObjectEntrySeparator(String text, int entryKeyIndex) {
        if (text == null || entryKeyIndex < 0) {
            return -1;
        }

        final int colonIndex = text.indexOf(':', entryKeyIndex);
        if (colonIndex < 0) {
            return -1;
        }

        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escape = false;
        char quote = 0;

        for (int i = colonIndex + 1; i < text.length(); i++) {
            final char c = text.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    inString = false;
                    quote = 0;
                }
                continue;
            }

            if (c == '"' || c == '\'' || c == '`') {
                inString = true;
                quote = c;
                continue;
            }

            switch (c) {
                case '(':
                    parenDepth++;
                    break;
                case ')':
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                    break;
                case '{':
                    braceDepth++;
                    break;
                case '}':
                    if (braceDepth > 0) {
                        braceDepth--;
                    }
                    break;
                case '[':
                    bracketDepth++;
                    break;
                case ']':
                    if (bracketDepth > 0) {
                        bracketDepth--;
                    }
                    break;
                case ',':
                    if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                        return i;
                    }
                    break;
                default:
                    break;
            }
        }

        return -1;
    }

    private static boolean jme$shouldInject(String resource, String text) {
        if (text == null) {
            return false;
        }

        final String normalized = text.toLowerCase();
        final boolean looksLikeHtml = normalized.contains("<html") || normalized.contains("<app-root") || normalized.contains("</body>");
        if (!looksLikeHtml) {
            return false;
        }

        if (resource == null || resource.isEmpty() || "/".equals(resource)) {
            return true;
        }

        final String normalizedResource = resource.toLowerCase();
        return normalizedResource.endsWith(".html") || normalized.contains("app-map") || normalized.contains("wrapper");
    }

    private static String jme$normalizeResourcePath(String resource) {
        String normalizedResource = resource == null ? "" : resource.toLowerCase(Locale.ENGLISH);
        final int queryIndex = normalizedResource.indexOf('?');
        if (queryIndex >= 0) {
            normalizedResource = normalizedResource.substring(0, queryIndex);
        }
        final int hashIndex = normalizedResource.indexOf('#');
        if (hashIndex >= 0) {
            normalizedResource = normalizedResource.substring(0, hashIndex);
        }
        return normalizedResource;
    }

    private static String jme$merge(String first, String second) {
        final String normalizedFirst = first == null ? "" : first.trim();
        final String normalizedSecond = second == null ? "" : second.trim();
        if (normalizedFirst.isEmpty()) {
            return normalizedSecond;
        } else if (normalizedSecond.isEmpty()) {
            return normalizedFirst;
        } else {
            return normalizedFirst + "\n\n" + normalizedSecond;
        }
    }

    private static String jme$readClasspathResource(String resourcePath) {
        try (InputStream inputStream = WebserverResourcesMixin.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return "";
            }

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }
}
