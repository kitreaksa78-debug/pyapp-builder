package com.example.groqpool

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

class MainActivity : ComponentActivity() {
    private lateinit var keyInput: EditText
    private lateinit var status: TextView
    private val requestCode = 4411

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        box.addView(TextView(this).apply { text = "Groq Pool AI • Auto-Model Mode"; textSize = 22f })
        box.addView(TextView(this).apply {
            text = "ដាក់តែ Groq API Key ប៉ុណ្ណោះ — App នឹងរើស Model ឥតគិតថ្លៃដោយស្វ័យប្រវត្តិ និងប្ដូរ Model ភ្លាមៗពេលអស់ Limit (429)។"
            textSize = 13f
            setPadding(0, 8, 0, 16)
        })

        keyInput = EditText(this).apply { hint = "Groq API Key (gsk_...)"; inputType = 129 }
        box.addView(keyInput)

        box.addView(Button(this).apply { text = "SAVE KEY SECURELY"; setOnClickListener {
            try {
                val k = keyInput.text.toString().trim()
                SecureStore.save(this@MainActivity, k)
                status.text = "Key saved securely on phone."
            } catch (e: Exception) {
                status.text = "Save failed: ${e.message}"
            }
        }})

        box.addView(Button(this).apply { text = "TEST & AUTO-DETECT FREE MODELS"; setOnClickListener { testConnection() } })
        box.addView(Button(this).apply { text = "START SCREEN CAPTURE + OVERLAY"; setOnClickListener { startCaptureFlow() } })
        box.addView(Button(this).apply { text = "STOP OVERLAY"; setOnClickListener {
            stopService(Intent(this@MainActivity, CaptureOverlayService::class.java))
            status.text = "Overlay stopped."
        }})

        status = TextView(this).apply { text = "Status: Ready (Enter Groq Key)"; textSize = 15f; setPadding(0, 16, 0, 12) }
        box.addView(status)

        setContentView(ScrollView(this).apply { addView(box) })
        val saved = SecureStore.read(this)
        if (!saved.isNullOrBlank()) keyInput.setText(saved)
    }

    private fun testConnection() {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) {
            status.text = "សូមបញ្ចូល Groq API Key ជាមុនសិន។"; return
        }
        status.text = "កំពុងស្វែងរក Model ឥតគិតថ្លៃពី Groq..."
        Thread {
            try {
                val models = GroqModelManager.getActiveModels(key, forceRefresh = true)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Reply OK")
                    })
                }
                val (usedModel, _) = GroqModelManager.sendWithAutoFailover(
                    apiKey = key,
                    messages = messages,
                    maxTokens = 10,
                    onStatusUpdate = { msg -> runOnUiThread { status.text = msg } }
                )
                runOnUiThread {
                    status.text = "ជោគជ័យ! Active Model: $usedModel\n(មាន ${models.size} models សម្រាប់ប្ដូរ Auto ពេលអស់ Limit)"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "បរាជ័យ: ${e.message}" }
            }
        }.start()
    }

    private fun startCaptureFlow() {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) {
            status.text = "សូមបញ្ចូល Groq API Key ជាមុនសិន។"; return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "សូមបើកសិទ្ធិ Display over other apps រួចចុច START ម្ដងទៀត។"; return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("Deprecated Android callback retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("apiKey", keyInput.text.toString().trim())
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "Screen capture started! App will auto-switch models when needed."
        } else {
            status.text = "Screen capture permission was not granted."
        }
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
