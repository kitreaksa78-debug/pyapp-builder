package app.hack.eightballpool;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OVERLAY = 2084;
    private static final int REQUEST_IMAGE = 2085;

    private EditText keyInput;
    private Spinner modelSpinner;
    private TextView statusText;
    private TextView resultText;
    private String currentKey = "";

    private final String[] models = new String[] {
            "qwen/qwen3.8-27b",
            "meta-llama/llama-4-scout-17b-16e-instruct"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        loadSavedKey();
    }

    private void buildScreen() {
        int pad = dp(16);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("8BallPool AI Practice");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Groq API settings • Vision practice feedback");
        subtitle.setTextColor(0xFFB8C7D1);
        subtitle.setTextSize(14);
        page.addView(subtitle);

        TextView keyLabel = label("Groq API Key");
        page.addView(keyLabel);
        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextColor(0xFFFFFFFF);
        keyInput.setHintTextColor(0xFF9AA8B2);
        keyInput.setHint("gsk_...");
        keyInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(keyInput, matchWrap());

        TextView modelLabel = label("Vision Model");
        page.addView(modelLabel);
        modelSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, models);
        modelSpinner.setAdapter(adapter);
        page.addView(modelSpinner, matchWrap());

        Button save = button("Save Key Securely");
        save.setOnClickListener(v -> saveKey());
        page.addView(save);

        Button test = button("Test Groq Connection");
        test.setOnClickListener(v -> testConnection());
        page.addView(test);

        statusText = label("Connection: Not tested");
        statusText.setTextColor(0xFFFFC857);
        page.addView(statusText);

        Button image = button("Choose Image & Analyze");
        image.setOnClickListener(v -> chooseImage());
        page.addView(image);

        resultText = label("Analysis result will appear here.");
        resultText.setTextColor(0xFFE1EAF0);
        page.addView(resultText);

        Button overlay = button("Start Floating Overlay");
        overlay.setOnClickListener(v -> startOverlay());
        page.addView(overlay);

        TextView warning = label("Security: the key is encrypted on this phone. It is sent over HTTPS to the configured backend for each request. Use only a backend you control.");
        warning.setTextColor(0xFF9FB0BC);
        page.addView(warning);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setPadding(0, dp(12), 0, dp(6));
        v.setTextColor(0xFFD7E2E8);
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String selectedModel() {
        return String.valueOf(modelSpinner.getSelectedItem());
    }

    private void loadSavedKey() {
        try {
            currentKey = SecureKeyStore.read(this);
            if (!currentKey.isEmpty()) keyInput.setText(currentKey);
        } catch (Exception e) {
            statusText.setText("Connection: secure storage unavailable");
        }
    }

    private void saveKey() {
        String value = keyInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "សូមបញ្ចូល Groq API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SecureKeyStore.save(this, value);
            currentKey = value;
            Toast.makeText(this, "បានរក្សាទុក Key ដោយការអ៊ិនគ្រីប", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getKey() {
        String typed = keyInput.getText().toString().trim();
        return typed.isEmpty() ? currentKey : typed;
    }

    private void testConnection() {
        String key = getKey();
        if (key.isEmpty()) {
            statusText.setText("Connection: Failed — API Key missing");
            return;
        }
        statusText.setText("Connection: Connecting...");
        GroqApiClient.test(key, selectedModel(), new GroqApiClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                statusText.setText("Connection: Connected ✓  (" + result.optString("model", selectedModel()) + ")");
                currentKey = key;
            }
            @Override public void onError(String message) {
                statusText.setText("Connection: Failed — " + message);
            }
        });
    }

    private void chooseImage() {
        if (getKey().isEmpty()) {
            Toast.makeText(this, "សូមបញ្ចូល និង Save API Key ជាមុន", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            analyzeSelectedImage(data.getData());
        } else if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) Control.start(this);
        }
    }

    private void analyzeSelectedImage(Uri uri) {
        final String apiKey = getKey();
        final String model = selectedModel();
        resultText.setText("Analyzing image...");
        new Thread(() -> {
            try {
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
                if (bitmap == null) throw new Exception("Cannot read image");
                int maxSide = 1280;
                int w = bitmap.getWidth(), h = bitmap.getHeight();
                float scale = Math.min(1f, (float) maxSide / Math.max(w, h));
                if (scale < 1f) {
                    Bitmap resized = Bitmap.createScaledBitmap(bitmap,
                            Math.round(w * scale), Math.round(h * scale), true);
                    bitmap.recycle();
                    bitmap = resized;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
                bitmap.recycle();
                byte[] bytes = out.toByteArray();
                GroqApiClient.analyze(bytes, apiKey, model, new GroqApiClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        JSONObject analysis = result.optJSONObject("analysis");
                        resultText.setText(analysis == null ? result.toString() : analysis.toString());
                    }
                    @Override public void onError(String message) {
                        resultText.setText("Analysis failed: " + message);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> resultText.setText("Image error: " + e.getMessage()));
            }
        }).start();
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }
        Control.start(this);
        Toast.makeText(this, "Floating overlay started", Toast.LENGTH_SHORT).show();
    }
}
