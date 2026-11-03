package org.pyapp.tgbot;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Telegram bot រត់ក្នុងទូរស័ព្ទ — ឆ្លើយតបសារដោយស្វ័យប្រវត្តិ។
 *
 * - ដាក់ token ពី @BotFather រួចចុច Start
 * - ចេញពី app ⇒ bot ផ្អាកស្វ័យប្រវត្តិ (សន្សំថ្ម)
 * - ត្រឡប់មក app ⇒ bot ចាប់ផ្តើមឡើងវិញ (បើអ្នកមិនបានចុច Stop)
 */
public class MainActivity extends Activity {

    private static final String API = "https://api.telegram.org/bot";

    private EditText tokenInput;
    private EditText templateInput;
    private TextView statusView;
    private TextView logView;

    private volatile boolean running = false;
    private volatile String template = "";
    private String token = "";
    private boolean userWantsRunning = false;
    private Thread poller;
    private long offset = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Telegram Bot (Java)");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("ដាក់ bot token ពី @BotFather រួចចុច Start — bot នឹងឆ្លើយតបស្វ័យប្រវត្តិ។\n{name} = ឈ្មោះអ្នកផ្ញើ · {text} = សារដែលទទួលបាន");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        root.addView(hint);

        tokenInput = new EditText(this);
        tokenInput.setHint("123456789:AAExampleToken");
        tokenInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(tokenInput, topMargin(dp(10)));

        templateInput = new EditText(this);
        templateInput.setText("សួស្តី {name}! ខ្ញុំបានទទួលសារ: {text}");
        templateInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(templateInput, topMargin(dp(8)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("Start");
        start.setAllCaps(false);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBot();
            }
        });
        row.addView(start);

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setAllCaps(false);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBot();
            }
        });
        row.addView(stop);
        root.addView(row, topMargin(dp(8)));

        statusView = new TextView(this);
        statusView.setText("ស្ថានភាព: បានឈប់");
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(statusView, topMargin(dp(8)));

        logView = new TextView(this);
        logView.setText("នៅរង់ចាំ…");
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(logView, topMargin(dp(10)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) {
            running = false;
            setStatus("ស្ថានភាព: ផ្អាក (ចេញពី app)");
            log("ចេញពី app — បញ្ឈប់ bot បណ្ដោះអាសន្ន។");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userWantsRunning && !running && !token.isEmpty()) {
            log("ត្រឡប់មក app — ចាប់ផ្ដើម bot ឡើងវិញ។");
            startPolling();
        }
    }

    @Override
    protected void onDestroy() {
        userWantsRunning = false;
        running = false;
        super.onDestroy();
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

    private void setStatus(String text) {
        statusView.setText(text);
    }

    private void log(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                logView.append("\n[" + stamp + "] " + line);
            }
        });
    }

    private void startBot() {
        String entered = tokenInput.getText().toString().trim();
        if (entered.isEmpty()) {
            logView.setText("សូមបញ្ចូល bot token សិន។");
            return;
        }
        token = entered;
        template = templateInput.getText().toString();
        userWantsRunning = true;
        startPolling();
    }

    private void startPolling() {
        if (running) {
            log("bot កំពុងដំណើរការរួចហើយ។");
            return;
        }
        running = true;
        setStatus("ស្ថានភាព: កំពុងដំណើរការ ✓");
        logView.setText("កំពុងភ្ជាប់ Telegram…");
        poller = new Thread(new Runnable() {
            @Override
            public void run() {
                pollLoop(token);
            }
        });
        poller.start();
    }

    private void stopBot() {
        userWantsRunning = false;
        running = false;
        setStatus("ស្ថានភាព: បានឈប់");
        log("បានបញ្ឈប់ bot។");
    }

    private void pollLoop(String botToken) {
        while (running) {
            try {
                String url = API + botToken + "/getUpdates?timeout=25&offset=" + offset;
                JSONObject root = new JSONObject(httpGet(url, 40000));
                if (!root.optBoolean("ok")) {
                    log("Telegram បដិសេធ: " + root.optString("description"));
                    pause(4000);
                    continue;
                }
                JSONArray updates = root.optJSONArray("result");
                if (updates == null || updates.length() == 0) {
                    continue;
                }
                for (int i = 0; i < updates.length() && running; i++) {
                    JSONObject update = updates.optJSONObject(i);
                    if (update == null) {
                        continue;
                    }
                    offset = Math.max(offset, update.optLong("update_id") + 1);
                    JSONObject message = update.optJSONObject("message");
                    if (message == null) {
                        continue;
                    }
                    String text = message.optString("text", "");
                    JSONObject from = message.optJSONObject("from");
                    JSONObject chat = message.optJSONObject("chat");
                    String name = from == null ? "មិត្តភក្តិ" : from.optString("first_name", "មិត្តភក្តិ");
                    long chatId = chat == null ? 0 : chat.optLong("id");
                    log("← " + name + ": " + text);
                    if (chatId == 0) {
                        continue;
                    }
                    sendMessage(botToken, chatId, buildReply(text, name));
                }
            } catch (SocketTimeoutException timeout) {
                // long-poll ធម្មតា (គ្មានសារថ្មី) — មិនត្រូវរាយ error
                continue;
            } catch (Exception e) {
                if (running) {
                    log("បណ្តាញរអាក់រអួល — កំពុងព្យាយាមឡើងវិញ…");
                    pause(2000);
                }
            }
        }
    }

    private String buildReply(String text, String name) {
        if (text.equals("/start")) {
            return "សូមស្វាគមន៍ " + name + "! ខ្ញុំជា bot ឆ្លើយតបស្វ័យប្រវត្តិ។\n"
                    + "សរសេរអ្វីក៏បាន ខ្ញុំនឹងឆ្លើយតបវិញភ្លាម។";
        }
        if (text.equals("/help")) {
            return "ពាក្យបញ្ជា:\n/start — ចាប់ផ្តើម\n/help — ជំនួយ\n"
                    + "សារធម្មតា — ខ្ញុំឆ្លើយតបស្វ័យប្រវត្តិ";
        }
        return template.replace("{name}", name).replace("{text}", text);
    }

    private void sendMessage(String botToken, long chatId, String text) {
        try {
            String body = "chat_id=" + chatId + "&text=" + URLEncoder.encode(text, "UTF-8");
            JSONObject root = new JSONObject(
                    httpPost(API + botToken + "/sendMessage", body, 20000));
            if (!root.optBoolean("ok")) {
                log("ផ្ញើមិនបាន: " + root.optString("description"));
            } else {
                log("→ " + text);
            }
        } catch (Exception e) {
            log("ផ្ញើមិនបាន: " + e);
        }
    }

    private String httpGet(String address, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Accept", "application/json");
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String httpPost(String address, String form, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] payload = form.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.close();
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
        }
        return sb.toString();
    }

    private void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
