package org.justnoone.jme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultilingualNameTest {

    @Test
    void normalModeKeepsSeparatorForTheWebMap() {
        // MTR's web map splits on "|" and styles the CJK line itself; the old code turned this into "東京 / Tokyo".
        assertEquals("東京|Tokyo", MultilingualName.formatName("東京|Tokyo", MultilingualName.Mode.NORMAL));
        assertEquals("東京|とうきょう|Tokyo", MultilingualName.formatName(" 東京|とうきょう|Tokyo ", MultilingualName.Mode.NORMAL));
    }

    @Test
    void cjkOnlyKeepsAllCjkParts() {
        assertEquals("東京|とうきょう", MultilingualName.formatName("東京|とうきょう|Tokyo", MultilingualName.Mode.CJK_ONLY));
        assertEquals("서울역", MultilingualName.formatName("서울역|Seoul Station", MultilingualName.Mode.CJK_ONLY));
    }

    @Test
    void nonCjkOnlyKeepsLatinParts() {
        assertEquals("Tokyo", MultilingualName.formatName("東京|とうきょう|Tokyo", MultilingualName.Mode.NON_CJK_ONLY));
        assertEquals("Central|Centre", MultilingualName.formatName("中環|Central|Centre", MultilingualName.Mode.NON_CJK_ONLY));
    }

    @Test
    void singlePartNamesAreFilteredByCharacter() {
        assertEquals("1號線", MultilingualName.formatName("Line 1號線", MultilingualName.Mode.CJK_ONLY));
        assertEquals("Line 1", MultilingualName.formatName("Line 1號線", MultilingualName.Mode.NON_CJK_ONLY));
        // Nothing left after filtering: fall back to the full name instead of an empty label.
        assertEquals("Tokyo", MultilingualName.formatName("Tokyo", MultilingualName.Mode.CJK_ONLY));
    }

    @Test
    void routeVariationsSurvive() {
        assertEquals("荃灣綫|Tsuen Wan Line||Express / 快車|Fast", MultilingualName.formatRouteName("荃灣綫|Tsuen Wan Line||Express||快車|Fast", MultilingualName.Mode.NORMAL));
        assertEquals("荃灣綫||快車", MultilingualName.formatRouteName("荃灣綫|Tsuen Wan Line||快車|Express", MultilingualName.Mode.CJK_ONLY));
    }

    @Test
    void detectsCjkOutsideTheBasicBlocks() {
        assertTrue(MultilingualName.isCjk("𠀋".codePointAt(0))); // CJK Extension B (supplementary plane)
        assertTrue(MultilingualName.isCjk('ㄅ')); // Bopomofo
        assertTrue(MultilingualName.isCjk('。')); // CJK punctuation
        assertTrue(MultilingualName.isCjk('Ａ')); // fullwidth form
        assertFalse(MultilingualName.isCjk('A'));
        assertFalse(MultilingualName.isCjk('é'));
    }
}
