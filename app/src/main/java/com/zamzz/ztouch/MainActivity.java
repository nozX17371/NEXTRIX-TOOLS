package com.zamzzz.ztouch;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0A0F0A"));
        root.setPadding(50, 100, 50, 50);

        TextView title = new TextView(this);
        title.setText("Z TOUCH");
        title.setTextSize(38);
        title.setTextColor(Color.parseColor("#00FF41"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("FULL MATRIX EDITION");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#00AA2A"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 60);
        root.addView(sub);

        Button btnAcc = new Button(this);
        btnAcc.setText(">> AKTIFKAN AKSESIBILITAS");
        btnAcc.setTextColor(Color.parseColor("#00FF41"));
        btnAcc.setBackgroundColor(Color.parseColor("#0F1F0F"));
        btnAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(btnAcc);

        Button btnOverlay = new Button(this);
        btnOverlay.setText(">> IZINKAN OVERLAY");
        btnOverlay.setTextColor(Color.parseColor("#00FF41"));
        btnOverlay.setBackgroundColor(Color.parseColor("#0F1F0F"));
        btnOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
                } else {
                    Toast.makeText(this, "Overlay OK", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnOverlay);

        Button btnStart = new Button(this);
        btnStart.setText(">> JALANKAN Z TOUCH");
        btnStart.setTextColor(Color.parseColor("#00FF41"));
        btnStart.setBackgroundColor(Color.parseColor("#0F1F0F"));
        btnStart.setOnClickListener(v -> {
            Toast.makeText(this, "Z TOUCH AKTIF. Buka game lo.", Toast.LENGTH_LONG).show();
            moveTaskToBack(true);
        });
        root.addView(btnStart);

        TextView info = new TextView(this);
        info.setText("\n\nFITUR:\n" +
                "- Auto Tap\n" +
                "- Auto Swipe\n" +
                "- Drag Sync\n" +
                "- Sensitivity X-Y\n" +
                "- Macro Rekam & Putar\n" +
                "- Crosshair Presisi\n" +
                "- Monitor FPS/CPU/RAM");
        info.setTextSize(12);
        info.setTextColor(Color.parseColor("#66FF88"));
        root.addView(info);

        setContentView(root);
    }
  }
