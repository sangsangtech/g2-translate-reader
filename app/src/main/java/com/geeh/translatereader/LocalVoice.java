package com.geeh.translatereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.*;
import java.util.*;

/** Installed offline voices for the selected language only. */
final class LocalVoice {
    private static LocalVoice instance;
    private TextToSpeech tts;
    private final Context context;
    private final SharedPreferences prefs;
    private String engine;
    private TranslationLanguage language;
    private int generation;
    private List<Voice> voices = List.of();
    private List<TextToSpeech.EngineInfo> engines = List.of();
    private boolean ready;
    private final Map<String, String> pending = new LinkedHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    static LocalVoice get(Context c) { if (instance == null) instance = new LocalVoice(c.getApplicationContext()); return instance; }
    private LocalVoice(Context c) {
        context = c; prefs = c.getSharedPreferences("voice", Context.MODE_PRIVATE);
        ReaderState.loadLanguage(c); language = ReaderState.language;
        initializeEngine(prefs.getString("engine", ""));
    }
    private void initializeEngine(String name) {
        int request = ++generation;
        ready = false; voices = List.of(); pending.clear(); ReaderState.tracker.reset();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        engine = name; ReaderState.status = "휴대폰 음성을 준비하는 중";
        TextToSpeech.OnInitListener listener = result -> main.post(() -> { if (generation == request) initialize(result, request); });
        tts = name.isEmpty() ? new TextToSpeech(context, listener) : new TextToSpeech(context, listener, name);
    }
    private void initialize(int result, int request) {
        if (result != TextToSpeech.SUCCESS) { ReaderState.status = "음성 엔진을 시작하지 못했습니다. 다른 엔진을 선택해주세요."; return; }
        engines = new ArrayList<>(tts.getEngines());
        Set<Voice> available = tts.getVoices();
        voices = available == null ? List.of() : available.stream()
            .filter(v -> v.getLocale().getLanguage().equals(language.code) && !v.isNetworkConnectionRequired())
            .filter(v -> v.getFeatures() == null || !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            .sorted(Comparator.comparingInt(Voice::getQuality).reversed()
                .thenComparingInt(v -> v.getLocale().equals(language.locale) ? 0 : 1)
                .thenComparing(v -> v.getLocale().toLanguageTag()).thenComparing(Voice::getName)).toList();
        String saved = prefs.getString(voiceKey(), language == TranslationLanguage.ENGLISH ? prefs.getString("voice:" + engine, "") : "");
        Voice selected = voices.stream().filter(v -> v.getName().equals(saved)).findFirst().orElse(voices.isEmpty() ? null : voices.get(0));
        if (selected == null || tts.setVoice(selected) == TextToSpeech.ERROR) {
            ReaderState.status = "이 엔진에 설치된 오프라인 " + language.label + " 음성이 없습니다. 다른 엔진을 선택하거나 휴대폰 TTS 설정에서 " + language.label + " 음성을 설치해주세요."; return;
        }
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setSpeechRate(rate()); tts.setPitch(pitch());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) { main.post(() -> { if (generation == request && pending.containsKey(id)) ReaderState.lastText = pending.get(id); }); }
            public void onDone(String id) { main.post(() -> { if (generation == request) finished(id, false); }); }
            public void onError(String id) { main.post(() -> { if (generation == request) finished(id, true); }); }
            public void onStop(String id, boolean interrupted) { main.post(() -> { if (generation == request) finished(id, true); }); }
        });
        ready = true; prefs.edit().putString("engine", engine).apply(); ReaderState.status = "오프라인 " + language.label + " 음성 준비 완료";
    }
    private String voiceKey() { return "voice:" + engine + ":" + language.code; }
    void useLanguage(TranslationLanguage selected) { if (language != selected) { language = selected; initializeEngine(engine); } }
    android.content.Intent installVoiceIntent() {
        String active = engine.isEmpty() && tts != null ? tts.getDefaultEngine() : engine;
        return new android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(active);
    }
    void refreshVoices() { initializeEngine(engine); }
    List<TextToSpeech.EngineInfo> engines() { return List.copyOf(engines); }
    List<Voice> voices() { return List.copyOf(voices); }
    String voiceLabel(Voice voice) {
        String quality = voice.getQuality() >= Voice.QUALITY_HIGH ? "고음질" : "일반 음질";
        return voice.getLocale().getDisplayName(Locale.KOREAN) + " · 음성 " + (voices.indexOf(voice) + 1) + " · " + quality;
    }
    String description() {
        String activeEngine = engine.isEmpty() && tts != null ? tts.getDefaultEngine() : engine;
        String name = engines.stream().filter(e -> e.name.equals(activeEngine)).map(e -> e.label).findFirst().orElse("휴대폰 기본 엔진");
        Voice voice = ready ? tts.getVoice() : null;
        return name + "\n" + (voice == null ? "사용할 " + language.label + " 음성 없음 / 준비 중" : voiceLabel(voice));
    }
    boolean selectEngine(String name) {
        if (!name.isEmpty() && engines.stream().noneMatch(e -> e.name.equals(name))) return false;
        initializeEngine(name); return true;
    }
    boolean selectVoice(String name) {
        if (!ready) return false;
        Voice voice = voices.stream().filter(v -> v.getName().equals(name)).findFirst().orElse(null);
        if (voice == null) return false;
        stop();
        if (tts.setVoice(voice) == TextToSpeech.ERROR) { ReaderState.status = "이 음성을 선택하지 못했습니다."; return false; }
        prefs.edit().putString(voiceKey(), name).apply();
        ReaderState.status = "음성이 변경됐습니다. 시험 버튼으로 들어보세요."; return true;
    }
    float rate() { return Math.max(.7f, Math.min(1.3f, prefs.getFloat("rate", .97f))); }
    float pitch() { return Math.max(.8f, Math.min(1.2f, prefs.getFloat("pitch", 1f))); }
    void setRate(float rate) { rate = Math.max(.7f, Math.min(1.3f, rate)); prefs.edit().putFloat("rate", rate).apply(); if (ready) tts.setSpeechRate(rate); }
    void setPitch(float pitch) { pitch = Math.max(.8f, Math.min(1.2f, pitch)); prefs.edit().putFloat("pitch", pitch).apply(); if (ready) tts.setPitch(pitch); }
    private void finished(String id, boolean error) {
        String text = pending.remove(id);
        if (text == null) return;
        if (error) ReaderState.status = "휴대폰 음성 재생 오류 · ‘감지한 마지막 문장 읽기’로 다시 재생할 수 있습니다.";
        ReaderState.drainSpeech(context);
    }
    boolean speak(String text) {
        if (!ready) return false;
        if (pending.size() >= 3) return false;
        String id = UUID.randomUUID().toString(); pending.put(id, text);
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) {
            pending.remove(id); ReaderState.status = "휴대폰 음성이 문장을 받지 못했습니다. 대기 문장을 유지합니다."; return false;
        }
        return true;
    }
    void stop() { if (tts != null) tts.stop(); pending.clear(); ReaderState.tracker.reset(); }
    boolean ready() { return ready; }
    int pendingCount() { return pending.size(); }
}
