package com.geeh.translatereader;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Presentation only: capture, OCR and the speech queue stay in their existing owners. */
public final class MainActivity extends Activity {
    private static final int BG = 0xFFF2F2F2, INK = 0xFF171717, MUTED = 0xFF686868;
    private static final int ACCENT = INK, SELECTED = 0xFFE8E8E8, LINE = 0xFFE3E3E3, WHITE = Color.WHITE;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { public void run() { renderState(); main.postDelayed(this, 500); } };
    private TextView status, heroTitle, heroCaption, badge, translation, queue, voiceDescription, screenPermission, settingsStatus, screenSummary;
    private Button primary, replay, voiceInstall, screenGrant, homeTab, settingsTab;
    private Switch autoRead, screenMode;
    private ScrollView homePage, settingsPage;
    private int page;
    private boolean syncing;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ReaderState.loadLanguage(this);
        page = saved == null ? 0 : saved.getInt("page", 0);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(WHITE);
        // ReaderTheme defines the system bar appearance. Accessing the window's
        // insets controller before setContentView creates its DecorView crashes.
        LinearLayout root = column(); root.setBackgroundColor(BG); setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        FrameLayout pages = new FrameLayout(this); root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        homePage = scroll(); settingsPage = scroll(); pages.addView(homePage); pages.addView(settingsPage);
        buildHome(); buildSettings();
        LinearLayout nav = row(); nav.setBackgroundColor(WHITE); nav.setPadding(dp(18), dp(8), dp(18), dp(8));
        homeTab = navButton("통역", () -> showPage(0)); settingsTab = navButton("설정", () -> showPage(1));
        addEqual(nav, homeTab, 4); addEqual(nav, settingsTab, 4);
        View divider = new View(this); divider.setBackgroundColor(LINE); root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(nav); showPage(page); LocalVoice.get(this); renderState();
    }

    private void buildHome() {
        TranslationLanguage language = ReaderState.language;
        LinearLayout body = pageBody(homePage);
        LinearLayout header = row();
        LinearLayout identity = column(); text(identity, "G2  /  COMPANION", 11, MUTED, true); text(identity, "번역 읽기", 28, INK, true);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        Button help = button("?", WHITE, INK, this::showGuide); help.setContentDescription("Even 연결 방법");
        header.addView(help, new LinearLayout.LayoutParams(dp(48), dp(48))); body.addView(header); gap(body, 24);

        LinearLayout languageCard = card(body, WHITE, 20); LinearLayout languages = row();
        LinearLayout from = column(); text(from, "내 언어", 12, MUTED, false); text(from, "한국어", 22, INK, true);
        languages.addView(from, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = new TextView(this); arrow.setText("⇄"); arrow.setTextColor(MUTED); arrow.setTextSize(26); arrow.setGravity(Gravity.CENTER);
        languages.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout to = column(); text(to, "상대방 언어  ▾", 12, MUTED, false); text(to, language.label, 22, INK, true);
        languages.addView(to, new LinearLayout.LayoutParams(0, -2, 1)); languageCard.addView(languages);
        languageCard.setOnClickListener(v -> chooseLanguage()); languageCard.setFocusable(true);
        languageCard.setContentDescription("통역 언어 한국어와 " + language.label + ", 누르면 변경");
        gap(body, 16);

        LinearLayout hero = card(body, WHITE, 24);
        badge = text(hero, "●  준비 중", 12, MUTED, false); gap(hero, 16);
        heroTitle = text(hero, "대화를 시작해볼까요?", 25, INK, false);
        heroCaption = text(hero, "상대방의 말은 안경으로,\n내 말은 휴대폰 목소리로.", 15, MUTED, false);
        gap(hero, 22); primary = button("Even 화면 연결하기", ACCENT, WHITE, () -> { if (CaptureService.running) stopReading(); else startCapture(); });
        hero.addView(primary, new LinearLayout.LayoutParams(-1, dp(56)));
        status = text(hero, "", 12, MUTED, false); status.setPadding(0, dp(12), 0, 0);
        gap(body, 16);

        LinearLayout automatic = card(body, WHITE, 18);
        autoRead = toggle(automatic, "자동으로 읽기", "새 번역이 나오면 순서대로 읽어요.", ReaderState.autoRead);
        autoRead.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            ReaderState.autoRead = checked; ReaderState.tracker.reset(); renderState();
        }); gap(body, 16);

        LinearLayout recent = card(body, WHITE, 20); LinearLayout recentHeader = row();
        TextView title = standalone("최근 번역", 15, INK, true); recentHeader.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        replay = button("읽어주기", 0xFFF0F0F0, INK, this::readLatest); recentHeader.addView(replay, new LinearLayout.LayoutParams(dp(100), dp(48))); recent.addView(recentHeader);
        gap(recent, 12); translation = text(recent, "번역문이 여기에 표시돼요.", 20, INK, false);
        translation.setTextIsSelectable(true); translation.setMinHeight(dp(62));
        queue = text(recent, "", 12, MUTED, false); gap(body, 16);

        Button sample = button("목소리 미리 듣기", WHITE, INK, this::previewVoice); body.addView(sample, full());
        gap(body, 12); screenSummary = text(body, "", 12, MUTED, false);
        gap(body, 12); actionRow(body, "연결 방법 보기", "Even 앱의 언어도 함께 맞춰주세요.", this::showGuide);
    }

    private void buildSettings() {
        TranslationLanguage language = ReaderState.language;
        LinearLayout body = pageBody(settingsPage);
        text(body, "MAKE IT YOURS", 11, MUTED, true); text(body, "나에게 맞게", 28, INK, true);
        text(body, "목소리와 화면을 편하게 조절하세요.", 14, MUTED, false); gap(body, 24);
        section(body, "목소리"); LinearLayout voiceCard = card(body, WHITE, 20);
        voiceDescription = text(voiceCard, "음성을 준비하고 있어요.", 14, MUTED, false);
        actionRow(voiceCard, language.label + " 목소리", "마음에 드는 목소리를 들어보고 골라보세요.", this::chooseVoice);
        Button sample = button("선택한 목소리 듣기", 0xFFF0F0F0, INK, this::previewVoice); voiceCard.addView(sample, full()); gap(voiceCard, 18);
        slider(voiceCard, "읽기 속도", 70, 130, Math.round(LocalVoice.get(this).rate() * 100), value -> LocalVoice.get(this).setRate(value / 100f), true);
        gap(voiceCard, 16); slider(voiceCard, "음높이", 80, 120, Math.round(LocalVoice.get(this).pitch() * 100), value -> LocalVoice.get(this).setPitch(value / 100f), false);
        divider(voiceCard); actionRow(voiceCard, "음성 엔진", "휴대폰에 설치된 음성 서비스를 선택해요.", this::chooseEngine);
        voiceInstall = button(language.label + " 음성 설치", 0xFFF0F0F0, INK, this::openVoiceData); voiceCard.addView(voiceInstall, full());
        text(voiceCard, "오프라인 목소리를 사용해요. 음성이 없다면 먼저 설치해주세요.", 12, MUTED, false); gap(body, 24);

        section(body, "대화 중 화면"); LinearLayout displayCard = card(body, WHITE, 20);
        screenMode = toggle(displayCard, "화면 켜두기", "번역 중 화면이 자동으로 꺼지지 않아요.", ReadingScreen.enabled(this));
        screenMode.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            ReadingScreen.setEnabled(this, checked); updateScreenMode();
            if (checked && !Settings.canDrawOverlays(this)) openOverlaySettings();
        }); gap(displayCard, 16);
        slider(displayCard, "기본 밝기", 5, 50, ReadingScreen.percent(this), value -> { ReadingScreen.setPercent(this, value); updateScreenMode(); }, false);
        screenPermission = text(displayCard, "", 12, MUTED, false);
        screenGrant = button("표시 권한 허용하기", 0xFFF0F0F0, INK, this::openOverlaySettings); displayCard.addView(screenGrant, full());
        text(displayCard, "화면 위 ‘밝게’ 버튼으로 평소 밝기로 바꿀 수 있어요. 전원 버튼으로 잠그면 번역 읽기가 끝나요.", 12, MUTED, false); gap(body, 24);

        section(body, "도움말"); LinearLayout support = card(body, WHITE, 20);
        actionRow(support, "연결 방법", "처음 연결하거나 언어를 바꿀 때", this::showGuide); divider(support);
        actionRow(support, "동작 상태 확인", "소리가 나오지 않거나 번역이 빠질 때", this::showDiagnostics);
        settingsStatus = text(support, "", 12, MUTED, false); gap(body, 24);
        text(body, "G2 번역 읽기  ·  0.2.2", 12, MUTED, true);
        text(body, "화면과 번역문은 이 앱에 저장하지 않아요.\nEven 공식 앱과 함께 사용하는 개인용 보조 앱입니다.", 12, MUTED, false);
    }

    private void renderState() {
        if (status == null || screenMode == null) return;
        boolean running = CaptureService.running, active = running && ReaderState.armed;
        boolean voiceReady = LocalVoice.get(this).ready();
        set(badge, running ? "●  화면 연결됨" : "○  연결 대기");
        set(heroTitle, active ? (ReaderState.autoRead ? "번역을 읽고 있어요" : "번역을 확인해보세요") : running ? "읽기가 멈췄어요" : "대화를 시작해볼까요?");
        set(heroCaption, active ? (ReaderState.autoRead ? "Even의 새 번역을 순서대로 읽어드려요." : "읽어주기를 누르거나 자동 읽기를 켜세요.") : "상대방의 말은 안경으로,\n내 말은 휴대폰 목소리로.");
        set(primary, running ? "번역 읽기 종료" : "Even 화면 연결하기");
        set(status, ReaderState.status); set(settingsStatus, ReaderState.status);
        List<String> candidates = ReaderState.candidates;
        set(translation, candidates.isEmpty() ? "번역문이 여기에 표시돼요." : candidates.get(candidates.size() - 1));
        translation.setTextColor(candidates.isEmpty() ? MUTED : INK);
        set(queue, ReaderState.queueDescription(this));
        replay.setEnabled(voiceReady && !candidates.isEmpty()); replay.setAlpha(replay.isEnabled() ? 1f : .45f);
        set(voiceDescription, LocalVoice.get(this).description());
        syncing = true; autoRead.setChecked(ReaderState.autoRead); screenMode.setChecked(ReadingScreen.enabled(this)); syncing = false;
        boolean enabled = ReadingScreen.enabled(this), allowed = Settings.canDrawOverlays(this);
        set(screenPermission, !enabled ? "화면 유지를 켜면 연결 중에만 적용돼요." : !allowed ? "‘다른 앱 위에 표시’ 권한을 허용해주세요." : "연결 중 밝기 " + ReadingScreen.percent(this) + "% · 종료하면 원래대로 돌아와요.");
        screenGrant.setVisibility(enabled && !allowed ? View.VISIBLE : View.GONE);
        set(screenSummary, enabled && allowed ? "화면 유지 켜짐  ·  기본 밝기 " + ReadingScreen.percent(this) + "%" : enabled ? "화면 유지  ·  표시 권한 필요" : "화면 유지 꺼짐  ·  설정에서 켤 수 있어요.");
    }
    private void showPage(int selected) {
        page = selected; homePage.setVisibility(page == 0 ? View.VISIBLE : View.GONE); settingsPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        homeTab.setBackground(ripple(page == 0 ? SELECTED : WHITE, 10)); settingsTab.setBackground(ripple(page == 1 ? SELECTED : WHITE, 10));
        homeTab.setSelected(page == 0); settingsTab.setSelected(page == 1);
    }
    private void stopReading() { ReaderState.stop(); LocalVoice.get(this).stop(); stopService(new Intent(this, CaptureService.class)); main.post(this::renderState); }
    private void previewVoice() {
        if (!LocalVoice.get(this).speak(ReaderState.language.sample)) inform("목소리를 재생할 수 없어요", LocalVoice.get(this).ready() ? "현재 읽고 있는 문장이 끝나면 다시 눌러주세요." : "설정에서 사용할 목소리를 선택하거나 음성을 설치해주세요.");
    }
    private void readLatest() {
        List<String> c = ReaderState.candidates;
        if (!c.isEmpty() && !LocalVoice.get(this).speak(c.get(c.size() - 1))) inform("음성을 기다리고 있어요", "현재 읽고 있는 문장이 끝나면 다시 눌러주세요.");
    }
    private void showGuide() {
        TranslationLanguage l = ReaderState.language;
        inform("Even과 연결하기", "1. Even 앱에서 " + l.label + " ↔ 한국어 번역을 시작하세요.\n\n2. 안경 표시 방향은 " + l.label + " → 한국어, 휴대폰 필터는 " + l.outgoing() + "로 맞추세요.\n\n3. 이 앱에서 ‘Even 화면 연결하기’를 누른 뒤 ‘앱 하나 → Even’을 선택하세요.\n\n4. 번역문을 확인한 뒤 자동 읽기를 켜세요.\n\n이 앱의 언어 선택이 Even 설정을 바꾸지는 않아요.");
    }
    private void showDiagnostics() {
        inform("동작 상태", ReaderState.status + "\n\n" + ReaderState.queueDescription(this) + "\n\n" + ReaderState.diagnostic + "\n\n마지막 재생 문장\n" + ReaderState.lastText);
    }
    private void inform(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("확인", null).show(); }
    private void chooseEngine() {
        LocalVoice voice = LocalVoice.get(this); List<android.speech.tts.TextToSpeech.EngineInfo> engines = voice.engines();
        List<String> labels = new ArrayList<>(); labels.add("휴대폰 기본 엔진"); for (var engine : engines) labels.add(engine.label);
        new AlertDialog.Builder(this).setTitle("음성 엔진").setItems(labels.toArray(String[]::new), (dialog, which) -> voice.selectEngine(which == 0 ? "" : engines.get(which - 1).name)).show();
    }
    private void chooseVoice() {
        LocalVoice voice = LocalVoice.get(this); List<android.speech.tts.Voice> voices = voice.voices();
        if (voices.isEmpty()) { inform("목소리가 필요해요", ReaderState.language.label + " 음성을 설치하거나 다른 음성 엔진을 선택해주세요."); return; }
        String[] labels = voices.stream().map(voice::voiceLabel).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(ReaderState.language.label + " 목소리").setItems(labels, (dialog, which) -> { if (voice.selectVoice(voices.get(which).getName())) voice.speak(ReaderState.language.sample); }).show();
    }
    private void chooseLanguage() {
        if (CaptureService.running) { inform("연결을 먼저 종료해주세요", "번역 읽기를 종료한 뒤 언어를 바꿀 수 있어요."); return; }
        TranslationLanguage[] languages = TranslationLanguage.values();
        String[] labels = Arrays.stream(languages).map(l -> "한국어 ↔ " + l.label).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("상대방의 언어").setSingleChoiceItems(labels, ReaderState.language.ordinal(), (dialog, which) -> {
            dialog.dismiss(); if (languages[which] != ReaderState.language) { ReaderState.selectLanguage(this, languages[which]); recreate(); }
        }).show();
    }
    private void openVoiceData() {
        if (CaptureService.running) { inform("연결을 먼저 종료해주세요", "번역 읽기를 종료한 뒤 음성을 설치할 수 있어요."); return; }
        try { startActivityForResult(LocalVoice.get(this).installVoiceIntent(), 40); }
        catch (ActivityNotFoundException e) { inform("휴대폰 설정에서 설치해주세요", "이 음성 엔진은 설치 화면을 제공하지 않아요. 휴대폰 ‘텍스트 읽어주기’ 설정에서 " + ReaderState.language.label + " 음성을 설치하거나 다른 엔진을 선택해주세요."); }
    }
    private void startCapture() {
        if (!LocalVoice.get(this).ready()) { showPage(1); inform("목소리를 먼저 준비해주세요", ReaderState.language.label + " 목소리를 선택한 뒤 다시 연결해주세요."); return; }
        if (CaptureService.running) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 30); return;
        }
        requestCapture();
    }
    private void requestCapture() { startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), 20); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); if (request == 30) requestCapture(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 40) LocalVoice.get(this).refreshVoices();
        if (request == 20 && result == RESULT_OK && data != null) startForegroundService(new Intent(this, CaptureService.class).putExtra("result", result).putExtra("data", data));
    }
    private void openOverlaySettings() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (ActivityNotFoundException e) { inform("휴대폰 설정에서 허용해주세요", "‘다른 앱 위에 표시’에서 G2 번역 읽기를 허용해주세요."); }
    }
    private void updateScreenMode() { if (CaptureService.instance != null) CaptureService.instance.updateReadingScreen(); renderState(); }
    @Override protected void onResume() { super.onResume(); updateScreenMode(); main.removeCallbacks(refresh); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("page", page); super.onSaveInstanceState(out); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private ScrollView scroll() { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setClipToPadding(false); s.setVerticalScrollBarEnabled(false); return s; }
    private LinearLayout pageBody(ScrollView scroll) { LinearLayout b = column(); b.setPadding(dp(22), dp(24), dp(22), dp(28)); scroll.addView(b); return b; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(color == INK ? 0x33FFFFFF : 0x14000000), rounded(color, radius), rounded(WHITE, radius)); }
    private LinearLayout card(LinearLayout parent, int color, int padding) {
        LinearLayout c = column(); GradientDrawable bg = rounded(color, 12); if (color == WHITE) bg.setStroke(dp(1), LINE); c.setBackground(bg);
        c.setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); parent.addView(c, full()); return c;
    }
    private TextView standalone(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("kern");
        // Regular display type and medium labels, with system CJK fallback.
        t.setTypeface(Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), bold && size < 22 ? 500 : 400, false));
        t.setLetterSpacing(size >= 22 ? -.025f : size <= 11 ? .08f : -.01f);
        t.setLineSpacing(dp(size >= 20 ? 4 : 3), 1f); t.setIncludeFontPadding(false); return t;
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) { TextView t = standalone(value, size, color, bold); t.setPadding(0, dp(3), 0, dp(3)); parent.addView(t, full()); return t; }
    private void set(TextView view, String value) { if (!view.getText().toString().equals(value)) view.setText(value); }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void section(LinearLayout body, String title) { text(body, title, 14, INK, true); gap(body, 10); }
    private void divider(LinearLayout body) { gap(body, 14); View v = new View(this); v.setBackgroundColor(LINE); body.addView(v, new LinearLayout.LayoutParams(-1, dp(1))); gap(body, 8); }
    private Button button(String label, int background, int foreground, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(foreground); b.setTextSize(14); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setLetterSpacing(0); b.setBackground(ripple(background, 10)); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(14), dp(10), dp(14), dp(10)); b.setElevation(0); b.setStateListAnimator(null); b.setOnClickListener(v -> action.run()); return b;
    }
    private Button navButton(String label, Runnable action) { return button(label, WHITE, INK, action); }
    private void addEqual(LinearLayout parent, View child, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1); p.setMargins(dp(margin), 0, dp(margin), 0); parent.addView(child, p); }
    private void actionRow(LinearLayout parent, String title, String subtitle, Runnable action) {
        LinearLayout r = row(); r.setPadding(0, dp(12), 0, dp(12)); r.setMinimumHeight(dp(64)); r.setBackground(ripple(Color.TRANSPARENT, 12));
        LinearLayout labels = column(); text(labels, title, 15, INK, true); text(labels, subtitle, 12, MUTED, false); r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = standalone("›", 25, MUTED, false); chevron.setPadding(dp(12), 0, dp(4), 0); r.addView(chevron); r.setOnClickListener(v -> action.run()); r.setFocusable(true); r.setContentDescription(title + ". " + subtitle); parent.addView(r, full());
    }
    private Switch toggle(LinearLayout parent, String title, String subtitle, boolean checked) {
        LinearLayout r = row(); LinearLayout labels = column(); text(labels, title, 16, INK, true); text(labels, subtitle, 12, MUTED, false);
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); Switch s = new Switch(this); s.setShowText(false); s.setChecked(checked); s.setContentDescription(title); s.setMinimumHeight(dp(48)); s.setMinimumWidth(dp(52));
        s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{ACCENT, 0xFF969696}));
        s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{0xFFBDBDBD, LINE})); r.addView(s); parent.addView(r, full()); return s;
    }
    private void slider(LinearLayout parent, String title, int min, int max, int initial, java.util.function.IntConsumer change, boolean rate) {
        LinearLayout line = row(); TextView label = standalone(title, 14, INK, true); line.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = standalone(rate ? String.format(Locale.KOREAN, "%.2f×", initial / 100f) : initial + "%", 14, INK, true); line.addView(value); parent.addView(line);
        SeekBar bar = new SeekBar(this); bar.setMin(min); bar.setMax(max); bar.setProgress(initial); bar.setContentDescription(title); bar.setProgressTintList(ColorStateList.valueOf(ACCENT)); bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean fromUser) { value.setText(rate ? String.format(Locale.KOREAN, "%.2f×", n / 100f) : n + "%"); if (fromUser) change.accept(n); }
            public void onStartTrackingTouch(SeekBar b) { }
            public void onStopTrackingTouch(SeekBar b) { }
        });
    }
}
