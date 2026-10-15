package com.zamzzz.ztouch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class TouchService extends AccessibilityService {

    private int tapDelay = 100;
    private int tapDuration = 50;
    private int swipeDuration = 300;
    private int swipeDistance = 500;
    private float sensX = 1.0f;
    private float sensY = 1.0f;

    private WindowManager windowManager;
    private View overlayView;
    private View crosshairView;
    private View settingsView;
    private boolean autoTapRunning = false;
    private boolean autoSwipeRunning = false;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private boolean autoDragSyncActive = false;

    private Thread autoTapThread;
    private Thread autoSwipeThread;
    private Thread playThread;

    private int tapX = 500, tapY = 500;
    private int crossX = 500, crossY = 500;
    private long recordStartTime = 0;

    private final List<MacroEvent> recordedEvents = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    static class MacroEvent {
        long time;
        int x, y, x2, y2;
        boolean isSwipe;
        MacroEvent(long t, int x, int y) { this.time=t; this.x=x; this.y=y; this.isSwipe=false; }
        MacroEvent(long t, int x, int y, int x2, int y2) {
            this.time=t; this.x=x; this.y=y; this.x2=x2; this.y2=y2; this.isSwipe=true;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showOverlay();
        Toast.makeText(this, "Z TOUCH V2 aktif", Toast.LENGTH_SHORT).show();
    }

    private void showOverlay() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        Button btnTap = overlayView.findViewById(R.id.btn_tap);
        Button btnSwipe = overlayView.findViewById(R.id.btn_swipe);
        Button btnDragSync = overlayView.findViewById(R.id.btn_dragsync);
        Button btnCross = overlayView.findViewById(R.id.btn_cross);
        Button btnRec = overlayView.findViewById(R.id.btn_rec);
        Button btnPlay = overlayView.findViewById(R.id.btn_play);
        Button btnSet = overlayView.findViewById(R.id.btn_set);
        Button btnClose = overlayView.findViewById(R.id.btn_close);

        btnTap.setOnClickListener(v -> {
            if (autoTapRunning) { stopAutoTap(); btnTap.setText("TAP"); }
            else { startAutoTap(); btnTap.setText("STOP"); }
        });

        btnSwipe.setOnClickListener(v -> {
            if (autoSwipeRunning) { stopAutoSwipe(); btnSwipe.setText("SWIPE"); }
            else { startAutoSwipe(); btnSwipe.setText("STOP"); }
        });

        btnDragSync.setOnClickListener(v -> {
            if (autoDragSyncActive) {
                autoDragSyncActive = false;
                btnDragSync.setText("DRAG");
                Toast.makeText(this, "Drag Sync OFF", Toast.LENGTH_SHORT).show();
            } else {
                autoDragSyncActive = true;
                btnDragSync.setText("SYNC ON");
                Toast.makeText(this, "Drag Sync ON - drag di layar", Toast.LENGTH_SHORT).show();
            }
        });

        btnRec.setOnClickListener(v -> {
            if (isRecording) { stopRecording(); btnRec.setText("REC"); }
            else { startRecording(); btnRec.setText("STOP"); }
        });

        btnPlay.setOnClickListener(v -> {
            if (isPlaying) { stopPlaying(); btnPlay.setText("PLAY"); }
            else {
                if (recordedEvents.isEmpty()) {
                    Toast.makeText(this, "Belum ada macro", Toast.LENGTH_SHORT).show();
                    return;
                }
                startPlaying();
                btnPlay.setText("STOP");
            }
        });

        btnCross.setOnClickListener(v -> showCrosshair());
        btnSet.setOnClickListener(v -> showSettings());
        btnClose.setOnClickListener(v -> {
            stopAll();
            if (overlayView != null) windowManager.removeView(overlayView);
            if (crosshairView != null) windowManager.removeView(crosshairView);
            if (settingsView != null) windowManager.removeView(settingsView);
            overlayView = null; crosshairView = null; settingsView = null;
        });

        enableDrag(overlayView, params);
        windowManager.addView(overlayView, params);
    }

    private void enableDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float tX, tY;
            boolean dragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        tX = event.getRawX(); tY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(event.getRawX() - tX);
                        int dy = (int)(event.getRawY() - tY);
                        if (Math.abs(dx) > 15 || Math.abs(dy) > 15) dragging = true;
                        params.x = initX + dx;
                        params.y = initY + dy;
                        windowManager.updateViewLayout(view, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) view.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void showCrosshair() {
        if (crosshairView != null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        crosshairView = LayoutInflater.from(this).inflate(R.layout.crosshair_layout, null);

        crosshairView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                crossX = (int) event.getRawX();
                crossY = (int) event.getRawY();
                tapX = crossX; tapY = crossY;
                Toast.makeText(this, "Titik: " + crossX + ", " + crossY, Toast.LENGTH_SHORT).show();
                windowManager.removeView(crosshairView);
                crosshairView = null;
            }
            return true;
        });

        windowManager.addView(crosshairView, params);
    }

    private void startAutoTap() {
        autoTapRunning = true;
        autoTapThread = new Thread(() -> {
            while (autoTapRunning) {
                performTap(tapX, tapY, tapDuration);
                try { Thread.sleep(tapDelay); } catch (InterruptedException e) { break; }
            }
        });
        autoTapThread.start();
    }
    private void stopAutoTap() {
        autoTapRunning = false;
        if (autoTapThread != null) { autoTapThread.interrupt(); autoTapThread = null; }
    }

    private void startAutoSwipe() {
        autoSwipeRunning = true;
        autoSwipeThread = new Thread(() -> {
            while (autoSwipeRunning) {
                performSwipe(tapX, tapY, tapX, tapY + swipeDistance, swipeDuration);
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
        });
        autoSwipeThread.start();
    }
    private void stopAutoSwipe() {
        autoSwipeRunning = false;
        if (autoSwipeThread != null) { autoSwipeThread.interrupt(); autoSwipeThread = null; }
    }

    private void startRecording() {
        recordedEvents.clear();
        recordStartTime = System.currentTimeMillis();
        isRecording = true;
        Toast.makeText(this, "Rekam mulai - tap layar", Toast.LENGTH_SHORT).show();
    }
    private void stopRecording() {
        isRecording = false;
        Toast.makeText(this, "Rekaman: " + recordedEvents.size() + " event", Toast.LENGTH_SHORT).show();
    }

    private void startPlaying() {
        isPlaying = true;
        playThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            for (MacroEvent ev : recordedEvents) {
                if (!isPlaying) break;
                long wait = ev.time - (System.currentTimeMillis() - start);
                if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException e) { break; } }
                if (ev.isSwipe) performSwipe(ev.x, ev.y, ev.x2, ev.y2, swipeDuration);
                else performTap(ev.x, ev.y, tapDuration);
            }
            isPlaying = false;
            uiHandler.post(() -> {
                if (overlayView != null) {
                    Button b = overlayView.findViewById(R.id.btn_play);
                    if (b != null) b.setText("PLAY");
                }
            });
        });
        playThread.start();
    }
    private void stopPlaying() {
        isPlaying = false;
        if (playThread != null) { playThread.interrupt(); playThread = null; }
    }

    private void showSettings() {
        if (settingsView != null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        settingsView = LayoutInflater.from(this).inflate(R.layout.settings_layout, null);

        SeekBar sbSensX = settingsView.findViewById(R.id.sb_sens_x);
        SeekBar sbSensY = settingsView.findViewById(R.id.sb_sens_y);
        SeekBar sbDelay = settingsView.findViewById(R.id.sb_delay);
        SeekBar sbSwipe = settingsView.findViewById(R.id.sb_swipe);
        TextView tvInfo = settingsView.findViewById(R.id.tv_set_info);
        Button btnApply = settingsView.findViewById(R.id.btn_apply_set);

        sbSensX.setProgress((int)(sensX * 50));
        sbSensY.setProgress((int)(sensY * 50));
        sbDelay.setProgress(tapDelay);
        sbSwipe.setProgress(swipeDistance / 10);

        Runnable update = () -> tvInfo.setText("Sens X: " + String.format("%.1f", sensX) +
                "  |  Sens Y: " + String.format("%.1f", sensY) +
                "\nDelay: " + tapDelay + "ms" +
                "  |  Swipe: " + swipeDistance + "px");
        update.run();

        sbSensX.setOnSeekBarChangeListener(new SimpleSeek(c -> { sensX = c / 50f; update.run(); }));
        sbSensY.setOnSeekBarChangeListener(new SimpleSeek(c -> { sensY = c / 50f; update.run(); }));
        sbDelay.setOnSeekBarChangeListener(new SimpleSeek(c -> { tapDelay = Math.max(20, c); update.run(); }));
        sbSwipe.setOnSeekBarChangeListener(new SimpleSeek(c -> { swipeDistance = Math.max(100, c * 10); update.run(); }));

        btnApply.setOnClickListener(v -> {
            windowManager.removeView(settingsView);
            settingsView = null;
            Toast.makeText(this, "Setting tersimpan", Toast.LENGTH_SHORT).show();
        });

        windowManager.addView(settingsView, params);
    }

    static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        interface Cb { void on(int p); }
        final Cb cb;
        SimpleSeek(Cb cb) { this.cb = cb; }
        public void onProgressChanged(SeekBar sb, int p, boolean u) { cb.on(p); }
        public void onStartTrackingTouch(SeekBar sb) {}
        public void onStopTrackingTouch(SeekBar sb) {}
    }

    private void performTap(int x, int y, int duration) {
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(path, 0, duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void performSwipe(int x1, int y1, int x2, int y2, int duration) {
        Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2);
        GestureDescription.StrokeDescription s = new GestureDescription.StrokeDescription(path, 0, duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(s).build(), null, null);
    }

    private void stopAll() {
        stopAutoTap(); stopAutoSwipe(); stopRecording(); stopPlaying();
        autoDragSyncActive = false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isRecording && event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            long t = System.currentTimeMillis() - recordStartTime;
            recordedEvents.add(new MacroEvent(t, tapX, tapY));
        }
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        stopAll();
        if (overlayView != null) windowManager.removeView(overlayView);
        if (crosshairView != null) windowManager.removeView(crosshairView);
        if (settingsView != null) windowManager.removeView(settingsView);
        super.onDestroy();
    }
}
