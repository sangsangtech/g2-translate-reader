package com.geeh.translatereader;

import java.util.*;

/** Reconciles overlapping detections from the Korean and Japanese models. */
final class OcrRows {
    record Row(String text, int left, int top, int right, int bottom) { }
    static List<String> merge(List<Row> korean, List<Row> japanese) {
        List<Row> rows = new ArrayList<>(korean);
        for (Row jp : japanese) {
            if (!TranslationLanguage.JAPANESE.accepts(jp.text)) continue;
            List<Row> overlap = rows.stream().filter(ko -> overlaps(ko, jp)).toList();
            boolean kana = jp.text.matches(".*[\\p{IsHiragana}\\p{IsKatakana}].*");
            boolean hangul = overlap.stream().anyMatch(ko -> ko.text.matches(".*[가-힣ㄱ-ㅎㅏ-ㅣ].*"));
            // Kana is strong evidence for a Japanese target. For ambiguous all-Han
            // output retain a Korean source if the Korean model recognized Hangul.
            if (kana || !hangul) { rows.removeAll(overlap); rows.add(jp); }
        }
        rows.sort(Comparator.comparingInt(Row::top).thenComparingInt(Row::left));
        return rows.stream().map(Row::text).toList();
    }
    private static boolean overlaps(Row a, Row b) {
        int width = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
        int height = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        long smaller = Math.min((long)(a.right - a.left) * (a.bottom - a.top), (long)(b.right - b.left) * (b.bottom - b.top));
        return smaller > 0 && (long)width * height >= smaller * .5;
    }
}
