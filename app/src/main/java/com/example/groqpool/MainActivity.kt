package com.example.groqpool

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
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
    private lateinit var modelInput: EditText
    private lateinit var backendInput: EditText
    private lateinit var status: TextView
    private val requestCode = 4411

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        box.addView(TextView(this).apply { text = "Groq Pool AI • Live practice overlay"; textSize = 22f })
        keyInput = EditText(this).apply { hint = "Groq API Key"; inputType = 129 }
        modelInput = EditText(this).apply { hint = "Vision model ID"; setText("meta-llama/llama-4-scout-17b-16e-instruct") }
        backendInput = EditText(this).apply { hint = "HTTPS backend URL (e.g. https://your-service.onrender.com)"; setText("https://YOUR-RENDER-SERVICE.onrender.com") }
        box.addView(keyInput); box.addView(modelInput); box.addView(backendInput)
        box.addView(Button(this).apply { text = "SAVE KEY SECURELY"; setOnClickListener {
            try { SecureStore.save(this@MainActivity, keyInput.text.toString()); status.text = "Key saved on this phone." }
            catch (e: Exception) { status.text = "Save failed: ${e.message}" }
        }})
        box.addView(Button(this).apply { text = "TEST BACKEND + GROQ"; setOnClickListener { testConnection() } })
        box.addView(Button(this).apply { text = "START SCREEN CAPTURE + OVERLAY"; setOnClickListener { startCaptureFlow() } })
        box.addView(Button(this).apply { text = "STOP OVERLAY"; setOnClickListener { stopService(Intent(this@MainActivity, CaptureOverlayService::class.java)) } })
        status = TextView(this).apply { text = "Status: waiting"; textSize = 16f }
        box.addView(status)
        box.addView(TextView(this).apply {
            text = "Screen capture starts only after Android's permission dialog. Keep the capture notification visible. This is a practice guide, not an auto-play or guaranteed-accuracy tool."
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(box) })
        val saved = SecureStore.read(this)
        if (!saved.isNullOrBlank()) keyInput.setText(saved)
    }

    private fun testConnection() {
        val key = keyInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val backend = backendInput.text.toString().trim().trimEnd('/')
        if (key.isBlank() || model.isBlank() || !backend.startsWith("https://") || backend.contains("YOUR-RENDER")) {
            status.text = "Enter API key, model, and your real HTTPS backend URL."; return
        }
        Thread {
            try {
                val c = (URL("$backend/health").openConnection() as HttpURLConnection)
                c.connectTimeout = 10000; c.readTimeout = 10000
                val body = c.inputStream.bufferedReader().readText()
                runOnUiThread { status.text = "Backend OK: $body" }
            } catch (e: Exception) { runOnUiThread { status.text = "Connection failed: ${e.message}" } }
        }.start()
    }

    private fun startCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "Allow Display over other apps, then tap START again."; return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("Deprecated Android callback retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("data", data)
                putExtra("backend", backendInput.text.toString().trim().trimEnd('/'))
                putExtra("model", modelInput.text.toString().trim())
                putExtra("apiKey", keyInput.text.toString().trim())
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "Screen capture started. Switch to the game; tap STOP in app to end."
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
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return gen.generateKey()
    }
    fun save(ctx: Context, value: String) {
        require(value.isNotBlank()) { "API key is empty" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        ctx.getSharedPreferences(PREF, 0).edit().putString("key", enc).putString("iv", iv).apply()
    }
    fun read(ctx: Context): String? {
        val p = ctx.getSharedPreferences(PREF, 0)
        val enc = p.getString("key", null) ?: return null
        val iv = Base64.decode(p.getString("iv", "") ?: "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP)))
    }
}
