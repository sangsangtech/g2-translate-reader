package com.geeh.translatereader;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.view.WindowManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.nio.ByteBuffer;
import java.util.*;

public final class CaptureService extends Service {
    static volatile boolean running;
    static CaptureService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private TextRecognizer recognizer;
    private TextRecognizer japaneseRecognizer;
    private boolean destroyed;
    private ReadingScreen readingScreen;
    private boolean busy;
    private long lastFrame;
    private int width, height;
    private String stopReason;
    private List<String> latestLines;
    private final Runnable settle = new Runnable() {
        public void run() {
            if (running && latestLines != null) ReaderState.process(CaptureService.this, latestLines, "화면 글자 인식 " + latestLines.size() + "줄");
            if (running) ReaderState.drainSpeech(CaptureService.this);
            main.postDelayed(this, 350);
        }
    };
    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override public void onStop() {
            boolean screenOff = !getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked();
            stopReason = screenOff ? "화면이 꺼지거나 잠겨 Android가 캡처를 종료했습니다. 화면을 켜고 캡처를 다시 시작해주세요." : "화면 공유가 종료되었습니다. 캡처를 다시 시작해주세요.";
            stopSelf();
        }
        @Override public void onCapturedContentResize(int w, int h) { if (running && w > 0 && h > 0) resize(w, h); }
    };
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if ("read".equals(intent.getAction())) {
            List<String> c = ReaderState.candidates; if (!c.isEmpty()) LocalVoice.get(this).speak(c.get(c.size() - 1));
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        ReaderState.loadLanguage(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("capture", "번역 읽기", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CaptureService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent read = PendingIntent.getService(this, 2, new Intent(this, CaptureService.class).setAction("read"), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("G2 번역 읽기 실행 중")
            .setContentText("Even의 " + ReaderState.language.outgoing() + " 화면에서 " + ReaderState.language.label + " 번역을 감지합니다.").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "마지막 문장 읽기", read).build())
            .addAction(new Notification.Action.Builder(null, "중지", stop).build()).build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try {
            Intent data = intent.getParcelableExtra("data");
            if (data == null) { stopSelf(); return START_NOT_STICKY; }
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("result", 0), data);
            projection.registerCallback(callback, main);
            recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            if (ReaderState.language == TranslationLanguage.JAPANESE) japaneseRecognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            instance = this; running = true; ReaderState.tracker.reset(); ReaderState.armed = true;
            main.post(settle);
            ReaderState.status = "캡처로 새 " + ReaderState.language.label + " 번역을 기다리는 중";
            Rect bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            resize(bounds.width(), bounds.height());
            readingScreen = new ReadingScreen(this);
            readingScreen.update();
        } catch (RuntimeException e) { stopReason = "화면 캡처를 시작하지 못했습니다. 다시 시작해주세요."; stopSelf(); }
        return START_NOT_STICKY;
    }
    private void resize(int w, int h) {
        double scale = Math.min(1.0, 1920.0 / Math.max(w, h));
        int nextW = Math.max(1, (int)(w * scale)), nextH = Math.max(1, (int)(h * scale));
        if (reader != null && width == nextW && height == nextH) return;
        width = nextW; height = nextH;
        ImageReader old = reader;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::frame, main);
        int density = getResources().getDisplayMetrics().densityDpi;
        if (display == null) display = projection.createVirtualDisplay("Even translation reader", width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main);
        else { display.resize(width, height, density); display.setSurface(reader.getSurface()); }
        if (old != null) old.close();
    }
    private void frame(ImageReader source) {
        Image image;
        try { image = source.acquireLatestImage(); } catch (IllegalStateException e) { return; }
        if (image == null) return;
        Bitmap bitmap = null;
        try {
            long now = SystemClock.elapsedRealtime();
            if (destroyed || !running || !ReaderState.armed || busy || now - lastFrame < 500) return;
            lastFrame = now;
            Image.Plane plane = image.getPlanes()[0]; ByteBuffer buffer = plane.getBuffer();
            int strideWidth = plane.getRowStride() / plane.getPixelStride();
            Bitmap padded = Bitmap.createBitmap(strideWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            padded.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
            if (bitmap != padded) padded.recycle();
        } catch (RuntimeException e) { ReaderState.status = "화면을 읽지 못했습니다. 캡처를 다시 시작해주세요."; }
        finally { image.close(); }
        if (bitmap == null) return;
        final Bitmap captured = bitmap;
        busy = true;
        InputImage input = InputImage.fromBitmap(captured, 0);
        var koreanTask = recognizer.process(input);
        var japaneseTask = japaneseRecognizer == null ? null : japaneseRecognizer.process(input);
        // whenAllComplete keeps the bitmap alive until both readers have finished,
        // including when one model fails before the other has finished reading it.
        var complete = japaneseTask == null ? Tasks.whenAllComplete(koreanTask) : Tasks.whenAllComplete(koreanTask, japaneseTask);
        complete.addOnCompleteListener(task -> {
            try {
                if (destroyed || !running || !ReaderState.armed) return;
                if (!koreanTask.isSuccessful() || (japaneseTask != null && !japaneseTask.isSuccessful())) {
                    latestLines = null; ReaderState.status = "글자 인식 오류. 화면 캡처를 다시 시작해주세요."; return;
                }
                List<OcrRows.Row> korean = rows(koreanTask.getResult());
                latestLines = japaneseTask == null ? korean.stream().map(OcrRows.Row::text).toList() : OcrRows.merge(korean, rows(japaneseTask.getResult()));
            } finally { captured.recycle(); busy = false; if (destroyed) closeRecognizers(); }
        });
    }
    private List<OcrRows.Row> rows(Text result) {
        List<OcrRows.Row> rows = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) for (Text.Line line : block.getLines()) {
            Rect r = line.getBoundingBox();
            if (r != null) rows.add(new OcrRows.Row(line.getText(), r.left, r.top, r.right, r.bottom));
        }
        rows.sort(Comparator.comparingInt(OcrRows.Row::top).thenComparingInt(OcrRows.Row::left));
        return rows;
    }
    private void closeRecognizers() {
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        if (japaneseRecognizer != null) { japaneseRecognizer.close(); japaneseRecognizer = null; }
    }
    @Override public void onDestroy() {
        destroyed = true;
        if (readingScreen != null) readingScreen.close();
        running = false; instance = null; ReaderState.stop(); LocalVoice.get(this).stop();
        if (stopReason != null) ReaderState.status = stopReason;
        main.removeCallbacks(settle); latestLines = null;
        if (display != null) display.release();
        if (reader != null) { reader.setOnImageAvailableListener(null, null); reader.close(); }
        if (projection != null) { projection.unregisterCallback(callback); projection.stop(); }
        if (!busy) closeRecognizers();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    void updateReadingScreen() { if (readingScreen != null) readingScreen.update(); }
}
