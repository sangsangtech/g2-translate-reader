package com.geeh.translatereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

/** A visible, session-scoped window; never changes global brightness or timeout. */
final class ReadingScreen {
    private final Context context;
    private final WindowManager windows;
    private LinearLayout panel;
    private WindowManager.LayoutParams layout;
    private Button brightness;
    private boolean normalBrightness;

    ReadingScreen(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("reading_screen", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static int percent(Context c) { return Math.max(5, Math.min(50, prefs(c).getInt("brightness", 10))); }
    static void setEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("enabled", value).apply(); }
    static void setPercent(Context c, int value) { prefs(c).edit().putInt("brightness", Math.max(5, Math.min(50, value))).apply(); }

    void update() {
        if (!enabled(context) || !Settings.canDrawOverlays(context)) { close(); return; }
        if (panel == null) {
            normalBrightness = false;
            panel = new LinearLayout(context);
            GradientDrawable background = new GradientDrawable(); background.setColor(0xFF171717); background.setCornerRadius(dp(12));
            panel.setBackground(background); panel.setPadding(dp(4), 0, dp(4), 0);
            brightness = new Button(context); brightness.setAllCaps(false); brightness.setTextSize(12);
            brightness.setOnClickListener(v -> { normalBrightness = !normalBrightness; update(); });
            brightness.setTextColor(Color.WHITE); brightness.setBackgroundColor(Color.TRANSPARENT); brightness.setMinHeight(dp(48)); brightness.setStateListAnimator(null);
            panel.addView(brightness);
            Button dismiss = new Button(context); dismiss.setText("×");
            dismiss.setContentDescription("화면 유지와 밝기 조절 끄기");
            dismiss.setTextColor(Color.WHITE); dismiss.setTextSize(22); dismiss.setBackgroundColor(Color.TRANSPARENT); dismiss.setStateListAnimator(null);
            dismiss.setOnClickListener(v -> { setEnabled(context, false); close(); });
            panel.addView(dismiss, new LinearLayout.LayoutParams(dp(48), -1));
            layout = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.END; layout.y = dp(8); layout.x = dp(8);
            layout.setTitle("G2 번역 화면 유지");
            applyBrightness();
            try { windows.addView(panel, layout); }
            catch (RuntimeException e) { panel = null; ReaderState.status = "화면 유지 버튼을 표시하지 못했습니다. 다른 앱 위에 표시 권한을 확인해주세요."; }
        } else {
            applyBrightness();
            try { windows.updateViewLayout(panel, layout); }
            catch (RuntimeException e) { close(); ReaderState.status = "화면 유지가 해제되었습니다. 표시 권한을 확인해주세요."; }
        }
    }
    private void applyBrightness() {
        layout.screenBrightness = normalBrightness ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : percent(context) / 100f;
        brightness.setText(normalBrightness ? "화면 유지 · 어둡게" : percent(context) + "% · 밝게");
    }
    void close() {
        if (panel != null) {
            try { windows.removeViewImmediate(panel); } catch (IllegalArgumentException ignored) { }
            panel = null;
        }
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
