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
    private static final String JME_ROUTE_TYPE_METRO = "train_metro:new eo(\"subway\",\"Metro\"),";
    private static final String JME_ROUTE_TYPE_BUS = "train_bus:new eo(\"directions_bus\",\"Bus\"),";
    private static final String JME_ROUTE_TYPE_TRAM = "train_tram:new eo(\"tram\",\"Tram\"),";
    private static final String JME_ROUTE_TYPE_SBAHN = "train_sbahn:new eo(\"directions_railway_2\",\"S-Bahn\"),";
    private static final String JME_ROUTE_TYPES_ANCHOR_PREFIX = "train_high_speed:new eo(";

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void jme$injectCustomCssAndJs(String resource, CallbackInfoReturnable<String> cir) {
        final String currentContent = cir.getReturnValue();
        if (currentContent == null || currentContent.isEmpty()) {
            return;
        }

        if (jme$looksLikeMainBundle(resource, currentContent)) {
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

    private static boolean jme$looksLikeMainBundle(String resource, String text) {
        if (text == null || !text.contains("train_normal:new eo(") || !text.contains("train_high_speed:new eo(")) {
            return false;
        }

        if (resource == null || resource.isEmpty()) {
            return true;
        }

        final String normalizedResource = jme$normalizeResourcePath(resource);
        if (!normalizedResource.endsWith(".js")) {
            return false;
        }
        return normalizedResource.contains("main-") || normalizedResource.contains("main.");
    }

    private static String jme$patchRouteTypeEntries(String bundleText) {
        if (bundleText == null || bundleText.isEmpty()) {
            return bundleText;
        }

        final StringBuilder missingRouteTypes = new StringBuilder();
        if (!bundleText.contains("train_metro:new eo(")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_METRO);
        }
        if (!bundleText.contains("train_bus:new eo(")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_BUS);
        }
        if (!bundleText.contains("train_tram:new eo(")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_TRAM);
        }
        if (!bundleText.contains("train_sbahn:new eo(")) {
            missingRouteTypes.append(JME_ROUTE_TYPE_SBAHN);
        }

        if (missingRouteTypes.length() == 0) {
            return bundleText;
        }

        final int anchorStart = bundleText.indexOf(JME_ROUTE_TYPES_ANCHOR_PREFIX);
        if (anchorStart < 0) {
            return bundleText;
        }

        final int anchorInsertIndex = bundleText.indexOf("),", anchorStart);
        if (anchorInsertIndex < 0) {
            return bundleText;
        }

        final int insertIndex = anchorInsertIndex + 2;
        return bundleText.substring(0, insertIndex) + missingRouteTypes + bundleText.substring(insertIndex);
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
