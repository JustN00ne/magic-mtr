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
    private static final String JME_ROUTE_TYPE_METRO = "train_metro:{icon:\"subway\",text:\"Metro\"},";
    private static final String JME_ROUTE_TYPE_BUS = "train_bus:{icon:\"directions_bus\",text:\"Bus\"},";
    private static final String JME_ROUTE_TYPE_TRAM = "train_tram:{icon:\"tram\",text:\"Tram\"},";
    private static final String JME_ROUTE_TYPE_SBAHN = "train_sbahn:{icon:\"directions_railway_2\",text:\"S-Bahn\"},";

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

        if (!text.contains("train_normal:") || !text.contains("train_high_speed:") || !text.contains("boat_normal:")) {
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

        final StringBuilder missingRouteTypes = new StringBuilder();
        if (!bundleText.contains("train_metro:")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_METRO);
        }
        if (!bundleText.contains("train_bus:")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_BUS);
        }
        if (!bundleText.contains("train_tram:")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_TRAM);
        }
        if (!bundleText.contains("train_sbahn:")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_SBAHN);
        }

        if (missingRouteTypes.length() == 0) {
            return bundleText;
        }

        final int anchorStart = bundleText.indexOf("train_high_speed:");
        if (anchorStart < 0) {
            return bundleText;
        }

        final int entrySeparatorIndex = jme$findObjectEntrySeparator(bundleText, anchorStart);
        if (entrySeparatorIndex < 0) {
            return bundleText;
        }

        final int insertIndex = entrySeparatorIndex + 1;
        return bundleText.substring(0, insertIndex) + missingRouteTypes + bundleText.substring(insertIndex);
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
