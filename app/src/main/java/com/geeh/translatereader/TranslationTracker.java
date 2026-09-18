package com.geeh.translatereader;

import java.util.*;
import java.util.regex.Pattern;
import java.text.Normalizer;

/** Only the selected outgoing language following Korean source text is eligible. */
public final class TranslationTracker {
    private static final Pattern KOREAN = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣ]");
    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Set<String> consumed = new HashSet<>();
    private final Map<String, String> planned = new LinkedHashMap<>();
    private final Map<String, String> waiting = new LinkedHashMap<>();
    private boolean baseline = true;
    private boolean backedUp;

    public static List<String> extract(List<String> lines) {
        return extract(lines, TranslationLanguage.ENGLISH);
    }
    public static List<String> extract(List<String> lines, TranslationLanguage language) {
        List<String> results = new ArrayList<>();
        boolean outgoing = false, hasKorean = false;
        StringBuilder english = new StringBuilder();
        for (String node : lines) for (String part : node.split("\\R")) {
            String line = Normalizer.normalize(part, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
            if (line.isEmpty()) continue;
            String direction = line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            // JA is the language code; also accept JP used by some localized screens.
            direction = direction.replace("JP", "JA");
            if (direction.matches("(KO(EN|JA)|(EN|JA)KO)")) {
                flush(results, english); hasKorean = false; outgoing = direction.equals("KO" + language.marker); continue;
            }
            if (!outgoing) continue;
            if (line.matches("(一時停止|終了|翻訳|再開|すべて)")) { flush(results, english); hasKorean = false; continue; }
            if (KOREAN.matcher(line).find()) {
                if (english.length() > 0) { flush(results, english); hasKorean = false; }
                // These controls must never become a source sentence.
                if (line.matches(".*(일시 정지|종료|새 번역|번역 읽기|다시 시작|인식 대기).*")) { hasKorean = false; continue; }
                hasKorean = true;
            } else if (hasKorean && language.accepts(line) && !line.matches("(?i)(all|en|ko|ja|jp|pause|end|translate|start)")) {
                if (english.length() > 0 && language == TranslationLanguage.ENGLISH) english.append(' ');
                english.append(line);
            }
        }
        flush(results, english);
        return results;
    }
    private static void flush(List<String> out, StringBuilder text) {
        if (text.length() > 0 && text.length() <= 1500) out.add(text.toString());
        text.setLength(0);
    }
    private static String key(String text) { return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }
    public void reset() { firstSeen.clear(); consumed.clear(); planned.clear(); waiting.clear(); baseline = true; backedUp = false; }
    public List<String> pending() { return List.copyOf(waiting.values()); }
    public boolean backedUp() { return backedUp; }
    /** Remove only after the voice engine has accepted the sentence. */
    public void accepted(String text) {
        Iterator<Map.Entry<String, String>> items = waiting.entrySet().iterator();
        while (items.hasNext()) {
            if (items.next().getValue().equals(text)) { items.remove(); return; }
        }
    }
    private static boolean sentenceEnded(String text) { return text.matches(".*[.!?。！？][\"'’”)」』】]?$"); }
    /** The old sentence must still end at a sentence boundary in the new text. */
    private static String continuation(String text, int prefixLetters) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        int offset = 0, letters = 0;
        while (offset < normalized.length() && letters < prefixLetters) {
            int cp = normalized.codePointAt(offset); offset += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) letters++;
        }
        while (offset < normalized.length() && Character.isWhitespace(normalized.charAt(offset))) offset++;
        if (offset >= normalized.length() || ".!?。！？".indexOf(normalized.charAt(offset)) < 0) return null;
        while (offset < normalized.length() && (Character.isWhitespace(normalized.charAt(offset)) || ".!?。！？\"'’”)」』】".indexOf(normalized.charAt(offset)) >= 0)) offset++;
        String tail = normalized.substring(offset).trim();
        return key(tail).isEmpty() ? null : tail;
    }
    public List<String> update(List<String> candidates, long now) {
        Set<String> current = new HashSet<>();
        for (String text : candidates) current.add(key(text));
        firstSeen.keySet().retainAll(current);
        if (baseline) { consumed.addAll(current); baseline = false; return List.of(); }
        backedUp = false;
        for (String text : candidates) {
            String k = key(text);
            if (k.isEmpty() || consumed.contains(k)) continue;
            firstSeen.putIfAbsent(k, now);
            long settle = sentenceEnded(text) ? 800 : 1500;
            if (now - firstSeen.get(k) < settle) continue;
            String toRead = text;
            var prefix = planned.entrySet().stream()
                .filter(e -> k.startsWith(e.getKey()) && sentenceEnded(e.getValue()))
                .max(Comparator.comparingInt(e -> e.getKey().length())).orElse(null);
            if (prefix != null) {
                String tail = continuation(text, prefix.getKey().codePointCount(0, prefix.getKey().length()));
                if (tail != null) toRead = tail;
            }
            // Keep stable text even if it scrolls out while the phone is speaking.
            // At the memory bound, retain all existing work and retry visible new rows.
            if (waiting.size() >= 64) { backedUp = true; continue; }
            consumed.add(k); waiting.put(k, toRead); planned.put(k, text);
            if (planned.size() > 100) planned.remove(planned.keySet().iterator().next());
        }
        // Stop rather than silently replay when a very long session exhausts memory bounds.
        if (consumed.size() > 3000) throw new IllegalStateException("문장이 많아 읽기를 멈췄습니다. 새 세션으로 시작해주세요.");
        return pending();
    }
}
