package org.pyapp.javaonly;

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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App Android សរសេរដោយ Java — កែកូដនេះបានសេរី។
 * package name បញ្ចូលដោយស្វ័យប្រវត្តិពីប្រអប់ "Package name" ខាងលើ។
 */
public class MainActivity extends Activity {

    // ដាក់ URL របស់ API នៅទីនេះ (បើមិនប្រើ ទុកទទេ "")
    private static final String API_URL = "https://pyapp-builder.onrender.com/api/health";

    private EditText input;
    private TextView output;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("សួស្តី! នេះជា app Java");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        heading.setTextColor(Color.parseColor("#0f172a"));
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(heading);

        TextView about = new TextView(this);
        about.setText("សរសេរកូដ Java ក្នុងប្រអប់ខាងលើ រួចចុច Build APK។");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        about.setTextColor(Color.parseColor("#475569"));
        about.setPadding(0, dp(10), 0, 0);
        root.addView(about);

        input = new EditText(this);
        input.setHint("សរសេរឈ្មោះរបស់អ្នក");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(input, topMargin(dp(16)));

        Button greet = new Button(this);
        greet.setText("ស្វាគមន៍");
        greet.setAllCaps(false);
        greet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    name = "មិត្តភក្តិ";
                }
                output.setText("សួស្តី " + name + "! 👋");
            }
        });
        root.addView(greet, topMargin(dp(16)));

        Button api = new Button(this);
        api.setText("ហៅ API");
        api.setAllCaps(false);
        api.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callApi();
            }
        });
        root.addView(api, topMargin(dp(16)));

        Button open = new Button(this);
        open.setText("បើកគេហទំព័រ");
        open.setAllCaps(false);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            }
        });
        root.addView(open, topMargin(dp(16)));

        output = new TextView(this);
        output.setText("លទ្ធផលនឹងបង្ហាញនៅទីនេះ។");
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        output.setTextColor(Color.parseColor("#334155"));
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(10), dp(10), dp(10), dp(10));
        output.setBackgroundColor(Color.parseColor("#f1f5f9"));
        output.setMovementMethod(new ScrollingMovementMethod());
        root.addView(output, topMargin(dp(16)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams topMargin(int pixels) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pixels;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void callApi() {
        if (API_URL.isEmpty()) {
            output.setText("API URL មិនបានកំណត់");
            return;
        }
        output.setText("កំពុងភ្ជាប់ " + API_URL + " …");
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
                    result = "HTTP " + code + "\n" + sb.toString().trim();
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
                        output.setText(out);
                    }
                });
            }
        }).start();
    }
}
