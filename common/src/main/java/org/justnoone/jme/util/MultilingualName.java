package org.justnoone.jme.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatting of MTR multilingual names ({@code "東京|Tokyo"}) for the web System Map.
 * <p>
 * MTR's web map splits names on {@code |} itself (its {@code splitName} pipe) and styles CJK parts separately, so the
 * separator must survive. Route names may additionally use {@code ||} to separate route variations.
 */
public final class MultilingualName {

    public enum Mode {
        NORMAL,
        CJK_ONLY,
        NON_CJK_ONLY
    }

    private MultilingualName() {
    }

    public static String formatName(String raw, Mode mode) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || mode == null || mode == Mode.NORMAL) {
            // Leave the "|" separators to the web map, which lays out each language on its own line.
            return value;
        }

        final boolean keepCjk = mode == Mode.CJK_ONLY;
        final List<String> keptParts = new ArrayList<>();
        final String[] parts = value.split("\\|");
        if (parts.length > 1) {
            for (final String partRaw : parts) {
                final String part = partRaw.trim();
                if (!part.isEmpty() && containsCjk(part) == keepCjk) {
                    keptParts.add(part);
                }
            }
        }

        if (keptParts.isEmpty()) {
            // Single-part (or unsplittable) name: filter characters instead of whole parts.
            final String filtered = filterByCjk(value, keepCjk);
            return filtered.isEmpty() ? value.replace('|', ' ').trim() : filtered;
        }
        return String.join("|", keptParts);
    }

    public static String formatRouteName(String raw, Mode mode) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || !value.contains("||")) {
            return formatName(value, mode);
        }

        final String[] parts = value.split("\\|\\|");
        final String base = formatName(parts[0], mode);
        final StringBuilder variations = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String formatted = formatName(parts[i], mode).trim();
            if (formatted.isEmpty()) {
                continue;
            }
            if (variations.length() > 0) {
                variations.append(" / ");
            }
            variations.append(formatted);
        }
        return variations.length() == 0 ? base : base + "||" + variations;
    }

    public static boolean containsCjk(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            if (isCjk(codePoint)) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    public static boolean isCjk(int codePoint) {
        final Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.of(codePoint);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        switch (script) {
            case HAN:
            case HIRAGANA:
            case KATAKANA:
            case HANGUL:
            case BOPOMOFO:
                return true;
            default:
                break;
        }

        final Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS;
    }

    private static String filterByCjk(String text, boolean keepCjk) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '|') {
                out.append(' ');
                continue;
            }

            final boolean isCjk = isCjk(codePoint);
            if (keepCjk && !isCjk) {
                // Keep digits, spacing and simple punctuation for readability next to CJK names (eg line numbers).
                if (Character.isDigit(codePoint) || codePoint == '-' || codePoint == '_' || codePoint == '/' || codePoint == '(' || codePoint == ')' || codePoint == '.' || codePoint == ',') {
                    out.appendCodePoint(codePoint);
                } else if (Character.isWhitespace(codePoint)) {
                    out.append(' ');
                }
                continue;
            }
            if (!keepCjk && isCjk) {
                continue;
            }
            out.appendCodePoint(codePoint);
        }

        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }
}
