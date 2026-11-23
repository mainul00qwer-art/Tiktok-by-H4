package com.hridoy.tiktokautobot;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private TextView statusBadge;
    private Button enableServiceBtn;
    private Button openTikTokBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#050B14"));
        root.setPadding(dp(20), dp(36), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Main Cyber Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(24));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0A1628"));
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(2), Color.parseColor("#00F2EA"));
        card.setBackground(cardBg);

        TextView title = new TextView(this);
        title.setText("🎵 AUTO BOT BY HRIDOY");
        title.setTextColor(Color.parseColor("#00F2EA"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("⚡ NATIVE TIKTOK APP FLOATING AUTOMATOR");
        subtitle.setTextColor(Color.parseColor("#00E676"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        card.addView(subtitle);

        statusBadge = new TextView(this);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(statusBadge);

        TextView instructions = new TextView(this);
        instructions.setText(
                "\nHOW TO USE ON TIKTOK APP:\n\n" +
                "1. Tap 'Enable Floating Window Service' below and turn ON 'Auto Bot By Hridoy' in Accessibility settings.\n\n" +
                "2. Tap 'Open TikTok App' — the floating 🎵 Auto Bot window will stay right on top of your TikTok app!\n\n" +
                "3. Go to any TikTok video, tap '1. Mark Steps' on the floating window, and tap:\n" +
                "   Share / 3-Dot ➔ Report ➔ Reason ➔ Submit.\n\n" +
                "4. Tap 'Stop Marking', choose Speed & Repeat count, and tap '2. Run Fast Automation'!"
        );
        instructions.setTextColor(Color.parseColor("#BAE6FD"));
        instructions.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        instructions.setPadding(0, dp(8), 0, dp(18));
        card.addView(instructions);

        enableServiceBtn = createCyberButton("1. ENABLE FLOATING WINDOW SERVICE", "#2979FF", "#00F2EA");
        enableServiceBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Turn ON 'Auto Bot By Hridoy' in Accessibility", Toast.LENGTH_LONG).show();
        });
        card.addView(enableServiceBtn);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(12)));
        card.addView(spacer);

        openTikTokBtn = createCyberButton("2. OPEN TIKTOK APP WITH FLOATING BOT", "#00C853", "#00F2EA");
        openTikTokBtn.setOnClickListener(v -> launchTikTokApp());
        card.addView(openTikTokBtn);

        root.addView(card);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatusUI();
    }

    private void updateServiceStatusUI() {
        boolean enabled = isAccessibilityServiceEnabled();
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(8));

        if (enabled) {
            badgeBg.setColor(Color.parseColor("#062E22"));
            badgeBg.setStroke(dp(1), Color.parseColor("#00E676"));
            statusBadge.setText("🟢 FLOATING WINDOW ACTIVE & READY");
            statusBadge.setTextColor(Color.parseColor("#00E676"));
            enableServiceBtn.setText("✅ FLOATING SERVICE ENABLED (TAP TO MANAGE)");
        } else {
            badgeBg.setColor(Color.parseColor("#2A1215"));
            badgeBg.setStroke(dp(1), Color.parseColor("#FF5252"));
            statusBadge.setText("🔴 FLOATING SERVICE OFF — TAP BUTTON 1 BELOW");
            statusBadge.setTextColor(Color.parseColor("#FF5252"));
            enableServiceBtn.setText("1. ENABLE FLOATING WINDOW SERVICE");
        }
        statusBadge.setBackground(badgeBg);
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> list =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId() != null && info.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void launchTikTokApp() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable 'Auto Bot By Hridoy' in Accessibility first!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        PackageManager pm = getPackageManager();
        String[] tiktokPackages = {
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically.go"
        };

        for (String pkg : tiktokPackages) {
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                return;
            }
        }

        Toast.makeText(this, "Floating Window is active on screen! Open your TikTok app now.", Toast.LENGTH_LONG).show();
    }

    private Button createCyberButton(String text, String bgHex, String strokeHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), Color.parseColor(strokeHex));
        btn.setBackground(gd);
        return btn;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
