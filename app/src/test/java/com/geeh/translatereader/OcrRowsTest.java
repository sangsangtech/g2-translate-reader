package com.geeh.translatereader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OcrRowsTest {
    private OcrRows.Row row(String text, int y) { return new OcrRows.Row(text, 10, y, 210, y + 20); }
    @Test public void combinesSourceAndJapaneseWithoutRepeatingOverlappingDetections() {
        List<String> result = OcrRows.merge(
            List.of(row("KO>JA", 10), row("안녕하세요", 50), row("오인식", 90)),
            List.of(row("KO>JA", 10), row("漢字誤認", 50), row("こんにちは。", 90)));
        assertEquals(List.of("KO>JA", "안녕하세요", "こんにちは。"), result);
        assertEquals(List.of("こんにちは。"), TranslationTracker.extract(result, TranslationLanguage.JAPANESE));
    }
    @Test public void preservesDistinctKanjiOnlyRowAndDisplayOrder() {
        assertEquals(List.of("KO>JA", "도쿄", "東京"), OcrRows.merge(
            List.of(row("도쿄", 50), row("KO>JA", 10)), List.of(row("東京", 90))));
    }
    @Test public void doesNotMergeSideBySideTextBoxes() {
        assertEquals(List.of("안내", "日本語"), OcrRows.merge(List.of(row("안내", 10)),
            List.of(new OcrRows.Row("日本語", 250, 10, 450, 30))));
    }
}
