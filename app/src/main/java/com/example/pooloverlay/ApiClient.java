package com.example.pooloverlay;

import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ApiClient {

    public static class ApiResult {
        public final int statusCode;
        public final String rawBody;
        public final JSONObject json;

        public ApiResult(int statusCode, String rawBody, JSONObject json) {
            this.statusCode = statusCode;
            this.rawBody = rawBody;
            this.json = json;
        }
    }

    public static ApiResult sendImage(String apiUrl, Bitmap bitmap, int width, int height) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] imageBytes = baos.toByteArray();

        // ១. សាកល្បងផ្ញើជា JSON ជាមុន (សម្រាប់ Server ដែលទទួល Content-Type: application/json)
        ApiResult jsonAttempt = postJson(apiUrl, imageBytes, width, height);
        if (jsonAttempt.statusCode == HttpURLConnection.HTTP_OK) {
            return jsonAttempt;
        }

        // ២. បើ Server មិនទទួល JSON (ឧទាហរណ៍ 400/415/422) សាកល្បងផ្ញើជា multipart/form-data វិញ
        ApiResult multipartAttempt = postMultipart(apiUrl, imageBytes);
        if (multipartAttempt.statusCode == HttpURLConnection.HTTP_OK) {
            return multipartAttempt;
        }

        return multipartAttempt.rawBody != null && !multipartAttempt.rawBody.isEmpty()
                ? multipartAttempt
                : jsonAttempt;
    }

    private static ApiResult postJson(String apiUrl, byte[] imageBytes, int width, int height) throws Exception {
        HttpURLConnection conn = null;
        try {
            String base64Img = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            JSONObject payload = new JSONObject();
            payload.put("image", base64Img);
            payload.put("image_base64", base64Img);
            payload.put("width", width);
            payload.put("height", height);
            payload.put("preview", false);

            byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setFixedLengthStreamingMode(bodyBytes.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static ApiResult postMultipart(String apiUrl, byte[] imageBytes) throws Exception {
        HttpURLConnection conn = null;
        try {
            String boundary = "----PoolOverlayBoundary" + System.currentTimeMillis();
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Accept", "application/json");

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"frame.jpg\"\r\n");
                dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                dos.write(imageBytes);
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush();
            }

            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static ApiResult readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = "";
        if (stream != null) {
            try (Scanner s = new Scanner(stream).useDelimiter("\\A")) {
                body = s.hasNext() ? s.next() : "";
            }
        }
        JSONObject parsed = null;
        try {
            if (body != null && !body.trim().isEmpty()) {
                parsed = new JSONObject(body);
            }
        } catch (Exception ignored) {
        }
        return new ApiResult(code, body, parsed);
    }

    public static JSONArray extractShotsArray(JSONObject json) {
        if (json == null) return null;
        if (json.has("shots") && json.optJSONArray("shots") != null) {
            return json.optJSONArray("shots");
        }
        JSONObject data = json.optJSONObject("data");
        if (data != null && data.has("shots") && data.optJSONArray("shots") != null) {
            return data.optJSONArray("shots");
        }
        return null;
    }

    public static JSONObject extractLinesObject(JSONObject json) {
        if (json == null) return null;
        if (json.has("lines") && json.optJSONObject("lines") != null) {
            return json.optJSONObject("lines");
        }
        JSONObject data = json.optJSONObject("data");
        if (data != null && data.has("lines") && data.optJSONObject("lines") != null) {
            return data.optJSONObject("lines");
        }
        return null;
    }
}
