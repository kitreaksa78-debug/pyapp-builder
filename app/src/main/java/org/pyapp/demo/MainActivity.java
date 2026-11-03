package org.pyapp.demo;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Telegram bot ២៤ម៉ោង ដោយប្រើ Foreground Service។
 *
 * - ចុច Start ⇒ bot ដើរក្នុង background (មាន notification ជាប់) ទោះចេញពី app ឬបិទអេក្រង់
 * - Notification បង្ហាញចំនួនសារដែលបានឆ្លើយ
 * - ចុច Stop ⇒ ឈប់
 */
public class MainActivity extends Activity {

    /** log រួមរវាង UI និង Service (thread-safe) */
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<String>());

    private EditText tokenInput;
    private EditText templateInput;
    private TextView statusView;
    private TextView logView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            renderLog();
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Telegram Bot 24/7 (Java)");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("ដាក់ bot token ពី @BotFather រួចចុច Start។ bot ដើរក្នុង background — "
                + "ចេញពី app ក៏នៅឆ្លើយតប។\n{name} = ឈ្មោះអ្នកផ្ញើ · {text} = សារដែលទទួលបាន");
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
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(statusView, topMargin(dp(8)));

        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(logView, topMargin(dp(10)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    static void log(String line) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        LOG.add("[" + stamp + "] " + line);
        while (LOG.size() > 200) {
            LOG.remove(0);
        }
    }

    private void renderLog() {
        StringBuilder sb = new StringBuilder();
        synchronized (LOG) {
            for (String line : LOG) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        if (sb.length() == 0) {
            sb.append("នៅរង់ចាំ…");
        }
        logView.setText(sb.toString());
    }

    private void updateStatus() {
        if (BotService.isRunning) {
            statusView.setText("ស្ថានភាព: កំពុងដំណើរការ ✓ (background)\n"
                    + "ឆ្លើយតបបាន " + BotService.replies + " សារ");
        } else {
            statusView.setText("ស្ថានភាព: បានឈប់");
        }
    }

    private void startBot() {
        String entered = tokenInput.getText().toString().trim();
        if (entered.isEmpty()) {
            log("សូមបញ្ចូល bot token សិន។");
            renderLog();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Intent service = new Intent(this, BotService.class);
        service.putExtra("token", entered);
        service.putExtra("template", templateInput.getText().toString());
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        log("បានចាប់ផ្តើម bot (foreground service)។");
        updateStatus();
    }

    private void stopBot() {
        stopService(new Intent(this, BotService.class));
        log("បានបញ្ឈប់ bot។");
        updateStatus();
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

    // ------------------------------------------------------------------ service
    /**
     * Service ដែល polling Telegram ក្នុង background។
     * ប្រកាសក្នុង manifest ដោយស្វ័យប្រវត្តិ (android:name=".MainActivity$BotService")។
     */
    public static class BotService extends Service {

        static final String API = "https://api.telegram.org/bot";
        static final String CHANNEL_ID = "telegram_bot";
        static final int NOTIFICATION_ID = 1;

        static volatile boolean isRunning = false;
        static volatile int replies = 0;

        private volatile boolean stopFlag = false;
        private Thread worker;
        private long offset = 0;
        private String token = "";
        private volatile String template = "";

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            isRunning = true;
            createChannel();
            startForeground(NOTIFICATION_ID, notification("កំពុងដំណើរការ…"));
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null) {
                token = intent.getStringExtra("token") == null ? "" : intent.getStringExtra("token");
                template = intent.getStringExtra("template") == null
                        ? "សួស្តី {name}! ខ្ញុំបានទទួលសារ: {text}"
                        : intent.getStringExtra("template");
            }
            if (worker == null || !worker.isAlive()) {
                stopFlag = false;
                worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        pollLoop();
                    }
                });
                worker.start();
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            stopFlag = true;
            isRunning = false;
            worker = null;
            MainActivity.log("service ឈប់ដំណើរការ។");
            super.onDestroy();
        }

        private void createChannel() {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Telegram Bot", NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        }

        private Notification notification(String text) {
            Intent open = new Intent(this, MainActivity.class);
            int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }
            builder.setContentTitle("Telegram Bot កំពុងដំណើរការ")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setOngoing(true)
                    .setContentIntent(pending);
            return builder.build();
        }

        private void updateNotification(String text) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                try {
                    manager.notify(NOTIFICATION_ID, notification(text));
                } catch (Exception ignored) {
                }
            }
        }

        private void pollLoop() {
            MainActivity.log("bot ចាប់ផ្តើម polling…");
            while (!stopFlag) {
                try {
                    String url = API + token + "/getUpdates?timeout=25&offset=" + offset;
                    JSONObject root = new JSONObject(httpGet(url, 40000));
                    if (!root.optBoolean("ok")) {
                        String description = root.optString("description");
                        MainActivity.log("Telegram បដិសេធ: " + description);
                        updateNotification("បញ្ហា: " + description);
                        pause(5000);
                        continue;
                    }
                    JSONArray updates = root.optJSONArray("result");
                    if (updates == null || updates.length() == 0) {
                        continue;
                    }
                    for (int i = 0; i < updates.length() && !stopFlag; i++) {
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
                        String name = from == null
                                ? "មិត្តភក្តិ" : from.optString("first_name", "មិត្តភក្តិ");
                        long chatId = chat == null ? 0 : chat.optLong("id");
                        MainActivity.log("← " + name + ": " + text);
                        if (chatId == 0) {
                            continue;
                        }
                        sendMessage(chatId, buildReply(text, name));
                    }
                } catch (SocketTimeoutException timeout) {
                    // long-poll ធម្មតា — មិនត្រូវរាយ error
                } catch (Exception e) {
                    if (!stopFlag) {
                        MainActivity.log("បណ្តាញរអាក់រអួល — ព្យាយាមឡើងវិញ…");
                        pause(2000);
                    }
                }
            }
        }

        private String buildReply(String text, String name) {
            if (text.equals("/start")) {
                return "សូមស្វាគមន៍ " + name + "! ខ្ញុំជា bot ឆ្លើយតបស្វ័យប្រវត្តិ ២៤ម៉ោង។\n"
                        + "សរសេរអ្វីក៏បាន ខ្ញុំនឹងឆ្លើយតបវិញភ្លាម។";
            }
            if (text.equals("/help")) {
                return "ពាក្យបញ្ជា:\n/start — ចាប់ផ្តើម\n/help — ជំនួយ\n"
                        + "សារធម្មតា — ខ្ញុំឆ្លើយតបស្វ័យប្រវត្តិ";
            }
            return template.replace("{name}", name).replace("{text}", text);
        }

        private void sendMessage(long chatId, String text) {
            try {
                String body = "chat_id=" + chatId + "&text=" + URLEncoder.encode(text, "UTF-8");
                JSONObject root = new JSONObject(
                        httpPost(API + token + "/sendMessage", body, 20000));
                if (!root.optBoolean("ok")) {
                    MainActivity.log("ផ្ញើមិនបាន: " + root.optString("description"));
                } else {
                    replies = replies + 1;
                    MainActivity.log("→ " + text);
                    updateNotification("ឆ្លើយតបបាន " + replies + " សារ");
                }
            } catch (Exception e) {
                MainActivity.log("ផ្ញើមិនបាន: " + e);
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
}
