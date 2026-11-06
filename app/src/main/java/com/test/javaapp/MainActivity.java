package com.test.javaapp;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        final TextView title = new TextView(this);
        title.setText("សួស្តី Java 👋");
        title.setTextSize(24f);
        title.setTextColor(Color.WHITE);
        Button b = new Button(this);
        b.setText("ចុចទីនេះ");
        b.setOnClickListener(v -> title.setText("បានចុច 🎉"));
        root.addView(title);
        root.addView(b);
        setContentView(root);
    }
}
