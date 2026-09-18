package com.geeh.translatereader;

import java.util.Locale;

public enum TranslationLanguage {
    ENGLISH("en", "EN", "영어", Locale.US, "Hello. Nice to meet you."),
    JAPANESE("ja", "JA", "일본어", Locale.JAPAN, "こんにちは。お会いできてうれしいです。");

    public final String code, marker, label, sample;
    public final Locale locale;
    TranslationLanguage(String code, String marker, String label, Locale locale, String sample) {
        this.code = code; this.marker = marker; this.label = label; this.locale = locale; this.sample = sample;
    }
    public String outgoing() { return "KO→" + marker; }
    public boolean accepts(String text) {
        return this == JAPANESE ? text.matches(".*[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}].*") : text.matches(".*[A-Za-z].*");
    }
    public static TranslationLanguage fromCode(String code) { return "ja".equals(code) ? JAPANESE : ENGLISH; }
}
