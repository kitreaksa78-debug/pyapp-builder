package com.example.groqpool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val captureRequest = 4102
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        val prefs = getSharedPreferences("groq_pool_settings", Context.MODE_PRIVATE)
        keyInput.setText(prefs.getString("api_key", ""))
        modelInput.setText(prefs.getString("model", "meta-llama/llama-4-scout-17b-16e-instruct"))
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF101827.toInt())
        }
        fun label(text: String, size: Float = 16f): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 10, 0, 8)
            }

        root.addView(label("🎱 Groq AI Pool Assistant", 24f))
        root.addView(label("Groq API Key"))
        keyInput = EditText(this).apply {
            hint = "gsk_..."
            setSingleLine(true)
            inputType = 0x00000081
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA7B8.toInt())
            setBackgroundColor(0xFF243247.toInt())
            setPadding(14, 12, 14, 12)
        }
        root.addView(keyInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("Groq Vision Model"))
        modelInput = EditText(this).apply {
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA7B8.toInt())
            setBackgroundColor(0xFF243247.toInt())
            setPadding(14, 12, 14, 12)
        }
        root.addView(modelInput, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this).apply {
            text = "Save API Key & Model"
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                if (!key.startsWith("gsk_")) {
                    status.text = "សូមពិនិត្យ Groq API Key (ជាទូទៅចាប់ផ្ដើមដោយ gsk_)"
                } else if (modelInput.text.toString().trim().isEmpty()) {
                    status.text = "សូមបញ្ចូល Model name"
                } else {
                    getSharedPreferences("groq_pool_settings", MODE_PRIVATE).edit()
                        .putString("api_key", key)
                        .putString("model", modelInput.text.toString().trim())
                        .apply()
                    status.text = "បានរក្សាទុកក្នុងទូរស័ព្ទនេះ"
                }
            }
        }
        root.addView(save)

        val start = Button(this).apply {
            text = "Enable Floating Overlay"
            setOnClickListener { beginOverlaySetup() }
        }
        root.addView(start)

        status = label("ស្ថានភាព៖ រង់ចាំការកំណត់")
        root.addView(status)
        root.addView(label(
            "របៀបប្រើ៖ 1) ដាក់ Groq Key  2) Save  3) អនុញ្ញាត Display over other apps  4) អនុញ្ញាត Screen Capture  5) បើកហ្គេម ហើយចុច Analyze ក្នុងផ្ទាំងអណ្ដែត។",
            14f
        ))
        root.addView(label(
            "សុវត្ថិភាព៖ កុំចែករំលែក API Key។ Key ត្រូវបានរក្សាទុកក្នុង private app preferences លើឧបករណ៍នេះ។",
            13f
        ))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun beginOverlaySetup() {
        val key = keyInput.text.toString().trim()
        if (!key.startsWith("gsk_")) {
            status.text = "សូមបញ្ចូល និង Save Groq API Key ជាមុន"
            return
        }
        val prefs = getSharedPreferences("groq_pool_settings", MODE_PRIVATE)
        if (prefs.getString("api_key", "").isNullOrBlank()) {
            status.text = "ចុច Save API Key & Model ជាមុន"
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            status.text = "សូមអនុញ្ញាត Display over other apps"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, PoolOverlayService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }
            startForegroundService(service)
            status.text = "Floating Overlay កំពុងដំណើរការ"
        } else if (requestCode == captureRequest) {
            status.text = "អ្នកបានបោះបង់ Screen Capture"
        }
    }
}
