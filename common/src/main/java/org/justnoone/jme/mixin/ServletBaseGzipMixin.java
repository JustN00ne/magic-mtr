package org.justnoone.jme.mixin;

import org.mtr.core.Main;
import org.mtr.core.servlet.HttpResponseStatus;
import org.mtr.core.servlet.ServletBase;
import org.mtr.libraries.javax.servlet.AsyncContext;
import org.mtr.libraries.javax.servlet.ServletOutputStream;
import org.mtr.libraries.javax.servlet.WriteListener;
import org.mtr.libraries.javax.servlet.http.HttpServletRequest;
import org.mtr.libraries.javax.servlet.http.HttpServletResponse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Enable gzip compression for MTR's embedded Jetty servlet responses.
 * <p>
 * This is especially important for the {@code /mtr/api/map/rails} endpoint which can become very large.
 */
@Mixin(value = ServletBase.class, remap = false)
public abstract class ServletBaseGzipMixin {

    @Unique
    private static final int JME_GZIP_MIN_BYTES = 1024;
    @Unique
    private static final int JME_WRITE_CHUNK_BYTES = 8192;

    @Inject(method = "sendResponse", at = @At("HEAD"), cancellable = true)
    private static void jme$sendResponseGzip(HttpServletResponse httpServletResponse, AsyncContext asyncContext, String content, String contentType, HttpResponseStatus httpResponseStatus, CallbackInfo ci) {
        try {
            final String safeContent = content == null ? "" : content;
            final String safeContentType = contentType == null ? "text/plain" : contentType;

            byte[] bytes = safeContent.getBytes(StandardCharsets.UTF_8);

            final boolean isRedirect = httpResponseStatus == HttpResponseStatus.REDIRECT;
            final boolean shouldGzip = !isRedirect && jme$shouldGzip(asyncContext, safeContentType, bytes.length);
            if (shouldGzip) {
                bytes = jme$gzip(bytes);
            }

            httpServletResponse.addHeader("Content-Type", safeContentType);
            httpServletResponse.addHeader("Access-Control-Allow-Origin", "*");
            if (shouldGzip) {
                httpServletResponse.addHeader("Content-Encoding", "gzip");
                httpServletResponse.addHeader("Vary", "Accept-Encoding");
            }
            if (isRedirect) {
                httpServletResponse.addHeader("Location", safeContent);
            }

            // Help clients and Jetty allocate buffers efficiently.
            try {
                httpServletResponse.setContentLength(bytes.length);
            } catch (Exception ignored) {
            }

            final ServletOutputStream servletOutputStream = httpServletResponse.getOutputStream();
            final byte[] finalBytes = bytes;
            final int[] offset = {0};

            servletOutputStream.setWriteListener(new WriteListener() {
                @Override
                public void onWritePossible() {
                    try {
                        while (servletOutputStream.isReady()) {
                            final int remaining = finalBytes.length - offset[0];
                            if (remaining <= 0) {
                                httpServletResponse.setStatus(httpResponseStatus.code);
                                asyncContext.complete();
                                return;
                            }

                            final int chunk = Math.min(remaining, JME_WRITE_CHUNK_BYTES);
                            servletOutputStream.write(finalBytes, offset[0], chunk);
                            offset[0] += chunk;
                        }
                    } catch (Exception e) {
                        Main.LOGGER.error("", e);
                        try {
                            asyncContext.complete();
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    try {
                        asyncContext.complete();
                    } catch (Exception ignored) {
                    }
                }
            });

            ci.cancel();
        } catch (Exception e) {
            Main.LOGGER.error("", e);
        }
    }

    @Unique
    private static boolean jme$shouldGzip(AsyncContext asyncContext, String contentType, int rawBytesLength) {
        if (rawBytesLength < JME_GZIP_MIN_BYTES) {
            return false;
        }

        final String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        final boolean isTextLike = normalizedType.contains("json")
                || normalizedType.startsWith("text/")
                || normalizedType.contains("javascript")
                || normalizedType.contains("xml")
                || normalizedType.contains("svg");
        if (!isTextLike) {
            return false;
        }

        try {
            final Object requestObject = asyncContext == null ? null : asyncContext.getRequest();
            if (!(requestObject instanceof HttpServletRequest)) {
                return true;
            }

            final HttpServletRequest request = (HttpServletRequest) requestObject;
            final String gzipParamRaw = request.getParameter("gzip");
            if (gzipParamRaw != null) {
                final String gzipParam = gzipParamRaw.trim().toLowerCase(Locale.ROOT);
                if ("0".equals(gzipParam) || "false".equals(gzipParam) || "off".equals(gzipParam) || "no".equals(gzipParam)) {
                    return false;
                }
                if ("1".equals(gzipParam) || "true".equals(gzipParam) || "on".equals(gzipParam) || "yes".equals(gzipParam)) {
                    return true;
                }
            }

            final String acceptEncoding = request.getHeader("Accept-Encoding");
            if (acceptEncoding == null || acceptEncoding.isEmpty()) {
                return false;
            }

            return acceptEncoding.toLowerCase(Locale.ROOT).contains("gzip");
        } catch (Exception ignored) {
            // Prefer returning the uncompressed payload on errors.
            return false;
        }
    }

    @Unique
    private static byte[] jme$gzip(byte[] raw) throws Exception {
        if (raw == null || raw.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.max(256, raw.length / 4));
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(raw);
            gzipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }
}

