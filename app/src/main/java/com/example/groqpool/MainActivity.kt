package com.example.groqpool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

class MainActivity : ComponentActivity() {
    private lateinit var keyInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var status: TextView
    private val requestCode = 4411
    private val models = listOf("qwen/qwen3.8-27b", "qwen/qwen3.6-27b")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        box.addView(TextView(this).apply {
            text = "Groq Pool AI • Practice Overlay"
            textSize = 23f
        })
        box.addView(TextView(this).apply {
            text = "Groq API Key"
            textSize = 17f
            setPadding(0, 22, 0, 6)
        })
        keyInput = EditText(this).apply {
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            singleLine = true
        }
        box.addView(keyInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(TextView(this).apply {
            text = "Vision Model"
            textSize = 17f
            setPadding(0, 18, 0, 6)
        })
        modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, models)
        }
        box.addView(modelSpinner)
        addButton(box, "SAVE KEY SECURELY") { saveKey() }
        addButton(box, "TEST GROQ CONNECTION") { testGroq() }
        addButton(box, "START SCREEN CAPTURE + OVERLAY") { startCaptureFlow() }
        addButton(box, "STOP OVERLAY") { stopService(Intent(this, CaptureOverlayService::class.java)); status.text = "Overlay stopped." }
        status = TextView(this).apply {
            text = "Status: enter your Groq API key. No Render Backend URL is needed."
            textSize = 16f
            setPadding(0, 18, 0, 0)
        }
        box.addView(status)
        box.addView(TextView(this).apply {
            text = "The key is encrypted on this phone and sent directly to api.groq.com over HTTPS. Screen capture requires Android permission. This is a practice guide; it does not control game actions or guarantee shot accuracy."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(box) })
        SecureStore.read(this)?.let { keyInput.setText(it) }
        val savedModel = getSharedPreferences("groq_settings", MODE_PRIVATE).getString("model", models[0])
        modelSpinner.setSelection(models.indexOf(savedModel).coerceAtLeast(0))
    }

    private fun addButton(box: LinearLayout, label: String, action: () -> Unit) {
        box.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun selectedModel(): String = modelSpinner.selectedItem?.toString() ?: models[0]

    private fun saveKey(): Boolean {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) { status.text = "Enter your Groq API key first."; return false }
        return try {
            SecureStore.save(this, key)
            getSharedPreferences("groq_settings", MODE_PRIVATE).edit().putString("model", selectedModel()).apply()
            status.text = "Key saved securely on this phone."
            true
        } catch (e: Exception) {
            status.text = "Save failed: ${e.message}"
            false
        }
    }

    private fun testGroq() {
        val key = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
        if (key.isBlank()) { status.text = "Enter and save your Groq API key first."; return }
        val model = selectedModel()
        status.text = "Connecting directly to Groq..."
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with exactly: GROQ_OK")))
                    .put("max_completion_tokens", 24)
                    .put("temperature", 0)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw Exception("HTTP $code: ${extractApiError(response)}")
                val answer = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                if (answer.isBlank()) throw Exception("Groq returned an empty response")
                SecureStore.save(this, key)
                getSharedPreferences("groq_settings", MODE_PRIVATE).edit().putString("model", model).apply()
                runOnUiThread { status.text = "Connected to Groq • $model" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Groq connection failed: ${e.message?.take(220)}" }
            } finally { conn?.disconnect() }
        }.start()
    }

    private fun extractApiError(raw: String): String = try {
        JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(180)
    } catch (_: Exception) { raw.take(180) }

    private fun startCaptureFlow() {
        val key = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
        if (key.isBlank()) { status.text = "Save your Groq API key before starting."; return }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "Allow Display over other apps, then tap START again."
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("Deprecated callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK && data != null) {
            val savedKey = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
            if (savedKey.isBlank()) { status.text = "No saved Groq key found."; return }
            SecureStore.save(this, savedKey)
            getSharedPreferences("groq_settings", MODE_PRIVATE).edit().putString("model", selectedModel()).apply()
            val serviceIntent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("model", selectedModel())
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            status.text = "Screen capture started. Switch to your practice table."
        } else status.text = "Screen capture permission was not granted."
    }
}

object SecureStore {
    private const val PREF = "secure_groq"
    private const val ALIAS = "GroqPoolAesKey"
    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }
    fun save(ctx: Context, value: String) {
        require(value.isNotBlank()) { "API key is empty" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("key", encrypted).putString("iv", iv).apply()
    }
    fun read(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("key", null) ?: return null
        val iv = Base64.decode(prefs.getString("iv", "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
