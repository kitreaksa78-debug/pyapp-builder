package app.hack.eightballpool;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends a user-approved screenshot to the Render backend. Never put GROQ_API_KEY in this app. */
public final class GroqVisionClient {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Replace with your deployed Render URL, e.g. https://your-service.onrender.com/api/vision/analyze
    public static final String ANALYZE_URL = "https://YOUR-DEDICATED-RENDER-SERVICE.onrender.com/api/vision/analyze";

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    private GroqVisionClient() {}

    public static void analyzeJpeg(byte[] jpegBytes, Callback callback) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            callback.onError("Screenshot is empty");
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
                JSONObject payload = new JSONObject();
                payload.put("image", "data:image/jpeg;base64," + encoded);
                URL url = new URL(ANALYZE_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = connection.getOutputStream()) { os.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                if (status < 200 || status >= 300) throw new Exception("Backend HTTP " + status + ": " + response);
                JSONObject result = new JSONObject(response.toString());
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Unknown network error" : e.getMessage();
                MAIN.post(() -> callback.onError(message));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
