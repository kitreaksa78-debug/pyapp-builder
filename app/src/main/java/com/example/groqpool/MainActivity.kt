package com.example.groqpool

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.media.projection.MediaProjectionManager
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.Color
import android.text.InputType
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText

    companion object {
        const val REQ_SCREEN_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("GroqPoolPrefs", MODE_PRIVATE)

        // Build UI programmatically to guarantee clean layout without missing XML files
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Groq Pool AI • Manual Model Mode"
            textSize = 22f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "បញ្ចូល Groq API Key និងឈ្មោះ Model ដែលអ្នកចង់ប្រើប្រាស់ដោយផ្ទាល់។"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }
        layout.addView(desc)

        keyInput = EditText(this).apply {
            hint = "Groq API Key (gsk_...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            setText(prefs.getString("groq_key", ""))
        }
        layout.addView(keyInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) })

        val modelDesc = TextView(this).apply {
            text = "ឈ្មោះ Model (ឧ. meta-llama/llama-3.2-11b-vision-preview ឬ qwen/...):"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        }
        layout.addView(modelDesc)

        modelInput = EditText(this).apply {
            hint = "meta-llama/llama-3.2-11b-vision-preview"
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            setText(prefs.getString("groq_model", "meta-llama/llama-3.2-11b-vision-preview"))
        }
        layout.addView(modelInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 36) })

        val saveBtn = Button(this).apply {
            text = "SAVE SETTINGS"
            setBackgroundColor(Color.parseColor("#00796B"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                val model = modelInput.text.toString().trim()
                prefs.edit().putString("groq_key", key).putString("groq_model", model).apply()
                Toast.makeText(this@MainActivity, "បានរក្សាទុកដោយជោគជ័យ!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(saveBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) })

        val startBtn = Button(this).apply {
            text = "START SCREEN CAPTURE + OVERLAY"
            setBackgroundColor(Color.parseColor("#388E3C"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                val model = modelInput.text.toString().trim()
                if (key.isBlank()) {
                    Toast.makeText(this@MainActivity, "សូមបញ្ចូល Groq API Key ជាមុនសិន!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("groq_key", key).putString("groq_model", model).apply()
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), REQ_SCREEN_CAPTURE)
            }
        }
        layout.addView(startBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) })

        val stopBtn = Button(this).apply {
            text = "STOP OVERLAY"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureOverlayService::class.java))
                Toast.makeText(this@MainActivity, "បានបញ្ឈប់ Overlay", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(stopBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            val key = keyInput.text.toString().trim()
            val model = modelInput.text.toString().trim().ifBlank { "meta-llama/llama-3.2-11b-vision-preview" }
            val svcIntent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("apiKey", key)
                putExtra("modelName", model)
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION.SDK_INT) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            Toast.makeText(this, "កំពុងចាប់ផ្តើម Overlay...", Toast.LENGTH_SHORT).show()
        }
    }
}
