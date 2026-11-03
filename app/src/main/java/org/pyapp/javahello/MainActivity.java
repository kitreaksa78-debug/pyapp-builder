package org.pyapp.javahello;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * បង្កើតដោយ PyApp Builder — app Android native សរសេរដោយ Java។
 * UI សង់ដោយកូដ (គ្មាន XML layout) ដូច្នេះ build មិនស្ទះដោយរឿង resource។
 */
public class MainActivity extends Activity {

    private static final String API_URL = "https://pyapp-builder.onrender.com/api/health";

    private TextView apiOut;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("\u179f\u17bd\u179f\u17d2\u178f\u17b8");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        heading.setTextColor(Color.parseColor("#0f172a"));
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(heading);

        TextView message = new TextView(this);
        message.setText("hello");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setTextColor(Color.parseColor("#334155"));
        message.setPadding(0, dp(12), 0, 0);
        root.addView(message);

        Button openButton = new Button(this);
        openButton.setText("\u1794\u17be\u1780");
        openButton.setAllCaps(false);
        openButton.setVisibility(View.VISIBLE);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            }
        });
        root.addView(openButton, buttonLayout());

        Button apiButton = new Button(this);
        apiButton.setText("ហៅ API");
        apiButton.setAllCaps(false);
        apiButton.setVisibility(View.VISIBLE);
        apiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callApi();
            }
        });
        root.addView(apiButton, buttonLayout());

        apiOut = new TextView(this);
        apiOut.setText("\u1785\u17bb\u1785 \u00ab\u17a0\u17c5 API\u00bb \u178a\u17be\u1798\u17d2\u1794\u17b8\u1791\u17b6\u1789\u1791\u17b7\u1793\u17d2\u1793\u1793\u17d0\u1799\u1796\u17b8 https://pyapp-builder.onrender.com/api/health");
        apiOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        apiOut.setTextColor(Color.parseColor("#334155"));
        apiOut.setTypeface(Typeface.MONOSPACE);
        apiOut.setPadding(dp(10), dp(10), dp(10), dp(10));
        apiOut.setBackgroundColor(Color.parseColor("#f1f5f9"));
        apiOut.setMovementMethod(new ScrollingMovementMethod());
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        outLp.topMargin = dp(14);
        root.addView(apiOut, outLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void callApi() {
        if (API_URL.isEmpty()) {
            apiOut.setText("API URL មិនបានកំណត់");
            return;
        }
        apiOut.setText("កំពុងភ្ជាប់ " + API_URL + " …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(API_URL).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Accept", "application/json");
                    int code = conn.getResponseCode();
                    InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        reader.close();
                    }
                    result = "HTTP " + code + "  (" + API_URL + ")\n" + sb.toString().trim();
                } catch (Exception e) {
                    result = "មិនអាចភ្ជាប់ API បាន: " + e;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                if (result.length() > 4000) {
                    result = result.substring(0, 4000) + " …";
                }
                final String out = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        apiOut.setText(out);
                    }
                });
            }
        }).start();
    }
}
