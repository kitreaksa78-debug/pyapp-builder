package com.example.groqpool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var keyInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var status: TextView
    private val requestCode = 4411
    private val models = listOf(
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "meta-llama/llama-4-maverick-17b-128e-instruct"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Groq Pool AI\n8 Ball Pool practice assistant"
            textSize = 23f
        })
        root.addView(TextView(this).apply {
            text = "Groq API Key (stored encrypted on this device)"
            textSize = 14f
        })
        keyInput = EditText(this).apply {
            hint = "gsk_..."
            inputType = 129
            singleLine = true
        }
        root.addView(keyInput, matchWidth())
        root.addView(TextView(this).apply { text = "Groq Vision Model" })
        modelSpinner = Spinner(this)
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        root.addView(modelSpinner, matchWidth())

        root.addView(Button(this).apply {
            text = "SAVE API KEY"
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                if (!key.startsWith("gsk_") || key.length < 20) {
                    status.text = "Please enter a valid Groq API key."
                } else try {
                    SecureStore.save(this@MainActivity, key)
                    status.text = "API key saved securely on this phone."
                } catch (e: Exception) {
                    status.text = "Could not save key: ${e.message}"
                }
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "TEST GROQ CONNECTION"
            setOnClickListener { testGroq() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "START SCREEN ANALYSIS + FLOATING GUIDE"
            setOnClickListener { startCaptureFlow() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "STOP FLOATING GUIDE"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureOverlayService::class.java))
                status.text = "Capture/overlay stopped."
            }
        }, matchWidth())
        status = TextView(this).apply {
            text = "Status: Ready"
            textSize = 16f
        }
        root.addView(status, matchWidth())
        root.addView(TextView(this).apply {
            text = "How to use:\n1. Save your Groq key.\n2. Test the connection.\n3. Allow 'Display over other apps'.\n4. Approve Android screen-capture prompt.\n5. Switch to 8 Ball Pool.\n\nThis app only displays practice guidance. It does not tap, aim, or play automatically. Screen images are sent to Groq for analysis. Model availability and vision support depend on Groq's current model access and account limits."
            textSize = 13f
        }, matchWidth())
        setContentView(ScrollView(this).apply { addView(root) })
        SecureStore.read(this)?.let { keyInput.setText(it) }
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 10 }

    private fun testGroq() {
        val key = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
        if (!key.startsWith("gsk_")) {
            status.text = "Enter and save your Groq API key first."
            return
        }
        val model = modelSpinner.selectedItem.toString()
        status.text = "Testing Groq..."
        Thread {
            try {
                val result = GroqClient.test(key, model)
                runOnUiThread { status.text = "Groq connection OK: $result" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Groq test failed: ${e.message}" }
            }
        }.start()
    }

    private fun startCaptureFlow() {
        val key = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
        if (!key.startsWith("gsk_")) {
            status.text = "Enter and save your Groq API key first."
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "Allow Display over other apps, then tap START again."
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("Android screen capture consent callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == Activity.RESULT_OK && data != null) {
            val key = keyInput.text.toString().trim().ifBlank { SecureStore.read(this).orEmpty() }
            val serviceIntent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("model", modelSpinner.selectedItem.toString())
                putExtra("apiKey", key)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            status.text = "Screen analysis started. Switch to the game. Android will show a capture notification."
        } else {
            status.text = "Screen capture permission was not granted."
        }
    }
}

object SecureStore {
    private const val PREFS = "groq_secure_prefs"
    private const val ALIAS = "GroqPoolApiKeyAES"
    private fun secretKey(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun save(context: Context, value: String) {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cipher", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .putString("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .apply()
    }
    fun read(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("cipher", null) ?: return null
        val ivText = prefs.getString("iv", null) ?: return null
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey(), javax.crypto.spec.GCMParameterSpec(
            128, android.util.Base64.decode(ivText, android.util.Base64.NO_WRAP)
        ))
        return String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
