package com.hridoy.tiktokautobot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating Window & Native Gesture Automation Service for the Official TikTok Android App.
 * Displays a draggable, minimizable Cyber Gaming Floating Window on top of TikTok.
 */
public class AutoBotAccessibilityService extends AccessibilityService {

    private static class RecordedStep {
        float x;
        float y;
        String label;

        RecordedStep(float x, float y, String label) {
            this.x = x;
            this.y = y;
            this.label = label;
        }
    }

    private WindowManager windowManager;
    private FrameLayout floatingRoot;
    private LinearLayout expandedPanel;
    private TextView minimizedFab;
    private FrameLayout touchCaptureOverlay;
    private WindowManager.LayoutParams floatingParams;
    private WindowManager.LayoutParams captureParams;

    private TextView stepCountBadge;
    private Button markBtn;
    private Button runBtn;
    private Button speedBtn;
    private Button repeatMinusBtn;
    private TextView repeatValueText;
    private Button repeatPlusBtn;
    private LinearLayout stepsListContainer;
    private TextView statusText;

    private final List<RecordedStep> steps = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isMarking = false;
    private boolean isRunning = false;
    private boolean isMinimized = false;

    private final int[] speedOptionsMs = {600, 1000, 1500, 2500, 4000};
    private final String[] speedLabels = {
            "0.6s (Normal)",
            "1.0s (Safe)",
            "1.5s (Slow Safe)",
            "2.5s (Ultra Safe)",
            "4.0s (Anti-Error)"
    };
    private int speedIndex = 1; // Default 1.0s (Safe)
    private int repeatCount = 10;
    private int currentCycle = 1;
    private int currentStepIdx = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadSavedSteps();
        createTouchCaptureOverlay();
        createFloatingWindow();
        renderStepsUI();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Auto-dismiss any native "Network error" or retry dialog if needed
    }

    @Override
    public void onInterrupt() {
        stopAutomation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutomation();
        if (windowManager != null) {
            if (floatingRoot != null) {
                try { windowManager.removeView(floatingRoot); } catch (Exception ignored) {}
            }
            if (touchCaptureOverlay != null) {
                try { windowManager.removeView(touchCaptureOverlay); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Full-screen transparent overlay shown ONLY while "1. Mark Steps" is active.
     * Captures exact (X, Y) coordinates + underlying TikTok button name, then forwards the tap!
     */
    private void createTouchCaptureOverlay() {
        touchCaptureOverlay = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.argb(18, 255, 193, 7));
        border.setStroke(dp(2), Color.parseColor("#FBBF24"));
        touchCaptureOverlay.setBackground(border);

        TextView hintBanner = new TextView(this);
        hintBanner.setText("🎯 MARK MODE: TAP TIKTOK BUTTONS (3-Dot ➔ Report ➔ Reason ➔ Submit)");
        hintBanner.setTextColor(Color.BLACK);
        hintBanner.setBackgroundColor(Color.parseColor("#FBBF24"));
        hintBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hintBanner.setTypeface(Typeface.DEFAULT_BOLD);
        hintBanner.setGravity(Gravity.CENTER);
        hintBanner.setPadding(dp(8), dp(6), dp(8), dp(6));
        FrameLayout.LayoutParams bannerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        touchCaptureOverlay.addView(hintBanner, bannerLp);

        touchCaptureOverlay.setOnTouchListener((v, event) -> {
            if (!isMarking) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                final float rawX = event.getRawX();
                final float rawY = event.getRawY();

                String nodeName = findNodeLabelAtPoint((int) rawX, (int) rawY);
                String stepLabel = (nodeName != null && !nodeName.isEmpty())
                        ? nodeName
                        : ("Tap (" + (int) rawX + ", " + (int) rawY + ")");

                steps.add(new RecordedStep(rawX, rawY, stepLabel));
                saveSteps();
                renderStepsUI();
                setStatus("Recorded #" + steps.size() + ": " + stepLabel, "#FBBF24");

                // Temporarily hide capture overlay, forward real tap to TikTok app, then restore capture overlay!
                setTouchCaptureVisible(false);
                mainHandler.postDelayed(() -> {
                    performHardwareTap(rawX, rawY, 55, () -> {
                        if (isMarking) {
                            mainHandler.postDelayed(() -> setTouchCaptureVisible(true), 250);
                        }
                    });
                }, 70);
                return true;
            }
            return false;
        });

        captureParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
    }

    private void setTouchCaptureVisible(boolean visible) {
        if (windowManager == null || touchCaptureOverlay == null) return;
        try {
            if (visible && touchCaptureOverlay.getParent() == null) {
                windowManager.addView(touchCaptureOverlay, captureParams);
                // Re-attach floating control panel above the capture overlay
                if (floatingRoot != null && floatingRoot.getParent() != null) {
                    windowManager.removeView(floatingRoot);
                    windowManager.addView(floatingRoot, floatingParams);
                }
            } else if (!visible && touchCaptureOverlay.getParent() != null) {
                windowManager.removeView(touchCaptureOverlay);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Creates the Draggable & Minimizable Cyber Gaming Floating Window on top of TikTok.
     */
    private void createFloatingWindow() {
        floatingRoot = new FrameLayout(this);

        // 1. Minimized Floating Icon (🎵)
        minimizedFab = new TextView(this);
        minimizedFab.setText("🎵");
        minimizedFab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        minimizedFab.setGravity(Gravity.CENTER);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(Color.parseColor("#0A1628"));
        fabBg.setStroke(dp(2), Color.parseColor("#00F2EA"));
        minimizedFab.setBackground(fabBg);
        minimizedFab.setVisibility(View.GONE);
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        floatingRoot.addView(minimizedFab, fabLp);

        // 2. Expanded Cyber Floating Panel
        expandedPanel = new LinearLayout(this);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#EB050B14"));
        panelBg.setCornerRadius(dp(12));
        panelBg.setStroke(dp(2), Color.parseColor("#00F2EA"));
        expandedPanel.setBackground(panelBg);

        // Header Row (Draggable)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(6));

        TextView title = new TextView(this);
        title.setText("🎵 Auto Bot By Hridoy");
        title.setTextColor(Color.parseColor("#00F2EA"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleLp);

        stepCountBadge = new TextView(this);
        stepCountBadge.setText("0 STEPS");
        stepCountBadge.setTextColor(Color.parseColor("#00E676"));
        stepCountBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        stepCountBadge.setTypeface(Typeface.DEFAULT_BOLD);
        stepCountBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        header.addView(stepCountBadge);

        Button minBtn = createMiniButton(" _ ", "#1E293B");
        minBtn.setOnClickListener(v -> toggleMinimize(true));
        header.addView(minBtn);

        expandedPanel.addView(header);

        // Main Action Buttons: 1. Mark Steps & 2. Run Fast Automation
        markBtn = createActionButton("1. MARK STEPS", "#2979FF");
        markBtn.setOnClickListener(v -> toggleMarkingMode());
        expandedPanel.addView(markBtn);

        View sp1 = new View(this);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(1, dp(5)));
        expandedPanel.addView(sp1);

        runBtn = createActionButton("2. RUN FAST AUTOMATION", "#00C853");
        runBtn.setOnClickListener(v -> {
            if (isRunning) stopAutomation();
            else startAutomation();
        });
        expandedPanel.addView(runBtn);

        View sp2 = new View(this);
        sp2.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        expandedPanel.addView(sp2);

        // Speed Selector Button
        speedBtn = createActionButton("SPEED: " + speedLabels[speedIndex], "#0F172A");
        speedBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        speedBtn.setOnClickListener(v -> {
            speedIndex = (speedIndex + 1) % speedOptionsMs.length;
            speedBtn.setText("SPEED: " + speedLabels[speedIndex]);
            setStatus("Speed: " + speedLabels[speedIndex], "#00F2EA");
        });
        expandedPanel.addView(speedBtn);

        View sp3 = new View(this);
        sp3.setLayoutParams(new LinearLayout.LayoutParams(1, dp(6)));
        expandedPanel.addView(sp3);

        // Repeat Row + Clear Steps
        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setOrientation(LinearLayout.HORIZONTAL);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);

        repeatMinusBtn = createMiniButton(" - ", "#1E293B");
        repeatMinusBtn.setOnClickListener(v -> {
            if (repeatCount > 5) repeatCount -= 5;
            else if (repeatCount > 1) repeatCount--;
            repeatValueText.setText("REPEAT: " + repeatCount + "x");
        });
        repeatRow.addView(repeatMinusBtn);

        repeatValueText = new TextView(this);
        repeatValueText.setText("REPEAT: " + repeatCount + "x");
        repeatValueText.setTextColor(Color.WHITE);
        repeatValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        repeatValueText.setTypeface(Typeface.DEFAULT_BOLD);
        repeatValueText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams repLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        repeatRow.addView(repeatValueText, repLp);

        repeatPlusBtn = createMiniButton(" +5 ", "#1E293B");
        repeatPlusBtn.setOnClickListener(v -> {
            repeatCount += 5;
            repeatValueText.setText("REPEAT: " + repeatCount + "x");
        });
        repeatRow.addView(repeatPlusBtn);

        Button clearBtn = createMiniButton("CLEAR", "#7F1D1D");
        clearBtn.setOnClickListener(v -> {
            stopAutomation();
            if (isMarking) toggleMarkingMode();
            steps.clear();
            saveSteps();
            renderStepsUI();
            setStatus("Cleared all steps.", "#00F2EA");
        });
        repeatRow.addView(clearBtn);

        expandedPanel.addView(repeatRow);

        // Compact Scrollable Steps List
        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(76)
        );
        scrollLp.topMargin = dp(6);
        stepsListContainer = new LinearLayout(this);
        stepsListContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(stepsListContainer);
        expandedPanel.addView(scroll, scrollLp);

        // Status Bar
        statusText = new TextView(this);
        statusText.setText("READY • OPEN TIKTOK VIDEO");
        statusText.setTextColor(Color.parseColor("#00F2EA"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(5), 0, 0);
        expandedPanel.addView(statusText);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(235), FrameLayout.LayoutParams.WRAP_CONTENT);
        floatingRoot.addView(expandedPanel, panelLp);

        floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = dp(12);
        floatingParams.y = dp(130);

        enableDragOnView(header);
        enableDragAndTapOnFab(minimizedFab);

        windowManager.addView(floatingRoot, floatingParams);
    }

    private void toggleMinimize(boolean minimize) {
        isMinimized = minimize;
        if (isMinimized) {
            expandedPanel.setVisibility(View.GONE);
            minimizedFab.setVisibility(View.VISIBLE);
        } else {
            minimizedFab.setVisibility(View.GONE);
            expandedPanel.setVisibility(View.VISIBLE);
        }
    }

    private void toggleMarkingMode() {
        if (isRunning) stopAutomation();
        isMarking = !isMarking;
        if (isMarking) {
            markBtn.setText("⏹ STOP MARKING");
            setButtonColor(markBtn, "#D97706");
            setTouchCaptureVisible(true);
            setStatus("Tap TikTok buttons to record...", "#FBBF24");
        } else {
            markBtn.setText("1. MARK STEPS");
            setButtonColor(markBtn, "#2979FF");
            setTouchCaptureVisible(false);
            setStatus("Ready (" + steps.size() + " steps saved)", "#00E676");
        }
    }

    private void startAutomation() {
        if (steps.isEmpty()) {
            setStatus("Record steps first!", "#FBBF24");
            return;
        }
        if (isMarking) toggleMarkingMode();

        isRunning = true;
        currentCycle = 1;
        currentStepIdx = 0;
        runBtn.setText("⏹ STOP AUTOMATION");
        setButtonColor(runBtn, "#E11D48");
        executeNextAutomationStep();
    }

    private void stopAutomation() {
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (runBtn != null) {
            runBtn.setText("2. RUN FAST AUTOMATION");
            setButtonColor(runBtn, "#00C853");
        }
    }

    private void executeNextAutomationStep() {
        if (!isRunning) return;

        if (currentStepIdx >= steps.size()) {
            if (currentCycle >= repeatCount) {
                setStatus("✅ Completed " + repeatCount + "x cycles!", "#00E676");
                stopAutomation();
                return;
            }
            currentCycle++;
            currentStepIdx = 0;
            int cyclePause = Math.max(speedOptionsMs[speedIndex] + 400, 1300);
            mainHandler.postDelayed(this::executeNextAutomationStep, cyclePause);
            return;
        }

        RecordedStep step = steps.get(currentStepIdx);
        setStatus("[" + currentCycle + "/" + repeatCount + "] Step " + (currentStepIdx + 1) + ": " + step.label, "#00F2EA");

        // Check if a matching UI node by text moved slightly on screen; otherwise use recorded (X, Y)
        float targetX = step.x;
        float targetY = step.y;
        Rect foundBounds = findNodeBoundsByLabel(step.label);
        if (foundBounds != null && foundBounds.width() > 0 && foundBounds.height() > 0) {
            targetX = foundBounds.centerX();
            targetY = foundBounds.centerY();
        }

        performHardwareTap(targetX, targetY, 55, () -> {
            if (!isRunning) return;
            currentStepIdx++;
            int delay = speedOptionsMs[speedIndex];
            if (step.label != null && step.label.toLowerCase().contains("submit")) {
                delay = Math.max(delay, 900);
            }
            mainHandler.postDelayed(this::executeNextAutomationStep, delay);
        });
    }

    /**
     * Dispatches a native Android hardware touch gesture at (x, y) inside the TikTok App.
     */
    private void performHardwareTap(float x, float y, long durationMs, Runnable onComplete) {
        Path tapPath = new Path();
        tapPath.moveTo(Math.max(1f, x), Math.max(1f, y));

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(tapPath, 0, durationMs);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                if (onComplete != null) mainHandler.post(onComplete);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                if (onComplete != null) mainHandler.post(onComplete);
            }
        }, null);
    }

    /**
     * Reads the TikTok Android App's AccessibilityNodeInfo tree at (x, y) to get the button name.
     */
    private String findNodeLabelAtPoint(int x, int y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        AccessibilityNodeInfo best = findSmallestNodeAt(root, x, y);
        if (best != null) {
            if (best.getText() != null && best.getText().length() > 0) {
                return best.getText().toString().trim().split("\n")[0];
            }
            if (best.getContentDescription() != null && best.getContentDescription().length() > 0) {
                return best.getContentDescription().toString().trim().split("\n")[0];
            }
        }
        return "";
    }

    private AccessibilityNodeInfo findSmallestNodeAt(AccessibilityNodeInfo node, int x, int y) {
        if (node == null || !node.isVisibleToUser()) return null;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) return null;

        AccessibilityNodeInfo bestChild = null;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo candidate = findSmallestNodeAt(child, x, y);
            if (candidate != null) {
                bestChild = candidate;
            }
        }
        return bestChild != null ? bestChild : node;
    }

    private Rect findNodeBoundsByLabel(String label) {
        if (label == null || label.isEmpty() || label.startsWith("Tap (")) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> matched = root.findAccessibilityNodeInfosByText(label);
        if (matched != null) {
            for (AccessibilityNodeInfo info : matched) {
                if (info.isVisibleToUser()) {
                    Rect r = new Rect();
                    info.getBoundsInScreen(r);
                    if (r.width() > 0 && r.height() > 0 && r.height() < 350) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    private void renderStepsUI() {
        if (stepCountBadge == null || stepsListContainer == null) return;
        stepCountBadge.setText(steps.size() + (steps.size() == 1 ? " STEP" : " STEPS"));
        stepsListContainer.removeAllViews();

        if (steps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Tap '1. MARK STEPS' then tap buttons on TikTok.");
            empty.setTextColor(Color.parseColor("#7DD3FC"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(4), dp(14), dp(4), dp(10));
            stepsListContainer.addView(empty);
            return;
        }

        for (int i = 0; i < steps.size(); i++) {
            RecordedStep s = steps.get(i);
            TextView row = new TextView(this);
            row.setText((i + 1) + ". " + s.label + "  [" + (int) s.x + "," + (int) s.y + "]");
            row.setTextColor(Color.WHITE);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            row.setPadding(dp(4), dp(2), dp(4), dp(2));
            stepsListContainer.addView(row);
        }
    }

    private void setStatus(String msg, String colorHex) {
        if (statusText != null) {
            statusText.setText(msg);
            statusText.setTextColor(Color.parseColor(colorHex));
        }
    }

    private void saveSteps() {
        try {
            JSONArray arr = new JSONArray();
            for (RecordedStep s : steps) {
                JSONObject obj = new JSONObject();
                obj.put("x", s.x);
                obj.put("y", s.y);
                obj.put("label", s.label);
                arr.put(obj);
            }
            SharedPreferences prefs = getSharedPreferences("hridoy_tiktok_bot", MODE_PRIVATE);
            prefs.edit().putString("saved_steps", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadSavedSteps() {
        steps.clear();
        try {
            SharedPreferences prefs = getSharedPreferences("hridoy_tiktok_bot", MODE_PRIVATE);
            String raw = prefs.getString("saved_steps", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                steps.add(new RecordedStep(
                        (float) o.getDouble("x"),
                        (float) o.getDouble("y"),
                        o.optString("label", "Step " + (i + 1))
                ));
            }
        } catch (Exception ignored) {}
    }

    private void enableDragOnView(View dragHandle) {
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        floatingParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        floatingParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingRoot, floatingParams);
                        return true;
                }
                return false;
            }
        });
    }

    private void enableDragAndTapOnFab(View fab) {
        fab.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        floatingParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        floatingParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingRoot, floatingParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY) < dp(10)) {
                            toggleMinimize(false);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private Button createActionButton(String text, String colorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(8), dp(7), dp(8), dp(7));
        setButtonColor(btn, colorHex);
        return btn;
    }

    private Button createMiniButton(String text, String colorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setMinimumWidth(0);
        btn.setMinWidth(0);
        btn.setMinimumHeight(0);
        btn.setMinHeight(0);
        btn.setPadding(dp(8), dp(4), dp(8), dp(4));
        setButtonColor(btn, colorHex);
        return btn;
    }

    private void setButtonColor(Button btn, String colorHex) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(colorHex));
        gd.setCornerRadius(dp(6));
        gd.setStroke(dp(1), Color.parseColor("#00F2EA"));
        btn.setBackground(gd);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
