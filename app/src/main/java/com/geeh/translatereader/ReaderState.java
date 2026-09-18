package com.geeh.translatereader;

import java.util.List;
import android.content.Context;
import android.os.SystemClock;

final class ReaderState {
    static TranslationLanguage language = TranslationLanguage.ENGLISH;
    static void loadLanguage(Context context) { language = TranslationLanguage.fromCode(context.getSharedPreferences("reader", Context.MODE_PRIVATE).getString("language", "en")); }
    static void selectLanguage(Context context, TranslationLanguage selected) {
        stop(); language = selected; autoRead = false;
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putString("language", selected.code).apply();
        diagnostic = selected.outgoing() + " 필터와 한국어 원문·" + selected.label + " 번역 표시를 확인해주세요.";
        lastText = "아직 읽은 번역문이 없습니다.";
        LocalVoice.get(context).useLanguage(selected);
    }
    static volatile boolean armed = false;
    static volatile boolean autoRead = false;
    static volatile String status = "준비 중";
    static volatile String lastText = "아직 읽은 번역문이 없습니다.";
    static volatile String diagnostic = "Even 번역 화면을 열면 감지 결과가 표시됩니다.";
    static volatile List<String> candidates = List.of();
    static final TranslationTracker tracker = new TranslationTracker();
    static String queueDescription(Context context) {
        int count = tracker.pending().size() + LocalVoice.get(context).pendingCount();
        return "재생 중·대기 " + count + "문장" + (tracker.backedUp() ? " · 대기가 많습니다. 잠시 대화를 멈추고 기다려주세요." : "");
    }
    static void drainSpeech(Context context) {
        if (!armed || !autoRead) return;
        LocalVoice voice = LocalVoice.get(context);
        for (String text : tracker.pending()) {
            if (!voice.speak(text)) break;
            tracker.accepted(text);
        }
    }
    static void process(Context context, List<String> lines, String source) {
        if (!armed) return;
        List<String> found = TranslationTracker.extract(lines, language);
        candidates = List.copyOf(found);
        diagnostic = source + " · " + language.label + " 번역 " + found.size() + "개\n" + (found.isEmpty() ? language.outgoing() + " 필터와 한국어 원문·" + language.label + " 번역 표시를 확인해주세요." : found.get(found.size() - 1));
        if (!autoRead) return;
        try {
            tracker.update(found, SystemClock.elapsedRealtime());
        } catch (IllegalStateException e) { stop(); status = e.getMessage(); }
    }
    static void stop() { armed = false; tracker.reset(); candidates = List.of(); status = "읽기 중지"; }
}
