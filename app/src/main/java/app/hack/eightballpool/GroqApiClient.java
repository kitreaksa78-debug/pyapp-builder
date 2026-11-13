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

/**
 * Sends the user-entered key over HTTPS to the configured backend for one request.
 * The backend must not log or persist the key. Use only a trusted HTTPS backend.
 */
public final class GroqApiClient {
    // Replace this with the deployed HTTPS URL for render-backend/server.js.
    public static final String BACKEND_BASE_URL = "https://pyapp-builder.onrender.com";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    private GroqApiClient() {}

    public static void test(String apiKey, String model, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("apiKey", apiKey);
            body.put("model", model);
        } catch (Exception e) {
            callback.onError("Cannot prepare request");
            return;
        }
        request("/api/groq/test", body, callback);
    }

    public static void analyze(byte[] imageBytes, String apiKey, String model, Callback callback) {
        if (imageBytes == null || imageBytes.length == 0) {
            callback.onError("រូបភាពទទេ");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            String encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            body.put("image", "data:image/jpeg;base64," + encoded);
            body.put("apiKey", apiKey);
            body.put("model", model);
        } catch (Exception e) {
            callback.onError("Cannot prepare image request");
            return;
        }
        request("/api/vision/analyze", body, callback);
    }

    private static void request(String path, JSONObject payload, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BACKEND_BASE_URL + path);
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
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                JSONObject json = response.length() == 0 ? new JSONObject() : new JSONObject(response.toString());
                if (status < 200 || status >= 300) {
                    String error = json.optString("error", "Backend HTTP " + status);
                    throw new Exception(error);
                }
                MAIN.post(() -> callback.onSuccess(json));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Network error" : e.getMessage();
                MAIN.post(() -> callback.onError(msg));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
